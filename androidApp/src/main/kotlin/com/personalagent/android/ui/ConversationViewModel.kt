package com.personalagent.android.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.personalagent.android.AppContainer
import com.personalagent.shared.agent.AgentIntent
import com.personalagent.shared.agent.IntentRouter
import com.personalagent.shared.cloud.CloudException
import com.personalagent.shared.cloud.CloudUnavailableException
import com.personalagent.shared.conversation.ConversationService
import com.personalagent.shared.util.SystemClock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** One line in the single conversational transcript. */
data class Message(
    val id: Long,
    val role: Role,
    val text: String,
) {
    enum class Role { USER, ASSISTANT, SYSTEM }
}

/** One chat thread in the drawer's history. Title is the first user message. */
data class ChatSession(
    val id: Long,
    val title: String,
    val messages: List<Message>,
)

/**
 * Drives the single conversational surface (UX Stream 1). The app no longer has
 * Notes/Reminders/Plan tabs; instead every typed turn is routed by the shared
 * [IntentRouter]:
 *
 *  - CreateNote / CreateReminder / AddPlanItem → invoke the existing [AppViewModel]
 *    actions (which persist via the shared store/reminder service) and append a
 *    SYSTEM confirmation. These capabilities are now invoked *behind the scenes*.
 *  - Ask → hand the raw text to [ConversationService.respond] and append the
 *    ASSISTANT reply. If no on-device model is installed the service returns an
 *    empty/short reply, which we replace with a friendly fallback rather than
 *    crashing.
 *
 * The transcript is the single source of truth for what the user sees; persistence
 * still lives entirely in the shared layer via [appVm].
 */
