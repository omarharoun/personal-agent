// 🔒 CRISIS-CRITICAL (Step 7) — consent-first only; autonomous action disabled; NOT-FOR-REAL-USERS until human + crisis-expert review.
package com.personalagent.shared.safety

import com.personalagent.shared.store.KeyValueStorage
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/**
 * 🔒 CRISIS-CRITICAL (Step 7). 🔒
 *
 * [TrustedContactsStore] backed by a [KeyValueStorage] — in production the
 * **encrypted** one, so the contacts are sealed at rest like all other user data.
 * Mirrors the JSON-list-under-one-key pattern of `PersistentLocalStore`.
 */
class PersistentTrustedContactsStore(
    private val storage: KeyValueStorage,
    private val json: Json = DEFAULT_JSON,
) : TrustedContactsStore {

    override fun all(): List<TrustedContact> {
        val raw = storage.get(KEY) ?: return emptyList()
        return runCatching { json.decodeFromString(SERIALIZER, raw) }.getOrDefault(emptyList())
    }

    override fun add(contact: TrustedContact) {
        val updated = all().filterNot { it.id == contact.id } + contact
        storage.put(KEY, json.encodeToString(SERIALIZER, updated))
    }

    override fun remove(id: String) {
        val updated = all().filterNot { it.id == id }
        storage.put(KEY, json.encodeToString(SERIALIZER, updated))
    }

    private companion object {
        const val KEY = "trusted_contacts"
        val SERIALIZER = ListSerializer(TrustedContact.serializer())
        val DEFAULT_JSON = Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
        }
    }
}
