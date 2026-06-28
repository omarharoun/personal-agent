import SwiftUI
import Shared

/// Self-contained Settings sub-view for the **bring-your-own-key cloud option**
/// (Stream 3). The integrator embeds this in the Settings surface (e.g. via a
/// `NavigationLink` from `SettingsView`); it does NOT touch `ContentView`.
///
/// Lets the user pick the active provider (Anthropic / OpenAI), paste an API key
/// into a SECURE field, and Save or Clear it.
///
/// 🔒 The key is written straight into the encrypted `CloudKeyStore`
/// (`IosFactories.createCloudKeyStore(crypto:)`, AES-GCM at rest). It is never
/// logged and the field is `SecureField`-masked. With no key set the app stays
/// fully on-device; a newly-saved key takes effect immediately (the cloud client
/// is resolved per-use via DynamicCloudClient — no restart needed).
///
/// Construct it with the app's shared `CloudKeyStore`, e.g. expose a
/// `cloudKeyStore` on `AppModel` built via
/// `IosFactories.shared.createCloudKeyStore(crypto:)` and pass it here.
struct CloudSettingsView: View {
    let keyStore: CloudKeyStore

    @State private var provider: CloudProvider = .anthropic
    /// Masked input. We never echo a stored key back into the field.
    @State private var keyInput: String = ""
    @State private var status: String?
    @State private var hasSavedKey: Bool = false

    /// Providers to list. Mirrors the shared `CloudProvider` enum.
    private let providers: [CloudProvider] = [.anthropic, .openai]

    var body: some View {
        Form {
            Section {
                Picker("Provider", selection: $provider) {
                    ForEach(providers, id: \.self) { p in
                        Text(p.displayName).tag(p)
                    }
                }
                .pickerStyle(.segmented)

                Text(hasSavedKey
                     ? "A key is saved for \(provider.displayName). Enter a new one to replace it."
                     : "No key saved for \(provider.displayName).")
                    .font(.footnote)
                    .foregroundStyle(.secondary)

                SecureField("API key", text: $keyInput)
                    .textInputAutocapitalization(.never)
                    .autocorrectionDisabled(true)

                HStack {
                    Button("Save") { save() }
                        .buttonStyle(.borderedProminent)
                    Button("Clear", role: .destructive) { clear() }
                        .buttonStyle(.bordered)
                }

                if let status {
                    Text(status)
                        .font(.footnote)
                        .foregroundStyle(.tint)
                }
            } header: {
                Text("Cloud assist (your API key)")
            } footer: {
                Text("Uses YOUR developer API key, billed separately by Anthropic/OpenAI. "
                     + "A Claude Pro or ChatGPT Plus consumer subscription CANNOT be used here. "
                     + "If no key is set, the app stays fully on-device.")
            }
        }
        .navigationTitle("Cloud assist")
        .navigationBarTitleDisplayMode(.inline)
        .onAppear {
            if let active = keyStore.activeProvider() { provider = active }
            refreshHasKey()
        }
        .onChange(of: provider) { _ in
            keyInput = ""
            status = nil
            refreshHasKey()
        }
    }

    private func refreshHasKey() {
        hasSavedKey = keyStore.hasKey(provider: provider)
    }

    private func save() {
        let trimmed = keyInput.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty else {
            status = "Enter a key to save."
            return
        }
        keyStore.setApiKey(provider: provider, key: trimmed)
        keyStore.setActiveProvider(provider: provider)
        keyInput = ""
        hasSavedKey = true
        status = "Saved — cloud assist is ready. No restart needed."
    }

    private func clear() {
        keyStore.clearApiKey(provider: provider)
        if keyStore.activeProvider() == provider {
            keyStore.clearActiveProvider()
        }
        keyInput = ""
        hasSavedKey = false
        status = "Cleared. The app is fully on-device for \(provider.displayName)."
    }
}
