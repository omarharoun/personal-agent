// RunTaskView.swift — agent task runs with live tool-use previews and a
// human-in-the-loop approval card (Android TaskRunScreen). Submits a task to
// /v1/runs, watches the SSE event stream (tool started/completed, reasoning,
// answer), and hydrates "what it found" + written documents from the transcript.

import SwiftUI
import Shared

@MainActor
final class RunTaskModel: ObservableObject {
    struct Activity: Identifiable {
        let id = UUID()
        let tool: String
        var done = false
        var durationSec: Double?
        var error = false
        var preview: String
    }

    @Published var running = false
    @Published var activities: [Activity] = []
    @Published var reasoning: String?
    @Published var answer = ""
    @Published var usageLine: String?
    @Published var findings: [ToolFinding] = []
    @Published var documents: [WrittenDocument] = []
    @Published var approvalCommand: String?
    @Published var approvalChoices: [String] = []
    @Published var error: String?

    private let client: HermesClient?
    private var runId: String?
    init(env: AppEnvironment) { self.client = env.makeClient() }

    func run(_ input: String) {
        let text = input.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !text.isEmpty, !running, let client else { return }
        running = true
        activities = []; reasoning = nil; answer = ""; usageLine = nil
        findings = []; documents = []; approvalCommand = nil; error = nil

        _Concurrency.Task {
            do {
                let started = try await client.startRun(input: text)
                runId = started.runId
                try await LifeAgentIos.shared.runEvents(
                    client: client, runId: started.runId,
                    onToolStarted: { tool, preview in
                        _Concurrency.Task { @MainActor in self.activities.append(Activity(tool: tool, preview: preview)) }
                    },
                    onToolCompleted: { tool, dur, err in
                        _Concurrency.Task { @MainActor in
                            if let i = self.activities.lastIndex(where: { $0.tool == tool && !$0.done }) {
                                self.activities[i].done = true
                                self.activities[i].durationSec = dur.doubleValue
                                self.activities[i].error = err.boolValue
                            }
                        }
                    },
                    onReasoning: { t in _Concurrency.Task { @MainActor in self.reasoning = t } },
                    onDelta: { d in _Concurrency.Task { @MainActor in self.answer += d } },
                    onCompleted: { output, inTok, outTok, total in
                        _Concurrency.Task { @MainActor in
                            if !output.isEmpty { self.answer = output }
                            if total.int64Value > 0 {
                                self.usageLine = "\(inTok.int64Value) in · \(outTok.int64Value) out · \(total.int64Value) total tokens"
                            }
                        }
                    },
                    onFailed: { m in _Concurrency.Task { @MainActor in self.error = m } },
                    onApprovalRequested: { command, choices in
                        _Concurrency.Task { @MainActor in self.approvalCommand = command; self.approvalChoices = choices }
                    },
                    onApprovalResolved: { _ in _Concurrency.Task { @MainActor in self.approvalCommand = nil } })

                // Hydrate results from the transcript (the stream carried calls, not content).
                if let msgs = try? await client.sessionMessages(sessionId: started.runId) {
                    findings = LifeAgentIos.shared.runFindings(messages: msgs)
                    documents = LifeAgentIos.shared.runDocuments(messages: msgs)
                }
            } catch {
                self.error = hermesMessage(error) ?? "The task couldn't run."
            }
            running = false
        }
    }

    func respondApproval(_ choice: String) {
        guard let client, let runId else { return }
        approvalCommand = nil
        _Concurrency.Task { try? await client.submitApproval(runId: runId, choice: choice) }
    }
}

