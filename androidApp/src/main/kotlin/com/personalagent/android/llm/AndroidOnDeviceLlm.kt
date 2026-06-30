package com.personalagent.android.llm

import android.content.Context
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import com.google.mediapipe.tasks.genai.llminference.LlmInferenceSession
import com.personalagent.shared.conversation.GenOptions
import com.personalagent.shared.conversation.OnDeviceLlm
import com.personalagent.shared.conversation.OutputSanitizer
import com.personalagent.shared.conversation.PromptBuilder
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
 * a self-contained native runtime (no Google Play Services, no server). It loads
 * a small instruction-tuned model shipped as a single **`.task` bundle**. The
 * curated [com.personalagent.shared.provisioning.DefaultModelCatalog] offers
 * ungated `.task` conversions (SmolLM 135M / Qwen2.5 0.5B / TinyLlama 1.1B from
 * Google's `litert-community`); whichever the user installs is resolved by
 * [LlmModelProvisioning.resolveModelFile]. (Raw GGUF files do NOT load here — the
 * runtime is `.task`-only, and the catalog is matched to that.)
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
    /**
     * Optional APK asset path of a BUNDLED `.task` model (e.g.
     * `models/llm/SmolLM-…task`). When set and no model is installed at
     * [modelFile] yet, it is materialized (copied) from assets into [modelFile]
     * on first use — so the on-device LLM works out of the box on a fresh install
     * with no download. Copy happens off the main thread (see [ensureEngine]).
     */
    private val bundledAssetPath: String? = null,
) : OnDeviceLlm {

    private val initMutex = Mutex()
    private val generateMutex = Mutex()

    @Volatile private var engine: LlmInference? = null

    override val isAvailable: Boolean
        get() = (modelFile.exists() && modelFile.length() > 0L) ||
            (bundledAssetPath != null && assetExists(bundledAssetPath))

    private fun assetExists(path: String): Boolean = try {
        context.assets.open(path).use { true }
    } catch (_: Throwable) {
        false
    }

    override suspend fun generate(prompt: String, options: GenOptions): String =
        // Reuse the streaming path so stop/maxTokens semantics are identical, then
        // sanitize any leaked chat-template/special tokens out of the visible text.
        OutputSanitizer.sanitize(
            generateStream(prompt, options).toList().joinToString(separator = ""),
        )

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

        // The bundled SmolLM-135M is a ChatML model: present the assembled prompt
        // as a ChatML user turn and OPEN an assistant turn, so the model continues
        // as the assistant instead of emitting raw <|im_start|>/<|im_end|> markers.
        val session = try {
            LlmInferenceSession.createFromOptions(engine, sessionOptions).also {
                it.addQueryChunk(toChatMl(prompt))
            }
        } catch (t: Throwable) {
            generateMutex.unlock()
            close(t)
            return@callbackFlow
        }

        // Register the model's turn-ending tokens as STOP sequences (in addition to
        // any caller-supplied ones) so generation halts cleanly on <|im_end|> /
        // <|endoftext|> and those tokens are trimmed off rather than streamed.
        val stopOptions = options.copy(stop = (options.stop + CHATML_STOP_SEQUENCES).distinct())
        // Tracks stop-sequence + maxTokens enforcement across streamed deltas.
        val gate = StreamGate(stopOptions)

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
            // If a bundled model asset is configured and nothing is installed yet,
            // materialize it now (runs on Dispatchers.Default — see generateStream).
            val resolved = materializeBundledIfNeeded()
            check(resolved.exists() && resolved.length() > 0L) {
                "LLM model not provisioned at ${resolved.absolutePath} — see LlmModelProvisioning"
            }
            val options = LlmInference.LlmInferenceOptions.builder()
                .setModelPath(resolved.absolutePath)
                .setMaxTokens(maxTokenCapacity)
                .build()
            LlmInference.createFromOptions(context.applicationContext, options).also { engine = it }
        }
    }

    /**
     * Copy the bundled `.task` asset into [modelFile] once, if needed. Uses a
     * temp file + atomic rename so a partial copy is never mistaken for a complete
     * model. No-op if a model is already present or no asset is bundled.
     */
    private fun materializeBundledIfNeeded(): File {
        if (modelFile.exists() && modelFile.length() > 0L) return modelFile
        val asset = bundledAssetPath ?: return modelFile
        modelFile.parentFile?.mkdirs()
        val tmp = File(modelFile.absolutePath + ".tmp")
        context.assets.open(asset).use { input ->
            tmp.outputStream().use { output -> input.copyTo(output, bufferSize = 1 shl 16) }
        }
        if (!tmp.renameTo(modelFile)) {
            tmp.copyTo(modelFile, overwrite = true)
            tmp.delete()
        }
        return modelFile
    }

    /**
     * Wrap the assembled prompt in ChatML for the bundled SmolLM (a ChatML model).
     *
     * The shared [PromptBuilder] already assembles persona + memory + the user turn
     * and ends with an `[Assistant]` cue. We present that whole assembly as one
     * ChatML *user* turn and open an *assistant* turn, so the model continues as the
     * assistant and ends with `<|im_end|>` (which we stop on + trim) instead of
     * leaking raw template markers. Idempotent-ish: if the prompt is already ChatML
     * (starts with `<|im_start|>`) it is passed through unchanged.
     */
    private fun toChatMl(prompt: String): String {
        if (prompt.trimStart().startsWith(IM_START)) return prompt
        val body = prompt.substringBeforeLast(PromptBuilder.SECTION_ASSISTANT).trimEnd()
        return buildString {
            append(IM_START).append("user\n").append(body).append(IM_END).append('\n')
            append(IM_START).append("assistant\n")
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

        // ChatML control tokens for the bundled SmolLM model.
        private const val IM_START = "<|im_start|>"
        private const val IM_END = "<|im_end|>"

        /**
         * Turn-ending / sentinel tokens registered as stop sequences for the local
         * model, so generation halts on them and they are trimmed from the output
         * (the [OutputSanitizer] is the second line of defence for anything that
         * still leaks through a `.task` bundle's baked-in template).
         */
        private val CHATML_STOP_SEQUENCES = listOf(IM_END, "<|endoftext|>", IM_START)
    }
}
