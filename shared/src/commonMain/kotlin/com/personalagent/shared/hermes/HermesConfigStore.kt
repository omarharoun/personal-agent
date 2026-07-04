package com.personalagent.shared.hermes

import com.personalagent.shared.store.KeyValueStorage

/**
 * Persists the connection to the user's Hermes.
 *
 * 🔒 REVIEW REQUIRED — credential + session-key storage.
 * This store is constructed over an ENCRYPTED [KeyValueStorage] (Android
 * Keystore-sealed; see `AppContainer.encrypted`). The API key and the
 * `X-Hermes-Session-Key` are secrets: a leaked session key is a memory-access
 * scope and a leaked API key is server access. They are:
 *   - written only through the sealed [KeyValueStorage] (never plaintext prefs,
 *     never logs, excluded from cloud backups by the platform store),
 *   - never returned in any toString()/log path,
 *   - clearable via [disconnect].
 * A human must review this against the platform secure-store guarantees before
 * real users rely on it.
 *
 * The session key is minted ONCE on first connect and then kept stable for the
 * life of the install, so the agent remembers this user across conversations.
 * (v1 is single-user by decision — we do not promise multi-user isolation. See
 * docs/PHASE0.md.)
 */
class HermesConfigStore(
    private val storage: KeyValueStorage,
) {
    /** True once the user has completed the Connect flow at least once. */
    fun isConfigured(): Boolean =
        !storage.get(KEY_BASE_URL).isNullOrBlank() && !storage.get(KEY_API_KEY).isNullOrBlank()

    /** The stable per-user memory scope, minted + persisted on first read. */
    fun sessionKey(): String {
        storage.get(KEY_SESSION_KEY)?.takeIf { it.isNotBlank() }?.let { return it }
        val fresh = HermesConfig.newSessionKey()
        storage.put(KEY_SESSION_KEY, fresh)
        return fresh
    }

    /** Load the saved config, or null if not configured. */
    fun load(): HermesConfig? {
        val base = storage.get(KEY_BASE_URL)?.takeIf { it.isNotBlank() } ?: return null
        val key = storage.get(KEY_API_KEY)?.takeIf { it.isNotBlank() } ?: return null
        return HermesConfig(baseUrl = base, apiKey = key, sessionKey = sessionKey())
    }

    /**
     * Save a verified connection. [baseUrl] should already be normalized via
     * [HermesConfig.normalizeBaseUrl]; the session key is preserved (minted if
     * this is the first connect) so memory continuity survives reconnects.
     */
    fun save(baseUrl: String, apiKey: String): HermesConfig {
        storage.put(KEY_BASE_URL, baseUrl)
        storage.put(KEY_API_KEY, apiKey)
        return HermesConfig(baseUrl = baseUrl, apiKey = apiKey, sessionKey = sessionKey())
    }

    /**
     * Forget the connection: clears the base URL and API key. The session key is
     * KEPT by default so that reconnecting to the same Hermes still lands in the
     * same memory scope; pass [forgetMemoryScope] = true to fully reset identity.
     */
    fun disconnect(forgetMemoryScope: Boolean = false) {
        storage.remove(KEY_BASE_URL)
        storage.remove(KEY_API_KEY)
        if (forgetMemoryScope) storage.remove(KEY_SESSION_KEY)
    }

    private companion object {
        const val KEY_BASE_URL = "hermes.base_url"
        const val KEY_API_KEY = "hermes.api_key"
        const val KEY_SESSION_KEY = "hermes.session_key"
    }
}
