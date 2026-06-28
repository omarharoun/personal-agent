import SwiftUI
import Shared

/// Settings — the place to provision / replace / delete the on-device AI model
/// after onboarding, plus an at-a-glance status of whether AI is ready.
struct SettingsView: View {
    @EnvironmentObject var model: AppModel

    var body: some View {
        NavigationStack {
            Form {
                Section("Intelligence") {
                    NavigationLink {
                        AiModelSettingsView(model: model)
                    } label: {
                        HStack {
                            Label("AI Model", systemImage: "cpu")
                            Spacer()
                            Text(model.llmAvailable ? "Ready" : "Not set up")
                                .foregroundStyle(model.llmAvailable ? .green : .secondary)
                                .font(.subheadline)
                        }
                    }
                } footer: {
                    Text("Download, replace, or remove the on-device model. "
                         + "Everything runs offline once installed.")
                }

                // BYO-key cloud (Anthropic / OpenAI). Off by default → on-device only.
                Section("Cloud (optional)") {
                    NavigationLink {
                        CloudSettingsView(keyStore: model.cloudKeyStore)
                    } label: {
                        Label("Cloud provider & API key", systemImage: "key")
                    }
                } footer: {
                    Text("Optionally connect your own Anthropic (Claude) or OpenAI key "
                         + "for harder questions. Billed separately by the provider; a "
                         + "Claude Pro / ChatGPT Plus subscription can't be used. With no "
                         + "key set, the app stays fully on-device.")
                }
            }
            .navigationTitle("Settings")
        }
    }
}

/// Hosts `ModelSetupView` for the Settings (management) context. Owns the
/// `ModelSetupModel` as a `@StateObject`, built from the app's provisioner, and
/// refreshes installed state on appear so it reflects on-disk reality.
struct AiModelSettingsView: View {
    @StateObject private var setup: ModelSetupModel

    @MainActor
    init(model: AppModel) {
        _setup = StateObject(wrappedValue: model.makeModelSetupModel())
    }

    var body: some View {
        ModelSetupView(setup: setup)
            .navigationTitle("AI Model")
            .navigationBarTitleDisplayMode(.inline)
            .onAppear { setup.refreshInstalled() }
    }
}
