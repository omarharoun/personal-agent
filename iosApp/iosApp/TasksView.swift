// TasksView.swift — a device-local to-do list (Android TasksScreen). Add / toggle
// / remove, with open tasks first and a done section below.

import SwiftUI
import Shared

@MainActor
final class TasksModel: ObservableObject {
    @Published var tasks: [Shared.Task] = []
    private let env: AppEnvironment
    init(env: AppEnvironment) { self.env = env; refresh() }
    func refresh() { tasks = env.taskStore.all() }

    func add(_ text: String) {
        let t = text.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !t.isEmpty else { return }
        let now = LifeAgentIos.shared.nowMillis()
        env.taskStore.add(task: Shared.Task(id: Ids.shared.next(nowMillis: now), text: t, done: false, createdAt: now, completedAt: nil))
        refresh()
    }
    func toggle(_ id: String, _ done: Bool) {
        env.taskStore.setDone(id: id, done: done, nowMillis: LifeAgentIos.shared.nowMillis()); refresh()
    }
    func remove(_ id: String) { env.taskStore.remove(id: id); refresh() }
}

struct TasksView: View {
    @StateObject private var model: TasksModel
    @Environment(\.theme) private var theme
    @State private var draft = ""

    init(env: AppEnvironment) { _model = StateObject(wrappedValue: TasksModel(env: env)) }

    private var open: [Shared.Task] { model.tasks.filter { !$0.done } }
    private var done: [Shared.Task] { model.tasks.filter { $0.done } }

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 12) {
                Text("A private to-do list kept on this device.").font(.callout).foregroundColor(theme.onSurfaceVariant)
                HStack {
                    TextField("Add a task…", text: $draft)
                        .foregroundColor(theme.onSurface)
                        .padding(12).background(theme.surfaceVariant).clipShape(RoundedCornerShape(8))
                    Button("Add") { model.add(draft); draft = "" }
                        .foregroundColor(theme.onPrimary).padding(.horizontal, 16).padding(.vertical, 12)
                        .background(theme.primary).clipShape(RoundedCornerShape(8))
                        .disabled(draft.trimmingCharacters(in: .whitespaces).isEmpty)
                }
                if model.tasks.isEmpty {
                    Text("No tasks yet — add one above.").font(.callout).foregroundColor(theme.onSurfaceVariant)
                }
                ForEach(open, id: \.id) { row($0) }
                if !done.isEmpty {
                    Text("DONE").hermesDisplayLabel(size: 11).foregroundColor(theme.onSurfaceVariant).padding(.top, 8)
                    ForEach(done, id: \.id) { row($0) }
                }
            }
            .padding(16)
        }
    }

    private func row(_ t: Shared.Task) -> some View {
        HStack(spacing: 10) {
            Button { model.toggle(t.id, !t.done) } label: {
                Image(systemName: t.done ? "checkmark.circle.fill" : "circle")
                    .foregroundColor(t.done ? theme.primary : theme.onSurfaceVariant)
            }.buttonStyle(.plain)
            Text(t.text).foregroundColor(t.done ? theme.onSurfaceVariant : theme.onSurface).strikethrough(t.done)
            Spacer()
            Button { model.remove(t.id) } label: { Image(systemName: "xmark").foregroundColor(theme.onSurfaceVariant) }
        }
        .padding(12).background(theme.surface).clipShape(RoundedCornerShape(12))
        .overlay(RoundedCornerShape(12).stroke(theme.outline, lineWidth: 1))
    }
}
