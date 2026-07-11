package com.personalagent.shared.ios

import com.personalagent.shared.chat.ChatStore
import com.personalagent.shared.chat.StoredConversation
import com.personalagent.shared.chat.StoredMessage
import com.personalagent.shared.appearance.AccentOption
import com.personalagent.shared.appearance.AccentPalette
import com.personalagent.shared.appearance.AppearanceStore
import com.personalagent.shared.crypto.EncryptedKeyValueStorage
import com.personalagent.shared.crypto.IosNativeKeyStore
import com.personalagent.shared.crypto.IosSecretKeyProvider
import com.personalagent.shared.crypto.SecretKeyProvider
import com.personalagent.shared.genui.ComposeResult
import com.personalagent.shared.genui.ComposedView
import com.personalagent.shared.genui.FactsCollector
import com.personalagent.shared.genui.GenerativeUiService
import com.personalagent.shared.genui.SuggestionChip
import com.personalagent.shared.genui.SuggestionChips
import com.personalagent.shared.model.Reminder
import com.personalagent.shared.hermes.ChatStreamEvent
import com.personalagent.shared.hermes.DueReminder
import com.personalagent.shared.hermes.HermesClient
import com.personalagent.shared.hermes.HermesConfig
import com.personalagent.shared.hermes.HermesConfigStore
import com.personalagent.shared.hermes.HermesJob
import com.personalagent.shared.hermes.HermesMessage
import com.personalagent.shared.hermes.HermesReminderPoller
import com.personalagent.shared.hermes.RunEvent
import com.personalagent.shared.hermes.SessionHydration
import com.personalagent.shared.hermes.ToolFinding
import com.personalagent.shared.hermes.WrittenDocument
import com.personalagent.shared.hermes.HermesWireMessage
import com.personalagent.shared.hermes.NotifiedReminderStore
import com.personalagent.shared.hermes.ReflectionCadence
import com.personalagent.shared.hermes.ReflectionStore
import com.personalagent.shared.hermes.ReminderHistory
import com.personalagent.shared.hermes.ReminderHistoryStore
import com.personalagent.shared.hermes.ReminderRecord
import com.personalagent.shared.hermes.ReminderStatus
import com.personalagent.shared.hermes.ReminderView
import com.personalagent.shared.hermes.oneShotScheduleMinutes
import com.personalagent.shared.home.HomeCacheStore
import com.personalagent.shared.knowledge.KnowledgeGraph
import com.personalagent.shared.knowledge.KnowledgeGraphService
import com.personalagent.shared.knowledge.KnowledgeGraphStore
import com.personalagent.shared.hermes.LearningPrompts
import com.personalagent.shared.hermes.LifePrompts
import com.personalagent.shared.learning.LearningAdaptation
import com.personalagent.shared.learning.LearningGoal
import com.personalagent.shared.learning.LearningKind
import com.personalagent.shared.learning.LearningRecommendationParser
import com.personalagent.shared.learning.LearningResource
import com.personalagent.shared.learning.LearningStatus
import com.personalagent.shared.learning.LearningStatusText
import com.personalagent.shared.learning.LearningStore
import com.personalagent.shared.model.Ids
import com.personalagent.shared.notes.MemoStore
import com.personalagent.shared.profile.ProfileStore
import com.personalagent.shared.safety.CrisisAssessment
import com.personalagent.shared.safety.CrisisLevel
import com.personalagent.shared.safety.CrisisResponder
import com.personalagent.shared.safety.CrisisResponse
import com.personalagent.shared.safety.DefaultCrisisResourceProvider
import com.personalagent.shared.safety.KeywordCrisisRecognizer
import com.personalagent.shared.safety.TrustedContactsStore
import com.personalagent.shared.store.IosKeyValueStorage
import com.personalagent.shared.tasks.TaskStore
import com.personalagent.shared.util.SystemClock
import kotlinx.coroutines.flow.collect

