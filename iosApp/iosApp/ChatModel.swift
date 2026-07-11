// ChatModel.swift — the chat surface driver, a faithful Swift port of Android's
// ConversationViewModel. Streams replies from the user's Hermes over the shared
// Kotlin client, persists every thread to the 🔒 sealed-at-rest ChatStore, and
// consults the consent-first crisis recognizer on each user turn.

import SwiftUI
import Shared

@MainActor
final class ChatModel: ObservableObject {

    struct UIMessage: Identifiable, Equatable {
        enum Role { case user, assistant, system }
        let id: Int64
        let role: Role
        var text: String
        let time: Int64
        // Generative UI: an assistant turn may carry a composed native view, or be
        // in the transient "composing…" state.
        var composing: Bool = false
        var view: ComposedView? = nil
        /// Bumps whenever [view] changes, so Equatable/diffing works without the
        /// (non-Swift-Equatable) Kotlin ComposedView participating in ==.
        var viewToken: Int = 0

        static func == (l: UIMessage, r: UIMessage) -> Bool {
            l.id == r.id && l.role == r.role && l.text == r.text &&
                l.time == r.time && l.composing == r.composing && l.viewToken == r.viewToken
        }
    }

    struct UISession: Identifiable {
        let id: Int64
        var title: String
        var messages: [UIMessage]
        let conversationId: String
        let createdAt: Int64
        var updatedAt: Int64
        var fromHermes: Bool
        var hydrated: Bool
    }

    @Published private(set) var sessions: [UISession] = []
    @Published var currentChatId: Int64 = 0
    @Published private(set) var sending = false
    @Published var activeCrisis: CrisisResponse?

    static let newChatTitle = "New chat"
    private static let emptyReplyFallback =
        "Hermes didn't send any text back. Check that your Hermes has a working model provider configured."
    private static let genericError = "Something went wrong talking to your Hermes."

    private let env: AppEnvironment
    private let client: HermesClient?
    private var nextMessageId: Int64 = 0
    private var nextSessionId: Int64 = 1
    private var convSeq: Int64 = 0
    private let seed = String(UInt32.random(in: 0..<UInt32.max), radix: 16)

    /// Messages of the currently-open thread.
    var messages: [UIMessage] { sessions.first { $0.id == currentChatId }?.messages ?? [] }

    init(env: AppEnvironment) {
        self.env = env
        self.client = env.makeClient()

        let restored = env.chatStore.all().map { Self.toSession($0) }
        nextSessionId = (restored.map { $0.id }.max() ?? 0) + 1
        nextMessageId = (restored.flatMap { $0.messages }.map { $0.id }.max() ?? -1) + 1

        let now = LifeAgentIos.shared.nowMillis()
        let freshId = nextSessionId; nextSessionId += 1
        let fresh = UISession(id: freshId, title: Self.newChatTitle, messages: [],
                              conversationId: newConversationId(), createdAt: now,
                              updatedAt: now, fromHermes: false, hydrated: true)
        sessions = restored + [fresh]
        currentChatId = freshId
        hydrateFromHermes()
    }

    // MARK: - Actions

    func dismissCrisis() { activeCrisis = nil }

    func newChat() {
        if let cur = sessions.first(where: { $0.id == currentChatId }), cur.messages.isEmpty { return }
        let now = LifeAgentIos.shared.nowMillis()
        let id = nextSessionId; nextSessionId += 1
        sessions.append(UISession(id: id, title: Self.newChatTitle, messages: [],
                                  conversationId: newConversationId(), createdAt: now,
                                  updatedAt: now, fromHermes: false, hydrated: true))
        currentChatId = id
    }

    func selectChat(_ id: Int64) {
        guard let s = sessions.first(where: { $0.id == id }) else { return }
        currentChatId = id
        if s.fromHermes && !s.hydrated { hydrateMessages(s) }
    }

    func deleteChat(_ id: Int64) {
        env.chatStore.remove(id: id)
        sessions.removeAll { $0.id == id }
        if currentChatId == id {
            if let next = sessions.last { currentChatId = next.id } else { newChat() }
        }
    }

