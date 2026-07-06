package com.personalagent.android

import android.content.Context
import com.personalagent.android.notification.AndroidReminderScheduler
import com.personalagent.android.onboarding.AgeGateRepository
import com.personalagent.android.onboarding.OnboardingRepository
import com.personalagent.android.onboarding.SecuritySetupRepository
import com.personalagent.shared.crypto.AndroidSecretKeyProvider
import com.personalagent.shared.crypto.EncryptedKeyValueStorage
import com.personalagent.shared.crypto.SecretKeyProvider
import com.personalagent.shared.hermes.HermesClient
import com.personalagent.shared.hermes.HermesConfig
import com.personalagent.shared.hermes.HermesConfigStore
import com.personalagent.shared.hermes.HermesReminderPoller
import com.personalagent.shared.hermes.NotifiedReminderStore
import com.personalagent.shared.hermes.ReflectionStore
import com.personalagent.shared.hermes.ReminderHistoryStore
import com.personalagent.shared.chat.ChatStore
import com.personalagent.shared.home.HomeCacheStore
import com.personalagent.shared.notes.MemoStore
import com.personalagent.shared.profile.ProfileStore
import com.personalagent.shared.reminder.ReminderService
import com.personalagent.shared.tasks.TaskStore
import com.personalagent.shared.safety.CrisisRecognizer
import com.personalagent.shared.safety.CrisisResourceProvider
import com.personalagent.shared.safety.CrisisResponder
import com.personalagent.shared.safety.DefaultCrisisResourceProvider
import com.personalagent.shared.safety.KeywordCrisisRecognizer
import com.personalagent.shared.safety.TrustedContactsStore
import com.personalagent.shared.store.AndroidKeyValueStorage
import com.personalagent.shared.store.KeyValueStorage
import com.personalagent.shared.store.LocalStore
import com.personalagent.shared.store.PersistentLocalStore
import com.personalagent.shared.util.SystemClock

/**
 * Manual DI container for the **Hermes Life Agent client**. Hermes is the brain
 * (memory, skills, scheduling, model) — this app only orchestrates and presents,
 * so there is no on-device model / embedder / vector store here anymore. What
 * remains is the connection to the user's Hermes, secure storage, local reminder
 * delivery, and the consent-first crisis-safety spine.
 *
 * 🔒 Everything sensitive is sealed at rest by [EncryptedKeyValueStorage] over the
 * hardware-backed [AndroidSecretKeyProvider] (Android Keystore AES-256-GCM).
 * NOT-FOR-REAL-USERS until the SECURITY_REVIEW gates are signed off.
 */
class AppContainer(context: Context) {
    private val appContext = context.applicationContext

    /** The process-wide application context (for WorkManager/notifications). */
    val androidContext: Context get() = appContext

    /** 🔒 The single hardware-backed key provider shared by every encrypted store. */
    private val secretKeyProvider: SecretKeyProvider = AndroidSecretKeyProvider()

    /** 🔒 Build an encrypted [KeyValueStorage] backed by file [fileName]. */
    private fun encrypted(fileName: String): KeyValueStorage =
        EncryptedKeyValueStorage(
            delegate = AndroidKeyValueStorage(appContext, fileName),
            crypto = secretKeyProvider,
        )

    /** Local app state (notes/reminders/plan scaffolding). Sealed at rest. */
    val store: LocalStore = PersistentLocalStore(encrypted("personal_agent_store"))

    // --- Home dashboard: name + local Tasks/Memos indexes (sealed at rest) -----

    /** The user's display name for the personal greeting (user-set or agent-derived). */
    val profileStore: ProfileStore = ProfileStore(encrypted("profile"))

    /** Local to-do list (device-local by design; see [TaskStore]). */
    val taskStore: TaskStore = TaskStore(encrypted("tasks"))

    /**
     * Local index of saved memos so the app can show them back. The authoritative
     * copy lives in the user's Hermes memory (see [MemoStore]).
     */
    val memoStore: MemoStore = MemoStore(encrypted("memos"))

