// AppEnvironment.swift — the iOS DI container, mirroring Android's AppContainer.
//
// Builds the 🔒 hardware-backed crypto provider and every encrypted-at-rest store
// through the shared Kotlin `LifeAgentIos` facade, so SwiftUI never constructs
// Kotlin objects with default args. Nothing sensitive is written in plaintext.

import SwiftUI
import Shared

@MainActor
final class AppEnvironment: ObservableObject {

    // 🔒 One hardware-backed key provider (Keychain + Secure Enclave + CryptoKit)
    // shared by every encrypted store.
    let crypto: any SecretKeyProvider

    // 🔒 The connection to the user's own Hermes (base URL, API key, memory scope).
    let configStore: HermesConfigStore

    // Local, sealed-at-rest stores (mirror AppContainer).
    let chatStore: ChatStore
    let memoStore: MemoStore
    let taskStore: TaskStore
    let reminderHistory: ReminderHistoryStore
    let notifiedReminders: NotifiedReminderStore
    let homeCache: HomeCacheStore
    let profileStore: ProfileStore
    let reflectionStore: ReflectionStore
    let knowledgeStore: KnowledgeGraphStore
    let knowledgeService: KnowledgeGraphService

    // Phase 6 — authoritative local store of learning goals + resources.
    let learningStore: LearningStore

    // Appearance: the user-selectable accent color (shared curated palette).
    let appearanceStore: AppearanceStore
    /// Published so picking an accent re-themes the whole app live.
    @Published var accentId: String

    // 🔒 Crisis safety (consent-first; contacts NO ONE automatically).
    let trustedContacts: TrustedContactsStore
    let crisisRecognizer: KeywordCrisisRecognizer
    let crisisResponder: CrisisResponder

    /// True once the user has completed the Connect flow at least once.
    @Published var isConnected: Bool

    init() {
        let ios = LifeAgentIos.shared

        // 🔒 Ensure the device data key exists before any store touches disk.
        let keyStore = IosSecretKeyStore()
        // Kotlin/Native renames the bridged `ensureKey()` to `ensureKey_()`.
        try? keyStore.ensureKey_()
        let crypto = ios.createCrypto(native: keyStore)
        self.crypto = crypto

        let configStore = ios.hermesConfigStore(crypto: crypto)
        self.configStore = configStore
        let chatStore = ios.chatStore(crypto: crypto)
        self.chatStore = chatStore
        self.memoStore = ios.memoStore(crypto: crypto)
        self.taskStore = ios.taskStore(crypto: crypto)
        self.reminderHistory = ios.reminderHistoryStore(crypto: crypto)
        self.notifiedReminders = ios.notifiedReminderStore(crypto: crypto)
        self.homeCache = ios.homeCacheStore(crypto: crypto)
        self.profileStore = ios.profileStore(crypto: crypto)
        self.reflectionStore = ios.reflectionStore(crypto: crypto)
        let knowledgeStore = ios.knowledgeGraphStore(crypto: crypto)
        self.knowledgeStore = knowledgeStore
        self.knowledgeService = ios.knowledgeGraphService(chat: chatStore, kg: knowledgeStore)
        self.learningStore = ios.learningStore(crypto: crypto)
        let appearanceStore = ios.appearanceStore(crypto: crypto)
        self.appearanceStore = appearanceStore
        self.accentId = appearanceStore.accentId()
        self.trustedContacts = ios.trustedContactsStore(crypto: crypto)
        self.crisisRecognizer = ios.crisisRecognizer()
        self.crisisResponder = ios.crisisResponder()

        self.isConnected = configStore.isConfigured()
    }

    /// The resolved accent option for the current selection (from the shared list).
    var accentOption: AccentOption { LifeAgentIos.shared.accentById(id: accentId) }

    /// Persist + apply a new accent (re-themes the app immediately).
    func setAccent(_ id: String) {
        appearanceStore.setAccentId(id: id)
        accentId = id
    }

    /// A live client for the saved connection, or nil if not connected yet.
    func makeClient() -> HermesClient? {
        guard let cfg = configStore.load() else { return nil }
        return LifeAgentIos.shared.client(config: cfg)
    }

    /// A one-off client for an unsaved config (the Connect test).
    func makeClient(for config: HermesConfig) -> HermesClient {
        LifeAgentIos.shared.client(config: config)
    }

    func refreshConnected() { isConnected = configStore.isConfigured() }

    /// 🔒 Trust boundary: forget the configured backend (keep the memory-scope key
    /// so reconnecting to the same Hermes lands in the same memory).
    func disconnect() {
        configStore.disconnect(forgetMemoryScope: false)
        isConnected = false
    }
}
