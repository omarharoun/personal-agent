package com.personalagent.android.ui.voice

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.vosk.LibVosk
import org.vosk.LogLevel
import org.vosk.Model
import org.vosk.Recognizer
import org.vosk.android.RecognitionListener
import org.vosk.android.SpeechService
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.ZipInputStream

/**
 * Our OWN fully-offline speech-to-text, backed by **Vosk** (Apache-2.0).
 *
 * Why Vosk (over whisper.cpp/whisper.tflite): the `com.alphacephei:vosk-android`
 * AAR ships a ready-to-use JNI engine (`libvosk.so`) plus a streaming
 * [SpeechService] that reads the mic through `AudioRecord` and emits partial +
 * final hypotheses — no NDK build, no manual tensor plumbing. whisper.cpp would
 * mean compiling native code and hand-rolling the audio loop; Vosk gives a solid
 * offline result with far less integration risk.
 *
 * Privacy (CLAUDE.md — no external backend we control for user data): all audio is
 * captured and transcribed **on the device** by our bundled engine. Nothing is
 * sent to Google or any cloud. The only network touch is a **one-time download of
 * the public ~40 MB language model** on first voice use (kept in app-private
 * storage); user speech never leaves the phone. This removes the previous reliance
 * on the phone's Google offline speech pack.
 *
 * Audio → engine → transcript path:
 *  1. [start] builds a [Recognizer] over the loaded [Model] at 16 kHz and hands it
 *     to a [SpeechService], which opens `AudioRecord` (16 kHz mono PCM16) and feeds
 *     frames to the recognizer on its own thread.
 *  2. Partial hypotheses stream to `onPartial`; on [stop] the recognizer flushes a
 *     final hypothesis to `onFinal` (the composer then sends that text to Hermes,
 *     exactly like typed text).
 */
class VoskEngine(context: Context) {

    private val appContext = context.applicationContext
    private var model: Model? = null
    private var speechService: SpeechService? = null

    /** Unzipped model directory in app-private storage. */
    private val modelDir = File(appContext.filesDir, MODEL_DIR)

    init {
        runCatching { LibVosk.setLogLevel(LogLevel.WARNINGS) }
    }

    /** True once the model files are present on disk (download completed earlier). */
    fun modelOnDisk(): Boolean = File(modelDir, "conf/mfcc.conf").exists()

    /** True once the model is loaded into memory and ready to recognize. */
    fun modelLoaded(): Boolean = model != null

