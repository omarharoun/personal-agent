package com.personalagent.android.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.personalagent.android.AppContainer
import com.personalagent.shared.hermes.ChatStreamEvent
import com.personalagent.shared.hermes.HermesClient
import com.personalagent.shared.hermes.HermesConfig
import com.personalagent.shared.hermes.HermesException
import com.personalagent.shared.hermes.HermesWireMessage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** One line in a conversation transcript. */
data class Message(
    val id: Long,
    val role: Role,
    val text: String,
) {
    enum class Role { USER, ASSISTANT, SYSTEM }
}

/**
 * One chat thread in the drawer's history. [conversationId] is the stable
 * `X-Hermes-Session-Id` for this thread — new chat → new id, so the server
 * threads short-term context per conversation while the app-wide
 * `X-Hermes-Session-Key` keeps long-term memory continuous across all of them.
 */
data class ChatSession(
    val id: Long,
    val title: String,
    val messages: List<Message>,
    val conversationId: String,
)

/**
 * Drives the chat surface, streaming replies from the user's **Hermes** agent
 * (Hermes is the brain — memory, notes, reminders, skills all run server-side).
 *
 * Every typed turn goes straight to `POST /v1/chat/completions` (SSE). We append
 * an empty assistant message and grow it as [ChatStreamEvent.Delta]s arrive, so
 * the reply renders token-by-token. On any failure the assistant bubble shows a
 * plain-language reason from [HermesException] — the cardinal rule from the old
 * on-device build still holds: every send resolves to a reply or a visible error,
 * never a forever-spinner.
 */
class ConversationViewModel(
    private val hermes: HermesClient,
) : ViewModel() {

    private var nextMessageId = 0L
    private var nextSessionId = 1L
    private var convSeq = 0L

    private fun newConversationId(): String = "lifeagent-conv-${SESSION_SEED}-${convSeq++}"

    private val _sessions = MutableStateFlow(
        listOf(ChatSession(0L, NEW_CHAT_TITLE, emptyList(), newConversationId()))
    )
    val sessions: StateFlow<List<ChatSession>> = _sessions.asStateFlow()

    private val _currentId = MutableStateFlow(0L)
    val currentChatId: StateFlow<Long> = _currentId.asStateFlow()

    val messages: StateFlow<List<Message>> =
        combine(_sessions, _currentId) { sessions, id ->
            sessions.firstOrNull { it.id == id }?.messages ?: emptyList()
        }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val _sending = MutableStateFlow(false)
    val sending: StateFlow<Boolean> = _sending.asStateFlow()

    /** Append a message to [sessionId]; returns the new message id. */
    private fun appendTo(sessionId: Long, role: Message.Role, text: String): Long {
        val mid = nextMessageId++
        _sessions.update { list ->
            list.map { sess ->
                if (sess.id != sessionId) sess
                else {
                    val title =
                        if (sess.title == NEW_CHAT_TITLE && role == Message.Role.USER)
                            text.take(48).trim()
                        else sess.title
                    sess.copy(messages = sess.messages + Message(mid, role, text), title = title)
                }
            }
        }
        return mid
    }

    /** Replace the text of message [messageId] in [sessionId] (streaming growth). */
    private fun setText(sessionId: Long, messageId: Long, text: String) {
        _sessions.update { list ->
            list.map { sess ->
                if (sess.id != sessionId) sess
                else sess.copy(messages = sess.messages.map { m ->
                    if (m.id == messageId) m.copy(text = text) else m
                })
            }
        }
    }

    private fun append(role: Message.Role, text: String) = appendTo(_currentId.value, role, text)

    /** Wire history for [sessionId]: user+assistant turns, oldest first. */
    private fun wireMessagesFor(sessionId: Long): List<HermesWireMessage> {
        val msgs = _sessions.value.firstOrNull { it.id == sessionId }?.messages ?: return emptyList()
        return msgs
            .filter { it.role == Message.Role.USER || it.role == Message.Role.ASSISTANT }
            .filter { it.text.isNotBlank() }
            .map {
                HermesWireMessage(
                    role = if (it.role == Message.Role.USER) "user" else "assistant",
                    content = it.text,
                )
            }
    }

    private fun conversationIdFor(sessionId: Long): String =
        _sessions.value.firstOrNull { it.id == sessionId }?.conversationId ?: newConversationId()

    fun newChat() {
        val current = _sessions.value.firstOrNull { it.id == _currentId.value }
        if (current != null && current.messages.isEmpty()) return
        val id = nextSessionId++
        _sessions.update { it + ChatSession(id, NEW_CHAT_TITLE, emptyList(), newConversationId()) }
        _currentId.value = id
    }

    fun selectChat(id: Long) {
        if (_sessions.value.any { it.id == id }) _currentId.value = id
    }

    /** Handle one user turn: echo it, then stream the agent's reply. */
    fun send(input: String) {
        val text = input.trim()
        if (text.isEmpty() || _sending.value) return
        val target = _currentId.value
        appendTo(target, Message.Role.USER, text)

        // Snapshot the wire history (includes the user turn just added) BEFORE the
        // empty assistant placeholder, so we don't send a blank assistant message.
        val wire = wireMessagesFor(target)
        val convId = conversationIdFor(target)
        val assistantId = appendTo(target, Message.Role.ASSISTANT, "")

        viewModelScope.launch {
            _sending.value = true
            val builder = StringBuilder()
            try {
                hermes.streamChat(wire, sessionId = convId)
                    .catch { e -> throw e }
                    .collect { event ->
                        when (event) {
                            is ChatStreamEvent.Delta -> {
                                builder.append(event.text)
                                setText(target, assistantId, builder.toString())
                            }
                            ChatStreamEvent.Done -> Unit
                        }
                    }
                if (builder.isBlank()) {
                    setText(target, assistantId, EMPTY_REPLY_FALLBACK)
                }
            } catch (e: HermesException) {
                setText(target, assistantId, e.message ?: GENERIC_ERROR)
            } catch (e: Throwable) {
                setText(target, assistantId, "$GENERIC_ERROR (${e.message ?: e::class.simpleName})")
            } finally {
                _sending.value = false
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        hermes.close()
    }

    companion object {
        const val NEW_CHAT_TITLE = "New chat"
        // A per-VM seed so conversation ids are unique across app runs without
        // needing a platform UUID in shared code.
        private val SESSION_SEED = kotlin.random.Random.nextInt(0, Int.MAX_VALUE).toString(16)
        const val EMPTY_REPLY_FALLBACK =
            "Hermes didn't send any text back. Check that your Hermes has a working model provider configured."
        const val GENERIC_ERROR =
            "Something went wrong talking to your Hermes."
    }

    /** Factory: builds the conversation VM with a live Hermes client from the container. */
    class Factory(private val container: AppContainer) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            val client = container.hermesClientOrNull()
                // Gated by the Connect screen, so this should never be null; fail
                // loudly rather than silently mis-wiring if it ever is.
                ?: error("Hermes is not configured — Connect screen should gate this.")
            return ConversationViewModel(client) as T
        }
    }
}
