package com.personalagent.android.ui

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.personalagent.android.AppContainer
import com.personalagent.android.notification.ReminderScheduling
import com.personalagent.shared.hermes.HermesClient
import com.personalagent.shared.hermes.HermesException
import com.personalagent.shared.hermes.ReminderHistory
import com.personalagent.shared.hermes.ReminderHistoryStore
import com.personalagent.shared.hermes.ReminderRecord
import com.personalagent.shared.hermes.ReminderView
import com.personalagent.shared.hermes.oneShotScheduleMinutes
import com.personalagent.shared.util.SystemClock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Reminders backed by the user's Hermes (`/api/jobs`), with a local history so
 * fired/past reminders stay visible with a clear status instead of vanishing.
 * Hermes is the source of truth for live jobs; the local [ReminderHistoryStore]
 * (non-sensitive metadata) preserves reminders after Hermes cleans up the one-shot.
 */
class RemindersViewModel(
    private val hermes: HermesClient,
    private val history: ReminderHistoryStore,
    private val appContext: Context,
) : ViewModel() {

    data class State(
        val reminders: List<ReminderView> = emptyList(),
        val loading: Boolean = true,
        val error: String? = null,
        val message: String? = null,
    )

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    init { refresh() }

    fun refresh() {
        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            val now = SystemClock.nowMillis()
            try {
                val live = hermes.listJobs()
                // Keep history in sync so these reminders survive server-side cleanup.
                live.forEach { j ->
                    history.upsert(ReminderRecord(j.id, j.label, j.nextRunAtMillis ?: now))
                }
                val views = ReminderHistory.merge(live, history.all(), now)
                _state.update { it.copy(reminders = views, loading = false, error = null) }
            } catch (e: Throwable) {
                // Even offline, still show history so the list never goes blank.
                val views = ReminderHistory.merge(emptyList(), history.all(), now)
                val msg = (e as? HermesException)?.message ?: e.message ?: "Couldn't reach Hermes."
                _state.update {
                    it.copy(reminders = views, loading = false, error = if (views.isEmpty()) msg else null)
                }
            }
        }
    }

    /** Create a one-shot reminder [title], firing [minutesFromNow] minutes out. */
    fun create(title: String, minutesFromNow: Long) {
        val text = title.trim()
        if (text.isBlank()) {
            _state.update { it.copy(message = "Enter what to be reminded about.") }
            return
        }
        val now = SystemClock.nowMillis()
        val targetMillis = now + minutesFromNow * 60_000L
        viewModelScope.launch {
            try {
                val job = hermes.createJob(
                    name = text,
                    schedule = oneShotScheduleMinutes(now, targetMillis),
                    prompt = "Remind the user: $text",
                )
                // Persist to history immediately so it survives server-side cleanup.
                history.upsert(ReminderRecord(job.id, text, job.nextRunAtMillis ?: targetMillis))
                job.nextRunAtMillis?.let { ReminderScheduling.pollAt(appContext, it + 5_000L, job.id) }
                ReminderScheduling.ensurePeriodic(appContext)
                _state.update { it.copy(message = "Reminder set") }
                refresh()
            } catch (e: HermesException) {
                _state.update { it.copy(message = e.message) }
            } catch (e: Throwable) {
                _state.update { it.copy(message = e.message ?: "Couldn't set the reminder.") }
            }
        }
    }

    /** Cancel a live reminder (server-side) or clear a past one from history. */
    fun dismiss(view: ReminderView) {
        viewModelScope.launch {
            try {
                if (view.live) runCatching { hermes.deleteJob(view.id) }
                history.remove(view.id)
                refresh()
            } catch (e: Throwable) {
                _state.update { it.copy(message = e.message ?: "Couldn't update the reminder.") }
            }
        }
    }

    fun consumeMessage() = _state.update { it.copy(message = null) }

    override fun onCleared() {
        super.onCleared()
        hermes.close()
    }

    class Factory(private val container: AppContainer) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            val client = container.hermesClientOrNull()
                ?: error("Hermes is not configured — Connect screen should gate this.")
            return RemindersViewModel(client, container.reminderHistoryStore, container.androidContext) as T
        }
    }
}
