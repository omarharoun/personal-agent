package com.personalagent.android.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.personalagent.android.AppContainer
import com.personalagent.shared.hermes.HermesClient
import com.personalagent.shared.hermes.HermesException
import com.personalagent.shared.hermes.HermesWireMessage
import com.personalagent.shared.model.Ids
import com.personalagent.shared.notes.Memo
import com.personalagent.shared.notes.MemoStore
import com.personalagent.shared.util.SystemClock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Quick memo capture. The note's content is sent to the user's **Hermes memory**
 * (server-side) so the agent can recall it in chat. A small LOCAL index
 * ([MemoStore]) mirrors what was saved so the Notes screen and home can list
 * recent memos back — Hermes has no "list my notes" endpoint. The index is a
 * display convenience, sealed at rest; it is not a second authoritative store.
 */
class NotesViewModel(
    private val hermes: HermesClient,
    private val memos: MemoStore,
) : ViewModel() {

    data class State(
        val saving: Boolean = false,
        val recent: List<Memo> = emptyList(),
        val message: String? = null,
    )

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    init { refresh() }

    fun refresh() = _state.update { it.copy(recent = memos.all()) }

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
                val now = SystemClock.nowMillis()
                memos.add(Memo(id = Ids.next(now), text = note, savedAt = now))
                _state.update {
                    it.copy(
                        saving = false,
                        recent = memos.all(),
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

    /** Remove a memo from the LOCAL index only (Hermes memory is untouched). */
    fun forget(id: String) {
        memos.remove(id)
        _state.update { it.copy(recent = memos.all()) }
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
            return NotesViewModel(client, container.memoStore) as T
        }
    }
}
