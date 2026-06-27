package com.personalagent.shared.memory

/**
 * Contract for turning text into a dense vector used for semantic memory recall.
 *
 * This is the agreed cross-platform seam between the shared memory layer
 * (`MemoryService` / vector index) and each platform's on-device embedding model:
 *   - Android: all-MiniLM-L6-v2 (Core ML/ONNX/TFLite), 384-dim.
 *   - iOS: Apple **NaturalLanguage** `NLEmbedding` — see iosApp `IosEmbedder`
 *     wrapped by [com.personalagent.shared.memory.IosEmbedderAdapter];
 *     Apple-defined dimension (typically 512 for English sentence embeddings).
 *
 * The two platforms MAY report different [dimension]s — that is acceptable for
 * v1 because the vector index is built and queried **per-device**; vectors are
 * never compared across platforms.
 *
 * [embed] is `suspend` because a real model does non-trivial CPU work that must
 * not block the UI thread.
 *
 * INTEGRATION NOTE (coordinator): this interface is the Step 2 contract supplied
 * verbatim by the brief. If the memory-layer change also introduces it, keep a
 * single copy — the definition is intentionally identical.
 */
interface Embedder {
    /** Length of every vector returned by [embed]; constant for the instance. */
    val dimension: Int

    /** Embed [text] into a [dimension]-length vector. Never returns empty. */
    suspend fun embed(text: String): FloatArray
}
