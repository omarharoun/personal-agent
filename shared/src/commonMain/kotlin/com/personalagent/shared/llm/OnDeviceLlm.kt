package com.personalagent.shared.llm

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * Knobs for a single generation call. Defaults match the Step 3 brief.
 *
 * @property maxTokens hard cap on tokens to generate (keeps latency/memory bounded).
 * @property temperature 0f = greedy/deterministic, higher = more random sampling.
 * @property stop substrings that, once produced, end generation (exclusive).
 */
data class GenOptions(
    val maxTokens: Int = 512,
    val temperature: Float = 0.7f,
    val stop: List<String> = emptyList(),
)

/**
 * A small local LLM that runs **fully on-device with no network**.
 *
 * 🤝 SHARED CONTRACT — Step 3. The portable contract lives here in `:shared`; the
 * two platform siblings each provide a *real* on-device implementation bridged in
 * from native code:
 *   - Android: a quantized GGUF model via llama.cpp (JNI), or MediaPipe LLM.
 *   - iOS: a quantized model via **MLX Swift** (Metal / Apple-silicon).
 *
 * The same seam pattern as Step 1/2 is used on each side: native code implements a
 * *synchronous* platform seam, and a thin Kotlin adapter lifts it to this
 * `suspend` / [Flow] contract on `Dispatchers.Default`. Having native code
 * implement a Kotlin `suspend` function (or produce a [Flow]) directly is the
 * fragile corner of the interop, so we avoid it.
 */
interface OnDeviceLlm {
    /** True once the model weights are present on this device and loadable. */
    val isAvailable: Boolean

    /**
     * Generate a full completion for [prompt]. Suspends; runs off the caller's
     * thread in the platform adapter. Throws if the model is unavailable.
     */
    suspend fun generate(prompt: String, options: GenOptions = GenOptions()): String

    /**
     * Stream the completion token-by-token (each emission is the newest text
     * chunk, not the cumulative string). Cold: generation starts on collection.
     */
    fun generateStream(prompt: String, options: GenOptions = GenOptions()): Flow<String>
}

/**
 * Portable, dependency-free [OnDeviceLlm] used on targets with no on-device model
 * (the JVM target, CI, and as a safe default before weights are provisioned). It
 * reports unavailable and refuses to fabricate output — mirroring how
 * `HashingEmbedder` keeps the memory engine runnable without a real model, but
 * here there is no meaningful text to invent, so we fail loudly instead.
 */
class UnavailableOnDeviceLlm(
    private val reason: String = "No on-device LLM is provisioned on this target.",
) : OnDeviceLlm {
    override val isAvailable: Boolean = false

    override suspend fun generate(prompt: String, options: GenOptions): String =
        throw IllegalStateException(reason)

    override fun generateStream(prompt: String, options: GenOptions): Flow<String> =
        flow { throw IllegalStateException(reason) }
}