/**
 * The single Swift-facing entry point into the shared Hermes Life Agent stack.
 *
 * Rationale for a facade (mirrors [com.personalagent.shared.ios.IosFactories], but
 * for the Hermes-CLIENT path the SwiftUI parity build actually uses):
 *  - Kotlin **default arguments don't cross the ObjC/Swift bridge**, so SwiftUI
 *    can't write `ChatStore(storage)` (cap has a default) or `HermesClient(config)`
 *    (engine/timeouts default). Every factory below fills those in on the Kotlin
 *    side and returns a ready object.
 *  - Kotlin **`Flow` can't be consumed from Swift** ergonomically without SKIE, so
 *    each streaming API is re-exposed as a `suspend fun … (on… : (T) -> Unit)` —
 *    the supported interop direction (Swift *calls* Kotlin; Kotlin collects). A
 *    `suspend fun` bridges to Swift `async throws` natively.
 *
 * 🔒 Every store is sealed at rest by [EncryptedKeyValueStorage] over the
 * Swift-provided [IosNativeKeyStore] (Keychain + Secure Enclave + CryptoKit
 * AES-GCM). Nothing sensitive is ever written to plaintext [IosKeyValueStorage].
 */
object LifeAgentIos {

    // --- Crypto + encrypted stores -------------------------------------------

    /** 🔒 Wrap the Swift `IosSecretKeyStore` as the shared [SecretKeyProvider]. */
    fun createCrypto(native: IosNativeKeyStore): SecretKeyProvider =
        IosSecretKeyProvider(native)

    private fun enc(crypto: SecretKeyProvider, suite: String) =
        EncryptedKeyValueStorage(IosKeyValueStorage(suite), crypto)

    /** 🔒 Connection to the user's own Hermes (base URL + key + memory-scope key). */
    fun hermesConfigStore(crypto: SecretKeyProvider): HermesConfigStore =
        HermesConfigStore(enc(crypto, "hermes_connection"))

    fun chatStore(crypto: SecretKeyProvider): ChatStore =
        ChatStore(enc(crypto, "chat_history"))

    fun memoStore(crypto: SecretKeyProvider): MemoStore =
        MemoStore(enc(crypto, "memos"))

    fun taskStore(crypto: SecretKeyProvider): TaskStore =
        TaskStore(enc(crypto, "tasks"))

    fun reminderHistoryStore(crypto: SecretKeyProvider): ReminderHistoryStore =
        ReminderHistoryStore(enc(crypto, "reminder_history"))

    fun notifiedReminderStore(crypto: SecretKeyProvider): NotifiedReminderStore =
        NotifiedReminderStore(enc(crypto, "reminder_notified"))

    fun homeCacheStore(crypto: SecretKeyProvider): HomeCacheStore =
        HomeCacheStore(enc(crypto, "home_cache"))

    fun profileStore(crypto: SecretKeyProvider): ProfileStore =
        ProfileStore(enc(crypto, "profile"))

    fun reflectionStore(crypto: SecretKeyProvider): ReflectionStore =
        ReflectionStore(enc(crypto, "reflection"))

    fun knowledgeGraphStore(crypto: SecretKeyProvider): KnowledgeGraphStore =
        KnowledgeGraphStore(enc(crypto, "knowledge_graph"))

    fun knowledgeGraphService(chat: ChatStore, kg: KnowledgeGraphStore): KnowledgeGraphService =
        KnowledgeGraphService(chat, kg)

    // --- Phase 6: Learning Guide ---------------------------------------------

    /** AUTHORITATIVE local store of learning goals + resources (sealed at rest). */
    fun learningStore(crypto: SecretKeyProvider): LearningStore =
        LearningStore(enc(crypto, "learning"))

    /**
     * Build a [LearningGoal] with all fields explicit (Kotlin default args don't
     * cross the Swift bridge), stamping a fresh id + time.
     *
     * NOTE: named `make…` not `new…` — a `new`-prefixed function collides with the
     * Objective-C "new" method family and K/N bridges it to Swift as
     * `doNewLearningGoal`, which is surprising. `make…` bridges verbatim.
     */
    fun makeLearningGoal(topic: String, why: String?, level: String?, style: String?): LearningGoal {
        val now = SystemClock.nowMillis()
        return LearningGoal(
            id = Ids.next(now),
            topic = topic,
            why = why?.ifBlank { null },
            level = level?.ifBlank { null },
            style = style?.ifBlank { null },
            createdAt = now,
        )
    }

    /** Human label for a resource kind (enum comparison kept on the Kotlin side). */
    fun learningKindLabel(kind: LearningKind): String = when (kind) {
        LearningKind.VIDEO -> "Video"
        LearningKind.ARTICLE -> "Article"
        LearningKind.COURSE -> "Course"
        LearningKind.DOCS -> "Docs"
        LearningKind.INTERACTIVE -> "Interactive"
        LearningKind.OTHER -> "Resource"
    }

