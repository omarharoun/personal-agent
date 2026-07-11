package com.personalagent.android.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.personalagent.android.AppContainer
import com.personalagent.shared.chat.ChatStore
import com.personalagent.shared.chat.StoredConversation
import com.personalagent.shared.chat.StoredMessage
import com.personalagent.shared.hermes.ChatStreamEvent
import com.personalagent.shared.hermes.HermesClient
import com.personalagent.shared.hermes.HermesConfig
import com.personalagent.shared.hermes.HermesException
import com.personalagent.shared.hermes.HermesWireMessage
import com.personalagent.shared.hermes.LifePrompts
import com.personalagent.shared.safety.CrisisLevel
import com.personalagent.shared.safety.CrisisRecognizer
import com.personalagent.shared.safety.CrisisResponder
import com.personalagent.shared.safety.CrisisResponse
import com.personalagent.shared.util.SystemClock
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
    /** Epoch millis the line was recorded (0 for legacy/unstamped). */
    val time: Long = 0L,
    /** Generative-UI: a natively-rendered composed view carried by this assistant turn. */
    val view: com.personalagent.shared.genui.ComposedView? = null,
    /** Generative-UI: true while the agent is composing this turn's view. */
    val composing: Boolean = false,
) {
    enum class Role { USER, ASSISTANT, SYSTEM }
}

/**
 * One chat thread in the drawer's history. [conversationId] is the stable
 * `X-Hermes-Session-Id` for this thread — new chat → new id, so the server
 * threads short-term context per conversation while the app-wide
 * `X-Hermes-Session-Key` keeps long-term memory continuous across all of them.
 *
 * [fromHermes] marks a thread surfaced from the server's `/api/sessions` list whose
 * messages may not be loaded yet ([hydrated] == false → fetch them on first open).
 */
