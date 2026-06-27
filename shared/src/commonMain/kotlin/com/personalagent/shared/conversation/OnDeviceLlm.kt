package com.personalagent.shared.conversation

import kotlinx.coroutines.flow.Flow

/**
 * Decoding knobs for one generation call.
 *
 * 🤝 SHARED CONTRACT — all three agents build to this EXACT shape. The shared
 * orchestration constructs it; the two platform siblings (Android / iOS) consume
 * it when they drive their real on-device LLM. Defaults make `GenOptions()` a
 * sensible everyday-turn configuration.
 *
 * @param maxTokens hard cap on generated tokens (keeps local latency bounded).
 * @param temperature sampling temperature; lower = more deterministic.
 * @param stop optional stop sequences; generation halts before emitting any of them.
 */
data class GenOptions(
    val maxTokens: Int = 512,
    val temperature: Float = 0.7f,
    val stop: List<String> = emptyList(),
)

/**
 * A locally-runnable language model. **No network** — every implementation runs
 * fully on-device.
 *
 * 🤝 SHARED CONTRACT — three agents build to these EXACT signatures:
 *   - `:shared` provides the portable, testable [FakeOnDeviceLlm] (test source)
 *     so [ConversationService] is provable in CI with no model and no network.
 *   - the Android sibling provides a real implementation (e.g. a GGUF/llama.cpp
 *     or MediaPipe LLM Inference engine).
 *   - the iOS sibling provides a real implementation (e.g. an MLX / Core ML model).
 *
 * Nothing above this interface changes when the real models drop in.
 */
interface OnDeviceLlm {
    /**
     * Whether a usable model is loaded and ready right now. Implementations may
     * return false before a model file is downloaded/initialised, or on a device
     * that can't host one. [ConversationService] can branch on this.
     */
    val isAvailable: Boolean

    /** Generate a single complete response for [prompt] under [options]. */
    suspend fun generate(prompt: String, options: GenOptions = GenOptions()): String

    /**
     * Stream the response for [prompt] as incremental chunks (typically tokens or
     * small token groups). Concatenating every emitted chunk in order yields the
     * same logical text as [generate].
     */
    fun generateStream(prompt: String, options: GenOptions = GenOptions()): Flow<String>
}
