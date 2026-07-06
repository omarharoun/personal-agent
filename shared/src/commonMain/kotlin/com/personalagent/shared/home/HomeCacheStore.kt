package com.personalagent.shared.home

import com.personalagent.shared.store.KeyValueStorage
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Persistent, stale-while-revalidate cache for the home dashboard's networked
 * content — the **Goals** card, whose list is derived by asking the user's agent
 * (`/v1/chat/completions`). Without this, the home re-queried the agent on every
 * appearance ("Loading…" each time), which is slow and wasteful.
 *
 * The other three cards (Tasks, Memos, Reminders) are already backed by their own
 * persistent local stores ([com.personalagent.shared.tasks.TaskStore],
 * [com.personalagent.shared.notes.MemoStore],
 * [com.personalagent.shared.hermes.ReminderHistoryStore]) and render instantly
 * from disk, so only the agent-derived goals need caching here.
 *
 * Policy: the home paints [goals] immediately from cache (no blocking spinner if a
 * cache exists), then refreshes the agent query in the background only when the
 * cache is older than [STALE_AFTER_MS] or the user forces a manual refresh; a
 * subtle refreshing indicator is shown instead of a full "Loading…". A first-ever
 * load (empty cache) is the only time the full loading state appears.
 *
 * Sealed at rest like every other store (the cached goal text is user memory).
 */
@Serializable
data class HomeCache(
    val goals: List<String> = emptyList(),
    val goalsUpdatedAt: Long = 0L,
)

class HomeCacheStore(
    private val storage: KeyValueStorage,
) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    /** The cached home snapshot, or an empty one if nothing has been cached yet. */
    fun load(): HomeCache {
        val raw = storage.get(KEY) ?: return HomeCache()
        return runCatching { json.decodeFromString(HomeCache.serializer(), raw) }
            .getOrDefault(HomeCache())
    }

    /** Persist a fresh goals list with its fetch time. */
    fun putGoals(goals: List<String>, nowMillis: Long) {
        val updated = load().copy(goals = goals, goalsUpdatedAt = nowMillis)
        storage.put(KEY, json.encodeToString(HomeCache.serializer(), updated))
    }

    /** True when the cached goals are missing or older than [STALE_AFTER_MS]. */
    fun goalsAreStale(cache: HomeCache, nowMillis: Long): Boolean =
        cache.goalsUpdatedAt == 0L || nowMillis - cache.goalsUpdatedAt >= STALE_AFTER_MS

    companion object {
        private const val KEY = "home_cache"

        /** Goals refresh cadence: re-ask the agent at most this often on home open. */
        const val STALE_AFTER_MS: Long = 6 * 60 * 60 * 1000L // 6 hours
    }
}
