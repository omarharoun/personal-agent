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

    // MARK: - UX Stream 1: single conversational surface
    //
    // The app no longer has Notes/Reminders/Plan tabs. Every typed turn is routed
    // by the SHARED `IntentRouter` (same logic as Android): note/reminder/plan
    // capabilities are invoked behind the scenes and confirmed in-line; everything
    // else is answered by the on-device `conversationService`.

    /// One line in the conversation transcript.
    struct ChatMessage: Identifiable {
        enum Role { case user, assistant, system }
        let id = UUID()
        let role: Role
        let text: String
    }

    // Starts empty so the surface shows a Claude-style home ("What's on your
    // mind?" + example prompt chips) until the first message is sent.
    @Published var messages: [ChatMessage] = []

    /// True while an AI reply is in flight (disables the send button).
    @Published var sending: Bool = false

    /// Friendly fallback when no on-device model is installed (mirrors Android).
    private let modelUnavailableFallback =
        "I can't answer that yet — there's no AI model running on this device. "
        + "Install one from Settings (the gear), or add an API key, and I'll be able to chat. "
        + "Notes, reminders, and plans still work right now."

    private func appendMessage(_ role: ChatMessage.Role, _ text: String) {
        messages.append(ChatMessage(role: role, text: text))
    }

    /// Handle one user turn: echo it, route it through the shared `IntentRouter`,
    /// and either confirm a saved capability or fetch an AI reply.
    func send(_ input: String) async {
        let text = input.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !text.isEmpty, !sending, isReady else { return }
        appendMessage(.user, text)

        // Shared, model-free router. `IntentRouter` is a Kotlin `object` → `.shared`.
        let intent = IntentRouter.shared.parse(input: text, nowMillis: clock.nowMillis())

        // Plain KMP/Obj-C interop: the sealed interface is bridged as a base class
        // with flattened subclass names (same pattern as `ScheduleResultRejected`).
        switch intent {
        case let note as AgentIntentCreateNote:
            await addNote(title: note.title, body: note.body)
            let preview = note.title.isEmpty ? note.body : note.title
            appendMessage(.system, "Saved a note: \(preview)")

        case let reminder as AgentIntentCreateReminder:
            if let whenMillis = reminder.whenMillisHint {
                await scheduleReminderAt(title: reminder.text, triggerAtMillis: whenMillis.int64Value)
                appendMessage(.system, "Reminder set: \(reminder.text)")
            } else {
                // No parseable time — default to ~1h and tell the user (mirrors Android).
                let defaultAt = clock.nowMillis() + 60 * 60_000
                await scheduleReminderAt(title: reminder.text, triggerAtMillis: defaultAt)
                appendMessage(
                    .system,
                    "I didn't catch a time, so I set a reminder for about an hour from now: "
                        + "\(reminder.text). Tell me \"in N minutes/hours\" to change it."
                )
            }

        case let plan as AgentIntentAddPlanItem:
            await addPlanItem(title: plan.title)
            appendMessage(.system, "Added to your plan: \(plan.title)")

        case let askIntent as AgentIntentAsk:
            await ask(askIntent.text)

        default:
            // Defensive: unknown intent → treat as a question.
            await ask(text)
        }
    }

    /// Generate an AI reply and append it — OR append a VISIBLE error that names
    /// the failing stage. The device bug was a silent stall (no reply, no error);
    /// every send must resolve to a rendered message, never an empty spinner.
    private func ask(_ text: String) async {
        sending = true
        defer { sending = false }
        guard isReady else {
            appendMessage(.assistant, modelUnavailableFallback)
            return
        }
        do {
            // Short-term memory: prior USER/ASSISTANT turns of this chat (oldest
            // first), excluding the current user turn just appended by send().
            let history: [ConversationTurn] = Array(
                messages.filter { $0.role == .user || $0.role == .assistant }.dropLast()
            ).map {
                ConversationTurn(
                    role: $0.role == .user ? ChatRole.user : ChatRole.assistant,
                    text: $0.text)
            }
            let reply = try await conversationService.respond(userText: text, history: history)
            let trimmed = reply.trimmingCharacters(in: .whitespacesAndNewlines)
            if trimmed.isEmpty {
                appendMessage(.assistant, "I didn't get any text back that time. Please try again.")
            } else {
                appendMessage(.assistant, trimmed)
            }
        } catch {
            // KMP surfaces CloudUnavailableException / CloudException as NSError
            // whose description carries our stage-named message ("No cloud
            // provider/key is configured…", "API error 401: invalid x-api-key").
            let desc = error.localizedDescription
            if desc.localizedCaseInsensitiveContains("no cloud provider")
                || desc.localizedCaseInsensitiveContains("not configured") {
                appendMessage(.assistant, modelUnavailableFallback)
            } else {
                appendMessage(
                    .assistant,
                    "I couldn't reach the model: \(desc). "
                        + "Check your connection and your API key in Settings.")
            }
        }
    }

    /// Schedule a reminder at an ABSOLUTE epoch-millis trigger time (the router
    /// returns absolute times). `scheduleReminder(title:minutesFromNow:)` above is
    /// kept for any remaining minute-based callers.
    private func scheduleReminderAt(title: String, triggerAtMillis: Int64) async {
        guard isReady else { return }
        let result = try? await reminderService.schedule(title: title, triggerAtMillis: triggerAtMillis, note: "")
        if let rejected = result as? ScheduleResultRejected {
            switch rejected.reason {
            case .blankTitle: message = "Enter a title"
            case .triggerInPast: message = "Pick a future time"
            default: message = "Could not set reminder"
            }
        }
        await refresh()
    }

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
    /// BYO-key cloud wallet (Stream 3). Exposed so the Settings cloud section can
    /// read/write provider keys; keys are stored encrypted, never in plaintext.
    private(set) var cloudKeyStore: CloudKeyStore!

    /// Exposed to the UI so it can show whether the local model is ready.
    @Published var llmAvailable: Bool = false

    // MARK: On-device model provisioning ("Set up your AI")
    //
    // The shared `ModelProvisioner` contract drives the curated download → verify
    // → install pipeline (see `IosModelProvisioner` + the iosMain adapter). The
    // user always initiates it from the onboarding step or Settings — no
    // auto-download. Built lazily in `buildServices` (needs an unlocked device).
    private var modelProvisioner: ModelProvisioner!

    /// True after first-run encryption setup but before the user has been through
    /// the once-only "Set up your AI" step. Gates `OnboardingFlowView`.
    @Published var needsModelOnboarding: Bool = false

    /// Non-sensitive "has the user seen the AI-setup step?" flag, in UserDefaults
    /// (UI state, not user data). The Android sibling keeps the equivalent in its
    /// encrypted KeyValueStorage — both record only that the flow ran.
    private let onboardingCompleteKey = "ai_model_onboarding_complete"

    // MARK: 🔞 18+ age gate (the first gate, before encryption setup)
    //
    // The app is restricted to adults. This is checked before the encrypted store
    // even exists (it precedes recovery setup), so the boolean confirmation lives
    // in UserDefaults — it is a non-sensitive UI flag, not user data, and the date
    // of birth itself is never stored. Mirrors the Android `AgeGateRepository`.

    /// True until the user has confirmed they are 18 or older. Gates `AgeGateView`.
    @Published var needsAgeConfirmation: Bool
    private let ageConfirmedKey = "age_18plus_confirmed"

    // MARK: 🔒 Step 7 crisis-safety (consent-first; autonomous action disabled)
    //
    // The recognizer classifies ONLY (no autonomous outreach). The support view
    // is surfaced via `distress` after an EXPLICIT user action (a self check-in),
    // never by silently scanning the user. Trusted contacts are hand-added by the
    // user and stored encrypted at rest like everything else.
    private var crisisRecognizer: CrisisRecognizer!
    private var resourceProvider: CrisisResourceProvider!
    private var crisisResponder: CrisisResponder!
    private var trustedContactsStore: TrustedContactsStore!

    /// The user's hand-curated trusted contacts (added explicitly, with consent).
    @Published var trustedContacts: [TrustedContact] = []

    /// Drives the support sheet. Non-nil only after an explicit check-in that the
    /// recognizer flagged as POSSIBLE_DISTRESS. An Identifiable wrapper because a
    /// Kotlin `CrisisResponse` can't conform to `Identifiable` for `.sheet(item:)`.
    @Published var distress: DistressPresentation?

    init() {
        self.needsAgeConfirmation = !UserDefaults.standard.bool(forKey: ageConfirmedKey)
        let ready = keyStore.isSetUp
        self.needsSetup = !ready
        if ready {
            buildServices()
            // Returning user who set up before this feature still gets the
            // once-only AI-setup step (unless they've already completed it).
            self.needsModelOnboarding = !UserDefaults.standard.bool(forKey: onboardingCompleteKey)
        }
    }

    // MARK: - 🔞 18+ age gate

    /// Whether a date of birth meets the 18+ requirement, via the shared,
    /// unit-tested logic (`com.personalagent.shared.age`). The date of birth is
    /// evaluated on-device and never stored.
    func meetsAgeRequirement(year: Int32, month: Int32, day: Int32) -> Bool {
        let c = Calendar.current.dateComponents([.year, .month, .day], from: Date())
        let today = CalendarDate(year: Int32(c.year ?? 0), month: Int32(c.month ?? 0), day: Int32(c.day ?? 0))
        let dob = CalendarDate(year: year, month: month, day: day)
        return AgeGateKt.meetsMinimumAge(dob: dob, today: today, minAge: AgeGateKt.MINIMUM_AGE_YEARS)
    }

    /// Record that the user confirmed they are 18 or older and proceed. Only a
    /// positive confirmation is ever stored (never the date of birth).
    func confirmAgeIsAtLeast18() {
        UserDefaults.standard.set(true, forKey: ageConfirmedKey)
        needsAgeConfirmation = false
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
        // Recovery setup done → advance into the once-only "Set up your AI" step.
        needsModelOnboarding = !UserDefaults.standard.bool(forKey: onboardingCompleteKey)
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
        // BYO-key cloud (Stream 3): keys stored ENCRYPTED via the same crypto.
        self.cloudKeyStore = IosFactories.shared.createCloudKeyStore(crypto: crypto)
        // Derive the cloud client from the user's BYO-key selection; with no key
        // set it stays UnavailableCloudClient → fully on-device.
        self.conversationService = IosFactories.shared.createConversationService(
            llm: llm,
            store: store,
            embedder: embedder,
            crypto: crypto,
            cloudKeyStore: cloudKeyStore
        )
        self.clock = IosFactories.shared.systemClock()
        self.modelProvisioner = IosFactories.shared.createModelProvisioner(native: IosModelProvisioner())
        refreshLlmAvailability()

        // 🔒 Step 7 crisis-safety services. The trusted-contacts store reuses the
        // SAME encryption key (`crypto`) as the main store.
        self.crisisRecognizer = IosFactories.shared.createCrisisRecognizer()
        self.resourceProvider = IosFactories.shared.createCrisisResourceProvider()
        self.crisisResponder = IosFactories.shared.createCrisisResponder()
        self.trustedContactsStore = IosFactories.shared.createTrustedContactsStore(crypto: crypto)
    }

    // MARK: - On-device model provisioning

    /// Build a `ModelSetupModel` for the onboarding step or Settings, wired to the
    /// shared provisioner. Its `onInstalledChange` re-derives `llmAvailable` so the
    /// rest of the app lights up the moment a model is installed (or removed).
    func makeModelSetupModel() -> ModelSetupModel {
        ModelSetupModel(provisioner: modelProvisioner) { [weak self] in
            self?.refreshLlmAvailability()
        }
    }

    /// Mark the once-only "Set up your AI" step done (whether the user installed a
    /// model or skipped) and reveal the main app.
    func completeModelOnboarding() {
        UserDefaults.standard.set(true, forKey: onboardingCompleteKey)
        needsModelOnboarding = false
        refreshLlmAvailability()
    }

    /// Re-derive whether on-device AI is ready. True if the MLX weights are present
    /// (`IosOnDeviceLlm`) OR a verified catalog model has been provisioned.
    ///
    /// ⚠️ FLAG: the shared `ModelCatalog` lists LiteRT `.task` bundles while the iOS
    /// LLM runtime is MLX (a safetensors directory). A provisioned catalog model
    /// therefore marks AI as "set up" for the UX, but wiring it into *MLX inference*
    /// needs MLX-format catalog entries (shared-catalog owner) — see the report.
    func refreshLlmAvailability() {
        guard isReady else { return }
        let catalogInstalled = DefaultModelCatalog().options().contains { modelProvisioner.isInstalled(option: $0) }
        llmAvailable = llm.isAvailable || catalogInstalled
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
            self.trustedContacts = try await trustedContactsStore.all()
        } catch {
            self.message = "Failed to load: \(error.localizedDescription)"
        }
    }

    // MARK: - 🔒 Step 7 crisis-safety (consent-first; autonomous action disabled)

    /// The crisis resources to display, sourced from the SHARED provider.
    var crisisResources: [CrisisResource] {
        guard isReady else { return [] }
        return resourceProvider.resourcesFor(regionHint: nil)
    }

    /// EXPLICIT, user-initiated self check-in. The user taps to share how they're
    /// feeling; only then do we classify. If the (conservative, on-device)
    /// recognizer flags POSSIBLE_DISTRESS, we surface the calm support view. This
    /// is the ONLY thing that happens — no message is sent, no one is contacted,
    /// nothing is logged or escalated.
    func checkIn(_ text: String) {
        guard isReady, !text.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty else { return }
        // canonical: assess(userText:) -> CrisisAssessment(level, rationale)
        let assessment = crisisRecognizer.assess(userText: text)
        if assessment.level == CrisisLevel.possibleDistress {
            // respond(...) is nil for NONE; non-nil here. Reviewed copy + resources
            // come from the shared CrisisResponder — nothing is sent or contacted.
            if let response = crisisResponder.respond(assessment: assessment, regionHint: nil) {
                distress = DistressPresentation(response: response)
            }
        } else {
            message = "Thanks for checking in. Support resources are always here if you want them."
        }
    }

    /// Open the support view directly (e.g. from a "Support resources" button),
    /// without any classification — resources are always available on request.
    func openSupportResources() {
        guard isReady else { return }
        let assessment = CrisisAssessment(
            level: CrisisLevel.possibleDistress,
            rationale: "User explicitly opened support resources."
        )
        if let response = crisisResponder.respond(assessment: assessment, regionHint: nil) {
            distress = DistressPresentation(response: response)
        }
    }

    /// Add a trusted contact. Calling this IS the user's up-front, explicit
    /// consent — it only happens when they fill in the setup form and tap Add.
    func addTrustedContact(name: String, phone: String, relation: String) async {
        guard isReady, !name.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty else { return }
        let phoneOrNil = phone.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty ? nil : phone
        let now = clock.nowMillis()
        // canonical: add(contact:) takes a fully-formed TrustedContact; consentedAt
        // is set now because filling the form + tapping Add IS the consent.
        let contact = TrustedContact(
            id: Ids.shared.next(nowMillis: now),
            name: name,
            relationship: relation,
            phone: phoneOrNil,
            consentedAt: now
        )
        try? await trustedContactsStore.add(contact: contact)
        await refresh()
    }

    func removeTrustedContact(_ id: String) async {
        guard isReady else { return }
        try? await trustedContactsStore.remove(id: id)
        await refresh()
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
