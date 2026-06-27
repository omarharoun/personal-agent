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

    /// On-device, no-network embedder (Apple NaturalLanguage) bridged to the
    /// shared `Embedder` contract. Held here ready to be handed to the shared
    /// `MemoryService` once the memory-layer sibling lands it (Step 2):
    ///   `IosFactories.shared.createMemoryService(store: store, embedder: embedder)`
    /// — mirroring how `reminderService` is constructed above.
    private let embedder: Embedder

    /// On-device, no-network LLM (MLX Swift, quantized Llama 3.2) bridged to the
    /// shared `OnDeviceLlm` contract (Step 3). `isAvailable` reflects whether the
    /// model weights have been provisioned on this device (see
    /// `LlmModelProvisioner`). Held here ready to back assistant features in later
    /// steps; nothing above the `OnDeviceLlm` contract changes when they land.
    ///
    /// `generate` (a Kotlin `suspend` fun) is consumed cleanly from Swift as
    /// `async` below. The token-streaming variant (`generateStream` → Kotlin
    /// `Flow<String>`) is driven on the iOS side by `IosOnDeviceLlm`'s `onToken`
    /// callback and is consumed by shared Kotlin code; we deliberately do not
    /// consume the raw Kotlin `Flow` from Swift (that's the fragile interop
    /// corner — see `IosLlmAdapter`).
    private let llm: OnDeviceLlm

    /// Step-4 orchestrator: retrieve → build → generate locally, escalating to the
    /// cloud only when the policy says so AND a provider is configured. Cloud is
    /// OFF here (`cloudConfig: nil`), so escalation prep (minimize + anonymize via
    /// `DefaultPayloadPrep`) is wired but nothing leaves the device.
    ///
    /// To enable cloud escalation, build a zero-retention `CloudConfig` (base URL +
    /// model + API key supplied at runtime, never hardcoded) and pass it below.
    private let conversationService: ConversationService

    /// Exposed to the UI so it can show whether the local model is ready.
    @Published var llmAvailable: Bool = false

    init() {
        self.store = IosFactories.shared.createLocalStore()
        self.reminderService = IosFactories.shared.createReminderService(
            store: store,
            scheduler: IosReminderScheduler()
        )
        self.embedder = IosFactories.shared.createEmbedder(native: IosEmbedder())
        self.llm = IosFactories.shared.createOnDeviceLlm(native: IosOnDeviceLlm())
        self.conversationService = IosFactories.shared.createConversationService(
            llm: llm,
            store: store,
            embedder: embedder,
            cloudConfig: nil   // cloud escalation OFF until a zero-retention provider is configured
        )
        self.clock = IosFactories.shared.systemClock()
        self.llmAvailable = llm.isAvailable
    }

    /// Answer one turn through the shared Step-4 orchestrator (memory-grounded,
    /// privacy-preserving escalation). Returns the full reply.
    func respond(to userText: String) async -> String? {
        do {
            return try await conversationService.respond(userText: userText)
        } catch {
            message = "Failed to respond: \(error.localizedDescription)"
            return nil
        }
    }

    /// One-shot prompt → full completion, fully on-device. Returns nil and sets
    /// `message` if the model hasn't been provisioned yet.
    func askLocalModel(_ prompt: String) async -> String? {
        guard llm.isAvailable else {
            message = "On-device model not installed yet."
            return nil
        }
        do {
            return try await llm.generate(prompt: prompt, options: IosFactories.shared.defaultGenOptions())
        } catch {
            message = "Generation failed: \(error.localizedDescription)"
            return nil
        }
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
