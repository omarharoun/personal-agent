package com.personalagent.android.ui.voice

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import kotlinx.coroutines.launch

/**
 * Hold-to-record voice input backed by our OWN bundled, fully-offline engine
 * ([VoskEngine], Apache-2.0) — NOT the phone's Google offline speech pack.
 *
 * Privacy posture (CLAUDE.md — no external backend we control for user data): audio
 * is captured and transcribed entirely **on the device** by the bundled Vosk
 * engine; speech never leaves the phone. The only network touch is a one-time
 * download of the public ~40 MB language model on first use. Only the resulting
 * text is handed to [onFinal], which the composer sends to Hermes like typed text.
 *
 * Usage: press-and-hold the mic → [start]; release → [stop]. [state] drives the UI
 * (partial transcript, model-download progress, and error/status text). The visible
 * red-dot/timer indicator is driven by the composer's touch state, independent of
 * this engine, so a hold always gives immediate feedback.
 */
class VoiceController internal constructor(
    private val ensurePermissionThenStart: () -> Unit,
    private val stopFn: () -> Unit,
    val state: VoiceState,
) {
    fun start() = ensurePermissionThenStart()
    fun stop() = stopFn()
    fun clearError() { state.error = null }
}

class VoiceState {
    /** True while the engine is actively recognizing (mic open). */
    var listening by mutableStateOf(false)
        internal set
    /** Live partial transcript while recording. */
    var partial by mutableStateOf("")
        internal set
    /** Set when voice can't be used at all on this device. */
    var unavailable by mutableStateOf(false)
        internal set
    /** True while the one-time offline model is downloading/unpacking. */
    var downloading by mutableStateOf(false)
        internal set
    /** Model download progress, 0f‥1f (only meaningful while [downloading]). */
    var downloadProgress by mutableFloatStateOf(0f)
        internal set
    /** True once the model is downloaded AND loaded into memory. */
    var modelReady by mutableStateOf(false)
        internal set
    /**
     * User-facing status/error message (permission denied, model downloading,
     * failure, etc). Null while idle. The composer surfaces this so voice never
     * silently "does nothing".
     */
    var error by mutableStateOf<String?>(null)
        internal set
}

@Composable
fun rememberVoiceController(onFinal: (String) -> Unit): VoiceController {
    val context = LocalContext.current
    val state = remember { VoiceState() }
    val scope = rememberCoroutineScope()
    val engine = remember { VoskEngine(context) }
    val onFinalUpdated by rememberUpdatedState(onFinal)

    // If the model was already downloaded on a previous run, load it up front so the
    // first hold records immediately.
    LaunchedEffect(Unit) {
        if (engine.modelOnDisk() && engine.loadModel()) state.modelReady = true
    }
    DisposableEffect(Unit) { onDispose { engine.release() } }

    // Actually open the mic and stream recognition. Requires a loaded model +
    // granted permission (both ensured before this runs).
    val startListening = remember {
        {
            state.partial = ""
            state.error = null
            state.listening = true
            engine.start(
                onPartial = { state.partial = it },
                onFinal = { text ->
                    state.listening = false
                    state.partial = ""
                    if (text.isNotBlank()) onFinalUpdated(text)
                },
                onError = { msg ->
                    state.listening = false
                    state.error = msg
                },
            )
        }
    }

    // Ensure the offline model is present + loaded, then run [andThen]. On first use
    // this kicks off the ~40 MB download with progress; never a silent no-op.
    val ensureModelThen = remember {
        fn@{ andThen: () -> Unit ->
            if (state.modelReady) { andThen(); return@fn }
            if (state.downloading) return@fn
            state.downloading = true
            state.downloadProgress = 0f
            state.error = "Setting up offline voice (one-time ~40 MB download)…"
            scope.launch {
                val onDisk = engine.modelOnDisk() || engine.downloadModel { p -> state.downloadProgress = p }
                val loaded = onDisk && engine.loadModel()
                state.downloading = false
                if (loaded) {
                    state.modelReady = true
                    state.error = "Voice ready — press and hold to record."
                } else {
                    state.error = "Couldn't set up offline voice. Check your connection and try again."
                }
            }
        }
    }

    // Runtime RECORD_AUDIO permission — requested on the first hold. We do NOT
    // auto-start on grant (the finger is already lifted, so we'd record-after-
    // release). Instead we prompt the user to hold again; a denial gets a clear,
    // non-silent message.
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            state.error = "Mic ready — press and hold to record."
            // Warm up the model now so the next hold is instant.
            ensureModelThen {}
        } else {
            state.error = "Microphone access is needed for voice messages. Enable it in Settings."
        }
    }

    val ensurePermissionThenStart = remember {
        {
            val granted = ContextCompat.checkSelfPermission(
                context, Manifest.permission.RECORD_AUDIO,
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) {
                permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            } else {
                ensureModelThen { startListening() }
            }
        }
    }

    val stopFn = remember {
        {
            if (state.listening) engine.stop()
        }
    }

    return remember { VoiceController(ensurePermissionThenStart, { stopFn() }, state) }
}