    /**
     * Download the public Vosk model zip and unpack it into app-private storage.
     * [onProgress] reports 0f‥1f (download is 0‥0.9, unzip finishes to 1f). Safe to
     * call off the main thread; returns true when the model is on disk.
     */
    suspend fun downloadModel(onProgress: (Float) -> Unit): Boolean = withContext(Dispatchers.IO) {
        if (modelOnDisk()) return@withContext true
        val tmpZip = File(appContext.cacheDir, "$MODEL_DIR.zip")
        try {
            Log.d(TAG, "downloading model from $MODEL_URL")
            val conn = (URL(MODEL_URL).openConnection() as HttpURLConnection).apply {
                connectTimeout = 30_000
                readTimeout = 30_000
                instanceFollowRedirects = true
            }
            conn.connect()
            if (conn.responseCode !in 200..299) {
                Log.e(TAG, "model download HTTP ${conn.responseCode}")
                return@withContext false
            }
            val total = conn.contentLengthLong
            conn.inputStream.use { input ->
                tmpZip.outputStream().use { out ->
                    val buf = ByteArray(1 shl 16)
                    var read = 0L
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n)
                        read += n
                        if (total > 0) onProgress(0.9f * (read.toFloat() / total))
                    }
                }
            }
            Log.d(TAG, "downloaded ${tmpZip.length()} bytes; unzipping")
            unzipInto(tmpZip, appContext.filesDir)
            onProgress(1f)
            val ok = modelOnDisk()
            Log.d(TAG, "model on disk = $ok at ${modelDir.absolutePath}")
            ok
        } catch (t: Throwable) {
            Log.e(TAG, "model download/unzip failed", t)
            // Leave nothing half-written that would read as "ready".
            runCatching { modelDir.deleteRecursively() }
            false
        } finally {
            runCatching { tmpZip.delete() }
        }
    }

    /** Load the on-disk model into memory (heavy — call off the main thread). */
    suspend fun loadModel(): Boolean = withContext(Dispatchers.IO) {
        if (model != null) return@withContext true
        try {
            model = Model(modelDir.absolutePath)
            Log.d(TAG, "model loaded")
            true
        } catch (t: Throwable) {
            Log.e(TAG, "model load failed", t)
            false
        }
    }

    /**
     * Begin recording + streaming recognition. Callbacks arrive on the main thread.
     * Requires [loadModel] to have succeeded and RECORD_AUDIO to be granted.
     */
    fun start(
        onPartial: (String) -> Unit,
        onFinal: (String) -> Unit,
        onError: (String) -> Unit,
    ) {
        val m = model
        if (m == null) { onError("Voice model isn't ready yet."); return }
        try {
            val recognizer = Recognizer(m, SAMPLE_RATE)
            val service = SpeechService(recognizer, SAMPLE_RATE)
            speechService = service
            service.startListening(object : RecognitionListener {
                override fun onPartialResult(hypothesis: String?) {
                    onPartial(hypothesis.field("partial"))
                }
                override fun onResult(hypothesis: String?) {
                    // Segment result on a pause — treat as (interim) final text too.
                    val t = hypothesis.field("text")
                    if (t.isNotBlank()) onPartial(t)
                }
                override fun onFinalResult(hypothesis: String?) {
                    Log.d(TAG, "onFinalResult: $hypothesis")
                    onFinal(hypothesis.field("text"))
                }
                override fun onError(exception: Exception?) {
                    Log.e(TAG, "recognition error", exception)
                    onError(exception?.message ?: "Couldn't capture that — try again.")
                }
                override fun onTimeout() {}
            })
            Log.d(TAG, "SpeechService listening")
        } catch (t: Throwable) {
            Log.e(TAG, "failed to start recognition", t)
            onError("Couldn't start the microphone — try again.")
        }
    }

    /** Stop recording; the recognizer flushes a final hypothesis to `onFinal`. */
    fun stop() {
        speechService?.let {
            runCatching { it.stop() }
            it.shutdown()
        }
        speechService = null
    }

    fun release() {
        stop()
        runCatching { model?.close() }
        model = null
    }

    private fun String?.field(name: String): String =
        if (this.isNullOrBlank()) "" else runCatching { JSONObject(this).optString(name, "").trim() }.getOrDefault("")

    /** Unzip [zip] into [targetDir], guarding against zip-slip path traversal. */
    private fun unzipInto(zip: File, targetDir: File) {
        val canonicalTarget = targetDir.canonicalPath
        ZipInputStream(zip.inputStream().buffered()).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                val outFile = File(targetDir, entry.name)
                if (!outFile.canonicalPath.startsWith(canonicalTarget + File.separator)) {
                    throw SecurityException("Zip entry escapes target dir: ${entry.name}")
                }
                if (entry.isDirectory) {
                    outFile.mkdirs()
                } else {
                    outFile.parentFile?.mkdirs()
                    outFile.outputStream().use { zis.copyTo(it) }
                }
                zis.closeEntry()
                entry = zis.nextEntry
            }
        }
    }

    companion object {
        private const val TAG = "VoskEngine"
        private const val SAMPLE_RATE = 16000.0f
        // Small English model (~40 MB zipped). Public, downloaded once, cached.
        const val MODEL_DIR = "vosk-model-small-en-us-0.15"
        const val MODEL_URL = "https://alphacephei.com/vosk/models/vosk-model-small-en-us-0.15.zip"
    }
}