    /**
     * Step 2 — get the agent's next-resource prompt (defaults filled Kotlin-side)
     * and parse its reply into sanitized, inert resources. Kept as two calls so
     * Swift does the (async) network in between.
     */
    fun recommendPrompt(goal: LearningGoal, avoid: List<LearningResource>, adaptationHint: String?): String =
        LearningPrompts.recommendNext(goal, avoid, adaptationHint)

    fun parseRecommendations(reply: String, goalId: String): List<LearningResource> =
        LearningRecommendationParser.parse(reply, goalId, SystemClock.nowMillis(), 3)

    /** Step 3 — adaptation hint from tracked history (null if no signal yet). */
    fun learningAdaptationHint(resources: List<LearningResource>): String? =
        LearningAdaptation.hint(resources)

    /** The one-tap status options + their labels (enum handling stays Kotlin-side). */
    fun learningTapOptions(): List<LearningStatus> = LearningStatusText.TAP_OPTIONS

    fun learningStatusLabel(status: LearningStatus): String = LearningStatusText.label(status)

    /** Stable string key for a status, so Swift can compare without touching enums. */
    fun learningStatusKey(status: LearningStatus): String = status.name

    /** Prompt that syncs a status change to Hermes memory as the current focus. */
    fun recordStatusPrompt(goal: LearningGoal, resource: LearningResource, status: LearningStatus): String =
        LearningPrompts.recordStatus(goal, resource.title, LearningStatusText.memoryPhrase(status))

    /**
     * Step 4 — the Phase-4 reflection prompt with a quiet learning touch woven in
     * ONLY when something is in progress (else identical to before).
     */
    fun reflectionPromptWithLearning(reflectionStore: ReflectionStore, learningStore: LearningStore): String {
        val base = LifePrompts.reflection(reflectionStore.load().cadence.promptWord)
        val focus = learningStore.currentFocus() ?: return base
        return base + LearningPrompts.reflectionLearningAddon(focus.goal.topic, focus.resource.title)
    }

    // --- Appearance: user-selectable accent color ----------------------------

    /** Persisted accent-color choice (shared curated list; sealed at rest). */
    fun appearanceStore(crypto: SecretKeyProvider): AppearanceStore =
        AppearanceStore(enc(crypto, "appearance"))

    /** The full curated accent list — Swift reads id, name and the rgb fields. */
    fun accentOptions(): List<AccentOption> = AccentPalette.OPTIONS

    fun accentById(id: String): AccentOption = AccentPalette.byId(id)

    fun defaultAccentId(): String = AccentPalette.DEFAULT_ID

    // --- Generative UI: the agent composes a native view on demand -----------

    /** The fixed suggestion chips (shared copy with Android). */
    fun suggestionChips(): List<SuggestionChip> = SuggestionChips.ALL

    /** Plan-row tap → learning resource STARTED (enum handling kept Kotlin-side). */
    fun setLearningResourceStarted(store: LearningStore, resourceId: String) =
        store.setStatus(resourceId, LearningStatus.STARTED, SystemClock.nowMillis())

    /** Plan-row tick on a learning resource → FINISHED/STARTED (enum kept Kotlin-side). */
    fun setLearningResourceDone(store: LearningStore, resourceId: String, done: Boolean) =
        store.setStatus(
            resourceId,
            if (done) LearningStatus.FINISHED else LearningStatus.STARTED,
            SystemClock.nowMillis(),
        )

    /**
     * Compose a native view for [ask] from the user's REAL local data + the user's
     * own Hermes, then fan the result to Swift: [onView] with a validated
     * [ComposedView] (Swift `switch`es over its blocks), or [onProse] with a plain
     * agent line when there's nothing to compose / Hermes is unreachable.
     *
     * Facts are gathered here (suspend store reads) and every number is pinned to
     * them by the shared parser — the model only narrates + selects layout.
     * Reminders come from the iOS [ReminderRecord] history (there's no LocalStore on
     * iOS); they're mapped to the shared [Reminder] shape for counting.
     */
    suspend fun composeView(
        client: HermesClient,
        taskStore: TaskStore,
        learningStore: LearningStore,
        chatStore: ChatStore,
        reminders: List<ReminderRecord>,
        ask: String,
        onView: (ComposedView) -> Unit,
        onProse: (String) -> Unit,
    ) {
        val now = SystemClock.nowMillis()
        val reminderModels = reminders.map {
            Reminder(
                id = it.id,
                title = it.text,
                note = "",
                triggerAtMillis = it.targetMillis,
                status = com.personalagent.shared.model.ReminderStatus.SCHEDULED,
                createdAt = now,
            )
        }
        val facts = FactsCollector.build(
            now = now,
            tasks = taskStore.all(),
            reminders = reminderModels,
            planItems = emptyList(),
            learning = learningStore.state(),
            conversations = chatStore.all(),
        )
        when (val result = GenerativeUiService(client).compose(ask, facts)) {
            is ComposeResult.Composed -> onView(result.view)
            is ComposeResult.Prose -> onProse(result.text)
        }
    }