    /**
     * Stale-while-revalidate cache for the home's networked card (Goals) so the
     * home paints instantly and doesn't re-query the agent on every appearance.
     */
    val homeCacheStore: HomeCacheStore = HomeCacheStore(encrypted("home_cache"))

    /**
     * Local, sealed-at-rest record of chat history so conversations survive an app
     * restart (Hermes keeps the authoritative server copy; this is the device's).
     */
    val chatStore: ChatStore = ChatStore(encrypted("chat_history"))

    // --- Hermes connection ----------------------------------------------------

    /**
     * 🔒 REVIEW REQUIRED — credential + session-key storage + trust boundary.
     * The connection to the user's OWN Hermes (base URL, API key, stable
     * `X-Hermes-Session-Key`), sealed at rest; never plaintext/logged/backed up.
     * No default or hidden backend — the app talks only to what the user configures.
     */
    val hermesConfigStore: HermesConfigStore = HermesConfigStore(encrypted("hermes_connection"))

    val isHermesConfigured: Boolean get() = hermesConfigStore.isConfigured()

    /** Live client for the saved connection, or null if not connected yet. */
    fun hermesClientOrNull(): HermesClient? =
        hermesConfigStore.load()?.let { HermesClient(it) }

    /** One-off client for an unsaved [config] (used by the Connect test). */
    fun hermesClientFor(config: HermesConfig): HermesClient = HermesClient(config)

    // --- Reminders (poll /api/jobs → local notification) ----------------------

    /** Notify-once markers (opaque job-id@run-time keys only — no reminder text). */
    val notifiedReminderStore: NotifiedReminderStore = NotifiedReminderStore(encrypted("reminder_notified"))

    /**
     * Local reminder history (id + short text + time) so fired reminders stay
     * visible even after Hermes cleans up the one-shot job. Non-sensitive schedule
     * metadata, sealed at rest.
     */
    val reminderHistoryStore: ReminderHistoryStore = ReminderHistoryStore(encrypted("reminder_history"))

    /** Reminder poller over the saved connection, or null if not connected. */
    fun reminderPollerOrNull(): Pair<HermesClient, HermesReminderPoller>? {
        val client = hermesClientOrNull() ?: return null
        return client to HermesReminderPoller(
            client = client,
            notified = notifiedReminderStore,
            now = { SystemClock.nowMillis() },
        )
    }

    /** Legacy local reminder service (drives AppViewModel's local scaffolding). */
    val reminderService: ReminderService =
        ReminderService(
            store = store,
            scheduler = AndroidReminderScheduler(appContext),
            clock = SystemClock,
        )

    // --- Reflection (Phase 4) -------------------------------------------------

    /** Reflection cadence + timestamps (no content). Sealed at rest. */
    val reflectionStore: ReflectionStore = ReflectionStore(encrypted("reflection"))

    // --- First-run / security scaffolding (kept; sealed at rest) --------------

    val securitySetup: SecuritySetupRepository = SecuritySetupRepository(encrypted("security_setup"))
    val ageGate: AgeGateRepository = AgeGateRepository(encrypted("age_gate"))
    val onboarding: OnboardingRepository = OnboardingRepository(encrypted("onboarding"))

    // --- 🔒 Crisis safety (Gate 2 — consent-first; NO autonomous action) ------

    val trustedContactsStore: TrustedContactsStore = TrustedContactsStore(encrypted("trusted_contacts"))

    /** 🔒 Placeholder crisis resources — verify + localize before real users. */
    val crisisResourceProvider: CrisisResourceProvider = DefaultCrisisResourceProvider()

    /** 🔒 Conservative recognizer; consulted per-turn to OFFER support, never to act. */
    val crisisRecognizer: CrisisRecognizer = KeywordCrisisRecognizer()

    /** 🔒 Builds the supportive, consent-first surface; contacts NO ONE. */
    val crisisResponder: CrisisResponder = CrisisResponder(crisisResourceProvider)
}
