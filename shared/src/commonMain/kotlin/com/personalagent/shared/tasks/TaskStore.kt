package com.personalagent.shared.tasks

import com.personalagent.shared.store.KeyValueStorage
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/** A short, actionable to-do the user can check off. */
@Serializable
data class Task(
    val id: String,
    val text: String,
    val done: Boolean = false,
    val createdAt: Long,
    val completedAt: Long? = null,
)

/**
 * A lightweight to-do list stored LOCALLY on this device (sealed at rest).
 *
 * Tasks are deliberately device-local rather than Hermes memory: checking one off
 * should be instant and work offline, and a to-do is throwaway state, not
 * something the agent needs to reason about. **Reminders** (time + notification)
 * remain the Hermes-backed feature; tasks are the quiet checklist beside them.
 * The Tasks screen is explicit that these live on this device.
 */
class TaskStore(private val storage: KeyValueStorage) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val serializer = ListSerializer(Task.serializer())

    /** Newest first. */
    fun all(): List<Task> {
        val raw = storage.get(KEY) ?: return emptyList()
        return runCatching { json.decodeFromString(serializer, raw) }
            .getOrDefault(emptyList())
            .sortedByDescending { it.createdAt }
    }

    fun add(task: Task) = save(all() + task)

    fun setDone(id: String, done: Boolean, nowMillis: Long) = save(
        all().map {
            if (it.id == id) it.copy(done = done, completedAt = if (done) nowMillis else null) else it
        }
    )

    fun remove(id: String) = save(all().filterNot { it.id == id })

    private fun save(tasks: List<Task>) = storage.put(KEY, json.encodeToString(serializer, tasks))

    private companion object {
        const val KEY = "tasks"
    }
}
