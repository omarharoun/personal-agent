package com.personalagent.shared.llm

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.trySendBlocking
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext

/**
 * Synchronous, Swift-facing seam for the on-device LLM.
 *
 * WHY a separate (non-`suspend`, non-`Flow`) interface, instead of letting Swift
 * implement [OnDeviceLlm] directly:
 *
 *   [OnDeviceLlm.generate] is `suspend` and [OnDeviceLlm.generateStream] returns a
 *   [Flow]. Having Swift *implement* a Kotlin `suspend` function or *produce* a
 *   Kotlin `Flow` across the Kotlin/Native bridge is the most fragile corner of
 *   the interop (the supported, stable direction is Swift *calling* Kotlin
 *   suspend/Flow as `async`/`AsyncSequence`). So Swift implements this plain
 *   synchronous interface — full generation returns a `String`, streaming pushes
 *   chunks through a Kotlin callback it just *invokes* — and [IosLlmAdapter]
 *   adapts it to the shared contract, moving the CPU/Metal work off the caller's
 *   thread. This mirrors `IosNativeEmbedder`/`IosEmbedderAdapter` (Step 2) and the
 *   Step 1 reminder-scheduler seam.
 *
 * Implemented in Swift by `IosOnDeviceLlm` (MLX Swift). See
 * `iosApp/iosApp/IosOnDeviceLlm.swift`.
 */
interface IosNativeLlm {
    /** True once model weights are present on device and the model is loadable. */
    val isAvailable: Boolean

    /**
     * Blocking full generation. Called by the adapter on `Dispatchers.Default`,
     * never the main thread. Options are flattened to primitives so the call
     * crosses the ObjC/Swift bridge cleanly.
     */
    fun generate(prompt: String, maxTokens: Int, temperature: Float, stop: List<String>): String

    /**
     * Blocking streaming generation: invokes [onToken] for each newly produced
     * text chunk and returns once generation is complete (or a stop sequence /
     * [maxTokens] is hit). [onToken] is a Kotlin closure the Swift side simply
     * calls — the supported interop direction.
     */
    fun generateStream(
        prompt: String,
        maxTokens: Int,
        temperature: Float,
        stop: List<String>,
        onToken: (String) -> Unit,
    )
}

/**
 * Bridges a Swift [IosNativeLlm] to the shared [OnDeviceLlm]. Generation runs on
 * [Dispatchers.Default] so native model inference never blocks the main/UI thread.
 */
class IosLlmAdapter(
    private val native: IosNativeLlm,
) : OnDeviceLlm {

    override val isAvailable: Boolean
        get() = native.isAvailable

    override suspend fun generate(prompt: String, options: GenOptions): String =
        withContext(Dispatchers.Default) {
            native.generate(prompt, options.maxTokens, options.temperature, options.stop)
        }

    /**
     * The native call is synchronous and blocking: it runs on the `channelFlow`
     * producer coroutine (shifted to [Dispatchers.Default] via [flowOn]) and feeds
     * tokens into the channel with [trySendBlocking], which applies natural
     * back-pressure if a slow collector falls behind. [buffer] decouples
     * producer/collector cadence so token emission isn't gated by UI rendering.
     */
    override fun generateStream(prompt: String, options: GenOptions): Flow<String> =
        channelFlow {
            native.generateStream(
                prompt = prompt,
                maxTokens = options.maxTokens,
                temperature = options.temperature,
                stop = options.stop,
            ) { token ->
                trySendBlocking(token)
            }
            // Returns once Swift finishes generating; channelFlow then closes.
        }
            .buffer()
            .flowOn(Dispatchers.Default)
}
