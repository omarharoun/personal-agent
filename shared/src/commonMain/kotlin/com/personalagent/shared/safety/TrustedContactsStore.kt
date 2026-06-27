// 🔒 CRISIS-CRITICAL (Step 7) — consent-first only; autonomous action disabled; NOT-FOR-REAL-USERS until human + crisis-expert review.
package com.personalagent.shared.safety

import com.personalagent.shared.model.Ids
import com.personalagent.shared.store.KeyValueStorage
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/**
 * 🔒 SHARED CRISIS-SAFETY CONTRACT (Step 7) — see [CrisisModels] for the
 * ownership / reconcile-at-merge note (canonical copy lives in
 * `feat/step7-shared`).
 *
 * The user's hand-curated list of [TrustedContact]s. Every entry is added by the
 * user explicitly and up front (that act IS the consent); nothing is inferred
 * from the device's address book or messages. Removal is always available.
 *
 * `suspend` so the encrypted/disk-backed implementation does real I/O off the
 * main thread (and so it crosses the Swift bridge as `async`).
 */
interface TrustedContactsStore {
    suspend fun all(): List<TrustedContact>

    /**
     * Add a contact the user typed in the setup view. All params are required
     * (Kotlin default args don't cross the ObjC/Swift bridge). Returns the
     * created contact (with its generated id + [TrustedContact.addedAtMillis]).
     */
    suspend fun add(name: String, phoneNumber: String?, relation: String, nowMillis: Long): TrustedContact

    suspend fun remove(id: String)
}

/**
 * [TrustedContactsStore] backed by a [KeyValueStorage]. Storage-agnostic by
 * design: tests inject `InMemoryKeyValueStorage`; iOS injects the
 * `EncryptedKeyValueStorage` (Keychain + Secure Enclave + AES-GCM) via
 * `IosFactories`, so trusted contacts are encrypted at rest like every other
 * entity. Mirrors `PersistentLocalStore`'s read/mutate pattern.
 */
class PersistentTrustedContactsStore(
    private val storage: KeyValueStorage,
    private val json: Json = DEFAULT_JSON,
) : TrustedContactsStore {

    private val mutex = Mutex()
    private val serializer = ListSerializer(TrustedContact.serializer())

    override suspend fun all(): List<TrustedContact> = read()

    override suspend fun add(
        name: String,
        phoneNumber: String?,
        relation: String,
        nowMillis: Long,
    ): TrustedContact {
        val contact = TrustedContact(
            id = Ids.next(nowMillis),
            name = name.trim(),
            phoneNumber = phoneNumber?.trim()?.ifBlank { null },
            relation = relation.trim(),
            addedAtMillis = nowMillis,
        )
        mutex.withLock {
            val updated = read() + contact
            storage.put(KEY, json.encodeToString(serializer, updated))
        }
        return contact
    }

    override suspend fun remove(id: String) = mutex.withLock {
        val updated = read().filterNot { it.id == id }
        storage.put(KEY, json.encodeToString(serializer, updated))
    }

    private fun read(): List<TrustedContact> {
        val raw = storage.get(KEY) ?: return emptyList()
        return runCatching { json.decodeFromString(serializer, raw) }.getOrDefault(emptyList())
    }

    companion object {
        const val KEY = "trusted_contacts"

        val DEFAULT_JSON = Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
        }
    }
}
