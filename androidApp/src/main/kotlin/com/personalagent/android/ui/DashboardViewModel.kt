package com.personalagent.android.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.personalagent.android.AppContainer
import com.personalagent.shared.hermes.HermesClient
import com.personalagent.shared.hermes.HermesWireMessage
import com.personalagent.shared.hermes.LifePrompts
import com.personalagent.shared.hermes.ReminderHistory
import com.personalagent.shared.hermes.ReminderHistoryStore
import com.personalagent.shared.hermes.ReminderStatus
import com.personalagent.shared.hermes.ReminderView
import com.personalagent.shared.home.HomeCache
import com.personalagent.shared.home.HomeCacheStore
import com.personalagent.shared.notes.Memo
import com.personalagent.shared.notes.MemoStore
import com.personalagent.shared.profile.ProfileStore
import com.personalagent.shared.tasks.Task
import com.personalagent.shared.tasks.TaskStore
import com.personalagent.shared.util.SystemClock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * The home dashboard — a warm, personal "life OS" board rather than a technical
 * feed. It assembles a live PREVIEW for each of the four life areas from REAL
 * sources:
 *  - **Goals**  — the agent's own memory (`/v1/chat/completions` via [LifePrompts]).
 *  - **Tasks**  — the local device to-do store ([TaskStore]).
 *  - **Memos**  — the local index of notes saved to Hermes memory ([MemoStore]).
 *  - **Reminders** — the user's Hermes jobs (`/api/jobs`) merged with local history.
 *
 * The greeting name comes from the user's Settings value if set; otherwise we ask
 * the agent once ("what's my first name?") and cache a plausible answer. If neither
 * is known we greet with no name — never a fabricated one.
 */
