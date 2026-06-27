package com.personalagent.shared.store

/**
 * The single, narrow seam between "what we store" and "how bytes hit the disk".
 *
 * 🔒 THIS IS THE STEP-5 ENCRYPTION SWAP POINT. 🔒
 * Everything above this interface (LocalStore, the app, tests) is encryption-
 * agnostic. To make data encrypted at rest we only replace the *implementation*
 * of this interface with one backed by an encrypted wallet / keystore — no
 * caller changes. See [InMemoryKeyValueStorage] and the platform file-backed
 * implementations, all of which are clearly marked as the placeholder.
 *
 * Keys are opaque strings; values are UTF-8 strings (JSON in practice).
 */
interface KeyValueStorage {
    fun get(key: String): String?
    fun put(key: String, value: String)
    fun remove(key: String)
    fun keys(): Set<String>
}

/**
 * Plaintext, process-local storage. Used by unit tests and as the common base
 * for behaviour. NOT for real user data on its own.
 *
 * // TODO Step 5: swap for encrypted wallet (this class is the placeholder).
 */
class InMemoryKeyValueStorage(
    initial: Map<String, String> = emptyMap(),
) : KeyValueStorage {
    private val map = LinkedHashMap<String, String>().apply { putAll(initial) }

    override fun get(key: String): String? = map[key]
    override fun put(key: String, value: String) { map[key] = value }
    override fun remove(key: String) { map.remove(key) }
    override fun keys(): Set<String> = map.keys.toSet()
}
