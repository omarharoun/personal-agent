// HermesLifeAgentView.swift — a minimal SwiftUI Connect + streaming Chat for the
// iOS Hermes Life Agent client, mirroring the Android Phase-1 flow.
//
// ⚠️ Requires macOS + Xcode to build/run (Kotlin/Native iOS + Swift link on a
// Mac). This is a ready-to-adopt starting point; to make it the app entry, point
// `iOSApp.swift`'s root at `HermesLifeAgentView()`.
//
// 🔒 REVIEW REQUIRED — credential + session-key storage + trust boundary:
//   - The API key + session key are stored in the iOS Keychain (below), never in
//     UserDefaults/plaintext/logs.
//   - The user-entered base URL is the ONLY backend — no default/hidden server.
// Mirrors the Android 🔒 gates; a human must review before real users rely on it.

import SwiftUI
import Security

// MARK: - Keychain-backed connection store (🔒)

enum HermesKeychain {
    private static let service = "com.personalagent.hermes"

    static func set(_ key: String, _ value: String) {
        let q: [String: Any] = [kSecClass as String: kSecClassGenericPassword,
                                kSecAttrService as String: service,
                                kSecAttrAccount as String: key]
        SecItemDelete(q as CFDictionary)
        var add = q
        add[kSecValueData as String] = value.data(using: .utf8)
        add[kSecAttrAccessible as String] = kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly
        SecItemAdd(add as CFDictionary, nil)
    }

    static func get(_ key: String) -> String? {
        let q: [String: Any] = [kSecClass as String: kSecClassGenericPassword,
                                kSecAttrService as String: service,
                                kSecAttrAccount as String: key,
                                kSecReturnData as String: true]
        var out: AnyObject?
        guard SecItemCopyMatching(q as CFDictionary, &out) == errSecSuccess,
              let data = out as? Data else { return nil }
        return String(data: data, encoding: .utf8)
    }

    static func remove(_ key: String) {
        SecItemDelete([kSecClass as String: kSecClassGenericPassword,
                       kSecAttrService as String: service,
                       kSecAttrAccount as String: key] as CFDictionary)
    }
}

struct HermesConnectionStore {
    func load() -> HermesConfig? {
        guard let base = HermesKeychain.get("base_url"), !base.isEmpty,
              let key = HermesKeychain.get("api_key"), !key.isEmpty else { return nil }
        return HermesConfig(baseURL: base, apiKey: key, sessionKey: sessionKey())
    }
    func save(baseURL: String, apiKey: String) {
        HermesKeychain.set("base_url", baseURL)
        HermesKeychain.set("api_key", apiKey)
    }
    func sessionKey() -> String {
        if let k = HermesKeychain.get("session_key"), !k.isEmpty { return k }
        let fresh = HermesConfig.newSessionKey()
        HermesKeychain.set("session_key", fresh)
        return fresh
    }
    func disconnect() { HermesKeychain.remove("base_url"); HermesKeychain.remove("api_key") }
}

// MARK: - Root

struct HermesLifeAgentView: View {
    @State private var config: HermesConfig? = HermesConnectionStore().load()
    private let store = HermesConnectionStore()

    var body: some View {
        if let config {
            HermesChatView(client: HermesClient(config: config)) {
                store.disconnect(); self.config = nil
            }
        } else {
            HermesConnectView { self.config = store.load() }
        }
    }
}

// MARK: - Connect

struct HermesConnectView: View {
    var onConnected: () -> Void
    private let store = HermesConnectionStore()

    @State private var baseURL = "http://"
    @State private var apiKey = ""
    @State private var testing = false
    @State private var error: String?

    var body: some View {
        NavigationView {
            Form {
                Section(footer: Text("Your data lives on your Hermes — we never see it.")) {
                    TextField("Hermes address (http://host:8642)", text: $baseURL)
                        .textInputAutocapitalization(.never).disableAutocorrection(true)
                    SecureField("API key", text: $apiKey)
                }
                if let error { Text(error).foregroundColor(.red) }
                Button(testing ? "Testing…" : "Test & Connect") { connect() }
                    .disabled(testing)
            }
            .navigationTitle("Connect your Life Agent")
        }
    }

    private func connect() {
        guard let norm = HermesConfig.normalizeBaseURL(baseURL) else {
            error = "Enter your Hermes address, e.g. http://192.168.1.20:8642"; return
        }
        guard !apiKey.isEmpty else { error = "Enter the API key you set on your Hermes."; return }
        testing = true; error = nil
        Task {
            let cfg = HermesConfig(baseURL: norm, apiKey: apiKey, sessionKey: store.sessionKey())
            do {
                let status = try await HermesClient(config: cfg).health()
                guard status.lowercased() == "ok" else {
                    testing = false; error = "Your Hermes replied but not with status ok."; return
                }
                store.save(baseURL: norm, apiKey: apiKey)
                testing = false; onConnected()
            } catch {
                testing = false; self.error = (error as? HermesError)?.message ?? error.localizedDescription
            }
        }
    }
}

// MARK: - Chat

private struct ChatMessage: Identifiable { let id = UUID(); let isUser: Bool; var text: String }

struct HermesChatView: View {
    let client: HermesClient
    var onDisconnect: () -> Void

    @State private var messages: [ChatMessage] = []
    @State private var draft = ""
    @State private var sending = false
    private let conversationId = "lifeagent-conv-\(Int.random(in: 0..<1_000_000))"

    var body: some View {
        NavigationView {
            VStack(spacing: 0) {
                ScrollView {
                    LazyVStack(alignment: .leading, spacing: 14) {
                        ForEach(messages) { m in
                            Text(m.text)
                                .frame(maxWidth: .infinity, alignment: m.isUser ? .trailing : .leading)
                                .padding(m.isUser ? 10 : 0)
                                .background(m.isUser ? Color.secondary.opacity(0.15) : .clear)
                                .cornerRadius(14)
                        }
                    }.padding()
                }
                HStack {
                    TextField("Message your Life Agent…", text: $draft)
                        .textFieldStyle(.roundedBorder)
                    Button("Send") { send() }.disabled(draft.isEmpty || sending)
                }.padding()
            }
            .navigationTitle("Life Agent")
            .toolbar { ToolbarItem(placement: .navigationBarTrailing) {
                Button("Disconnect", action: onDisconnect)
            } }
        }
    }

    private func send() {
        let text = draft.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !text.isEmpty else { return }
        draft = ""; sending = true
        messages.append(ChatMessage(isUser: true, text: text))
        let wire = messages.filter { !$0.text.isEmpty }
            .map { ["role": $0.isUser ? "user" : "assistant", "content": $0.text] }
        let replyIndex = messages.count
        messages.append(ChatMessage(isUser: false, text: ""))
        Task {
            do {
                for try await ev in client.streamChat(messages: wire, sessionId: conversationId) {
                    if case let .delta(t) = ev { messages[replyIndex].text += t }
                }
            } catch {
                messages[replyIndex].text = (error as? HermesError)?.message ?? "Something went wrong talking to your Hermes."
            }
            sending = false
        }
    }
}
