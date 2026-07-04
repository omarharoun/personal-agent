package com.personalagent.shared.hermes

import kotlin.random.Random

/**
 * Everything the app needs to talk to one user-owned Hermes instance.
 *
 * 🔒 TRUST BOUNDARY (REVIEW REQUIRED): the app trusts ONLY the instance the user
 * explicitly configures here. There is no default/hidden backend. [baseUrl] is a
 * server the *user* owns (their machine or their VPS); we never substitute one.
 *
 * 🔒 CREDENTIALS (REVIEW REQUIRED): [apiKey] and [sessionKey] are secrets. They
 * are persisted only through the platform secure store (Android Keystore-sealed
 * [com.personalagent.shared.store.KeyValueStorage]) and are never logged, echoed,
 * or written to plaintext/backups. See [HermesConfigStore].
 *
 * @param baseUrl the ROOT origin of the Hermes API server, e.g.
 *   `http://192.168.1.20:8642` (no trailing `/v1`). Normalize with [normalizeBaseUrl].
 *   Plain `http://` is permitted because a bring-your-own-Hermes runs on localhost
 *   or a LAN/VPN the user controls — but see [isPlaintextRemote] for the warning.
 * @param apiKey the `API_SERVER_KEY` the user set on their Hermes; sent as
 *   `Authorization: Bearer <apiKey>`.
 * @param sessionKey a STABLE per-user memory scope, `lifeagent:user-<id>` — sent
 *   as `X-Hermes-Session-Key`. This is what lets the agent remember this user
 *   across conversations. Generated once and persisted (see [newSessionKey]).
 */
data class HermesConfig(
    val baseUrl: String,
    val apiKey: String,
    val sessionKey: String,
) {
    val health: String get() = "$baseUrl/health"
    val capabilities: String get() = "$baseUrl/v1/capabilities"
    val models: String get() = "$baseUrl/v1/models"
    val chatCompletions: String get() = "$baseUrl/v1/chat/completions"
    val jobs: String get() = "$baseUrl/api/jobs"
    fun job(id: String): String = "$baseUrl/api/jobs/$id"

    /**
     * True when the base URL is plaintext `http://` AND not a loopback/private
     * address — i.e. a case where the API key and memory scope would cross a
     * network in the clear. The Connect screen warns on this (it does not block:
     * a VPN or trusted LAN is a legitimate Path-A setup, and the user owns both
     * ends).
     */
    val isPlaintextRemote: Boolean
        get() {
            val u = baseUrl.lowercase()
            if (!u.startsWith("http://")) return false
            val host = u.removePrefix("http://").substringBefore(':').substringBefore('/')
            val isLocal = host == "localhost" ||
                host == "127.0.0.1" ||
                host == "10.0.2.2" || // Android emulator → host loopback
                host.startsWith("10.") ||
                host.startsWith("192.168.") ||
                host == "0.0.0.0" ||
                (host.startsWith("172.") && run {
                    val second = host.removePrefix("172.").substringBefore('.').toIntOrNull()
                    second != null && second in 16..31
                })
            return !isLocal
        }

    companion object {
        /** The advertised model id the server routes on (Phase 0: `hermes-agent`). */
        const val DEFAULT_MODEL_ID = "hermes-agent"

        /**
         * Normalize user-entered base URL into a clean ROOT origin:
         *  - trims whitespace and trailing slashes,
         *  - adds `http://` if the user typed a bare host:port,
         *  - strips a trailing `/v1` (people paste the OpenAI base URL) so our
         *    endpoint builders append exactly one `/v1`.
         * Returns null if there's nothing usable.
         */
        fun normalizeBaseUrl(raw: String): String? {
            var s = raw.trim()
            if (s.isEmpty()) return null
            if (!s.startsWith("http://", true) && !s.startsWith("https://", true)) {
                s = "http://$s"
            }
            s = s.trimEnd('/')
            // Strip a trailing "/v1" (any case) — our builders re-append it.
            if (s.endsWith("/v1", ignoreCase = true)) s = s.dropLast(3).trimEnd('/')
            return s.ifEmpty { null }
        }

        /**
         * Mint a fresh stable memory-scope key: `lifeagent:user-<32 hex chars>`.
         * Generated ONCE per install and then persisted (see [HermesConfigStore]).
         * Well under the server's 256-char limit and free of control chars.
         */
        fun newSessionKey(random: Random = Random.Default): String {
            val hex = buildString(32) {
                repeat(16) {
                    val b = random.nextInt(256)
                    append("0123456789abcdef"[b ushr 4])
                    append("0123456789abcdef"[b and 0x0F])
                }
            }
            return "lifeagent:user-$hex"
        }
    }
}