struct RunTaskView: View {
    @StateObject private var model: RunTaskModel
    @Environment(\.theme) private var theme
    @State private var draft = ""
    init(env: AppEnvironment) { _model = StateObject(wrappedValue: RunTaskModel(env: env)) }

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 12) {
                Text("Give your agent a task. You'll see each tool it uses live, and approve anything sensitive before it runs.")
                    .font(.callout).foregroundColor(theme.onSurfaceVariant)

                TextField("What should the agent do?", text: $draft, axis: .vertical)
                    .lineLimit(2...5).foregroundColor(theme.onSurface)
                    .padding(12).background(theme.surfaceVariant).clipShape(RoundedCornerShape(8))
                Button { model.run(draft); draft = "" } label: {
                    HStack { if model.running { ProgressView().controlSize(.small) }; Text(model.running ? "Running…" : "Run task") }
                        .frame(maxWidth: .infinity).padding(.vertical, 12)
                        .foregroundColor(theme.onPrimary).background(theme.primary).clipShape(RoundedCornerShape(8))
                }.disabled(model.running || draft.trimmingCharacters(in: .whitespaces).isEmpty)

                if let err = model.error { Text(err).font(.callout).foregroundColor(theme.error) }

                if let cmd = model.approvalCommand { approvalCard(cmd) }

                if !model.activities.isEmpty || model.running {
                    Text("ACTIVITY").hermesDisplayLabel(size: 11).foregroundColor(theme.onSurfaceVariant).padding(.top, 6)
                    ForEach(model.activities) { a in activityRow(a) }
                    if model.running && model.activities.isEmpty {
                        HStack(spacing: 8) { ProgressView().controlSize(.small); Text("thinking…").foregroundColor(theme.onSurfaceVariant) }
                    }
                }

                if let reasoning = model.reasoning, !reasoning.isEmpty {
                    DisclosureGroup("Reasoning") {
                        Text(reasoning).font(.caption).foregroundColor(theme.onSurfaceVariant)
                    }.tint(theme.primary)
                }

                if !model.answer.isEmpty { MarkdownText(text: model.answer, color: theme.onBackground) }
                if let usage = model.usageLine { Text(usage).hermesMono(size: 11).foregroundColor(theme.onSurfaceVariant) }

                if !model.documents.isEmpty {
                    Text("DOCUMENTS").hermesDisplayLabel(size: 11).foregroundColor(theme.onSurfaceVariant).padding(.top, 6)
                    ForEach(Array(model.documents.enumerated()), id: \.offset) { _, d in
                        HStack(spacing: 8) {
                            Image(systemName: "doc.text").foregroundColor(theme.tertiary)
                            Text(d.filename).font(.callout).foregroundColor(theme.onSurface)
                            Spacer()
                            Text("\(d.content.count) chars").font(.caption2).foregroundColor(theme.onSurfaceVariant)
                        }
                        .padding(12).background(theme.surface).clipShape(RoundedCornerShape(12))
                        .overlay(RoundedCornerShape(12).stroke(theme.outline, lineWidth: 1))
                    }
                }

                if !model.findings.isEmpty {
                    Text("WHAT IT FOUND").hermesDisplayLabel(size: 11).foregroundColor(theme.onSurfaceVariant).padding(.top, 6)
                    ForEach(Array(model.findings.enumerated()), id: \.offset) { _, f in
                        VStack(alignment: .leading, spacing: 4) {
                            Text(f.tool).font(.caption.weight(.semibold)).foregroundColor(theme.onSurfaceVariant)
                            Text(String(f.result.prefix(300))).font(.caption).foregroundColor(theme.onSurface)
                        }
                        .padding(12).frame(maxWidth: .infinity, alignment: .leading)
                        .background(theme.surface).clipShape(RoundedCornerShape(12))
                        .overlay(RoundedCornerShape(12).stroke(theme.outline, lineWidth: 1))
                    }
                }
            }
            .padding(16)
        }
    }

    private func activityRow(_ a: RunTaskModel.Activity) -> some View {
        HStack(spacing: 10) {
            if !a.done { ProgressView().controlSize(.mini) }
            else { Image(systemName: a.error ? "xmark.circle" : "checkmark.circle")
                .foregroundColor(a.error ? theme.error : theme.tertiary) }
            Text("\(toolEmoji(a.tool)) \(a.tool)").font(.callout).foregroundColor(theme.onSurface)
            if !a.preview.isEmpty { Text(a.preview).font(.caption).foregroundColor(theme.onSurfaceVariant).lineLimit(1) }
            Spacer()
            if let d = a.durationSec { Text(String(format: "%.1fs", d)).font(.caption2).foregroundColor(theme.onSurfaceVariant) }
        }
    }

    private func approvalCard(_ cmd: String) -> some View {
        VStack(alignment: .leading, spacing: 10) {
            Text("🙋 APPROVAL NEEDED").hermesDisplayLabel(size: 11).foregroundColor(theme.onSecondaryContainer)
            Text(cmd).hermesMono(size: 12).foregroundColor(theme.onSurface)
                .padding(10).frame(maxWidth: .infinity, alignment: .leading)
                .background(theme.surface).clipShape(RoundedCornerShape(8))
            HStack {
                Button("Approve") { model.respondApproval("once") }.foregroundColor(theme.tertiary).fontWeight(.bold)
                Button("Deny") { model.respondApproval("deny") }.foregroundColor(theme.onSurfaceVariant)
                if model.approvalChoices.contains("session") {
                    Button("Approve for the rest") { model.respondApproval("session") }.foregroundColor(theme.primary)
                }
            }.font(.callout)
        }
        .padding(14).background(theme.secondaryContainer).clipShape(RoundedCornerShape(12))
        .overlay(RoundedCornerShape(12).stroke(theme.tertiary, lineWidth: 1))
    }

    private func toolEmoji(_ tool: String) -> String {
        let t = tool.lowercased()
        if t.hasPrefix("browser") { return "🌐" }
        if t.hasPrefix("web") { return "🔍" }
        if t.contains("file") || t.hasPrefix("write") { return "📄" }
        if t.contains("terminal") || t.contains("shell") || t.contains("bash") { return "💻" }
        if t.hasPrefix("image") { return "🎨" }
        if t.hasPrefix("search") { return "🔎" }
        if t.hasPrefix("memory") { return "💾" }
        return "⚙️"
    }
}
