import Foundation
import Shared

/// Observable view-model bridging the shared Kotlin business logic to SwiftUI.
/// All persistence + scheduling goes through the shared `LocalStore` /
/// `ReminderService`; SwiftUI never touches storage directly.
///
/// Kotlin `suspend` functions are exposed to Swift as `async` — we `await` them.
@MainActor
final class AppModel: ObservableObject {
    @Published var notes: [Note] = []
    @Published var reminders: [Reminder] = []
    @Published var planItems: [PlanItem] = []
    @Published var message: String?

    private let store: LocalStore
    private let reminderService: ReminderService
    private let clock: Clock

    init() {
        self.store = IosFactories.shared.createLocalStore()
        self.reminderService = IosFactories.shared.createReminderService(
            store: store,
            scheduler: IosReminderScheduler()
        )
        self.clock = IosFactories.shared.systemClock()
    }

    func refresh() async {
        do {
            let n = try await store.allNotes()
            let r = try await store.allReminders()
            let p = try await store.allPlanItems()
            self.notes = n.sorted { $0.updatedAt > $1.updatedAt }
            self.reminders = r.sorted { $0.triggerAtMillis < $1.triggerAtMillis }
            self.planItems = p
        } catch {
            self.message = "Failed to load: \(error.localizedDescription)"
        }
    }

    // MARK: Notes
    func addNote(title: String, body: String) async {
        let now = clock.nowMillis()
        let safeTitle = title.isEmpty ? "Untitled" : title
        try? await store.upsertNote(note: Note.companion.create(title: safeTitle, body: body, nowMillis: now))
        await refresh()
    }

    func editNote(_ note: Note, title: String, body: String) async {
        let now = clock.nowMillis()
        let edited = note.edited(title: title.isEmpty ? "Untitled" : title, body: body, nowMillis: now)
        try? await store.upsertNote(note: edited)
        await refresh()
    }

    func deleteNote(_ id: String) async {
        try? await store.deleteNote(id: id)
        await refresh()
    }

    // MARK: Reminders
    func scheduleReminder(title: String, minutesFromNow: Int64) async {
        let triggerAt = clock.nowMillis() + minutesFromNow * 60_000
        let result = try? await reminderService.schedule(title: title, triggerAtMillis: triggerAt, note: "")
        if result is ScheduleResultScheduled {
            message = "Reminder set"
        } else if let rejected = result as? ScheduleResultRejected {
            switch rejected.reason {
            case .blankTitle: message = "Enter a title"
            case .triggerInPast: message = "Pick a future time"
            default: message = "Could not set reminder"
            }
        }
        await refresh()
    }

    func cancelReminder(_ id: String) async {
        try? await reminderService.cancel(reminderId: id)
        await refresh()
    }

    // MARK: Plan
    func addPlanItem(title: String) async {
        guard !title.isEmpty else { return }
        let order = (planItems.map { $0.order }.max() ?? 0) + 1
        let item = PlanItem.companion.create(title: title, nowMillis: clock.nowMillis(), dueAtMillis: nil, order: order)
        try? await store.upsertPlanItem(item: item)
        await refresh()
    }

    func togglePlanItem(_ item: PlanItem) async {
        try? await store.upsertPlanItem(item: item.toggled())
        await refresh()
    }

    func deletePlanItem(_ id: String) async {
        try? await store.deletePlanItem(id: id)
        await refresh()
    }
}
