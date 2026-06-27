package com.personalagent.android.llm

import android.content.Context
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import com.google.mediapipe.tasks.genai.llminference.LlmInferenceSession
import com.personalagent.shared.conversation.GenOptions
import com.personalagent.shared.conversation.OnDeviceLlm
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File

/**
 * Real, fully on-device small LLM for Android.
 *
 * Runtime: **MediaPipe LLM Inference API** (`com.google.mediapipe:tasks-genai`),
 * a self-contained native runtime (no Google Play Services, no server). It runs
 * a small instruction-tuned model shipped as a single `.task` bundle —
 * default **Gemma 3 1B (int4)** for footprint; Llama 3.2 3B is a drop-in
 * alternative for quality. The final pick is a measurement decision on the
 * target phone (latency / RAM / quality), so the model path is configurable and
 * the default is just a sensible starting point.
 *
 * Everything runs offline: the `.task` weights are loaded from a real file path
 * (see [LlmModelProvisioning]) and inference issues no network calls.
 *
 * Concurrency: the native [LlmInference] engine and its sessions are not safe to
 * use concurrently, so a [Mutex] serializes every generation. The engine is
 * created lazily on first use and reused; a fresh [LlmInferenceSession] is
 * created per request so each call gets its own [GenOptions.temperature].
 *
 * [GenOptions] handling:
 * - `temperature` → session sampling temperature.
 * - `maxTokens`   → enforced client-side by stopping after that many emitted
 *   chunks (the engine is created with a generous token capacity).
 * - `stop`        → enforced client-side: emission halts at the first stop
 *   sequence and the trailing stop text is trimmed.
 *
 * `generate` is implemented on top of `generateStream` so both paths share
 * identical maxTokens/stop semantics.
 */
class AndroidOnDeviceLlm internal constructor(
    private val context: Context,
    private val modelFile: File,
    private val maxTokenCapacity: Int = DEFAULT_MAX_TOKEN_CAPACITY,
    private val topK: Int = DEFAULT_TOP_K,
) : OnDeviceLlm {

    private val initMutex = Mutex()
    private val generateMutex = Mutex()

    @Volatile private var engine: LlmInference? = null

    override val isAvailable: Boolean
        get() = modelFile.exists() && modelFile.length() > 0L

    override suspend fun generate(prompt: String, options: GenOptions): String =
        // Reuse the streaming path so stop/maxTokens semantics are identical.
        generateStream(prompt, options).toList().joinToString(separator = "")

    override fun generateStream(prompt: String, options: GenOptions): Flow<String> = callbackFlow {
        // Serialize the whole generation: the native session is single-use and
        // the engine is not concurrent. Released in awaitClose.
        generateMutex.lock()

        val engine = try {
            ensureEngine()
        } catch (t: Throwable) {
            generateMutex.unlock()
            close(t)
            return@callbackFlow
        }

        val sessionOptions = LlmInferenceSession.LlmInferenceSessionOptions.builder()
            .setTopK(topK)
            .setTemperature(options.temperature)
            .build()

        val session = try {
            LlmInferenceSession.createFromOptions(engine, sessionOptions).also {
                it.addQueryChunk(prompt)
            }
        } catch (t: Throwable) {
            generateMutex.unlock()
            close(t)
            return@callbackFlow
        }

        // Tracks stop-sequence + maxTokens enforcement across streamed deltas.
        val gate = StreamGate(options)

        // Partial results from MediaPipe are incremental deltas; `done` marks the
        // final callback. We count callbacks as token steps for maxTokens.
        session.generateResponseAsync { partial, done ->
            if (gate.finished) return@generateResponseAsync
            val emit = gate.accept(partial, done)
            if (emit.isNotEmpty()) trySend(emit)
            if (gate.finished) close()
        }

        awaitClose {
            try {
                session.close()
            } catch (_: Throwable) {
                // best-effort cleanup
            } finally {
                generateMutex.unlock()
            }
        }
    }.flowOn(Dispatchers.Default)

    /** Lazily creates (once) the native inference engine from the model file. */
    private suspend fun ensureEngine(): LlmInference {
        engine?.let { return it }
        return initMutex.withLock {
            engine?.let { return@withLock it }
            check(isAvailable) {
                "LLM model not provisioned at ${modelFile.absolutePath} — see LlmModelProvisioning"
            }
            val options = LlmInference.LlmInferenceOptions.builder()
                .setModelPath(modelFile.absolutePath)
                .setMaxTokens(maxTokenCapacity)
                .build()
            LlmInference.createFromOptions(context.applicationContext, options).also { engine = it }
        }
    }

    /** Releases the native engine. Safe to call once when the app is done with it. */
    fun close() {
        engine?.close()
        engine = null
    }

    /**
     * Applies [GenOptions.maxTokens] and [GenOptions.stop] to a stream of
     * incremental deltas. Buffers the full text so a stop sequence spanning
     * multiple deltas is still caught; returns only the new, pre-stop text to
     * emit and flips [finished] once a limit is reached.
     */
    private class StreamGate(private val options: GenOptions) {
        private val full = StringBuilder()
        private var emitted = 0
        private var steps = 0
        var finished = false
            private set

        fun accept(delta: String, done: Boolean): String {
            if (finished) return ""
            full.append(delta)
            steps++

            val stopIdx = earliestStop(full, options.stop)
            val limit = if (stopIdx >= 0) stopIdx else full.length
            val out = if (limit > emitted) full.substring(emitted, limit) else ""
            emitted = maxOf(emitted, limit)

            val hitMax = options.maxTokens in 1..steps
            if (done || stopIdx >= 0 || hitMax) finished = true
            return out
        }

        /** Index of the earliest occurrence of any stop sequence, or -1. */
        private fun earliestStop(text: CharSequence, stops: List<String>): Int {
            var best = -1
            for (s in stops) {
                if (s.isEmpty()) continue
                val i = text.indexOf(s)
                if (i >= 0 && (best == -1 || i < best)) best = i
            }
            return best
        }
    }

    companion object {
        /**
         * Token budget the native engine is created with (prompt + output share
         * this KV-cache capacity). Per-request [GenOptions.maxTokens] is enforced
         * on top of this, client-side.
         */
        const val DEFAULT_MAX_TOKEN_CAPACITY = 1024

        /** Top-K sampling pool size; a sensible default for small instruct models. */
        const val DEFAULT_TOP_K = 40
    }
}
