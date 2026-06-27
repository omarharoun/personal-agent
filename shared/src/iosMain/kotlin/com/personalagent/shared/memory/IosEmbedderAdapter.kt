package com.personalagent.shared.memory

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Synchronous, Swift-facing seam for on-device embeddings.
 *
 * WHY a separate (non-`suspend`) interface, instead of letting Swift implement
 * [Embedder] directly (the way `IosReminderScheduler` implements
 * `ReminderScheduler` in Step 1):
 *
 *   [Embedder.embed] is a `suspend` function, and having Swift *implement* a
 *   Kotlin `suspend` function across the Kotlin/Native bridge is the most
 *   fragile corner of the interop (the supported, stable direction is Swift
 *   *calling* Kotlin suspend funcs as `async`). So Swift implements this plain
 *   synchronous interface, and [IosEmbedderAdapter] adapts it to the shared
 *   `suspend` [Embedder] contract — moving the CPU work off the caller's thread.
 *
 * Implemented in Swift by `IosEmbedder` (Apple NaturalLanguage NLEmbedding).
 * See `iosApp/iosApp/IosEmbedder.swift`.
 */
interface IosNativeEmbedder {
    /** Stable vector length; must not change for the lifetime of the instance. */
    val dimension: Int

    /** Synchronous embed — returns a [dimension]-length [FloatArray]. */
    fun embed(text: String): FloatArray
}

/**
 * Bridges a Swift [IosNativeEmbedder] to the shared [Embedder] used by the
 * memory layer. Embedding runs on [Dispatchers.Default] so native model
 * inference never blocks the main/UI thread.
 */
class IosEmbedderAdapter(
    private val native: IosNativeEmbedder,
) : Embedder {

    // Read once: the Swift side guarantees this is constant for the instance.
    override val dimension: Int = native.dimension

    override suspend fun embed(text: String): FloatArray =
        withContext(Dispatchers.Default) { native.embed(text) }
}
