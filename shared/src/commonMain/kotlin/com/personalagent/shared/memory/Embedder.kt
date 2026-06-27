package com.personalagent.shared.memory

/**
 * Turns text into a fixed-length vector for semantic search.
 *
 * 🤝 SHARED CONTRACT — three agents build to this exact signature. The portable
 * pieces ([HashingEmbedder], MemoryService, the reference VectorIndex) live in
 * `:shared` and are owned by the `feat/step2-shared` branch. The two platform
 * siblings each provide a *real*, on-device implementation of THIS interface
 * (Android: [com.personalagent.android.embedding.AndroidEmbedder] — all-MiniLM-L6-v2
 * via ONNX Runtime; iOS: a CoreML/NaturalLanguage embedder).
 *
 * All implementations must be deterministic for a given input and must run
 * fully on-device with **no network**.
 *
 * ─────────────────────────────────────────────────────────────────────────────
 * ⚠️ INTEGRATION NOTE: This is a *minimal copy* of the contract so the Android
 * embeddings worktree compiles in isolation. The `feat/step2-shared` branch owns
 * the canonical version of this file (it also defines `HashingEmbedder` here).
 * On integration, take the shared branch's version of this file verbatim — the
 * `interface Embedder` signature is identical, so nothing in the Android impl
 * changes.
 * ─────────────────────────────────────────────────────────────────────────────
 */
interface Embedder {
    /** Length of every vector this embedder produces. Stable for its lifetime. */
    val dimension: Int

    /** Embed [text] into a [dimension]-length vector. */
    suspend fun embed(text: String): FloatArray
}
