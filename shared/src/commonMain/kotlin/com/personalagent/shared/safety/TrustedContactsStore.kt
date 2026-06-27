// 🔒 CRISIS-CRITICAL (Step 7) — autonomous action DISABLED; requires human + crisis-response-expert review; NOT-FOR-REAL-USERS.
package com.personalagent.shared.safety

import com.personalagent.shared.store.KeyValueStorage
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/**
 * Persists the user's [TrustedContact]s through the existing [KeyValueStorage]
 * seam — the SAME seam that Step 5 swaps for an encrypted-at-rest implementation.
 * This store adds no plaintext-specific behaviour: whether bytes are encrypted is
 * entirely the injected [storage]'s job (inject the encrypted store in the app;
 * tests inject the in-memory placeholder).
 *
 * 🔒 CONSENT-FIRST: a contact can only be stored *with consent captured up front*.
 * [add] requires a positive [TrustedContact.consentedAt] and rejects anything
 * else — there is deliberately no path to persist a contact without a consent
 * timestamp. Storing someone as a trusted contact is a meaningful step and must
 * be the user's explicit choice.
 */
class TrustedContactsStore(
    private val storage: KeyValueStorage,
    private val json: Json = DEFAULT_JSON,
) {
    private val mutex = Mutex()

    /**
     * Add (or update by id) a contact. Requires consent to have been captured:
     * [TrustedContact.consentedAt] must be > 0.
     * @throws IllegalArgumentException if consent was not captured.
     */
    suspend fun add(contact: TrustedContact) {
        require(contact.consentedAt > 0L) {
            "TrustedContact must carry an up-front consent timestamp (consentedAt > 0)."
        }
        mutex.withLock {
            val current = readAll()
            val idx = current.indexOfFirst { it.id == contact.id }
            val updated =
                if (idx >= 0) current.toMutableList().also { it[idx] = contact }
                else current + contact
            write(updated)
        }
    }

    suspend fun all(): List<TrustedContact> = mutex.withLock { readAll() }

    suspend fun get(id: String): TrustedContact? = mutex.withLock { readAll().firstOrNull { it.id == id } }

    suspend fun remove(id: String) = mutex.withLock {
        write(readAll().filterNot { it.id == id })
    }

    // --- internals ---

    private fun readAll(): List<TrustedContact> {
        val raw = storage.get(KEY_TRUSTED_CONTACTS) ?: return emptyList()
        return runCatching { json.decodeFromString(SERIALIZER, raw) }.getOrDefault(emptyList())
    }

    private fun write(contacts: List<TrustedContact>) {
        storage.put(KEY_TRUSTED_CONTACTS, json.encodeToString(SERIALIZER, contacts))
    }

    companion object {
        const val KEY_TRUSTED_CONTACTS = "trusted_contacts"

        private val SERIALIZER = ListSerializer(TrustedContact.serializer())

        val DEFAULT_JSON = Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
        }
    }
}
