package com.personalagent.android.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.personalagent.android.AppContainer
import com.personalagent.shared.hermes.HermesClient
import com.personalagent.shared.hermes.HermesException
import com.personalagent.shared.hermes.RunEvent
import com.personalagent.shared.hermes.RunUsage
import com.personalagent.shared.hermes.SessionHydration
import com.personalagent.shared.hermes.ToolFinding
import com.personalagent.shared.hermes.WrittenDocument
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** One tool call as it appears in the live activity card. */
data class ToolActivity(
    val tool: String,
    val preview: String,
    val done: Boolean = false,
    val durationSec: Double? = null,
    val error: Boolean = false,
)

/**
 * Drives a live agent RUN: submit a task → `POST /v1/runs` → stream
 * `GET /v1/runs/{id}/events` and render tool activity live, then HYDRATE what it
 * found + any written document from `/api/sessions/{run_id}/messages`. Real data
 * only — the events show the calls, the transcript shows the results.
 */
class TaskRunViewModel(
    private val hermes: HermesClient,
) : ViewModel() {

    data class State(
        val task: String = "",
        val running: Boolean = false,
        val activities: List<ToolActivity> = emptyList(),
        val reasoning: String? = null,
        val answer: String = "",
        val usage: RunUsage? = null,
        val findings: List<ToolFinding> = emptyList(),
        val documents: List<WrittenDocument> = emptyList(),
        val approvalNote: String? = null,
        val error: String? = null,
    )

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    fun run(input: String) {
        val task = input.trim()
        if (task.isBlank() || _state.value.running) return
        _state.value = State(task = task, running = true)
        viewModelScope.launch {
            try {
                val started = hermes.startRun(task)
                val runId = started.runId
                hermes.runEvents(runId).collect { ev -> handle(ev) }
                // Stream ended — hydrate results + documents from the transcript.
                runCatching { hermes.sessionMessages(runId) }.getOrNull()?.let { msgs ->
                    _state.update {
                        it.copy(
                            findings = SessionHydration.findings(msgs),
                            documents = SessionHydration.documents(msgs),
                        )
                    }
                }
            } catch (e: HermesException) {
                _state.update { it.copy(error = e.message) }
            } catch (e: Throwable) {
                _state.update { it.copy(error = e.message ?: "The task failed.") }
            } finally {
                _state.update { it.copy(running = false) }
            }
        }
    }

    private fun handle(ev: RunEvent) = _state.update { s ->
        when (ev) {
            is RunEvent.ToolStarted ->
                s.copy(activities = s.activities + ToolActivity(ev.tool, ev.preview))
            is RunEvent.ToolCompleted -> {
                // Mark the last not-yet-done activity for this tool as complete.
                val idx = s.activities.indexOfLast { it.tool == ev.tool && !it.done }
                if (idx < 0) s else s.copy(
                    activities = s.activities.toMutableList().also {
                        it[idx] = it[idx].copy(done = true, durationSec = ev.durationSec, error = ev.error)
                    },
                )
            }
            is RunEvent.Reasoning -> s.copy(reasoning = ev.text)
            is RunEvent.Delta -> s.copy(answer = s.answer + ev.text)
            is RunEvent.Completed -> s.copy(
                answer = ev.output.ifBlank { s.answer },
                usage = ev.usage ?: s.usage,
            )
            is RunEvent.Failed -> s.copy(error = ev.message)
            is RunEvent.ApprovalRequested ->
                s.copy(approvalNote = "Paused: a tool needs approval (\"${ev.command}\"). Approvals aren't wired in this build.")
        }
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
            return TaskRunViewModel(client) as T
        }
    }
}
