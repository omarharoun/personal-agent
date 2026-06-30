package com.personalagent.shared.cloud

import com.personalagent.shared.conversation.ChatRole
import com.personalagent.shared.conversation.GenOptions

/** One message in a cloud request's messages array (content already anonymized). */
data class CloudMessage(val role: ChatRole, val content: String)

/**
 * A remote (cloud) language model used only when a turn is **escalated** off-device.
 *
 * 🤝 SHARED CONTRACT — Step 4. Three agents build to this EXACT package
 * (`com.personalagent.shared.cloud`) and these EXACT signatures:
 *   - `:shared` (this slice) defines the contract, the escalation policy, the
 *     orchestration wiring, and a test [FakeCloudClient] (test source).
 *   - sibling `feat/step4-anonymizer` provides the real [PayloadPrep] (strips
 *     identifying detail before anything leaves the device + rehydrates the answer).
 *   - sibling `feat/step4-cloud-transport` provides the real [CloudClient]
 *     (HTTP transport to a hosted model).
 *
 * Privacy invariant: a [CloudClient] only ever receives text that has already
 * been through [PayloadPrep.prepare] — i.e. the **anonymized** payload. It must
 * never be handed raw user text or a [RehydrationMap]. The orchestration in
 * `ConversationService` enforces this ordering.
 */
interface CloudClient {
    /** Stable, human-readable identifier for logs/telemetry (no secrets). */
    val name: String

    /** Complete [prompt] remotely under [options] and return the full answer. */
    suspend fun complete(prompt: String, options: GenOptions = GenOptions()): String

    /**
     * Complete a multi-turn conversation: [system] is the persona/system prompt
     * (or null) and [messages] is the alternating user/assistant history ending
     * with the current user message — all already anonymized. Returns the full
     * (still-tokenized) answer; the caller rehydrates it.
     *
     * Default implementation flattens to the last message and calls [complete],
     * so existing transports keep working until they override this.
     */
    suspend fun completeConversation(
        messages: List<CloudMessage>,
        system: String? = null,
        options: GenOptions = GenOptions(),
    ): String = complete(messages.lastOrNull()?.content ?: "", options)
}

/**
 * The default [CloudClient] wired into `ConversationService`: there is **no**
 * cloud transport until the sibling provides one, so any actual escalation with
 * this client fails loudly rather than silently leaking or hanging.
 *
 * This keeps the safe default "local-only": with the default
 * [com.personalagent.shared.cloud.LocalOnlyEscalationPolicy] nothing escalates,
 * so this is never invoked; if a caller injects an escalating policy but forgets
 * a real transport, they get a clear error instead of a network call to nowhere.
 */
object UnavailableCloudClient : CloudClient {
    override val name: String = "unavailable"

    override suspend fun complete(prompt: String, options: GenOptions): String =
        throw CloudUnavailableException(
            "No CloudClient is configured. Inject a real transport " +
                "(sibling feat/step4-cloud-transport) before escalating off-device.",
        )
}

/** Raised when escalation is requested but no usable [CloudClient] is wired in. */
class CloudUnavailableException(message: String) : IllegalStateException(message)

/**
 * A [CloudClient] that **re-resolves its real client on every call** via [resolve].
 *
 * 🔧 This is what makes a newly-saved BYO API key take effect IMMEDIATELY, with no
 * app restart. The app wires this once into [com.personalagent.shared.conversation
 * .ConversationService]; each escalation calls [resolve] (e.g.
 * `cloudKeyStore.activeCloudClient()`), so the current provider + key are read at
 * use time rather than frozen at container-construction time. If no provider/key
 * is configured, [resolve] returns null and this throws [CloudUnavailableException]
 * (the app stays on-device).
 *
 * [resolve] reads the key from the encrypted store on each use; the key is never
 * logged and [name] never includes it.
 */
class DynamicCloudClient(
    private val resolve: () -> CloudClient?,
) : CloudClient {
    override val name: String = "byo-key (resolved per use)"

    override suspend fun complete(prompt: String, options: GenOptions): String =
        resolved().complete(prompt, options)

    override suspend fun completeConversation(
        messages: List<CloudMessage>,
        system: String?,
        options: GenOptions,
    ): String = resolved().completeConversation(messages, system, options)

    private fun resolved(): CloudClient = resolve() ?: throw CloudUnavailableException(
        "No cloud provider/key is configured. Set one in Settings → Cloud assist.",
    )
}
