// DashboardModel.swift — the Life-OS home driver, a faithful port of Android's
// DashboardViewModel. Assembles live previews for Goals (agent memory, stale-
// while-revalidate), Tasks (local), Memos (local), Reminders (local history).

import SwiftUI
import Shared

@MainActor
final class DashboardModel: ObservableObject {
    @Published var loading = true
    @Published var connected = false
    @Published var name: String?
    @Published var goalsLoading = false
    @Published var goalsRefreshing = false
    @Published var goals: [String] = []
    @Published var tasks: [Shared.Task] = []
    @Published var memos: [Memo] = []
    @Published var reminders: [ReminderView] = []

    private let env: AppEnvironment
    private let client: HermesClient?

    init(env: AppEnvironment) {
        self.env = env
        self.client = env.makeClient()
        refresh()
    }

    func refresh(force: Bool = false) {
        let ios = LifeAgentIos.shared
        let cache = env.homeCache.load()
        name = env.profileStore.displayName()
        goals = cache.goals
        tasks = env.taskStore.all().filter { !$0.done }
        memos = env.memoStore.all()
        reminders = ios.upcomingReminders(history: env.reminderHistory.all(), nowMillis: ios.nowMillis())
        loading = false
        _Concurrency.Task { await loadConnection() }
        _Concurrency.Task { await revalidateGoals(cache, force) }
        _Concurrency.Task { await maybeDeriveName() }
    }

    func toggleTask(_ id: String, _ done: Bool) {
        env.taskStore.setDone(id: id, done: done, nowMillis: LifeAgentIos.shared.nowMillis())
        tasks = env.taskStore.all().filter { !$0.done }
    }

    func statusName(_ view: ReminderView) -> String { LifeAgentIos.shared.reminderStatusName(view: view) }

    // MARK: - background revalidation

    private func loadConnection() async {
        guard let client else { return }
        let ok = ((try? await client.healthDetailed())?.isOk) ?? false
        connected = ok
    }

    private func revalidateGoals(_ cache: HomeCache, _ force: Bool) async {
        guard let client else { return }
        let ios = LifeAgentIos.shared
        let now = ios.nowMillis()
        let hasCache = !cache.goals.isEmpty
        if !force && hasCache && !env.homeCache.goalsAreStale(cache: cache, nowMillis: now) { return }
        goalsLoading = !hasCache
        goalsRefreshing = hasCache
        let msg = ios.wireMessage(role: "user", content: LifePrompts.shared.listGoals())
        let summary = try? await client.complete(messages: [msg], sessionId: "lifeagent-goals")
        if let summary {
            let parsed = Self.parseGoals(summary)
            env.homeCache.putGoals(goals: parsed, nowMillis: ios.nowMillis())
            goals = parsed
        }
        goalsLoading = false
        goalsRefreshing = false
    }

    private func maybeDeriveName() async {
        guard let client else { return }
        if name != nil || env.profileStore.derivedAttempted() { return }
        env.profileStore.markDerivedAttempted()
        let msg = LifeAgentIos.shared.wireMessage(
            role: "user",
            content: "What is my first name? Reply with only the name, or the single word UNKNOWN if you do not know.")
        guard let reply = try? await client.complete(messages: [msg], sessionId: "lifeagent-profile") else { return }
        if let nm = Self.plausibleFirstName(reply) {
            env.profileStore.setDerivedName(name: nm)
            name = env.profileStore.displayName()
        }
    }

    // MARK: - pure helpers (ported from DashboardViewModel)

    static func parseGoals(_ summary: String) -> [String] {
        var out: [String] = []
        for raw in summary.components(separatedBy: "\n") {
            let line = raw.trimmingCharacters(in: .whitespaces)
            guard let r = line.range(of: #"^([-*•]|\d+[.)])\s+"#, options: .regularExpression) else { continue }
            let content = String(line[r.upperBound...])
                .trimmingCharacters(in: CharacterSet(charactersIn: "*_`"))
                .trimmingCharacters(in: .whitespaces)
            if !content.isEmpty { out.append(content) }
        }
        return out
    }

    static func plausibleFirstName(_ raw: String) -> String? {
        var s = raw.trimmingCharacters(in: .whitespaces)
        s = s.trimmingCharacters(in: CharacterSet(charactersIn: ".!\"'")).trimmingCharacters(in: .whitespaces)
        if s.isEmpty || s.lowercased() == "unknown" { return nil }
        if s.count > 40 { return nil }
        let words = s.split(separator: " ")
        if words.count > 2 { return nil }
        if !s.allSatisfy({ $0.isLetter || $0 == " " || $0 == "-" || $0 == "'" }) { return nil }
        return words.first.map(String.init)
    }
}
