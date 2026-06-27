package com.personalagent.shared.store

import com.personalagent.shared.model.MemoryEntry
import com.personalagent.shared.model.Note
import com.personalagent.shared.model.PlanItem
import com.personalagent.shared.model.Reminder
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/**
 * The Step-1 [LocalStore]. Serializes each entity collection to JSON and
 * persists it through a [KeyValueStorage].
 *
 * Persistence is entirely delegated to the injected [storage]:
 *   - tests inject [InMemoryKeyValueStorage]
 *   - Android injects a file/SharedPreferences-backed storage
 *   - iOS injects a file/NSUserDefaults-backed storage
 *
 * 🔒 ENCRYPTION IS NOT HERE BY DESIGN. The bytes are encrypted (or not) by the
 * [KeyValueStorage] implementation. // TODO Step 5: inject an encrypted-wallet
 * KeyValueStorage; this class stays unchanged.
 */
class PersistentLocalStore(
    private val storage: KeyValueStorage,
    private val json: Json = DEFAULT_JSON,
) : LocalStore {

    private val mutex = Mutex()

    // --- Notes ---
    override suspend fun upsertNote(note: Note) =
        mutate(KEY_NOTES, Note.serializer().listSerializer()) { upsertById(it, note) { n -> n.id } }

    override suspend fun deleteNote(id: String) =
        mutate(KEY_NOTES, Note.serializer().listSerializer()) { list -> list.filterNot { it.id == id } }

    override suspend fun getNote(id: String): Note? = allNotes().firstOrNull { it.id == id }

    override suspend fun allNotes(): List<Note> = read(KEY_NOTES, Note.serializer().listSerializer())

    // --- Reminders ---
    override suspend fun upsertReminder(reminder: Reminder) =
        mutate(KEY_REMINDERS, Reminder.serializer().listSerializer()) { upsertById(it, reminder) { r -> r.id } }

    override suspend fun deleteReminder(id: String) =
        mutate(KEY_REMINDERS, Reminder.serializer().listSerializer()) { list -> list.filterNot { it.id == id } }

    override suspend fun getReminder(id: String): Reminder? = allReminders().firstOrNull { it.id == id }

    override suspend fun allReminders(): List<Reminder> = read(KEY_REMINDERS, Reminder.serializer().listSerializer())

    // --- Plan items ---
    override suspend fun upsertPlanItem(item: PlanItem) =
        mutate(KEY_PLAN, PlanItem.serializer().listSerializer()) { upsertById(it, item) { p -> p.id } }

    override suspend fun deletePlanItem(id: String) =
        mutate(KEY_PLAN, PlanItem.serializer().listSerializer()) { list -> list.filterNot { it.id == id } }

    override suspend fun allPlanItems(): List<PlanItem> =
        read(KEY_PLAN, PlanItem.serializer().listSerializer()).sortedBy { it.order }

    // --- Memory entries ---
    override suspend fun upsertMemoryEntry(entry: MemoryEntry) =
        mutate(KEY_MEMORY, MemoryEntry.serializer().listSerializer()) { upsertById(it, entry) { m -> m.id } }

    override suspend fun deleteMemoryEntry(id: String) =
        mutate(KEY_MEMORY, MemoryEntry.serializer().listSerializer()) { list -> list.filterNot { it.id == id } }

    override suspend fun allMemoryEntries(): List<MemoryEntry> =
        read(KEY_MEMORY, MemoryEntry.serializer().listSerializer())

    // --- internals ---

    private fun <T> read(
        key: String,
        serializer: kotlinx.serialization.KSerializer<List<T>>,
    ): List<T> {
        val raw = storage.get(key) ?: return emptyList()
        return runCatching { json.decodeFromString(serializer, raw) }.getOrDefault(emptyList())
    }

    private suspend fun <T> mutate(
        key: String,
        serializer: kotlinx.serialization.KSerializer<List<T>>,
        transform: (List<T>) -> List<T>,
    ) = mutex.withLock {
        val current = read(key, serializer)
        val updated = transform(current)
        storage.put(key, json.encodeToString(serializer, updated))
    }

    private fun <T> upsertById(list: List<T>, item: T, id: (T) -> String): List<T> {
        val idx = list.indexOfFirst { id(it) == id(item) }
        return if (idx >= 0) list.toMutableList().also { it[idx] = item } else list + item
    }

    private fun <T> kotlinx.serialization.KSerializer<T>.listSerializer() = ListSerializer(this)

    companion object {
        const val KEY_NOTES = "notes"
        const val KEY_REMINDERS = "reminders"
        const val KEY_PLAN = "plan_items"
        const val KEY_MEMORY = "memory_entries"

        val DEFAULT_JSON = Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
        }
    }
}
