package com.personalagent.android.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.personalagent.android.AppContainer
import com.personalagent.shared.hermes.HermesClient
import com.personalagent.shared.hermes.HermesException
import com.personalagent.shared.hermes.HermesWireMessage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Quick note capture — stored in the user's **Hermes memory**, server-side. The
 * app keeps NO second copy of note content (per the global rule): we send the
 * note to the agent to remember and show its confirmation. The small
 * [State.sessionCaptures] list is ephemeral UX feedback for the current screen
 * visit only (cleared when the app restarts), not a persistent store.
 */
class NotesViewModel(
    private val hermes: HermesClient,
) : ViewModel() {

    data class State(
        val saving: Boolean = false,
        val sessionCaptures: List<String> = emptyList(),
        val message: String? = null,
    )

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    fun saveNote(text: String) {
        val note = text.trim()
        if (note.isBlank() || _state.value.saving) return
        _state.update { it.copy(saving = true) }
        viewModelScope.launch {
            try {
                val reply = hermes.complete(
                    listOf(
                        HermesWireMessage(
                            role = "user",
                            content = "Please remember this note for me and store it in your memory: " +
                                "\"$note\". Reply with a short confirmation only.",
                        )
                    ),
                    sessionId = "lifeagent-notes",
                )
                _state.update {
                    it.copy(
                        saving = false,
                        sessionCaptures = listOf(note) + it.sessionCaptures,
                        message = reply.ifBlank { "Saved to your agent's memory." },
                    )
                }
            } catch (e: HermesException) {
                _state.update { it.copy(saving = false, message = e.message) }
            } catch (e: Throwable) {
                _state.update { it.copy(saving = false, message = e.message ?: "Couldn't save the note.") }
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
            return NotesViewModel(client) as T
        }
    }
}
