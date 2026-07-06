package com.personalagent.android.ui.voice

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat

/**
 * Hold-to-record voice input backed by the device's own [SpeechRecognizer].
 *
 * Privacy posture (matches CLAUDE.md — no external backend we control): the audio
 * is captured and transcribed by the *device's* system speech recognizer; this app
 * never uploads audio anywhere. Only the resulting text is handed to [onFinal],
 * which the composer then sends to the user's Hermes exactly like typed text.
 *
 * Usage: press-and-hold the mic → [start]; release → [stop]. [state] drives the
 * recording UI (live partial transcript + a "listening" flag). If the microphone
 * permission isn't granted yet, [start] requests it and begins as soon as it's
 * granted.
 */
class VoiceController internal constructor(
    private val ensurePermissionThenStart: () -> Unit,
    private val stopFn: () -> Unit,
    val state: VoiceState,
) {
    fun start() = ensurePermissionThenStart()
    fun stop() = stopFn()
}

class VoiceState {
    var listening by mutableStateOf(false)
        internal set
    var partial by mutableStateOf("")
        internal set
    /** Set when speech recognition is unavailable on this device. */
    var unavailable by mutableStateOf(false)
        internal set
}

@Composable
fun rememberVoiceController(onFinal: (String) -> Unit): VoiceController {
    val context = LocalContext.current
    val state = remember { VoiceState() }

    // Recreated lazily; the system recognizer must be built/used on the main thread.
    val recognizer = remember {
        if (SpeechRecognizer.isRecognitionAvailable(context)) {
            SpeechRecognizer.createSpeechRecognizer(context)
        } else {
            state.unavailable = true
            null
        }
    }

    val startListening = remember {
        fn@{
            val rec = recognizer ?: run { state.unavailable = true; return@fn }
            state.partial = ""
            state.listening = true
            rec.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {}
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() {}
                override fun onError(error: Int) {
                    state.listening = false
                }
                override fun onPartialResults(partialResults: Bundle?) {
                    partialResults
                        ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        ?.firstOrNull()
                        ?.let { state.partial = it }
                }
                override fun onEvent(eventType: Int, params: Bundle?) {}
                override fun onResults(results: Bundle?) {
                    state.listening = false
                    val text = results
                        ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        ?.firstOrNull()
                        ?.trim()
                        .orEmpty()
                    state.partial = ""
                    if (text.isNotEmpty()) onFinal(text)
                }
            })
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
                putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
            }
            runCatching { rec.startListening(intent) }
                .onFailure { state.listening = false }
        }
    }

    // Runtime RECORD_AUDIO permission — request on first hold, then start.
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted -> if (granted) startListening() }

    val ensurePermissionThenStart = remember {
        {
            val granted = ContextCompat.checkSelfPermission(
                context, Manifest.permission.RECORD_AUDIO,
            ) == PackageManager.PERMISSION_GRANTED
            if (granted) startListening() else permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    val stopFn = remember {
        {
            if (state.listening) runCatching { recognizer?.stopListening() }
        }
    }

    return remember { VoiceController(ensurePermissionThenStart, { stopFn() }, state) }
}