    /** 🔒 Crisis (Gate 2) — consent-first; contacts NO ONE automatically. */
    fun trustedContactsStore(crypto: SecretKeyProvider): TrustedContactsStore =
        TrustedContactsStore(enc(crypto, "trusted_contacts"))

    fun crisisRecognizer(): KeywordCrisisRecognizer = KeywordCrisisRecognizer()

    fun crisisResponder(): CrisisResponder = CrisisResponder(DefaultCrisisResourceProvider())

    // --- Hermes client + value constructors ----------------------------------

    fun client(config: HermesConfig): HermesClient = HermesClient(config)

    fun wireMessage(role: String, content: String): HermesWireMessage =
        HermesWireMessage(role = role, content = content)

    fun reminderRecord(id: String, text: String, targetMillis: Long): ReminderRecord =
        ReminderRecord(id = id, text = text, targetMillis = targetMillis)

    fun scheduleForMinutes(nowMillis: Long, targetMillis: Long): String =
        oneShotScheduleMinutes(nowMillis, targetMillis)

    fun mergeReminders(
        liveJobs: List<HermesJob>,
        history: List<ReminderRecord>,
        nowMillis: Long,
    ): List<ReminderView> = ReminderHistory.merge(liveJobs, history, nowMillis)

    /** "UPCOMING" | "DUE_NOW" | "DONE" for a [ReminderView] (enum kept in Kotlin). */
    fun reminderStatusName(view: ReminderView): String = view.status.name

    /** Home dashboard: only the not-yet-done reminders, from local history alone. */
    fun upcomingReminders(history: List<ReminderRecord>, nowMillis: Long): List<ReminderView> =
        ReminderHistory.merge(emptyList(), history, nowMillis)
            .filter { it.status == ReminderStatus.UPCOMING || it.status == ReminderStatus.DUE_NOW }

    fun nowMillis(): Long = SystemClock.nowMillis()

    // --- ChatStore <-> Swift value helpers -----------------------------------
    // StoredConversation/StoredMessage have default args, so build them here.

    fun storedMessage(id: Long, role: String, text: String, time: Long): StoredMessage =
        StoredMessage(id = id, role = role, text = text, time = time)

    fun storedConversation(
        id: Long,
        title: String,
        conversationId: String,
        createdAt: Long,
        updatedAt: Long,
        messages: List<StoredMessage>,
        fromHermes: Boolean,
    ): StoredConversation = StoredConversation(
        id = id,
        title = title,
        conversationId = conversationId,
        createdAt = createdAt,
        updatedAt = updatedAt,
        messages = messages,
        fromHermes = fromHermes,
    )

    // --- Flow → suspend+callback wrappers ------------------------------------

    /**
     * Stream a chat reply. Invokes [onDelta] for each text chunk on the calling
     * coroutine's context; the returned `async` completes when the stream ends
     * (or throws a `HermesException`, surfaced to Swift as a thrown error).
     */
    suspend fun streamChat(
        client: HermesClient,
        messages: List<HermesWireMessage>,
        sessionId: String?,
        onDelta: (String) -> Unit,
    ) {
        client.streamChat(messages, sessionId).collect { event ->
            if (event is ChatStreamEvent.Delta) onDelta(event.text)
        }
    }

    /**
     * Run one reminder poll (ask Hermes for jobs, work out which are newly due,
     * hand each to [notify], and record it so it doesn't fire again). Returns the
     * reminders surfaced this pass.
     */
    suspend fun pollReminders(
        client: HermesClient,
        notified: NotifiedReminderStore,
        notify: (DueReminder) -> Unit,
    ): List<DueReminder> =
        HermesReminderPoller(client, notified) { SystemClock.nowMillis() }.pollOnce(notify)

