// HermesClient.swift — the iOS Hermes Life Agent client (networking layer).
//
// ⚠️ Requires macOS + Xcode to build/run — it CANNOT be built in the Linux dev
// sandbox. It is written against the SAME wire contract the Android app ships and
// that was verified live against Hermes v0.18.0 (see docs/PHASE0.md–PHASE2.md):
//   GET  /health
//   POST /v1/chat/completions   (OpenAI SSE streaming; chat.completion.chunk → [DONE])
//   GET/POST/DELETE /api/jobs    (reminders)
// with headers: Authorization: Bearer <key>, X-Hermes-Session-Key (memory scope),
// X-Hermes-Session-Id (per-conversation).
//
// This is a pure-Swift URLSession implementation (no Kotlin-Flow interop needed),
// so it's a clean, self-contained starting point. It mirrors the shared Kotlin
// `HermesClient`; keep the two in sync.

import Foundation

struct HermesConfig {
    var baseURL: String   // ROOT origin, e.g. http://192.168.1.20:8642 (no /v1)
    var apiKey: String
    var sessionKey: String // stable per-user "lifeagent:user-<id>" (memory scope)

    static let sessionKeyHeader = "X-Hermes-Session-Key"
    static let sessionIdHeader = "X-Hermes-Session-Id"
    static let defaultModelId = "hermes-agent"

    /// Normalize user input into a root origin (add scheme, strip trailing /v1).
    static func normalizeBaseURL(_ raw: String) -> String? {
        var s = raw.trimmingCharacters(in: .whitespacesAndNewlines)
        if s.isEmpty { return nil }
        if !s.lowercased().hasPrefix("http://") && !s.lowercased().hasPrefix("https://") {
            s = "http://" + s
        }
        while s.hasSuffix("/") { s.removeLast() }
        if s.lowercased().hasSuffix("/v1") { s = String(s.dropLast(3)) }
        while s.hasSuffix("/") { s.removeLast() }
        return s.isEmpty ? nil : s
    }

    /// Mint a stable memory-scope key: lifeagent:user-<32 hex>.
    static func newSessionKey() -> String {
        var hex = ""
        for _ in 0..<16 { hex += String(format: "%02x", Int.random(in: 0..<256)) }
        return "lifeagent:user-\(hex)"
    }
}

struct HermesError: LocalizedError {
    let message: String
    var errorDescription: String? { message }
}

enum ChatStreamEvent {
    case delta(String)
    case done
}

/// Minimal wire models.
private struct Health: Decodable { let status: String?; let version: String? }
private struct ChatChunk: Decodable {
    struct Choice: Decodable { struct Delta: Decodable { let content: String? }; let delta: Delta? }
    let choices: [Choice]
}
struct HermesJob: Decodable, Identifiable {
    let id: String
    let name: String?
    let prompt: String?
    let schedule_display: String?
    let next_run_at: String?
    let state: String?
    var label: String { (name?.isEmpty == false ? name : prompt) ?? "Reminder" }
}
private struct JobsList: Decodable { let jobs: [HermesJob] }
private struct JobEnvelope: Decodable { let job: HermesJob }

final class HermesClient {
    let config: HermesConfig
    private let session: URLSession

    init(config: HermesConfig, session: URLSession = .shared) {
        self.config = config
        self.session = session
    }

    private func request(_ path: String, method: String = "GET", sessionId: String? = nil) -> URLRequest {
        var req = URLRequest(url: URL(string: config.baseURL + path)!)
        req.httpMethod = method
        if !config.apiKey.isEmpty { req.setValue("Bearer \(config.apiKey)", forHTTPHeaderField: "Authorization") }
        if !config.sessionKey.isEmpty { req.setValue(config.sessionKey, forHTTPHeaderField: HermesConfig.sessionKeyHeader) }
        if let sid = sessionId { req.setValue(sid, forHTTPHeaderField: HermesConfig.sessionIdHeader) }
        return req
    }

    /// GET /health — used by Connect to verify reachability + auth.
    func health() async throws -> String {
        let (data, resp) = try await session.data(for: request("/health"))
        try Self.check(resp, data)
        guard let h = try? JSONDecoder().decode(Health.self, from: data), let status = h.status else {
            throw HermesError(message: "That URL answered, but it doesn't look like a Hermes API server.")
        }
        return status
    }