data class ChatSession(
    val id: Long,
    val title: String,
    val messages: List<Message>,
    val conversationId: String,
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
    val fromHermes: Boolean = false,
    val hydrated: Boolean = true,
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
 *
 * **Persistence.** Threads and their messages are mirrored to a sealed-at-rest
 * [ChatStore] so history survives an app restart; on launch we rehydrate the local
 * history and, best-effort, merge any server-side sessions from `/api/sessions`
 * that aren't already on the device.
 */
class ConversationViewModel(
    private val hermes: HermesClient,
    private val chatStore: ChatStore,
    // 🔒 REVIEW REQUIRED — crisis handling (Gate 2). Consulted on each user turn to
    // OFFER a consent-first supportive surface; it never triggers any autonomous
    // action. Coarse + conservative by design (see KeywordCrisisRecognizer). Must
    // be reviewed by a crisis-response expert before real users rely on it.
    private val crisisRecognizer: CrisisRecognizer,
    private val crisisResponder: CrisisResponder,
    // Generative UI: the stores the honest facts are gathered from, + tap-action sinks.
    private val taskStore: com.personalagent.shared.tasks.TaskStore,
    private val localStore: com.personalagent.shared.store.LocalStore,
    private val learningStore: com.personalagent.shared.learning.LearningStore,
) : ViewModel() {

    /** Composition orchestrator on an isolated session id (never disturbs live chat). */
    private val genUi = com.personalagent.shared.genui.GenerativeUiService(hermes)

    /**
     * 🔒 A supportive [CrisisResponse] to surface (consent-first), or null. Set
     * when the recognizer flags possible distress in a user turn; the agent still
     * replies normally. The app contacts NO ONE automatically — the card only
     * offers resources + (on an explicit tap) opens the dialer to a trusted
     * contact the user pre-chose. Dismissable.
     */
    private val _activeCrisis = MutableStateFlow<CrisisResponse?>(null)
    val activeCrisis: StateFlow<CrisisResponse?> = _activeCrisis.asStateFlow()

    fun dismissCrisis() { _activeCrisis.value = null }

    private var nextMessageId = 0L
    private var nextSessionId = 1L
    private var convSeq = 0L

    private fun newConversationId(): String = "lifeagent-conv-${SESSION_SEED}-${convSeq++}"

    private val _sessions = MutableStateFlow<List<ChatSession>>(emptyList())
    val sessions: StateFlow<List<ChatSession>> = _sessions.asStateFlow()

    private val _currentId = MutableStateFlow(0L)
    val currentChatId: StateFlow<Long> = _currentId.asStateFlow()

    val messages: StateFlow<List<Message>> =
        combine(_sessions, _currentId) { sessions, id ->
            sessions.firstOrNull { it.id == id }?.messages ?: emptyList()
        }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val _sending = MutableStateFlow(false)
    val sending: StateFlow<Boolean> = _sending.asStateFlow()

    init {
        // Rehydrate the on-device history so past chats survive an app restart,
        // then open onto a fresh "New chat" (history stays reachable in the drawer
        // / History screen).
        val restored = chatStore.all().map { it.toSession() }
        nextSessionId = (restored.maxOfOrNull { it.id } ?: 0L) + 1
        nextMessageId = (restored.flatMap { it.messages }.maxOfOrNull { it.id } ?: -1L) + 1
        val fresh = ChatSession(
            id = nextSessionId++,
            title = NEW_CHAT_TITLE,
            messages = emptyList(),
            conversationId = newConversationId(),
            createdAt = SystemClock.nowMillis(),
            updatedAt = SystemClock.nowMillis(),
        )
        _sessions.value = restored + fresh
        _currentId.value = fresh.id

        // Best-effort: surface server-side conversations we don't have locally.
        hydrateFromHermes()
    }

    /** Append a message to [sessionId]; returns the new message id. */
    private fun appendTo(sessionId: Long, role: Message.Role, text: String): Long {
        val mid = nextMessageId++
        val now = SystemClock.nowMillis()
        _sessions.update { list ->
            list.map { sess ->
                if (sess.id != sessionId) sess
                else {
                    val title =
                        if (sess.title == NEW_CHAT_TITLE && role == Message.Role.USER)
                            text.take(48).trim().ifBlank { NEW_CHAT_TITLE }
                        else sess.title
                    sess.copy(
                        messages = sess.messages + Message(mid, role, text, now),
                        title = title,
                        updatedAt = now,
                    )
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

    /** Persist one thread to the sealed-at-rest store (no-op for an empty thread). */
    private fun persist(sessionId: Long) {
        val sess = _sessions.value.firstOrNull { it.id == sessionId } ?: return
        if (sess.messages.isEmpty()) return
        chatStore.upsert(sess.toStored())
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
        val now = SystemClock.nowMillis()
        val id = nextSessionId++
        _sessions.update { it + ChatSession(id, NEW_CHAT_TITLE, emptyList(), newConversationId(), now, now) }
        _currentId.value = id
    }

    fun selectChat(id: Long) {
        val sess = _sessions.value.firstOrNull { it.id == id } ?: return
        _currentId.value = id
        // A history entry hydrated from Hermes may not have its messages yet — pull
        // the transcript in on first open, then persist it locally.
        if (sess.fromHermes && !sess.hydrated) hydrateMessages(sess)
    }

    /** Delete a thread from the in-memory list and the on-device store. */
    fun deleteChat(id: Long) {
        chatStore.remove(id)
        _sessions.update { it.filterNot { s -> s.id == id } }
        if (_currentId.value == id) {
            // Fall back to the newest remaining non-empty thread, else a fresh one.
            val next = _sessions.value.lastOrNull()
            if (next != null) _currentId.value = next.id else newChat()
        }
    }

    /** Handle one user turn: echo it, then stream the agent's reply. */
    fun send(input: String) {
        val text = input.trim()
        if (text.isEmpty() || _sending.value) return
        val target = _currentId.value
        appendTo(target, Message.Role.USER, text)
        persist(target) // save the user turn immediately so nothing is lost mid-reply

        // 🔒 REVIEW REQUIRED (Gate 2) — consult the conservative crisis recognizer
        // on this turn. A hit only OFFERS support (message + resources + a
        // consent-based, user-tapped reach-out); it never acts autonomously and
        // never blocks the agent's own reply below.
        val assessment = crisisRecognizer.assess(text)
        if (assessment.level == CrisisLevel.POSSIBLE_DISTRESS) {
            _activeCrisis.value = crisisResponder.respond(assessment)
        }

        // Generative UI: a turn that reads like a request for a view ("how's my
        // week", "plan my evening", "summarize my day") composes a native card
        // instead of a plain streamed reply. Ordinary chat flows through untouched.
        if (com.personalagent.shared.genui.SuggestionChips.preferredViewFor(text) != null) {
            composeInto(target, text)
            return
        }

        // Snapshot the wire history (includes the user turn just added) BEFORE the
        // empty assistant placeholder, so we don't send a blank assistant message.
        // When the user is asking to schedule/automate something, prepend a single
        // system steer so the agent delivers IN-APP (no send()/push/external channel)
        // — the fix for the "scheduled task never delivered" failure.
        val wire = buildList {
            if (LifePrompts.looksLikeScheduling(text)) {
                add(HermesWireMessage(role = "system", content = LifePrompts.schedulingSteer()))
            }
            addAll(wireMessagesFor(target))
        }
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
                // Save the completed (or errored) assistant turn so the whole
                // exchange is on disk before the app can be killed.
                persist(target)
            }
        }
    }

    // --- Generative UI --------------------------------------------------------

    /** Tap on a fixed suggestion chip → echo its canonical prompt, then compose. */
    fun sendChip(chip: com.personalagent.shared.genui.SuggestionChip) {
        if (_sending.value) return
        val target = _currentId.value
        appendTo(target, Message.Role.USER, chip.prompt)
        persist(target)
        composeInto(target, chip.prompt)
    }

    /**
     * Gather the user's REAL local facts, ask the agent to compose a view on the
     * isolated genui session id, reconcile every number/id against the facts, and
     * attach the resulting [com.personalagent.shared.genui.ComposedView] to a fresh
     * assistant turn. Shows the "composing…" state meanwhile; degrades to an honest
     * local view or a plain prose line (never a broken card).
     */
    private fun composeInto(target: Long, ask: String) {
        val assistantId = appendComposing(target)
        viewModelScope.launch {
            _sending.value = true
            try {
                val now = SystemClock.nowMillis()
                val facts = com.personalagent.shared.genui.FactsCollector.build(
                    now = now,
                    tasks = taskStore.all(),
                    reminders = localStore.allReminders(),
                    planItems = localStore.allPlanItems(),
                    learning = learningStore.state(),
                    conversations = chatStore.all(),
                )
                when (val result = genUi.compose(ask, facts)) {
                    is com.personalagent.shared.genui.ComposeResult.Composed ->
                        setView(target, assistantId, result.view)
                    is com.personalagent.shared.genui.ComposeResult.Prose ->
                        setComposedText(target, assistantId, result.text)
                }
            } catch (e: Throwable) {
                setComposedText(target, assistantId, "I couldn't compose that view just now.")
            } finally {
                _sending.value = false
                persist(target)
            }
        }
    }

    /** Toggle a plan row's done state through the REAL store it came from. */
    fun togglePlanRow(row: com.personalagent.shared.genui.PlanRow) {
        val now = SystemClock.nowMillis()
        viewModelScope.launch {
            when (row.source) {
                com.personalagent.shared.genui.PlanRow.SOURCE_TASK ->
                    taskStore.setDone(row.id, !row.done, now)
                com.personalagent.shared.genui.PlanRow.SOURCE_PLAN ->
                    localStore.allPlanItems().firstOrNull { it.id == row.id }
                        ?.let { localStore.upsertPlanItem(it.copy(done = !it.done)) }
                com.personalagent.shared.genui.PlanRow.SOURCE_LEARNING ->
                    learningStore.setStatus(
                        row.id,
                        if (row.done) com.personalagent.shared.learning.LearningStatus.STARTED
                        else com.personalagent.shared.learning.LearningStatus.FINISHED,
                        now,
                    )
                else -> Unit // reminders are time-based; no "done" toggle
            }
            flipRowDone(row.id)
        }
    }

    /** "Start reading" a recommended resource → mark it STARTED (UI opens the URL). */
    fun markResourceStarted(resourceId: String) {
        learningStore.setStatus(resourceId, com.personalagent.shared.learning.LearningStatus.STARTED, SystemClock.nowMillis())
    }

    private fun appendComposing(sessionId: Long): Long {
        val mid = nextMessageId++
        val now = SystemClock.nowMillis()
        _sessions.update { list ->
            list.map { sess ->
                if (sess.id != sessionId) sess
                else sess.copy(messages = sess.messages + Message(mid, Message.Role.ASSISTANT, "", now, composing = true), updatedAt = now)
            }
        }
        return mid
    }

    private fun setView(sessionId: Long, messageId: Long, view: com.personalagent.shared.genui.ComposedView) {
        _sessions.update { list ->
            list.map { sess ->
                if (sess.id != sessionId) sess
                else sess.copy(messages = sess.messages.map { m ->
                    if (m.id == messageId) m.copy(view = view, composing = false, text = view.toPlainSummary()) else m
                })
            }
        }
    }

    private fun setComposedText(sessionId: Long, messageId: Long, text: String) {
        _sessions.update { list ->
            list.map { sess ->
                if (sess.id != sessionId) sess
                else sess.copy(messages = sess.messages.map { m ->
                    if (m.id == messageId) m.copy(text = text, composing = false, view = null) else m
                })
            }
        }
    }

    /** Flip the rendered done-state of a plan row (so the tick reflects immediately). */
    private fun flipRowDone(rowId: String) {
        _sessions.update { list ->
            list.map { sess ->
                sess.copy(messages = sess.messages.map { m ->
                    val v = m.view ?: return@map m
                    val blocks = v.blocks.map { b ->
                        if (b !is com.personalagent.shared.genui.ViewBlock.Plan) b
                        else b.copy(items = b.items.map { it -> if (it.id == rowId) it.copy(done = !it.done) else it })
                    }
                    m.copy(view = v.copy(blocks = blocks))
                })
            }
        }
    }

    // --- Hermes hydration -----------------------------------------------------

    /** Pull server-side sessions we don't already have and add them to history. */
    private fun hydrateFromHermes() {
        viewModelScope.launch {
            val cards = runCatching { hermes.sessions() }.getOrNull() ?: return@launch
            val known = _sessions.value.map { it.conversationId }.toMutableSet()
            val additions = ArrayList<ChatSession>()
            for (card in cards) {
                if (card.messageCount <= 0) continue
                if (card.id in known) continue
                known += card.id
                val now = card.lastActiveMillis ?: SystemClock.nowMillis()
                additions += ChatSession(
                    id = nextSessionId++,
                    title = card.displayTitle,
                    messages = emptyList(),
                    conversationId = card.id,
                    createdAt = card.startedAt?.let { (it * 1000).toLong() } ?: now,
                    updatedAt = now,
                    fromHermes = true,
                    hydrated = false,
                )
                if (additions.size >= MAX_HYDRATED) break
            }
            if (additions.isNotEmpty()) _sessions.update { it + additions }
        }
    }

    /** Fetch a hydrated thread's transcript from `/api/sessions/{id}/messages`. */
    private fun hydrateMessages(session: ChatSession) {
        viewModelScope.launch {
            val wire = runCatching { hermes.sessionMessages(session.conversationId) }.getOrNull()
                ?: return@launch
            val loaded = wire
                .filter { (it.role == "user" || it.role == "assistant") && !it.content.isNullOrBlank() }
                .map { m ->
                    Message(
                        id = nextMessageId++,
                        role = if (m.role == "user") Message.Role.USER else Message.Role.ASSISTANT,
                        text = m.content!!.trim(),
                        time = session.updatedAt,
                    )
                }
            if (loaded.isEmpty()) {
                // Mark hydrated so we don't retry a genuinely empty transcript.
                _sessions.update { list -> list.map { if (it.id == session.id) it.copy(hydrated = true) else it } }
                return@launch
            }
            _sessions.update { list ->
                list.map { if (it.id == session.id) it.copy(messages = loaded, hydrated = true) else it }
            }
            persist(session.id)
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
        /** Cap on server sessions merged into history on launch. */
        private const val MAX_HYDRATED = 40
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
            return ConversationViewModel(
                hermes = client,
                chatStore = container.chatStore,
                crisisRecognizer = container.crisisRecognizer,
                crisisResponder = container.crisisResponder,
                taskStore = container.taskStore,
                localStore = container.store,
                learningStore = container.learningStore,
            ) as T
        }
    }
}

// --- ChatStore <-> UI mapping ------------------------------------------------

private fun ChatSession.toStored(): StoredConversation = StoredConversation(
    id = id,
    title = title,
    conversationId = conversationId,
    createdAt = createdAt,
    updatedAt = updatedAt,
    messages = messages.map { StoredMessage(it.id, it.role.wire(), it.text, it.time) },
    fromHermes = fromHermes,
)

private fun StoredConversation.toSession(): ChatSession = ChatSession(
    id = id,
    title = title,
    messages = messages.map { Message(it.id, roleOf(it.role), it.text, it.time) },
    conversationId = conversationId,
    createdAt = createdAt,
    updatedAt = updatedAt,
    fromHermes = fromHermes,
    hydrated = true,
)

/**
 * A plain-text summary of a composed view, stored as the assistant turn's [text]
 * so chat history (which persists text only) still shows something truthful after
 * a relaunch — the rich card itself is ephemeral, like the prototype.
 */
private fun com.personalagent.shared.genui.ComposedView.toPlainSummary(): String = buildString {
    title?.let { append(it).append('\n') }
    blocks.forEach { b ->
        when (b) {
            is com.personalagent.shared.genui.ViewBlock.ProseLine -> append(b.text).append('\n')
            is com.personalagent.shared.genui.ViewBlock.StatGrid ->
                append(b.stats.joinToString("  ·  ") { "${it.value} ${it.label}" }).append('\n')
            is com.personalagent.shared.genui.ViewBlock.Plan -> {
                append(b.heading).append('\n')
                b.items.forEach { append(if (it.done) "  ✓ " else "  • ").append(it.title).append('\n') }
            }
            is com.personalagent.shared.genui.ViewBlock.ResourceRec ->
                append("→ ${b.resource.title}\n")
            is com.personalagent.shared.genui.ViewBlock.Sparkline -> Unit
        }
    }
}.trim().ifBlank { "(composed view)" }

private fun Message.Role.wire(): String = when (this) {
    Message.Role.USER -> "user"
    Message.Role.ASSISTANT -> "assistant"
    Message.Role.SYSTEM -> "system"
}

private fun roleOf(wire: String): Message.Role = when (wire) {
    "user" -> Message.Role.USER
    "system" -> Message.Role.SYSTEM
    else -> Message.Role.ASSISTANT
}
