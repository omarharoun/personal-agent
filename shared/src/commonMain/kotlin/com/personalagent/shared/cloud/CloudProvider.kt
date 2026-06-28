package com.personalagent.shared.cloud

/**
 * A bring-your-own-key cloud provider the user can opt into for off-device
 * escalation. Each entry carries the bits the transport needs to talk to that
 * provider's API: a human-readable [displayName], the [defaultBaseUrl] (TLS), and
 * a sensible [defaultModel]. The model is always **overridable** by the caller.
 *
 * 🔒 Privacy invariant (unchanged): a provider client is only ever reached AFTER
 * [PayloadPrep] anonymization, and only when the user has explicitly set a key.
 * The key itself is supplied at runtime from the ENCRYPTED [CloudKeyStore] —
 * never hardcoded, never logged.
 *
 * Billing note (surfaced in the Settings UIs): this uses the USER'S developer API
 * key, billed separately by Anthropic / OpenAI. A Claude Pro or ChatGPT Plus
 * consumer subscription CANNOT be used here.
 */
enum class CloudProvider(
    val displayName: String,
    val defaultBaseUrl: String,
    val defaultModel: String,
) {
    /** Anthropic Claude — `POST /v1/messages`, `x-api-key` + `anthropic-version`. */
    ANTHROPIC(
        displayName = "Anthropic (Claude)",
        defaultBaseUrl = "https://api.anthropic.com",
        defaultModel = "claude-3-5-sonnet-latest",
    ),

    /** OpenAI — `POST /v1/chat/completions`, `Authorization: Bearer`. */
    OPENAI(
        displayName = "OpenAI",
        defaultBaseUrl = "https://api.openai.com",
        defaultModel = "gpt-4o-mini",
    ),
    ;

    companion object {
        /** Parse a persisted enum name back to a provider, or null if unknown. */
        fun fromName(name: String?): CloudProvider? =
            name?.let { n -> entries.firstOrNull { it.name == n } }
    }
}
