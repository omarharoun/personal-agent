package com.personalagent.shared.cloud

import com.personalagent.shared.store.KeyValueStorage
import io.ktor.client.engine.HttpClientEngine

/**
 * Per-provider API-key wallet + the user's currently-selected active provider,
 * for the **bring-your-own-key** cloud option (Stream 3).
 *
 * 🔒 Keys are persisted through the injected [KeyValueStorage], which in the app
 * is the ENCRYPTED store (`EncryptedKeyValueStorage`): values are sealed at rest.
 * This class NEVER logs a key and redacts it from [toString]. Callers must pass an
 * encrypted [storage] in production — never a plaintext one.
 *
 * Privacy invariant (unchanged): a cloud client is only reachable after
 * [PayloadPrep] anonymization AND only when the user has set a key here. With no
 * active key, [activeCloudClient] returns null and the app stays fully on-device.
 *
 * Billing note (surfaced in the UI): the stored key is the USER'S OWN developer
 * API key, billed separately by Anthropic / OpenAI. A consumer Claude Pro /
 * ChatGPT Plus subscription cannot be used here.
 */
class CloudKeyStore(
    private val storage: KeyValueStorage,
) {
    /** Persist (or overwrite) the API [key] for [provider]. Blank clears it. */
    fun setApiKey(provider: CloudProvider, key: String) {
        val trimmed = key.trim()
        if (trimmed.isEmpty()) {
            clearApiKey(provider)
        } else {
            storage.put(apiKeyKey(provider), trimmed)
        }
    }

    /** The stored API key for [provider], or null if none is set. */
    fun apiKey(provider: CloudProvider): String? =
        storage.get(apiKeyKey(provider))?.takeIf { it.isNotBlank() }

    /** Remove the stored API key for [provider]. */
    fun clearApiKey(provider: CloudProvider) {
        storage.remove(apiKeyKey(provider))
    }

    /** Whether [provider] currently has a usable (non-blank) key. */
    fun hasKey(provider: CloudProvider): Boolean = apiKey(provider) != null

    /** Record which provider the user has selected as active. */
    fun setActiveProvider(provider: CloudProvider) {
        storage.put(ACTIVE_PROVIDER_KEY, provider.name)
    }

    /** The user's selected active provider, or null if none chosen. */
    fun activeProvider(): CloudProvider? =
        CloudProvider.fromName(storage.get(ACTIVE_PROVIDER_KEY))

    /** Clear the active-provider selection (does not touch stored keys). */
    fun clearActiveProvider() {
        storage.remove(ACTIVE_PROVIDER_KEY)
    }

    /**
     * Build a configured [CloudClient] for the active provider **iff** an active
     * provider is selected AND it has a key; otherwise null (→ stay on-device).
     *
     * The transport (and its platform engine) is created via [engineFactory], so
     * tests can inject a `MockEngine` and production can pass null to let Ktor pick
     * the compiled-in engine. The key is read here and handed straight to the
     * client over TLS — it is never logged.
     *
     * @param engineFactory yields the Ktor engine to use (or null for the platform
     *   default). Defaults to null so app callers can simply call
     *   `activeCloudClient()`.
     */
    fun activeCloudClient(
        engineFactory: () -> HttpClientEngine? = { null },
    ): CloudClient? {
        val provider = activeProvider() ?: return null
        val key = apiKey(provider) ?: return null
        val config = CloudConfig(
            baseUrl = provider.defaultBaseUrl,
            model = provider.defaultModel,
            apiKey = key,
        )
        return HttpCloudClient(config, engine = engineFactory(), provider = provider)
    }

    private fun apiKeyKey(provider: CloudProvider): String = "$API_KEY_PREFIX${provider.name}"

    /** 🔒 Never reveal stored keys. */
    override fun toString(): String = "CloudKeyStore(keys=REDACTED)"

    private companion object {
        const val API_KEY_PREFIX = "cloud.apiKey."
        const val ACTIVE_PROVIDER_KEY = "cloud.activeProvider"
    }
}
