package com.personalagent.shared.hermes

import com.personalagent.shared.store.KeyValueStorage
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/** Where a reminder is in its lifecycle (derived, for display). */
enum class ReminderStatus { UPCOMING, DUE_NOW, DONE }

/** A reminder as shown in the Reminders list — merged from Hermes + local history. */
data class ReminderView(
    val id: String,
    val text: String,
    val whenMillis: Long?,
    val status: ReminderStatus,
    /** True while Hermes still has the job (so it can be cancelled server-side). */
    val live: Boolean,
)

/**
 * A lightweight local record of a reminder so it stays visible as HISTORY even
 * after Hermes fires + removes the one-shot job from `/api/jobs`. This is
 * non-sensitive schedule metadata (id + short text + time), sealed at rest like
 * everything else — NOT a second copy of conversation content.
 */
@Serializable
data class ReminderRecord(
    val id: String,
    val text: String,
    val targetMillis: Long,
)

class ReminderHistoryStore(
    private val storage: KeyValueStorage,
    private val cap: Int = 200,
) {
    private val json = Json { ignoreUnknownKeys = true }
    private val serializer = ListSerializer(ReminderRecord.serializer())

    fun all(): List<ReminderRecord> {
        val raw = storage.get(KEY) ?: return emptyList()
        return runCatching { json.decodeFromString(serializer, raw) }.getOrDefault(emptyList())
    }

    /** Insert or update a record by id (keeps the newest [cap]). */
    fun upsert(record: ReminderRecord) {
        val merged = (all().filterNot { it.id == record.id } + record).takeLast(cap)
        storage.put(KEY, json.encodeToString(serializer, merged))
    }

    fun remove(id: String) {
        storage.put(KEY, json.encodeToString(serializer, all().filterNot { it.id == id }))
    }

    private companion object {
        const val KEY = "reminder_history"
    }
}

/**
 * Merge the live Hermes jobs with the local history into a single, status-tagged
 * list — so reminders never silently vanish when they fire and are cleaned up
 * server-side.
 */
object ReminderHistory {

    fun merge(
        liveJobs: List<HermesJob>,
        history: List<ReminderRecord>,
        nowMillis: Long,
    ): List<ReminderView> {
        val byId = LinkedHashMap<String, ReminderView>()

        // Start from local history (covers jobs Hermes has already cleaned up).
        history.forEach { r ->
            val status = if (r.targetMillis <= nowMillis) ReminderStatus.DONE else ReminderStatus.UPCOMING
            byId[r.id] = ReminderView(r.id, r.text, r.targetMillis, status, live = false)
        }

        // Live jobs override with fresher, authoritative status.
        liveJobs.forEach { j ->
            val runAt = j.nextRunAtMillis ?: byId[j.id]?.whenMillis
            val status = when {
                j.lastRunAt != null -> ReminderStatus.DONE
                runAt != null && runAt <= nowMillis -> ReminderStatus.DUE_NOW
                else -> ReminderStatus.UPCOMING
            }
            val text = j.label.takeIf { it.isNotBlank() && it != "Reminder" }
                ?: byId[j.id]?.text ?: j.label
            byId[j.id] = ReminderView(j.id, text, runAt, status, live = true)
        }

        // Upcoming (soonest first), then due-now, then done (most recent first).
        fun rank(s: ReminderStatus) = when (s) {
            ReminderStatus.UPCOMING -> 0
            ReminderStatus.DUE_NOW -> 1
            ReminderStatus.DONE -> 2
        }
        return byId.values.sortedWith(
            compareBy<ReminderView> { rank(it.status) }
                .thenBy { if (it.status == ReminderStatus.DONE) -(it.whenMillis ?: 0) else (it.whenMillis ?: Long.MAX_VALUE) }
        )
    }
}
