// GoalsView.swift — set + review personal goals (Android GoalsScreen). Goals live
// in the agent's Hermes memory; the screen adds one (category + text) and shows a
// markdown summary the agent recalls.

import SwiftUI
import Shared

@MainActor
final class GoalsModel: ObservableObject {
    @Published var summary = ""
    @Published var loading = false
    @Published var saving = false
    @Published var message: String?

    private let env: AppEnvironment
    private let client: HermesClient?
    init(env: AppEnvironment) { self.env = env; self.client = env.makeClient() }

    func refresh() {
        guard let client else { return }
        loading = true
        _Concurrency.Task {
            let msg = LifeAgentIos.shared.wireMessage(role: "user", content: LifePrompts.shared.listGoals())
            summary = (try? await client.complete(messages: [msg], sessionId: "lifeagent-goals")) ?? summary
            loading = false
        }
    }

    func add(category: String, text: String) {
        let goal = text.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !goal.isEmpty, let client else { return }
        saving = true
        _Concurrency.Task {
            let msg = LifeAgentIos.shared.wireMessage(role: "user", content: LifePrompts.shared.saveGoal(category: category, goal: goal))
            do {
                _ = try await client.complete(messages: [msg], sessionId: "lifeagent-goals")
                message = "Goal added"
            } catch { message = hermesMessage(error) ?? "Couldn't save the goal." }
            saving = false
            refresh()
        }
    }
}

struct GoalsView: View {
    @StateObject private var model: GoalsModel
    @Environment(\.theme) private var theme
    @State private var category = "Health"
    @State private var draft = ""
    private let onOpenLearning: () -> Void

    private let categories = ["Health", "Relationships", "Learning", "Habits", "Work", "Other"]
    init(env: AppEnvironment, onOpenLearning: @escaping () -> Void = {}) {
        _model = StateObject(wrappedValue: GoalsModel(env: env))
        self.onOpenLearning = onOpenLearning
    }

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 12) {
                Text("Set what “better” looks like for you. Your agent remembers your goals and supports you with them over time.")
                    .font(.callout).foregroundColor(theme.onSurfaceVariant)

                // Step 4 — weave: learning is a kind of goal, so link to the Learning
                // guide from here rather than building a parallel surface.
                Button { onOpenLearning() } label: {
                    Text("Learning something? Open the Learning guide →").font(.footnote)
                        .foregroundColor(theme.primary)
                }

                ScrollView(.horizontal, showsIndicators: false) {
                    HStack(spacing: 8) {
                        ForEach(categories, id: \.self) { c in
                            Button { category = c } label: {
                                Text(c).font(.footnote)
                                    .padding(.horizontal, 12).padding(.vertical, 8)
                                    .foregroundColor(category == c ? theme.onPrimary : theme.onSurface)
                                    .background(category == c ? theme.primary : theme.surfaceVariant)
                                    .clipShape(Capsule())
                            }
                        }
                    }
                }

                TextField("What's the goal?", text: $draft, axis: .vertical)
                    .lineLimit(2...4).foregroundColor(theme.onSurface)
                    .padding(12).background(theme.surfaceVariant).clipShape(RoundedCornerShape(8))

                Button { model.add(category: category, text: draft); draft = "" } label: {
                    HStack { if model.saving { ProgressView().controlSize(.small) }; Text("Add goal") }
                        .frame(maxWidth: .infinity).padding(.vertical, 12)
                        .foregroundColor(theme.onPrimary).background(theme.primary).clipShape(RoundedCornerShape(8))
                }
                .disabled(model.saving || draft.trimmingCharacters(in: .whitespaces).isEmpty)

                if let msg = model.message { Text(msg).font(.footnote).foregroundColor(theme.onSurfaceVariant) }

                HStack(spacing: 8) {
                    Text("YOUR GOALS").hermesDisplayLabel(size: 11).foregroundColor(theme.onSurfaceVariant)
                    if model.loading { ProgressView().controlSize(.mini) }
                }
                .padding(.top, 8)
                if model.summary.isEmpty && !model.loading {
                    Text("No goals yet — add one above.").font(.callout).foregroundColor(theme.onSurfaceVariant)
                } else {
                    MarkdownText(text: model.summary, color: theme.onSurface)
                }
            }
            .padding(16)
        }
        .onAppear { if model.summary.isEmpty { model.refresh() } }
    }
}
