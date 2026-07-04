package com.personalagent.android.ui

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.personalagent.android.AppContainer
import com.personalagent.android.notification.ReminderScheduling
import com.personalagent.shared.hermes.HermesClient
import com.personalagent.shared.hermes.HermesException
import com.personalagent.shared.hermes.HermesJob
import com.personalagent.shared.hermes.oneShotScheduleMinutes
import com.personalagent.shared.util.SystemClock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Reminders backed by the user's Hermes (`/api/jobs`) — Hermes is the source of
 * truth. The app creates one-shot reminders here, lists the live jobs, and
 * cancels them; delivery is by polling + local notification (see
 * [ReminderScheduling] / ReminderPollWorker). The app keeps no second copy of the
 * reminder text.
 */
class RemindersViewModel(
    private val hermes: HermesClient,
    private val appContext: Context,
) : ViewModel() {

    data class State(
        val reminders: List<HermesJob> = emptyList(),
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
            try {
                val jobs = hermes.listJobs().sortedBy { it.nextRunAtMillis ?: Long.MAX_VALUE }
                _state.update { it.copy(reminders = jobs, loading = false) }
            } catch (e: HermesException) {
                _state.update { it.copy(loading = false, error = e.message) }
            } catch (e: Throwable) {
                _state.update { it.copy(loading = false, error = e.message ?: "Couldn't load reminders.") }
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
                // Punctual local poll at the due time + an immediate refresh poll.
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

    fun delete(id: String) {
        viewModelScope.launch {
            try {
                hermes.deleteJob(id)
                refresh()
            } catch (e: Throwable) {
                _state.update { it.copy(message = e.message ?: "Couldn't cancel the reminder.") }
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
            return RemindersViewModel(client, container.androidContext) as T
        }
    }
}
