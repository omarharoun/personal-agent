package com.personalagent.shared.notes

import com.personalagent.shared.store.KeyValueStorage
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/** A saved memo, mirrored locally so the app can show it back. */
@Serializable
data class Memo(
    val id: String,
    val text: String,
    val savedAt: Long,
)

/**
 * A LOCAL index of the memos the user has saved.
 *
 * The **authoritative** copy of a note lives in the user's Hermes memory — that's
 * what the agent recalls in chat ("what notes have I saved about X?"). But Hermes
 * has no "list my notes" endpoint, so this device-local index exists purely so the
 * Notes screen and the home dashboard can SHOW recent memos back to the user.
 * Sealed at rest, capped, newest-first. Clearing it never touches Hermes memory.
 */
class MemoStore(
    private val storage: KeyValueStorage,
    private val cap: Int = 100,
) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val serializer = ListSerializer(Memo.serializer())

    /** Newest first. */
    fun all(): List<Memo> {
        val raw = storage.get(KEY) ?: return emptyList()
        return runCatching { json.decodeFromString(serializer, raw) }.getOrDefault(emptyList())
    }

    fun add(memo: Memo) {
        val merged = (listOf(memo) + all()).take(cap)
        storage.put(KEY, json.encodeToString(serializer, merged))
    }

    fun remove(id: String) =
        storage.put(KEY, json.encodeToString(serializer, all().filterNot { it.id == id }))

    private companion object {
        const val KEY = "memos"
    }
}
