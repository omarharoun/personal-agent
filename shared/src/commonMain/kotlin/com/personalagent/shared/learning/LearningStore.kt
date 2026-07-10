package com.personalagent.shared.learning

import com.personalagent.shared.store.KeyValueStorage
import kotlinx.serialization.json.Json

/**
 * The AUTHORITATIVE, device-local record of the user's learning journey — goals
 * and the resources recommended/started/finished/abandoned for each. Sealed at
 * rest (same encrypted [KeyValueStorage] as [com.personalagent.shared.tasks.TaskStore]
 * / [com.personalagent.shared.notes.MemoStore]); no database dependency.
 *
 * Hermes memory holds only the *current focus* (see
 * [com.personalagent.shared.hermes.LearningPrompts]) — this store is where the
 * full, adaptable history lives. Clearing it never touches Hermes memory.
 *
 * 🔒 Only the user's OWN state is stored here (goal, status, and the link/title/
 * one-sentence rationale the agent surfaced) — never fetched third-party article
 * bodies, never re-hosted content.
 */
class LearningStore(private val storage: KeyValueStorage) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    private fun load(): LearningState {
        val raw = storage.get(KEY) ?: return LearningState()
        return runCatching { json.decodeFromString(LearningState.serializer(), raw) }
            .getOrDefault(LearningState())
    }

    private fun save(state: LearningState) =
        storage.put(KEY, json.encodeToString(LearningState.serializer(), state))

    // --- Goals ----------------------------------------------------------------

    /** Active goals first, newest first within each group. */
    fun goals(): List<LearningGoal> =
        load().goals.sortedWith(compareByDescending<LearningGoal> { it.active }.thenByDescending { it.createdAt })

    fun activeGoals(): List<LearningGoal> = goals().filter { it.active }

    fun goal(id: String): LearningGoal? = load().goals.firstOrNull { it.id == id }

    fun addGoal(goal: LearningGoal) {
        val s = load()
        save(s.copy(goals = s.goals + goal))
    }

    /** Update the goal's remembered facts (level asked once, style if volunteered). */
    fun updateGoal(id: String, transform: (LearningGoal) -> LearningGoal) {
        val s = load()
        save(s.copy(goals = s.goals.map { if (it.id == id) transform(it) else it }))
    }

    /** Archive a goal (keeps its history) rather than deleting it. */
    fun setGoalActive(id: String, active: Boolean) = updateGoal(id) { it.copy(active = active) }

    fun removeGoal(id: String) {
        val s = load()
        save(s.copy(goals = s.goals.filterNot { it.id == id }, resources = s.resources.filterNot { it.goalId == id }))
    }

    // --- Resources ------------------------------------------------------------

    /** Resources for a goal, newest recommendation first. */
    fun resources(goalId: String): List<LearningResource> =
        load().resources.filter { it.goalId == goalId }.sortedByDescending { it.recommendedAt }

    /**
     * Add freshly-recommended resources for a goal, de-duplicated by URL against
     * what's already stored for that goal (so re-asking "what's next" doesn't
     * surface something already tracked). Returns the ones actually added.
     */
    fun addRecommendations(goalId: String, incoming: List<LearningResource>): List<LearningResource> {
        val s = load()
        val existingUrls = s.resources.filter { it.goalId == goalId }.map { it.url.normalizedUrl() }.toSet()
        val fresh = incoming.filter { it.url.normalizedUrl() !in existingUrls }
            .distinctBy { it.url.normalizedUrl() }
        if (fresh.isEmpty()) return emptyList()
        save(s.copy(resources = s.resources + fresh))
        return fresh
    }

    fun setStatus(resourceId: String, status: LearningStatus, nowMillis: Long) {
        val s = load()
        save(s.copy(resources = s.resources.map {
            if (it.id == resourceId) it.copy(status = status, updatedAt = nowMillis) else it
        }))
    }

    fun removeResource(resourceId: String) {
        val s = load()
        save(s.copy(resources = s.resources.filterNot { it.id == resourceId }))
    }

    private companion object {
        const val KEY = "learning"
    }
}

/** Loose URL normalization for de-dup: lowercase host+path, drop trailing slash/fragment. */
internal fun String.normalizedUrl(): String =
    trim().lowercase().substringBefore('#').trimEnd('/')