    // --- Crisis (Gate 2) — enum comparison kept on the Kotlin side --------------

    /**
     * 🔒 Consult the conservative recognizer for one user turn and, only on
     * POSSIBLE_DISTRESS, build the consent-first supportive [CrisisResponse]
     * (contacts NO ONE). Returns null otherwise. Keeps [CrisisLevel] comparison in
     * Kotlin so Swift never touches the bridged enum.
     */
    fun crisisResponseFor(
        recognizer: KeywordCrisisRecognizer,
        responder: CrisisResponder,
        text: String,
    ): CrisisResponse? {
        val assessment = recognizer.assess(text)
        return if (assessment.level == CrisisLevel.POSSIBLE_DISTRESS) responder.respond(assessment)
        else null
    }

    /** 🔒 The supportive surface on demand (user tapped "Find support"). */
    fun supportResponse(responder: CrisisResponder): CrisisResponse? =
        responder.respond(CrisisAssessment(CrisisLevel.POSSIBLE_DISTRESS, "User opened support."))

    // --- Reflection — enum construction/read kept on the Kotlin side ------------

    /** "OFF" | "WEEKLY" | "MONTHLY" for the currently saved cadence. */
    fun reflectionCadenceName(store: ReflectionStore): String = store.load().cadence.name

    fun setReflectionCadence(store: ReflectionStore, name: String, nowMillis: Long) {
        val cadence = when (name.uppercase()) {
            "WEEKLY" -> ReflectionCadence.WEEKLY
            "MONTHLY" -> ReflectionCadence.MONTHLY
            else -> ReflectionCadence.OFF
        }
        store.setCadence(cadence, nowMillis)
    }

    /** "weekly" | "monthly" prompt word for the saved cadence (weekly if OFF). */
    fun reflectionPromptWord(store: ReflectionStore): String = store.load().cadence.promptWord

    // --- Knowledge graph — provenance label (enum kept in Kotlin) ---------------

    /** "MODEL" | "KEYWORDS" | "EMPTY" for how the cached graph was produced. */
    fun knowledgeSourceName(graph: KnowledgeGraph): String = graph.source.name

    // --- Agent runs (/v1/runs) — sealed RunEvent fanned to per-variant callbacks --

    /**
     * Stream a run's live events (tool starts/completions, reasoning, answer deltas,
     * approval requests, completion/failure) to Swift. The Kotlin `when` over the
     * sealed [RunEvent] stays here so Swift never pattern-matches the bridged type.
     * Answer via [HermesClient.submitApproval] (called directly from Swift).
     */
    suspend fun runEvents(
        client: HermesClient,
        runId: String,
        onToolStarted: (tool: String, preview: String) -> Unit,
        onToolCompleted: (tool: String, durationSec: Double, error: Boolean) -> Unit,
        onReasoning: (String) -> Unit,
        onDelta: (String) -> Unit,
        onCompleted: (output: String, inputTokens: Long, outputTokens: Long, totalTokens: Long) -> Unit,
        onFailed: (String) -> Unit,
        onApprovalRequested: (command: String, choices: List<String>) -> Unit,
        onApprovalResolved: (String) -> Unit,
    ) {
        client.runEvents(runId).collect { ev ->
            when (ev) {
                is RunEvent.ToolStarted -> onToolStarted(ev.tool, ev.preview)
                is RunEvent.ToolCompleted -> onToolCompleted(ev.tool, ev.durationSec, ev.error)
                is RunEvent.Reasoning -> onReasoning(ev.text)
                is RunEvent.Delta -> onDelta(ev.text)
                is RunEvent.Completed -> onCompleted(
                    ev.output,
                    ev.usage?.inputTokens ?: 0L,
                    ev.usage?.outputTokens ?: 0L,
                    ev.usage?.totalTokens ?: 0L,
                )
                is RunEvent.Failed -> onFailed(ev.message)
                is RunEvent.ApprovalRequested -> onApprovalRequested(ev.command, ev.choices)
                is RunEvent.ApprovalResolved -> onApprovalResolved(ev.choice)
            }
        }
    }

    /** Tool results mined from a run transcript ("what it found"). */
    fun runFindings(messages: List<HermesMessage>): List<ToolFinding> =
        SessionHydration.findings(messages, 6)

    /** Documents the agent wrote during a run (write_file-style tool calls). */
    fun runDocuments(messages: List<HermesMessage>): List<WrittenDocument> =
        SessionHydration.documents(messages)
}
