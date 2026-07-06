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
) : ViewModel() {

    data class State(
        val loading: Boolean = true,
        val connected: Boolean = false,
        val name: String? = null,
        val goalsLoading: Boolean = true,
        val goals: List<String> = emptyList(),
        val tasks: List<Task> = emptyList(),
        val memos: List<Memo> = emptyList(),
        val reminders: List<ReminderView> = emptyList(),
    )

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    init { refresh() }

    /** Reload everything. Local stores are read synchronously; network in the background. */
    fun refresh() {
        // Local, instant.
        _state.update {
            it.copy(
                name = profile.displayName(),
                tasks = tasks.all().filter { t -> !t.done },
                memos = memos.all(),
                reminders = upcomingReminders(),
                loading = false,
            )
        }
        // Network, background.
        viewModelScope.launch { loadConnection() }
        viewModelScope.launch { loadGoals() }
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

    private suspend fun loadGoals() {
        _state.update { it.copy(goalsLoading = true) }
        val summary = runCatching {
            hermes.complete(
                listOf(HermesWireMessage("user", LifePrompts.listGoals())),
                sessionId = "lifeagent-goals",
            )
        }.getOrNull()
        _state.update {
            it.copy(goalsLoading = false, goals = summary?.let(::parseGoals) ?: emptyList())
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