    /// POST /v1/chat/completions (stream:true). Yields text deltas then .done.
    func streamChat(messages: [[String: String]], sessionId: String?) -> AsyncThrowingStream<ChatStreamEvent, Error> {
        var req = request("/v1/chat/completions", method: "POST", sessionId: sessionId)
        req.setValue("application/json", forHTTPHeaderField: "Content-Type")
        req.setValue("text/event-stream", forHTTPHeaderField: "Accept")
        let body: [String: Any] = ["model": HermesConfig.defaultModelId, "messages": messages, "stream": true]
        req.httpBody = try? JSONSerialization.data(withJSONObject: body)

        return AsyncThrowingStream { continuation in
            let task = Task {
                do {
                    let (bytes, resp) = try await session.bytes(for: req)
                    if let http = resp as? HTTPURLResponse, !(200...299).contains(http.statusCode) {
                        throw HermesError(message: Self.statusMessage(http.statusCode))
                    }
                    for try await line in bytes.lines {
                        guard line.hasPrefix("data:") else { continue }
                        let payload = line.dropFirst(5).trimmingCharacters(in: .whitespaces)
                        if payload == "[DONE]" { continuation.yield(.done); break }
                        if let d = payload.data(using: .utf8),
                           let chunk = try? JSONDecoder().decode(ChatChunk.self, from: d),
                           let text = chunk.choices.first?.delta?.content, !text.isEmpty {
                            continuation.yield(.delta(text))
                        }
                    }
                    continuation.finish()
                } catch {
                    continuation.finish(throwing: error)
                }
            }
            continuation.onTermination = { _ in task.cancel() }
        }
    }

    /// Convenience: collect a whole reply as one string.
    func complete(messages: [[String: String]], sessionId: String? = nil) async throws -> String {
        var out = ""
        for try await ev in streamChat(messages: messages, sessionId: sessionId) {
            if case let .delta(t) = ev { out += t }
        }
        return out.trimmingCharacters(in: .whitespacesAndNewlines)
    }

    // MARK: Reminders (/api/jobs)

    func listJobs() async throws -> [HermesJob] {
        let (data, resp) = try await session.data(for: request("/api/jobs"))
        try Self.check(resp, data)
        return (try JSONDecoder().decode(JobsList.self, from: data)).jobs
    }

    /// Create a one-shot reminder. `schedule` is a duration like "90m".
    func createJob(name: String, schedule: String, prompt: String) async throws -> HermesJob {
        var req = request("/api/jobs", method: "POST")
        req.setValue("application/json", forHTTPHeaderField: "Content-Type")
        req.httpBody = try JSONSerialization.data(withJSONObject: [
            "name": name, "schedule": schedule, "prompt": prompt, "deliver": "local",
        ])
        let (data, resp) = try await session.data(for: req)
        try Self.check(resp, data)
        return (try JSONDecoder().decode(JobEnvelope.self, from: data)).job
    }

    func deleteJob(id: String) async throws {
        let (data, resp) = try await session.data(for: request("/api/jobs/\(id)", method: "DELETE"))
        try Self.check(resp, data)
    }

    /// One-shot duration schedule ("<N>m") from now to a target date.
    static func oneShotScheduleMinutes(target: Date, now: Date = Date()) -> String {
        let minutes = max(1, Int(ceil(target.timeIntervalSince(now) / 60.0)))
        return "\(minutes)m"
    }

    // MARK: helpers

    private static func check(_ resp: URLResponse, _ data: Data) throws {
        guard let http = resp as? HTTPURLResponse else { return }
        if !(200...299).contains(http.statusCode) {
            throw HermesError(message: statusMessage(http.statusCode))
        }
    }

    private static func statusMessage(_ code: Int) -> String {
        switch code {
        case 401: return "Authentication failed (401). Check the API key matches your Hermes API_SERVER_KEY."
        case 403: return "Access refused (403). Confirm the API key and that API_SERVER_KEY is set on Hermes."
        case 404: return "Not found (404). Check the base URL — it should be your Hermes root, e.g. http://host:8642."
        case 500...599: return "Your Hermes hit a server error (\(code))."
        default: return "Hermes returned an error (\(code))."
        }
    }
}
