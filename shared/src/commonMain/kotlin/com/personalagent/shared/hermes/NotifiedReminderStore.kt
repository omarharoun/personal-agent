package com.personalagent.shared.hermes

import com.personalagent.shared.store.KeyValueStorage
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

/**
 * Remembers which reminder *firings* we've already raised a local notification
 * for, so polling never notifies the same reminder twice.
 *
 * It stores only opaque [HermesJob.fireKey]s (job-id @ run-time) — NOT the
 * reminder text — so no user content lands here. The list is capped so it can't
 * grow without bound as reminders come and go.
 */
class NotifiedReminderStore(
    private val storage: KeyValueStorage,
    private val cap: Int = 500,
) {
    private val json = Json { ignoreUnknownKeys = true }
    private val serializer = ListSerializer(String.serializer())

    fun all(): Set<String> {
        val raw = storage.get(KEY) ?: return emptySet()
        return runCatching { json.decodeFromString(serializer, raw).toSet() }.getOrDefault(emptySet())
    }

    /** Record [keys] as notified (idempotent), keeping only the newest [cap]. */
    fun markNotified(keys: Collection<String>) {
        if (keys.isEmpty()) return
        val merged = (all() + keys).toList().takeLast(cap)
        storage.put(KEY, json.encodeToString(serializer, merged))
    }

    /** Drop any recorded keys not in [validKeys] (reminders that no longer exist). */
    fun retainOnly(validKeys: Set<String>) {
        val kept = all().filter { it in validKeys }
        storage.put(KEY, json.encodeToString(serializer, kept))
    }

    private companion object {
        const val KEY = "notified_fire_keys"
    }
}
