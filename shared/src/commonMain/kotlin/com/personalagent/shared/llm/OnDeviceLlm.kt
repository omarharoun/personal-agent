package com.personalagent.shared.llm

import kotlinx.coroutines.flow.Flow

/**
 * Generation parameters for a single [OnDeviceLlm] request.
 *
 * @param maxTokens hard ceiling on tokens to generate for this request.
 * @param temperature sampling temperature; lower = more deterministic.
 * @param stop optional stop sequences — generation halts (and the trailing stop
 *   string is trimmed) as soon as any of these appears in the output.
 */
data class GenOptions(
    val maxTokens: Int = 512,
    val temperature: Float = 0.7f,
    val stop: List<String> = emptyList(),
)

/**
 * A small language model that runs **fully on-device, with no network** at
 * inference time.
 *
 * 🤝 SHARED CONTRACT — Step 3. The portable contract lives here in `:shared`; the
 * platform siblings each provide a *real* on-device implementation (Android: a
 * MediaPipe LLM Inference / `.task` model; iOS: an MLX / Core ML / llama.cpp
 * model). Nothing above this interface changes when a real runtime drops in.
 *
 * Implementations must:
 * - run entirely on-device (no inference-time network),
 * - serialize concurrent access (the underlying native engine is single-use),
 * - honor [GenOptions.maxTokens], [GenOptions.temperature] and
 *   [GenOptions.stop],
 * - keep [isAvailable] consistent with whether the model file is actually
 *   present, so the app can gate the feature without throwing.
 */
interface OnDeviceLlm {
    /**
     * True when the model is provisioned and the runtime can be created. When
     * false, [generate]/[generateStream] are not expected to work and callers
     * should gate the feature off.
     */
    val isAvailable: Boolean

    /** Generates a full completion for [prompt], awaiting the entire output. */
    suspend fun generate(prompt: String, options: GenOptions = GenOptions()): String

    /**
     * Streams the completion for [prompt] as incremental text chunks. The flow
     * completes when generation finishes (or a stop sequence / token limit is
     * hit) and fails if the model cannot run.
     */
    fun generateStream(prompt: String, options: GenOptions = GenOptions()): Flow<String>
}
