// ReflectionView.swift — gentle periodic check-ins (Android ReflectionScreen).
// Choose a cadence, or reflect now: the agent draws only on what it remembers to
// offer one warm, low-pressure reflection.

import SwiftUI
import Shared

@MainActor
final class ReflectionModel: ObservableObject {
    @Published var cadence = "OFF"      // OFF | WEEKLY | MONTHLY
    @Published var reflection = ""
    @Published var loading = false
    @Published var message: String?

    private let env: AppEnvironment
    private let client: HermesClient?
    init(env: AppEnvironment) {
        self.env = env; self.client = env.makeClient()
        cadence = LifeAgentIos.shared.reflectionCadenceName(store: env.reflectionStore)
    }

    func setCadence(_ name: String) {
        cadence = name
        LifeAgentIos.shared.setReflectionCadence(store: env.reflectionStore, name: name, nowMillis: LifeAgentIos.shared.nowMillis())
    }

    func reflectNow() {
        guard let client, !loading else { return }
        loading = true
        _Concurrency.Task {
            let ios = LifeAgentIos.shared
            let word = ios.reflectionPromptWord(store: env.reflectionStore)
            let msg = ios.wireMessage(role: "user", content: LifePrompts.shared.reflection(cadence: word))
            do {
                let out = try await client.complete(messages: [msg], sessionId: "lifeagent-reflection")
                reflection = out
                env.reflectionStore.markShown(now: ios.nowMillis())
            } catch { message = hermesMessage(error) ?? "Couldn't reflect right now." }
            loading = false
        }
    }

    func snoozeWeek() {
        let until = LifeAgentIos.shared.nowMillis() + 7 * 24 * 60 * 60 * 1000
        env.reflectionStore.snoozeUntil(untilMillis: until)
        message = "Snoozed for a week"
    }
}

struct ReflectionView: View {
    @StateObject private var model: ReflectionModel
    @Environment(\.theme) private var theme
    private let options = ["OFF", "WEEKLY", "MONTHLY"]
    init(env: AppEnvironment) { _model = StateObject(wrappedValue: ReflectionModel(env: env)) }

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 12) {
                Text("A gentle check-in, like a friend reaching out. Your agent draws only on what it remembers about you.")
                    .font(.callout).foregroundColor(theme.onSurfaceVariant)

                Text("How often?").font(.headline).foregroundColor(theme.onBackground)
                HStack(spacing: 8) {
                    ForEach(options, id: \.self) { o in
                        Button { model.setCadence(o) } label: {
                            Text(o.capitalized).font(.footnote)
                                .padding(.horizontal, 14).padding(.vertical, 8)
                                .foregroundColor(model.cadence == o ? theme.onPrimary : theme.onSurface)
                                .background(model.cadence == o ? theme.primary : theme.surfaceVariant)
                                .clipShape(Capsule())
                        }
                    }
                }

                Button { model.reflectNow() } label: {
                    HStack { if model.loading { ProgressView().controlSize(.small) }
                        Text(model.loading ? "Reflecting…" : "Reflect now") }
                        .frame(maxWidth: .infinity).padding(.vertical, 12)
                        .foregroundColor(theme.onPrimary).background(theme.primary).clipShape(RoundedCornerShape(8))
                }
                .disabled(model.loading)

                if let msg = model.message { Text(msg).font(.footnote).foregroundColor(theme.onSurfaceVariant) }

                if !model.reflection.isEmpty {
                    MarkdownText(text: model.reflection, color: theme.onSecondaryContainer)
                        .padding(16).frame(maxWidth: .infinity, alignment: .leading)
                        .background(theme.secondaryContainer).clipShape(RoundedCornerShape(12))
                }

                if model.cadence != "OFF" {
                    HStack {
                        Button("Snooze a week") { model.snoozeWeek() }.foregroundColor(theme.primary)
                        Button("Turn off") { model.setCadence("OFF") }.foregroundColor(theme.primary)
                    }
                    .font(.footnote).padding(.top, 4)
                }
            }
            .padding(16)
        }
    }
}
