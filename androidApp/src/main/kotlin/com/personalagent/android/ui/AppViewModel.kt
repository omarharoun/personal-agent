package com.personalagent.android.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.personalagent.android.AppContainer
import com.personalagent.shared.model.Note
import com.personalagent.shared.model.PlanItem
import com.personalagent.shared.model.Reminder
import com.personalagent.shared.reminder.ReminderService
import com.personalagent.shared.reminder.ScheduleResult
import com.personalagent.shared.store.LocalStore
import com.personalagent.shared.util.SystemClock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class UiState(
    val notes: List<Note> = emptyList(),
    val reminders: List<Reminder> = emptyList(),
    val planItems: List<PlanItem> = emptyList(),
    val message: String? = null,
)

/**
 * Single screen-state holder for the app. All persistence + scheduling goes
 * through the shared [LocalStore] / [ReminderService] — the UI never touches
 * platform storage directly.
 */
class AppViewModel(
    private val store: LocalStore,
    private val reminderService: ReminderService,
) : ViewModel() {

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    init { refresh() }

    fun refresh() = viewModelScope.launch {
        _state.update {
            it.copy(
                notes = store.allNotes().sortedByDescending { n -> n.updatedAt },
                reminders = store.allReminders().sortedBy { r -> r.triggerAtMillis },
                planItems = store.allPlanItems(),
            )
        }
    }

    fun consumeMessage() = _state.update { it.copy(message = null) }

    // --- Notes ---
    fun addNote(title: String, body: String) = viewModelScope.launch {
        if (title.isBlank() && body.isBlank()) return@launch
        val now = SystemClock.nowMillis()
        store.upsertNote(Note.create(title.ifBlank { "Untitled" }, body, now))
        refresh()
    }

    fun editNote(note: Note, title: String, body: String) = viewModelScope.launch {
        store.upsertNote(note.edited(title.ifBlank { "Untitled" }, body, SystemClock.nowMillis()))
        refresh()
    }

    fun deleteNote(id: String) = viewModelScope.launch {
        store.deleteNote(id)
        refresh()
    }

    // --- Reminders ---
    fun scheduleReminder(title: String, triggerAtMillis: Long) = viewModelScope.launch {
        when (val r = reminderService.schedule(title, triggerAtMillis)) {
            is ScheduleResult.Scheduled ->
                _state.update { it.copy(message = "Reminder set") }
            is ScheduleResult.Rejected -> {
                val why = when (r.reason) {
                    ScheduleResult.Rejected.Reason.BLANK_TITLE -> "Enter a title"
                    ScheduleResult.Rejected.Reason.TRIGGER_IN_PAST -> "Pick a future time"
                }
                _state.update { it.copy(message = why) }
            }
        }
        refresh()
    }

    fun cancelReminder(id: String) = viewModelScope.launch {
        reminderService.cancel(id)
        refresh()
    }

    // --- Plan ---
    fun addPlanItem(title: String) = viewModelScope.launch {
        if (title.isBlank()) return@launch
        val order = (_state.value.planItems.maxOfOrNull { it.order } ?: 0) + 1
        store.upsertPlanItem(PlanItem.create(title, SystemClock.nowMillis(), order = order))
        refresh()
    }

    fun togglePlanItem(item: PlanItem) = viewModelScope.launch {
        store.upsertPlanItem(item.toggled())
        refresh()
    }

    fun deletePlanItem(id: String) = viewModelScope.launch {
        store.deletePlanItem(id)
        refresh()
    }

    /** Factory so Compose can build this VM from the app container. */
    class Factory(private val container: AppContainer) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            AppViewModel(container.store, container.reminderService) as T
    }
}
