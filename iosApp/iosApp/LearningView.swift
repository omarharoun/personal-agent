// LearningView.swift — Phase 6 Learning Guide (Android LearningScreen). Declare
// what you want to learn (topic + why + level asked once + optional style); goals
// live in the authoritative local LearningStore and mirror to Hermes memory as the
// current focus. Step 2 adds free-open-web recommendations; Step 3 closes the loop.

import SwiftUI
import Shared

@MainActor
final class LearningModel: ObservableObject {
    @Published var goals: [LearningGoal] = []
    @Published var saving = false
    @Published var message: String?
    /// Web-search backend availability (checked via /v1/toolsets). nil = unknown.
    @Published var webAvailable: Bool?

    private let env: AppEnvironment
    private let client: HermesClient?
    init(env: AppEnvironment) { self.env = env; self.client = env.makeClient(); reload() }

    func reload() { goals = env.learningStore.goals() }

    /// Detect a web-search backend so Step 2 can say "unavailable" rather than fail silently.
    func checkWeb() {
        guard let client else { return }
        _Concurrency.Task {
            if let ts = try? await client.toolsets() {
                webAvailable = WebToolAvailability.shared.isWebSearchAvailable(toolsets: ts)
            }
        }
    }

    func add(topic: String, why: String, level: String, style: String) {
        let t = topic.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !t.isEmpty, !saving else { return }
        let goal = LifeAgentIos.shared.newLearningGoal(
            topic: t,
            why: why.isEmpty ? nil : why,
            level: level.isEmpty ? nil : level,
            style: style.isEmpty ? nil : style)
        env.learningStore.addGoal(goal: goal)
        reload()
        guard let client else { return }
        saving = true
        _Concurrency.Task {
            let msg = LifeAgentIos.shared.wireMessage(role: "user", content: LearningPrompts.shared.saveLearningGoal(goal: goal))
            do {
                _ = try await client.complete(messages: [msg], sessionId: "lifeagent-learning")
                message = "Learning goal saved"
            } catch {
                message = "Saved locally; couldn't sync to your agent's memory."
            }
            saving = false
        }
    }

    func archive(_ id: String) { env.learningStore.setGoalActive(id: id, active: false); reload() }
}

struct LearningView: View {
    @StateObject private var model: LearningModel
    @Environment(\.theme) private var theme
    @State private var topic = ""
    @State private var why = ""
    @State private var level = "Beginner"
    @State private var style = ""

    private let levels = ["Beginner", "Some experience", "Advanced"]
    init(env: AppEnvironment) { _model = StateObject(wrappedValue: LearningModel(env: env)) }

    private var activeGoals: [LearningGoal] { model.goals.filter { $0.active } }

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 12) {
                Text("Tell your agent what you want to get better at. It draws on what it knows about you and searches the free, open web to point you at the next right thing to learn — one honest suggestion at a time, never a listicle.")
                    .font(.callout).foregroundColor(theme.onSurfaceVariant)

                if model.webAvailable == false {
                    Text("Web search is unavailable on your Hermes. You can still set goals, but to get recommendations, enable a web-search backend in your Hermes config.")
                        .font(.footnote).foregroundColor(theme.onError)
                        .padding(12).frame(maxWidth: .infinity, alignment: .leading)
                        .background(theme.error.opacity(0.85)).clipShape(RoundedCornerShape(8))
                }

                field("What do you want to learn?", $topic)
                field("Why does it matter to you? (optional)", $why)

                Text("Where are you starting from?").font(.subheadline).foregroundColor(theme.onSurface)
                ScrollView(.horizontal, showsIndicators: false) {
                    HStack(spacing: 8) {
                        ForEach(levels, id: \.self) { l in
                            Button { level = l } label: {
                                Text(l).font(.footnote)
                                    .padding(.horizontal, 12).padding(.vertical, 8)
                                    .foregroundColor(level == l ? theme.onPrimary : theme.onSurface)
                                    .background(level == l ? theme.primary : theme.surfaceVariant)
                                    .clipShape(Capsule())
                            }
                        }
                    }
                }
                field("How do you like to learn? (optional — videos, hands-on…)", $style)

                Button {
                    model.add(topic: topic, why: why, level: level, style: style)
                    topic = ""; why = ""; style = ""
                } label: {
                    HStack { if model.saving { ProgressView().controlSize(.small) }; Text("Add learning goal") }
                        .frame(maxWidth: .infinity).padding(.vertical, 12)
                        .foregroundColor(theme.onPrimary).background(theme.primary).clipShape(RoundedCornerShape(8))
                }
                .disabled(model.saving || topic.trimmingCharacters(in: .whitespaces).isEmpty)

                if let msg = model.message { Text(msg).font(.footnote).foregroundColor(theme.onSurfaceVariant) }

                Text("WHAT YOU'RE LEARNING").hermesDisplayLabel(size: 11).foregroundColor(theme.onSurfaceVariant).padding(.top, 8)
                if activeGoals.isEmpty {
                    Text("No learning goals yet — add one above.").font(.callout).foregroundColor(theme.onSurfaceVariant)
                } else {
                    ForEach(activeGoals, id: \.id) { goalCard($0) }
                }
            }
            .padding(16)
        }
        .onAppear { model.reload(); model.checkWeb() }
    }

    private func field(_ label: String, _ text: Binding<String>) -> some View {
        TextField(label, text: text, axis: .vertical)
            .lineLimit(1...3).foregroundColor(theme.onSurface)
            .padding(12).background(theme.surfaceVariant).clipShape(RoundedCornerShape(8))
    }

    private func goalCard(_ goal: LearningGoal) -> some View {
        VStack(alignment: .leading, spacing: 4) {
            Text(goal.topic).font(.subheadline).fontWeight(.semibold).foregroundColor(theme.onSurface)
            if let w = goal.why { Text(w).font(.footnote).foregroundColor(theme.onSurfaceVariant) }
            let meta = [goal.level.map { "Level: \($0)" }, goal.style.map { "Prefers: \($0)" }].compactMap { $0 }.joined(separator: "  ·  ")
            if !meta.isEmpty { Text(meta).font(.caption2).foregroundColor(theme.onSurfaceVariant) }
            HStack {
                Spacer()
                Button("Archive") { model.archive(goal.id) }.font(.footnote).foregroundColor(theme.onSurfaceVariant)
            }
        }
        .padding(14).frame(maxWidth: .infinity, alignment: .leading)
        .background(theme.surface).clipShape(RoundedCornerShape(12))
        .overlay(RoundedCornerShape(12).stroke(theme.outline, lineWidth: 1))
    }
}
