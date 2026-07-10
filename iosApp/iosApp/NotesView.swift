// NotesView.swift — quick memo capture (Android NotesScreen). The note is sent to
// the user's Hermes memory (server-side) so the agent can recall it; a local index
// (MemoStore) mirrors saved memos so the screen + home can list them back.

import SwiftUI
import Shared

@MainActor
final class NotesModel: ObservableObject {
    @Published var saving = false
    @Published var recent: [Memo] = []
    @Published var message: String?

    private let env: AppEnvironment
    private let client: HermesClient?

    init(env: AppEnvironment) { self.env = env; self.client = env.makeClient(); refresh() }
    func refresh() { recent = env.memoStore.all() }

    func save(_ text: String) {
        let note = text.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !note.isEmpty, !saving, let client else { return }
        saving = true
        _Concurrency.Task {
            let msg = LifeAgentIos.shared.wireMessage(
                role: "user",
                content: "Please remember this note for me and store it in your memory: \"\(note)\". Reply with a short confirmation only.")
            do {
                let reply = try await client.complete(messages: [msg], sessionId: "lifeagent-notes")
                let now = LifeAgentIos.shared.nowMillis()
                env.memoStore.add(memo: Memo(id: Ids.shared.next(nowMillis: now), text: note, savedAt: now))
                recent = env.memoStore.all()
                message = reply.isEmpty ? "Saved to your agent's memory." : reply
            } catch {
                message = hermesMessage(error) ?? "Couldn't save the note."
            }
            saving = false
        }
    }

    func forget(_ id: String) { env.memoStore.remove(id: id); recent = env.memoStore.all() }
}

struct NotesView: View {
    @StateObject private var model: NotesModel
    @Environment(\.theme) private var theme
    @State private var draft = ""

    init(env: AppEnvironment) { _model = StateObject(wrappedValue: NotesModel(env: env)) }

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 12) {
                Text("Jot something down and your agent will remember it. Saved to your Hermes memory; a copy is listed here.")
                    .font(.callout).foregroundColor(theme.onSurfaceVariant)

                TextField("Something to remember…", text: $draft, axis: .vertical)
                    .lineLimit(2...5)
                    .foregroundColor(theme.onSurface)
                    .padding(12).background(theme.surfaceVariant).clipShape(RoundedCornerShape(8))
                    .overlay(RoundedCornerShape(8).stroke(theme.outline, lineWidth: 1))

                Button {
                    model.save(draft); draft = ""
                } label: {
                    HStack { if model.saving { ProgressView().controlSize(.small) }; Text("Save to memory") }
                        .frame(maxWidth: .infinity).padding(.vertical, 12)
                        .foregroundColor(theme.onPrimary).background(theme.primary).clipShape(RoundedCornerShape(8))
                }
                .disabled(model.saving || draft.trimmingCharacters(in: .whitespaces).isEmpty)

                if let msg = model.message {
                    Text(msg).font(.footnote).foregroundColor(theme.onSurfaceVariant)
                }

                if !model.recent.isEmpty {
                    Text("RECENT MEMOS").hermesDisplayLabel(size: 11).foregroundColor(theme.onSurfaceVariant).padding(.top, 8)
                    ForEach(model.recent, id: \.id) { memo in
                        HStack(alignment: .top, spacing: 10) {
                            Image(systemName: "note.text").foregroundColor(theme.tertiary)
                            VStack(alignment: .leading, spacing: 2) {
                                Text(memo.text).font(.callout).foregroundColor(theme.onSurface)
                                Text(DashboardView.stamp(memo.savedAt)).font(.caption2).foregroundColor(theme.onSurfaceVariant)
                            }
                            Spacer()
                            Button { model.forget(memo.id) } label: { Image(systemName: "xmark").foregroundColor(theme.onSurfaceVariant) }
                        }
                        .padding(12).background(theme.surface).clipShape(RoundedCornerShape(12))
                        .overlay(RoundedCornerShape(12).stroke(theme.outline, lineWidth: 1))
                    }
                }
            }
            .padding(16)
        }
    }
}
