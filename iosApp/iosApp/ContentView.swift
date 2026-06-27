import SwiftUI
import Shared

struct ContentView: View {
    @EnvironmentObject var model: AppModel

    var body: some View {
        // 🔒 Step 5: gate the app behind first-run encryption setup. Until a key
        // exists (and, for a new key, the user has confirmed they saved the
        // recovery code), no encrypted store exists and the app is not shown.
        if model.needsSetup {
            RecoverySetupView()
        } else {
            TabView {
                NotesView()
                    .tabItem { Label("Notes", systemImage: "note.text") }
                RemindersView()
                    .tabItem { Label("Reminders", systemImage: "bell") }
                PlanView()
                    .tabItem { Label("Plan", systemImage: "checklist") }
            }
        }
    }
}

// MARK: - Notes

struct NotesView: View {
    @EnvironmentObject var model: AppModel
    @State private var title = ""
    @State private var body = ""
    @State private var editing: Note?

    var body: some View {
        NavigationStack {
            Form {
                Section(editing == nil ? "New note" : "Edit note") {
                    TextField("Title", text: $title)
                    TextField("Note", text: $body, axis: .vertical)
                    HStack {
                        Button(editing == nil ? "Add note" : "Save") {
                            Task {
                                if let e = editing {
                                    await model.editNote(e, title: title, body: body)
                                } else {
                                    await model.addNote(title: title, body: body)
                                }
                                reset()
                            }
                        }
                        if editing != nil {
                            Button("Cancel", role: .cancel) { reset() }
                        }
                    }
                }
                Section("Notes") {
                    ForEach(model.notes, id: \.id) { note in
                        VStack(alignment: .leading) {
                            Text(note.title).font(.headline)
                            if !note.body.isEmpty { Text(note.body).font(.subheadline) }
                        }
                        .contentShape(Rectangle())
                        .onTapGesture { editing = note; title = note.title; body = note.body }
                    }
                    .onDelete { idx in
                        let ids = idx.map { model.notes[$0].id }
                        Task { for id in ids { await model.deleteNote(id) } }
                    }
                }
            }
            .navigationTitle("Notes")
        }
    }

    private func reset() { editing = nil; title = ""; body = "" }
}

// MARK: - Reminders

struct RemindersView: View {
    @EnvironmentObject var model: AppModel
    @State private var title = ""
    @State private var minutes: Int64 = 1

    private let formatter: DateFormatter = {
        let f = DateFormatter(); f.dateFormat = "EEE d MMM, HH:mm"; return f
    }()

    var body: some View {
        NavigationStack {
            Form {
                Section("New reminder") {
                    TextField("Reminder title", text: $title)
                    Picker("Fires in", selection: $minutes) {
                        Text("1 min").tag(Int64(1))
                        Text("5 min").tag(Int64(5))
                        Text("1 hour").tag(Int64(60))
                        Text("1 day").tag(Int64(1440))
                    }
                    Button("Set reminder") {
                        Task { await model.scheduleReminder(title: title, minutesFromNow: minutes); title = "" }
                    }
                }
                Section("Reminders") {
                    ForEach(model.reminders, id: \.id) { r in
                        VStack(alignment: .leading) {
                            Text(r.title).font(.headline)
                            Text("\(formatter.string(from: Date(timeIntervalSince1970: Double(r.triggerAtMillis) / 1000)))  •  \(statusLabel(r.status))")
                                .font(.subheadline).foregroundStyle(.secondary)
                        }
                    }
                    .onDelete { idx in
                        let ids = idx.map { model.reminders[$0].id }
                        Task { for id in ids { await model.cancelReminder(id) } }
                    }
                }
            }
            .navigationTitle("Reminders")
            .alert(model.message ?? "", isPresented: .constant(model.message != nil)) {
                Button("OK") { model.message = nil }
            }
        }
    }

    private func statusLabel(_ s: ReminderStatus) -> String {
        switch s {
        case .scheduled: return "Scheduled"
        case .fired: return "Fired"
        case .cancelled: return "Cancelled"
        default: return "\(s)"
        }
    }
}

// MARK: - Plan

struct PlanView: View {
    @EnvironmentObject var model: AppModel
    @State private var title = ""

    var body: some View {
        NavigationStack {
            Form {
                Section("New item") {
                    HStack {
                        TextField("Plan item", text: $title)
                        Button("Add") { Task { await model.addPlanItem(title: title); title = "" } }
                    }
                }
                Section("Plan") {
                    ForEach(model.planItems, id: \.id) { item in
                        HStack {
                            Image(systemName: item.done ? "checkmark.circle.fill" : "circle")
                                .onTapGesture { Task { await model.togglePlanItem(item) } }
                            Text(item.title).strikethrough(item.done)
                        }
                    }
                    .onDelete { idx in
                        let ids = idx.map { model.planItems[$0].id }
                        Task { for id in ids { await model.deletePlanItem(id) } }
                    }
                }
            }
            .navigationTitle("Plan")
        }
    }
}