class DashboardViewModel(
    private val hermes: HermesClient,
    private val profile: ProfileStore,
    private val tasks: TaskStore,
    private val memos: MemoStore,
    private val reminderHistory: ReminderHistoryStore,
    private val homeCache: HomeCacheStore,
) : ViewModel() {

    data class State(
        val loading: Boolean = true,
        val connected: Boolean = false,
        val name: String? = null,
        /** Full-screen loading for the Goals card — only on a first-ever fetch (empty cache). */
        val goalsLoading: Boolean = false,
        /** Subtle "refreshing" indicator — a background revalidate over cached goals. */
        val goalsRefreshing: Boolean = false,
        val goals: List<String> = emptyList(),
        val tasks: List<Task> = emptyList(),
        val memos: List<Memo> = emptyList(),
        val reminders: List<ReminderView> = emptyList(),
    )

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    init { refresh() }

    /**
     * Reload the home. Everything paints INSTANTLY from persistent local state —
     * tasks/memos/reminders from their own stores, goals from [HomeCacheStore] —
     * then a background revalidate updates the cards when fresh data arrives.
     *
     * @param force re-query the agent for goals even if the cache is still fresh
     *   (used by the manual "Refresh" action). Automatic reloads only re-query when
     *   the cached goals are stale, so the home never re-hits the agent every open.
     */
    fun refresh(force: Boolean = false) {
        // Local + cached, instant (no blocking spinner if a goals cache exists).
        val cache = homeCache.load()
        _state.update {
            it.copy(
                name = profile.displayName(),
                goals = cache.goals,
                tasks = tasks.all().filter { t -> !t.done },
                memos = memos.all(),
                reminders = upcomingReminders(),
                loading = false,
            )
        }
        // Network, background.
        viewModelScope.launch { loadConnection() }
        viewModelScope.launch { revalidateGoals(cache, force) }
        viewModelScope.launch { maybeDeriveName() }
    }

    /** Check off / un-check a task straight from the home card. */
    fun toggleTask(id: String, done: Boolean) {
        tasks.setDone(id, done, SystemClock.nowMillis())
        _state.update { it.copy(tasks = tasks.all().filter { t -> !t.done }) }
    }

    private suspend fun loadConnection() {
        val ok = runCatching { hermes.healthDetailed().isOk }.getOrDefault(false)
        _state.update { it.copy(connected = ok) }
    }

    /**
     * Stale-while-revalidate for the agent-derived goals. Serves cache immediately;
     * only queries the agent when [force] or the cache is stale/empty. Shows the
     * full loading state only on a first-ever fetch (empty cache), otherwise a
     * subtle refreshing indicator. A failed refresh keeps the cached goals.
     */
    private suspend fun revalidateGoals(cache: HomeCache, force: Boolean) {
        val now = SystemClock.nowMillis()
        val hasCache = cache.goals.isNotEmpty()
        if (!force && hasCache && !homeCache.goalsAreStale(cache, now)) return

        _state.update {
            it.copy(goalsLoading = !hasCache, goalsRefreshing = hasCache)
        }
        val summary = runCatching {
            hermes.complete(
                listOf(HermesWireMessage("user", LifePrompts.listGoals())),
                sessionId = "lifeagent-goals",
            )
        }.getOrNull()

        if (summary != null) {
            val goals = parseGoals(summary)
            homeCache.putGoals(goals, SystemClock.nowMillis())
            _state.update { it.copy(goals = goals, goalsLoading = false, goalsRefreshing = false) }
        } else {
            // Keep whatever we had cached; just drop the indicators.
            _state.update { it.copy(goalsLoading = false, goalsRefreshing = false) }
        }
    }

    /** Ask the agent for the user's name once, if we don't already have one. */
    private suspend fun maybeDeriveName() {
        if (profile.displayName() != null || profile.derivedAttempted()) return
        profile.markDerivedAttempted()
        val reply = runCatching {
            hermes.complete(
                listOf(
                    HermesWireMessage(
                        "user",
                        "What is my first name? Reply with only the name, or the single " +
                            "word UNKNOWN if you do not know.",
                    )
                ),
                sessionId = "lifeagent-profile",
            )
        }.getOrNull() ?: return
        plausibleFirstName(reply)?.let { name ->
            profile.setDerivedName(name)
            _state.update { it.copy(name = profile.displayName()) }
        }
    }

    private fun upcomingReminders(): List<ReminderView> {
        val now = SystemClock.nowMillis()
        return ReminderHistory.merge(emptyList(), reminderHistory.all(), now)
            .filter { it.status == ReminderStatus.UPCOMING || it.status == ReminderStatus.DUE_NOW }
    }

    override fun onCleared() {
        super.onCleared()
        hermes.close()
    }

    class Factory(private val container: AppContainer) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            val client: HermesClient = container.hermesClientOrNull()
                ?: error("Hermes is not configured — Connect screen should gate this.")
            return DashboardViewModel(
                hermes = client,
                profile = container.profileStore,
                tasks = container.taskStore,
                memos = container.memoStore,
                reminderHistory = container.reminderHistoryStore,
                homeCache = container.homeCacheStore,
            ) as T
        }
    }
}

// --- pure helpers (kept top-level for easy testing) -------------------------

/**
 * Pull the goal lines out of the agent's free-text reply — it returns a short
 * markdown bullet list (or a plain "no goals yet" sentence). We keep only the
 * bulleted/numbered items; if there are none we treat it as "no goals".
 */
internal fun parseGoals(summary: String): List<String> =
    summary.lineSequence()
        .map { it.trim() }
        .mapNotNull { line ->
            val m = Regex("""^([-*•]|\d+[.)])\s+(.+)$""").find(line) ?: return@mapNotNull null
            m.groupValues[2].trim().trim('*', '_', '`').trim().takeIf { it.isNotBlank() }
        }
        .toList()

/**
 * Accept a reply as a first name only if it looks like one: not "UNKNOWN", one or
 * two words, letters only. Returns the first token, or null. This keeps a chatty
 * or "I don't know" reply from ever becoming a fake greeting name.
 */
internal fun plausibleFirstName(raw: String): String? {
    val s = raw.trim().trim('.', '!', '"', '\'').trim()
    if (s.isEmpty() || s.equals("unknown", ignoreCase = true)) return null
    if (s.length > 40) return null
    val words = s.split(Regex("\\s+"))
    if (words.size > 2) return null
    if (!s.all { it.isLetter() || it == ' ' || it == '-' || it == '\'' }) return null
    return words.first()
}