class ConversationViewModel(
    private val appVm: AppViewModel,
    private val conversationService: ConversationService,
) : ViewModel() {

    private var nextMessageId = 0L
    private var nextSessionId = 1L

    // Multi-chat: an in-memory list of chat sessions for this app run, plus which
    // one is current. "New chat" adds a session; the drawer lists them by title
    // (first user message). Persistence across restarts is intentionally not wired
    // yet — these live for the session, which is enough for the drawer/history UX.
    private val _sessions = MutableStateFlow(listOf(ChatSession(0L, NEW_CHAT_TITLE, emptyList())))
    val sessions: StateFlow<List<ChatSession>> = _sessions.asStateFlow()

    private val _currentId = MutableStateFlow(0L)
    val currentChatId: StateFlow<Long> = _currentId.asStateFlow()

    /** Messages of the currently-selected chat (what the transcript renders). */
    val messages: StateFlow<List<Message>> =
        combine(_sessions, _currentId) { sessions, id ->
            sessions.firstOrNull { it.id == id }?.messages ?: emptyList()
        }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val _sending = MutableStateFlow(false)
    val sending: StateFlow<Boolean> = _sending.asStateFlow()

    /** Append a message to a SPECIFIC session (so a reply lands in the chat that
     *  asked, even if the user has since switched chats). Updates the title from
     *  the first user turn. */
    private fun appendTo(sessionId: Long, role: Message.Role, text: String) {
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
    }

    private fun append(role: Message.Role, text: String) =
        appendTo(_currentId.value, role, text)

    /** Start a fresh chat (reusing the current one if it's still empty). */
    fun newChat() {
        val current = _sessions.value.firstOrNull { it.id == _currentId.value }
        if (current != null && current.messages.isEmpty()) return
        val id = nextSessionId++
        _sessions.update { it + ChatSession(id, NEW_CHAT_TITLE, emptyList()) }
        _currentId.value = id
    }

    /** Switch to a previously-started chat. */
    fun selectChat(id: Long) {
        if (_sessions.value.any { it.id == id }) _currentId.value = id
    }

    /**
     * Handle one user turn: echo it, route it, and either confirm a saved
     * capability or stream/return an AI reply.
     */
    fun send(input: String) {
        val text = input.trim()
        if (text.isEmpty() || _sending.value) return
        append(Message.Role.USER, text)

        when (val intent = IntentRouter.parse(text, SystemClock.nowMillis())) {
            is AgentIntent.CreateNote -> {
                appVm.addNote(intent.title, intent.body)
                val preview = intent.title.ifBlank { intent.body }
                append(Message.Role.SYSTEM, "Saved a note: $preview")
            }

            is AgentIntent.CreateReminder -> {
                val whenMillis = intent.whenMillisHint
                if (whenMillis != null) {
                    appVm.scheduleReminder(intent.text, whenMillis)
                    append(
                        Message.Role.SYSTEM,
                        "Reminder set for ${formatWhen(whenMillis)}: ${intent.text}",
                    )
                } else {
                    // No time we could parse — default to a useful soon-ish nudge and
                    // tell the user, rather than silently dropping it or guessing wildly.
                    val defaultAt = SystemClock.nowMillis() + DEFAULT_REMINDER_DELAY_MILLIS
                    appVm.scheduleReminder(intent.text, defaultAt)
                    append(
                        Message.Role.SYSTEM,
                        "I didn't catch a time, so I set a reminder for " +
                            "${formatWhen(defaultAt)}: ${intent.text}. " +
                            "Tell me \"in N minutes/hours\" to change it.",
                    )
                }
            }

            is AgentIntent.AddPlanItem -> {
                appVm.addPlanItem(intent.title)
                append(Message.Role.SYSTEM, "Added to your plan: ${intent.title}")
            }

            is AgentIntent.Ask -> ask(intent.text)
        }
    }

    /**
     * Generate an AI reply for [text] and append it — OR append a visible error
     * that NAMES the failing stage. The cardinal rule (the device bug was a
     * forever-spinner with no reply and no error): every send must resolve to a
     * reply or a rendered message. We never swallow an exception to "" or leave
     * the spinner up.
     *
     *  - [CloudUnavailableException] → no local model AND no API key configured.
     *  - [CloudException] → the cloud was reached but failed: timeout, bad key
     *    (API error 401), wrong model (404), rate limit (429), parse error. Its
     *    message already names the cause (e.g. "API error 401: invalid x-api-key").
     *  - any other Throwable → an on-device model error (e.g. inference failed).
     */
    private fun ask(text: String) {
        // Pin the reply to the chat that asked, even if the user switches mid-flight.
        val target = _currentId.value
        viewModelScope.launch {
            _sending.value = true
            try {
                val reply = conversationService.respond(text)
                if (reply.isBlank()) {
                    appendTo(target, Message.Role.ASSISTANT, EMPTY_REPLY_FALLBACK)
                } else {
                    appendTo(target, Message.Role.ASSISTANT, reply.trim())
                }
            } catch (e: CloudUnavailableException) {
                appendTo(target, Message.Role.ASSISTANT, MODEL_UNAVAILABLE_FALLBACK)
            } catch (e: CloudException) {
                appendTo(
                    target, Message.Role.ASSISTANT,
                    "I couldn't reach the cloud model: ${e.message ?: "unknown error"}. " +
                        "Check your connection and your API key in Settings.",
                )
            } catch (e: Throwable) {
                appendTo(
                    target, Message.Role.ASSISTANT,
                    "Something went wrong generating a reply: " +
                        "${e.message ?: e::class.simpleName ?: "unknown error"}. " +
                        "If you don't have an on-device model, add an API key in Settings.",
                )
            } finally {
                _sending.value = false
            }
        }
    }

    private fun formatWhen(triggerAtMillis: Long): String {
        val deltaMin = ((triggerAtMillis - SystemClock.nowMillis()).coerceAtLeast(0)) / 60_000L
        return when {
            deltaMin < 1 -> "in under a minute"
            deltaMin < 60 -> "in $deltaMin min"
            else -> "in ${deltaMin / 60} h ${deltaMin % 60} min"
        }
    }

    companion object {
        const val NEW_CHAT_TITLE = "New chat"
        private const val DEFAULT_REMINDER_DELAY_MILLIS = 60 * 60_000L // 1 hour
        const val MODEL_UNAVAILABLE_FALLBACK =
            "No on-device model installed, and no API key is set. " +
                "Open the menu → Settings to download a model or add an API key, and I'll be able to chat. " +
                "Notes, reminders, and plans still work right now."
        const val EMPTY_REPLY_FALLBACK =
            "I didn't get any text back that time. Please try again."
    }

    /** Factory: builds the conversation VM from the container + the shared app VM. */
    class Factory(
        private val container: AppContainer,
        private val appVm: AppViewModel,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            ConversationViewModel(appVm, container.conversationService) as T
    }
}
