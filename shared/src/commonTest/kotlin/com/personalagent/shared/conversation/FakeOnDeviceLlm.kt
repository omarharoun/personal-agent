package com.personalagent.shared.conversation

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * A deterministic, scriptable [OnDeviceLlm] for tests — NO model, NO network.
 *
 * It can either:
 *   - return a fixed canned [response] (default), or
 *   - compute the response from the prompt via [respondWith] (e.g. echo the
 *     prompt) so a test can assert what the orchestration actually sent.
 *
 * It also captures the last prompt and options it was called with, and counts
 * calls, so tests can verify prompt assembly and that the model was invoked.
 *
 * The streaming path splits the computed response on whitespace and emits each
 * piece as a chunk, exercising [ConversationService.respondStream] for real while
 * staying fully deterministic.
 */
class FakeOnDeviceLlm(
    override val isAvailable: Boolean = true,
    private val response: String = "ok",
    private val respondWith: ((prompt: String, options: GenOptions) -> String)? = null,
) : OnDeviceLlm {

    var lastPrompt: String? = null
        private set
    var lastOptions: GenOptions? = null
        private set
    var callCount: Int = 0
        private set

    /** Convenience factory: a fake that echoes the exact prompt it received. */
    companion object {
        fun echo(): FakeOnDeviceLlm = FakeOnDeviceLlm(respondWith = { prompt, _ -> prompt })
    }

    private fun reply(prompt: String, options: GenOptions): String {
        lastPrompt = prompt
        lastOptions = options
        callCount++
        return respondWith?.invoke(prompt, options) ?: response
    }

    override suspend fun generate(prompt: String, options: GenOptions): String =
        reply(prompt, options)

    override fun generateStream(prompt: String, options: GenOptions): Flow<String> = flow {
        val text = reply(prompt, options)
        // Emit whitespace-delimited pieces, preserving separators so the
        // concatenation of all chunks reconstructs the original text exactly.
        val chunks = Regex("\\S+\\s*").findAll(text).map { it.value }.toList()
        if (chunks.isEmpty()) {
            if (text.isNotEmpty()) emit(text)
        } else {
            for (c in chunks) emit(c)
        }
    }
}