    /// Handle one user turn: echo it, then stream the agent's reply.
    func send(_ input: String) {
        let text = input.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !text.isEmpty, !sending, let client else { return }
        let target = currentChatId
        _ = appendTo(target, .user, text)
        persist(target)

        // 🔒 Gate 2 — consult the conservative crisis recognizer (offers support
        // only; never acts autonomously, never blocks the agent's reply).
        activeCrisis = LifeAgentIos.shared.crisisResponseFor(
            recognizer: env.crisisRecognizer, responder: env.crisisResponder, text: text)

        // Generative UI: a turn that reads like a view request composes a native
        // card instead of a plain streamed reply. Ordinary chat flows through.
        if SuggestionChips.shared.preferredViewFor(text: text) != nil {
            composeInto(target, ask: text)
            return
        }

        var wire: [HermesWireMessage] = []
        if LifePrompts.shared.looksLikeScheduling(text: text) {
            wire.append(LifeAgentIos.shared.wireMessage(role: "system",
                                                        content: LifePrompts.shared.schedulingSteer()))
        }
        wire.append(contentsOf: wireMessagesFor(target))
        let convId = conversationIdFor(target)
        let assistantId = appendTo(target, .assistant, "")

        sending = true
        _Concurrency.Task {
            var builder = ""
            do {
                try await LifeAgentIos.shared.streamChat(
                    client: client, messages: wire, sessionId: convId
                ) { delta in
                    builder += delta
                    let snapshot = builder
                    _Concurrency.Task { @MainActor in self.setText(target, assistantId, snapshot) }
                }
                if builder.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
                    setText(target, assistantId, Self.emptyReplyFallback)
                }
            } catch {
                setText(target, assistantId, hermesMessage(error) ?? Self.genericError)
            }
            sending = false
            persist(target)
        }
    }

    // MARK: - Generative UI

    /// Tap on a fixed suggestion chip → echo its canonical prompt, then compose.
    func sendChip(_ chip: SuggestionChip) {
        guard !sending else { return }
        let target = currentChatId
        _ = appendTo(target, .user, chip.prompt)
        persist(target)
        composeInto(target, ask: chip.prompt)
    }

    /// Gather REAL local facts + the user's own Hermes, compose a validated view,
    /// and attach it to a fresh assistant turn. Shows the "composing…" state and
    /// degrades to an honest local view / plain prose (never a broken card).
    private func composeInto(_ target: Int64, ask: String) {
        guard let client else {
            setComposedText(target, appendComposing(target), "Connect your Hermes to compose views.")
            return
        }
        let assistantId = appendComposing(target)
        sending = true
        _Concurrency.Task {
            do {
                try await LifeAgentIos.shared.composeView(
                    client: client,
                    taskStore: env.taskStore,
                    learningStore: env.learningStore,
                    chatStore: env.chatStore,
                    reminders: env.reminderHistory.all(),
                    ask: ask,
                    onView: { view in
                        _Concurrency.Task { @MainActor in self.setView(target, assistantId, view) }
                    },
                    onProse: { prose in
                        _Concurrency.Task { @MainActor in self.setComposedText(target, assistantId, prose) }
                    }
                )
            } catch {
                setComposedText(target, assistantId, "I couldn't compose that view just now.")
            }
            sending = false
            persist(target)
        }
    }

    /// Toggle a plan row through the REAL store it came from + reflect it live.
    /// `source` is a plain string; enum handling stays on the Kotlin side.
    func togglePlanRow(_ row: PlanRow) {
        switch row.source {
        case "task":
            env.taskStore.setDone(id: row.id, done: !row.done,
                                  nowMillis: LifeAgentIos.shared.nowMillis())
        case "learning":
            LifeAgentIos.shared.setLearningResourceDone(store: env.learningStore,
                                                        resourceId: row.id, done: !row.done)
        default:
            break // reminders/plan-items are not toggled on iOS
        }
        flipRowDone(row.id)
    }

    /// "Start reading" a resource → mark STARTED (the view opens the URL).
    func markResourceStarted(_ resourceId: String) {
        LifeAgentIos.shared.setLearningResourceStarted(store: env.learningStore, resourceId: resourceId)
    }

    @discardableResult
    private func appendComposing(_ sessionId: Int64) -> Int64 {
        let mid = nextMessageId; nextMessageId += 1
        let now = LifeAgentIos.shared.nowMillis()
        sessions = sessions.map { sess in
            guard sess.id == sessionId else { return sess }
            var s = sess
            s.messages.append(UIMessage(id: mid, role: .assistant, text: "", time: now, composing: true))
            s.updatedAt = now
            return s
        }
        return mid
    }

    private func setView(_ sessionId: Int64, _ messageId: Int64, _ view: ComposedView) {
        sessions = sessions.map { sess in
            guard sess.id == sessionId else { return sess }
            var s = sess
            s.messages = s.messages.map { m in
                guard m.id == messageId else { return m }
                var out = m
                out.composing = false
                out.view = view
                out.text = Self.plainSummary(view)
                out.viewToken += 1
                return out
            }
            return s
        }
    }

    private func setComposedText(_ sessionId: Int64, _ messageId: Int64, _ text: String) {
        sessions = sessions.map { sess in
            guard sess.id == sessionId else { return sess }
            var s = sess
            s.messages = s.messages.map { m in
                guard m.id == messageId else { return m }
                var out = m; out.composing = false; out.view = nil; out.text = text; out.viewToken += 1
                return out
            }
            return s
        }
    }

    private func flipRowDone(_ rowId: String) {
        sessions = sessions.map { sess in
            var s = sess
            s.messages = s.messages.map { m in
                guard let v = m.view else { return m }
                let newBlocks: [ViewBlock] = v.blocks.map { b in
                    guard let plan = b as? ViewBlockPlan else { return b }
                    let items = plan.items.map { it -> PlanRow in
                        it.id == rowId
                            ? PlanRow(id: it.id, title: it.title, time: it.time, note: it.note,
                                      source: it.source, sourceId: it.sourceId, done: !it.done,
                                      actionable: it.actionable)
                            : it
                    }
                    return ViewBlockPlan(heading: plan.heading, meta: plan.meta, items: items)
                }
                var out = m
                out.view = ComposedView(view: v.view, title: v.title, blocks: newBlocks, provenance: v.provenance)
                out.viewToken += 1
                return out
            }
            return s
        }
    }

    private static func plainSummary(_ view: ComposedView) -> String {
        var out = ""
        if let t = view.title { out += t + "\n" }
        for b in view.blocks {
            if let p = b as? ViewBlockProseLine { out += p.text + "\n" }
            else if let g = b as? ViewBlockStatGrid {
                out += g.stats.map { "\($0.value) \($0.label)" }.joined(separator: "  ·  ") + "\n"
            } else if let plan = b as? ViewBlockPlan {
                out += plan.heading + "\n"
                for it in plan.items { out += (it.done ? "  ✓ " : "  • ") + it.title + "\n" }
            } else if let rec = b as? ViewBlockResourceRec {
                out += "→ " + rec.resource.title + "\n"
            }
        }
        let trimmed = out.trimmingCharacters(in: .whitespacesAndNewlines)
        return trimmed.isEmpty ? "(composed view)" : trimmed
    }

    // MARK: - Mutation helpers

    private func newConversationId() -> String {
        let id = "lifeagent-conv-\(seed)-\(convSeq)"; convSeq += 1; return id
    }

    @discardableResult
    private func appendTo(_ sessionId: Int64, _ role: UIMessage.Role, _ text: String) -> Int64 {
        let mid = nextMessageId; nextMessageId += 1
        let now = LifeAgentIos.shared.nowMillis()
        sessions = sessions.map { sess in
            guard sess.id == sessionId else { return sess }
            var s = sess
            if s.title == Self.newChatTitle && role == .user {
                let t = String(text.prefix(48)).trimmingCharacters(in: .whitespacesAndNewlines)
                s.title = t.isEmpty ? Self.newChatTitle : t
            }
            s.messages.append(UIMessage(id: mid, role: role, text: text, time: now))
            s.updatedAt = now
            return s
        }
        return mid
    }

    private func setText(_ sessionId: Int64, _ messageId: Int64, _ text: String) {
        sessions = sessions.map { sess in
            guard sess.id == sessionId else { return sess }
            var s = sess
            s.messages = s.messages.map { $0.id == messageId ? UIMessage(id: $0.id, role: $0.role, text: text, time: $0.time) : $0 }
            return s
        }
    }

    private func persist(_ sessionId: Int64) {
        guard let s = sessions.first(where: { $0.id == sessionId }), !s.messages.isEmpty else { return }
        env.chatStore.upsert(conversation: toStored(s))
    }

    private func wireMessagesFor(_ sessionId: Int64) -> [HermesWireMessage] {
        guard let msgs = sessions.first(where: { $0.id == sessionId })?.messages else { return [] }
        return msgs
            .filter { ($0.role == .user || $0.role == .assistant) && !$0.text.isEmpty }
            .map { LifeAgentIos.shared.wireMessage(role: $0.role == .user ? "user" : "assistant", content: $0.text) }
    }

    private func conversationIdFor(_ sessionId: Int64) -> String {
        sessions.first(where: { $0.id == sessionId })?.conversationId ?? newConversationId()
    }

    // MARK: - Hermes hydration (best-effort)

    private func hydrateFromHermes() {
        guard let client else { return }
        _Concurrency.Task {
            guard let cards = try? await client.sessions() else { return }
            var known = Set(sessions.map { $0.conversationId })
            var additions: [UISession] = []
            for card in cards {
                if card.messageCount <= 0 { continue }
                if known.contains(card.id) { continue }
                known.insert(card.id)
                let now = card.lastActiveMillis?.int64Value ?? LifeAgentIos.shared.nowMillis()
                let started = (card.startedAt?.doubleValue).map { Int64($0 * 1000) } ?? now
                let id = nextSessionId; nextSessionId += 1
                additions.append(UISession(id: id, title: card.displayTitle, messages: [],
                                           conversationId: card.id, createdAt: started,
                                           updatedAt: now, fromHermes: true, hydrated: false))
                if additions.count >= 40 { break }
            }
            if !additions.isEmpty { sessions.append(contentsOf: additions) }
        }
    }

    private func hydrateMessages(_ session: UISession) {
        guard let client else { return }
        _Concurrency.Task {
            guard let wire = try? await client.sessionMessages(sessionId: session.conversationId) else { return }
            let loaded: [UIMessage] = wire.compactMap { m in
                let role = m.role
                guard (role == "user" || role == "assistant"), let c = m.content, !c.isEmpty else { return nil }
                let mid = nextMessageId; nextMessageId += 1
                return UIMessage(id: mid, role: role == "user" ? .user : .assistant,
                                 text: c.trimmingCharacters(in: .whitespacesAndNewlines), time: session.updatedAt)
            }
            sessions = sessions.map { s in
                guard s.id == session.id else { return s }
                var out = s; out.hydrated = true
                if !loaded.isEmpty { out.messages = loaded }
                return out
            }
            if !loaded.isEmpty { persist(session.id) }
        }
    }

    // MARK: - Mapping

    private static func roleOf(_ wire: String) -> UIMessage.Role {
        switch wire { case "user": return .user; case "system": return .system; default: return .assistant }
    }
    private func wire(_ role: UIMessage.Role) -> String {
        switch role { case .user: return "user"; case .assistant: return "assistant"; case .system: return "system" }
    }

    private static func toSession(_ c: StoredConversation) -> UISession {
        UISession(
            id: c.id, title: c.title,
            messages: c.messages.map { UIMessage(id: $0.id, role: roleOf($0.role), text: $0.text, time: $0.time) },
            conversationId: c.conversationId, createdAt: c.createdAt, updatedAt: c.updatedAt,
            fromHermes: c.fromHermes, hydrated: true)
    }

    private func toStored(_ s: UISession) -> StoredConversation {
        let msgs = s.messages.map {
            LifeAgentIos.shared.storedMessage(id: $0.id, role: wire($0.role), text: $0.text, time: $0.time)
        }
        return LifeAgentIos.shared.storedConversation(
            id: s.id, title: s.title, conversationId: s.conversationId,
            createdAt: s.createdAt, updatedAt: s.updatedAt, messages: msgs, fromHermes: s.fromHermes)
    }
}

/// Extract a user-actionable message from an error thrown across the KMP bridge.
/// Kotlin exceptions arrive as NSError wrapping the KotlinException in userInfo.
func hermesMessage(_ error: Error) -> String? {
    if let ex = (error as NSError).userInfo["KotlinException"] as? HermesException {
        return ex.message
    }
    if let ex = error as? HermesException { return ex.message }
    return nil
}
