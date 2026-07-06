package com.personalagent.android.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.personalagent.android.AppContainer
import com.personalagent.shared.model.Ids
import com.personalagent.shared.tasks.Task
import com.personalagent.shared.tasks.TaskStore
import com.personalagent.shared.util.SystemClock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * The to-do list. Tasks are stored LOCALLY on this device (see [TaskStore]) — no
 * network round-trip to check one off. Reminders (time + notification) remain the
 * Hermes-backed feature; this is the quiet checklist beside them.
 */
class TasksViewModel(
    private val store: TaskStore,
) : ViewModel() {

    data class State(val tasks: List<Task> = emptyList())

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    init { refresh() }

    fun refresh() = _state.update { it.copy(tasks = store.all()) }

    fun add(text: String) {
        val t = text.trim()
        if (t.isBlank()) return
        val now = SystemClock.nowMillis()
        store.add(Task(id = Ids.next(now), text = t, createdAt = now))
        refresh()
    }

    fun toggle(id: String, done: Boolean) {
        store.setDone(id, done, SystemClock.nowMillis())
        refresh()
    }

    fun remove(id: String) {
        store.remove(id)
        refresh()
    }

    class Factory(private val container: AppContainer) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            TasksViewModel(container.taskStore) as T
    }
}
