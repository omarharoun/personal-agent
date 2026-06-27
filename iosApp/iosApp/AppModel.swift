import Foundation
import Shared

/// Observable view-model bridging the shared Kotlin business logic to SwiftUI.
/// All persistence + scheduling goes through the shared `LocalStore` /
/// `ReminderService`; SwiftUI never touches storage directly.
///
/// Kotlin `suspend` functions are exposed to Swift as `async` — we `await` them.
///
/// 🔒 Step 5: persistence is now encrypted at rest. The store is an
/// `EncryptedKeyValueStorage` keyed by `IosSecretKeyProvider` (Keychain + Secure
/// Enclave + CryptoKit AES-GCM). The encryption key is created on first run via
/// the recovery-setup screen, so the data store/services are built lazily AFTER a
/// key exists (`needsSetup` gates the UI). Nothing above the `KeyValueStorage`
/// seam changed.
@MainActor
final class AppModel: ObservableObject {
    @Published var notes: [Note] = []
    @Published var reminders: [Reminder] = []
    @Published var planItems: [PlanItem] = []
    @Published var message: String?

    /// True until an encryption key exists on this device. While true the UI shows
    /// the recovery-setup screen instead of the app, and no encrypted store exists.
    @Published var needsSetup: Bool

    /// 🔒 The iOS secure key store (Swift, CryptoKit + Keychain + Secure Enclave).
    /// Owned here so both the setup screen and the encrypted data store share one
    /// instance (and its in-memory unwrapped-key cache).
    let keyStore = IosSecretKeyStore()
    private var crypto: SecretKeyProvider!

    // Built lazily once a key exists (see `buildServices`). Implicitly-unwrapped
    // because every call site is gated behind `needsSetup == false`.
    private var store: LocalStore!
    private var reminderService: ReminderService!
    private var clock: Clock!
    private var embedder: Embedder!
    private var llm: OnDeviceLlm!
    private var conversationService: ConversationService!

    /// Exposed to the UI so it can show whether the local model is ready.
    @Published var llmAvailable: Bool = false

    init() {
        let ready = keyStore.isSetUp
        self.needsSetup = !ready
        if ready {
            buildServices()
        }
    }

    /// True once the encrypted store + services exist (i.e. setup is complete).
    var isReady: Bool { store != nil }

    // MARK: - First-run setup

    /// Generate the encryption key and return the user-held recovery code to
    /// DISPLAY. The data store is not built until `finishSetup()` so the user must
    /// see and confirm the code first. Throws if a key already exists.
    func generateRecoveryCode() throws -> String {
        return try keyStore.setUp()
    }

    /// Restore the key on a new device from a previously-saved recovery code.
    func restore(fromRecoveryCode code: String) throws {
        try keyStore.restore(fromRecoveryCode: code)
    }

    /// Call once the key exists and (for the generate path) the user has confirmed
    /// they saved the recovery code. Builds the encrypted store + services and
    /// reveals the app.
    func finishSetup() async {
        buildServices()
        needsSetup = false
        await refresh()
    }

    /// 🔒 Construct the encrypted store and everything that depends on it.
    private func buildServices() {
        self.crypto = IosFactories.shared.createSecretKeyProvider(native: keyStore)
        self.store = IosFactories.shared.createLocalStore(crypto: crypto)
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
            crypto: crypto,
            cloudConfig: nil   // cloud escalation OFF until a zero-retention provider is configured
        )
        self.clock = IosFactories.shared.systemClock()
        self.llmAvailable = llm.isAvailable
    }

    /// Answer one turn through the shared Step-4 orchestrator (memory-grounded,
    /// privacy-preserving escalation). Returns the full reply.
    func respond(to userText: String) async -> String? {
        guard isReady else { return nil }
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
        guard isReady, llm.isAvailable else {
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
        guard isReady else { return }
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
        guard isReady else { return }
        let now = clock.nowMillis()
        let safeTitle = title.isEmpty ? "Untitled" : title
        try? await store.upsertNote(note: Note.companion.create(title: safeTitle, body: body, nowMillis: now))
        await refresh()
    }

    func editNote(_ note: Note, title: String, body: String) async {
        guard isReady else { return }
        let now = clock.nowMillis()
        let edited = note.edited(title: title.isEmpty ? "Untitled" : title, body: body, nowMillis: now)
        try? await store.upsertNote(note: edited)
        await refresh()
    }

    func deleteNote(_ id: String) async {
        guard isReady else { return }
        try? await store.deleteNote(id: id)
        await refresh()
    }

    // MARK: Reminders
    func scheduleReminder(title: String, minutesFromNow: Int64) async {
        guard isReady else { return }
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
        guard isReady else { return }
        try? await reminderService.cancel(reminderId: id)
        await refresh()
    }

    // MARK: Plan
    func addPlanItem(title: String) async {
        guard isReady, !title.isEmpty else { return }
        let order = (planItems.map { $0.order }.max() ?? 0) + 1
        let item = PlanItem.companion.create(title: title, nowMillis: clock.nowMillis(), dueAtMillis: nil, order: order)
        try? await store.upsertPlanItem(item: item)
        await refresh()
    }

    func togglePlanItem(_ item: PlanItem) async {
        guard isReady else { return }
        try? await store.upsertPlanItem(item: item.toggled())
        await refresh()
    }

    func deletePlanItem(_ id: String) async {
        guard isReady else { return }
        try? await store.deletePlanItem(id: id)
        await refresh()
    }
}
