// ConnectView.swift — the front door for bring-your-own-Hermes (Path A). Points
// the app at the user's own Hermes, tests it via GET /health, and persists the
// connection through the 🔒 encrypted HermesConfigStore. Mirrors Android's
// ConnectScreen. No default/hidden backend — the user-entered URL is the only one.

import SwiftUI
import Shared

struct ConnectView: View {
    @EnvironmentObject var env: AppEnvironment
    @Environment(\.theme) private var theme

    @State private var baseURL = "http://"
    @State private var apiKey = ""
    @State private var showKey = false
    @State private var testing = false
    @State private var error: String?

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 0) {
                Text("Connect your Life Agent")
                    .font(.title.bold())
                    .foregroundColor(theme.onBackground)
                    .padding(.top, 8)

                Text("This app is the front door to your own Hermes agent — the private ‘brain’ that remembers your notes, reminders, and reflections. Run Hermes on your computer or a small server, then point the app at it. Your data stays on your server; we never see it.")
                    .font(.body)
                    .foregroundColor(theme.onSurfaceVariant)
                    .padding(.top, 12)

                field(title: "Hermes address", text: $baseURL,
                      placeholder: "http://192.168.1.20:8642", secure: false)
                    .textInputAutocapitalization(.never)
                    .autocorrectionDisabled()
                    .keyboardType(.URL)
                    .padding(.top, 24)
                Text("The address where your Hermes API server is running (default port 8642).")
                    .font(.footnote)
                    .foregroundColor(theme.onSurfaceVariant)
                    .padding(.top, 4)

                apiKeyField
                    .padding(.top, 16)
                HStack(spacing: 6) {
                    Image(systemName: "lock.fill").font(.system(size: 12))
                    Text("The API_SERVER_KEY you set on your Hermes. Stored encrypted on this device only.")
                }
                .font(.footnote)
                .foregroundColor(theme.onSurfaceVariant)
                .padding(.top, 4)

                if let warn = plaintextWarning {
                    HStack(alignment: .top, spacing: 6) {
                        Image(systemName: "info.circle").font(.system(size: 13)).foregroundColor(theme.tertiary)
                        Text(warn).font(.footnote).foregroundColor(theme.onSurfaceVariant)
                    }
                    .padding(.top, 12)
                }

                if let error {
                    HStack(alignment: .top, spacing: 8) {
                        Image(systemName: "exclamationmark.triangle.fill")
                        Text(error)
                    }
                    .font(.callout)
                    .foregroundColor(theme.onErrorContainer)
                    .padding(12)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .background(theme.errorContainer)
                    .clipShape(RoundedCornerShape(8))
                    .padding(.top, 16)
                }

                Button(action: testAndConnect) {
                    HStack(spacing: 10) {
                        if testing { ProgressView().tint(theme.onPrimary) }
                        Text(testing ? "TESTING…" : "TEST & CONNECT")
                            .hermesDisplayLabel()
                    }
                    .frame(maxWidth: .infinity, minHeight: 52)
                    .foregroundColor(theme.onPrimary)
                    .background(theme.primary)
                    .clipShape(RoundedCornerShape(10))
                }
                .disabled(testing)
                .padding(.top, 24)
            }
            .padding(20)
        }
        .background(theme.background.ignoresSafeArea())
    }

    /// Non-blocking warning when the URL is a plaintext http:// remote host.
    private var plaintextWarning: String? {
        guard let norm = HermesConfig.companion.normalizeBaseUrl(raw: baseURL) else { return nil }
        let cfg = HermesConfig(baseUrl: norm, apiKey: "", sessionKey: "")
        return cfg.isPlaintextRemote
            ? "This is a plaintext (http://) address on a remote host. Your key and data would cross the network unencrypted. Prefer https, a VPN, or a local/LAN address."
            : nil
    }

    // MARK: fields

    @ViewBuilder private func field(title: String, text: Binding<String>, placeholder: String, secure: Bool) -> some View {
        VStack(alignment: .leading, spacing: 6) {
            Text(title).font(.caption).foregroundColor(theme.onSurfaceVariant)
            TextField(placeholder, text: text)
                .foregroundColor(theme.onSurface)
                .padding(14)
                .background(theme.surfaceVariant)
                .clipShape(RoundedCornerShape(8))
                .overlay(RoundedCornerShape(8).stroke(theme.outline, lineWidth: 1))
                .disabled(testing)
        }
    }

    private var apiKeyField: some View {
        VStack(alignment: .leading, spacing: 6) {
            Text("API key").font(.caption).foregroundColor(theme.onSurfaceVariant)
            HStack {
                Group {
                    if showKey { TextField("API key", text: $apiKey) }
                    else { SecureField("API key", text: $apiKey) }
                }
                .foregroundColor(theme.onSurface)
                .textInputAutocapitalization(.never)
                .autocorrectionDisabled()
                Button(showKey ? "Hide" : "Show") { showKey.toggle() }
                    .font(.footnote)
                    .foregroundColor(theme.primary)
            }
            .padding(14)
            .background(theme.surfaceVariant)
            .clipShape(RoundedCornerShape(8))
            .overlay(RoundedCornerShape(8).stroke(theme.outline, lineWidth: 1))
            .disabled(testing)
        }
    }

    // MARK: logic

    private func testAndConnect() {
        guard let norm = HermesConfig.companion.normalizeBaseUrl(raw: baseURL) else {
            error = "Enter your Hermes address, e.g. http://192.168.1.20:8642"; return
        }
        guard !apiKey.isEmpty else {
            error = "Enter the API key you set on your Hermes (API_SERVER_KEY)."; return
        }
        testing = true; error = nil
        let cfg = HermesConfig(baseUrl: norm, apiKey: apiKey, sessionKey: env.configStore.sessionKey())
        let client = env.makeClient(for: cfg)
        // `Task` is qualified — `import Shared` brings Kotlin's tasks.Task into scope.
        _Concurrency.Task {
            do {
                let health = try await client.health()
                guard (health.status ?? "").lowercased() == "ok" else {
                    testing = false
                    error = "Your Hermes replied but not with status ok. Is the API server healthy?"
                    return
                }
                _ = env.configStore.save(baseUrl: norm, apiKey: apiKey)
                client.close()
                testing = false
                env.refreshConnected()
            } catch {
                client.close()
                testing = false
                self.error = (error as? HermesException)?.message
                    ?? "Couldn't connect: \(error.localizedDescription)"
            }
        }
    }
}

// A rounded-rectangle shape used across the app (matches Hermes' small radii).
struct RoundedCornerShape: Shape {
    let radius: CGFloat
    init(_ radius: CGFloat) { self.radius = radius }
    func path(in rect: CGRect) -> Path {
        Path(roundedRect: rect, cornerRadius: radius)
    }
}
