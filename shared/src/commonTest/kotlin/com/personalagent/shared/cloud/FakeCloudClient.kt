package com.personalagent.shared.cloud

import com.personalagent.shared.conversation.GenOptions

/**
 * A deterministic, scriptable [CloudClient] for tests — NO network.
 *
 * It returns a canned [response] (or computes one from the prompt via
 * [respondWith]) and **captures every prompt it received**, so tests can assert
 * what the cloud actually saw. This is the hook for the privacy guarantee: once
 * the real anonymizer (sibling `feat/step4-anonymizer`) is wired in, tests can
 * assert that [receivedPrompts] never contains identifying detail.
 */
class FakeCloudClient(
    override val name: String = "fake-cloud",
    private val response: String = "cloud-answer",
    private val respondWith: ((prompt: String, options: GenOptions) -> String)? = null,
) : CloudClient {

    /** Every prompt this client was asked to complete, in order. */
    val receivedPrompts: MutableList<String> = mutableListOf()

    var lastPrompt: String? = null
        private set
    var lastOptions: GenOptions? = null
        private set
    var callCount: Int = 0
        private set

    override suspend fun complete(prompt: String, options: GenOptions): String {
        lastPrompt = prompt
        lastOptions = options
        callCount++
        receivedPrompts += prompt
        return respondWith?.invoke(prompt, options) ?: response
    }
}
