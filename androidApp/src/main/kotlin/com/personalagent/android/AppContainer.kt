package com.personalagent.android

import android.content.Context
import com.personalagent.android.embedding.EmbedderFactory
import com.personalagent.android.llm.AndroidModelProvisioner
import com.personalagent.android.llm.LlmModelProvisioning
import com.personalagent.android.notification.AndroidReminderScheduler
import com.personalagent.android.onboarding.AgeGateRepository
import com.personalagent.android.onboarding.OnboardingRepository
import com.personalagent.android.onboarding.SecuritySetupRepository
import com.personalagent.shared.provisioning.ModelProvisioner
import com.personalagent.shared.cloud.CloudClient
import com.personalagent.shared.cloud.CloudConfig
import com.personalagent.shared.cloud.CloudKeyStore
import com.personalagent.shared.cloud.DynamicCloudClient
import com.personalagent.shared.cloud.DefaultPayloadPrep
import com.personalagent.shared.cloud.HeuristicEscalationPolicy
import com.personalagent.shared.cloud.HttpCloudClient
import com.personalagent.shared.cloud.UnavailableCloudClient
import com.personalagent.shared.conversation.ConversationService
import com.personalagent.shared.conversation.OnDeviceLlm
import com.personalagent.shared.hermes.HermesClient
import com.personalagent.shared.hermes.HermesConfig
import com.personalagent.shared.hermes.HermesConfigStore
import com.personalagent.shared.memory.Embedder
import com.personalagent.shared.memory.InMemoryVectorIndex
import com.personalagent.shared.memory.MemoryService
import com.personalagent.shared.crypto.AndroidSecretKeyProvider
import com.personalagent.shared.crypto.SecretKeyProvider
import com.personalagent.shared.reminder.ReminderService
import com.personalagent.shared.safety.CrisisRecognizer
import com.personalagent.shared.safety.CrisisResourceProvider
import com.personalagent.shared.safety.CrisisResponder
import com.personalagent.shared.safety.DefaultCrisisResourceProvider
import com.personalagent.shared.safety.KeywordCrisisRecognizer
import com.personalagent.shared.safety.TrustedContactsStore
import com.personalagent.shared.store.AndroidKeyValueStorage
import com.personalagent.shared.crypto.EncryptedKeyValueStorage
import com.personalagent.shared.store.KeyValueStorage
import com.personalagent.shared.store.LocalStore
import com.personalagent.shared.store.PersistentLocalStore
import com.personalagent.shared.util.SystemClock

/**
 * Tiny manual DI container. Wires the shared business objects to their Android
 * implementations in one place. (Step 1 has no DI framework on purpose.)
 *
 * 🔒 Step 5 (DONE): the local store is now the real **encrypted** store —
 * [EncryptedKeyValueStorage] sealing every value with the hardware-backed
 * [AndroidSecretKeyProvider] before the plaintext [AndroidKeyValueStorage]
 * persists the ciphertext. The Step-1 plaintext SharedPreferences placeholder is
 * now only the ciphertext sink. Nothing above [KeyValueStorage] changed.
 * NOT-FOR-REAL-USERS until SECURITY_REVIEW Gate 1 sign-off.
 *
 * @param cloudConfig OFF by default (null) → cloud escalation is unavailable.
 *   To enable Step-4 cloud escalation, pass a [CloudConfig] for a **zero-retention
 *   provider** (base URL + model + API key). Never hardcode a key — supply it at
 *   runtime (e.g. user-entered / secure config).
 */
class AppContainer(
    context: Context,
    private val cloudConfig: CloudConfig? = null,
) {
    private val appContext = context.applicationContext

    /**
     * 🔒 The single hardware-backed key provider (AndroidKeyStore AES-256-GCM)
     * shared by every encrypted store below. One alias, one key; [ensureKey] is
     * idempotent so each [encrypted] wrapper can safely re-ensure it.
     */
    private val secretKeyProvider: SecretKeyProvider = AndroidSecretKeyProvider()

    /**
     * 🔒 Build an encrypted [KeyValueStorage]: values are sealed by
     * [secretKeyProvider] and the ciphertext persisted by the plaintext
     * [AndroidKeyValueStorage] file [fileName]. This is the Step-5 replacement for
     * using [AndroidKeyValueStorage] directly.
     */
    private fun encrypted(fileName: String): KeyValueStorage =
        EncryptedKeyValueStorage(
            delegate = AndroidKeyValueStorage(appContext, fileName),
            crypto = secretKeyProvider,
        )

    val store: LocalStore =
        PersistentLocalStore(encrypted("personal_agent_store"))

    /**
     * 🔒 REVIEW REQUIRED — credential + session-key storage + trust boundary.
     *
     * The connection to the user's OWN Hermes: base URL, API key, and the stable
     * `X-Hermes-Session-Key`. Sealed at rest by the same hardware-backed encrypted
     * [KeyValueStorage] as everything else — the API key and memory-scope key are
     * never persisted in plaintext, logged, or backed up. There is NO default or
     * hidden backend: the app talks only to the instance the user configures here.
     */
    val hermesConfigStore: HermesConfigStore =
        HermesConfigStore(encrypted("hermes_connection"))

    /** True once the user has connected to a Hermes at least once. */
    val isHermesConfigured: Boolean
        get() = hermesConfigStore.isConfigured()

    /**
     * Build a live [HermesClient] for the saved connection, or null if the user
     * hasn't connected yet. Cheap to call; the caller owns the returned client's
     * lifecycle (close when done). Re-reads config each call so a reconnect in
     * Settings takes effect immediately.
     */
    fun hermesClientOrNull(): HermesClient? =
        hermesConfigStore.load()?.let { HermesClient(it) }

    /** Build a one-off client for an unsaved [config] (used by the Connect test). */
    fun hermesClientFor(config: HermesConfig): HermesClient = HermesClient(config)

    /**
     * 🔒 First-run recovery-code setup state (verifier sealed at rest). The UI
     * gates app entry on [SecuritySetupRepository.isComplete].
     */
    val securitySetup: SecuritySetupRepository =
        SecuritySetupRepository(encrypted("security_setup"))

    /**
     * 🔞 18+ age-gate confirmation state. The very first onboarding gate: the app
     * is restricted to adults, so this is checked before anything else. Persists
     * only the boolean confirmation (never the date of birth).
     */
    val ageGate: AgeGateRepository =
        AgeGateRepository(encrypted("age_gate"))

    /**
     * First-run onboarding state (Welcome → recovery → AI model setup → Done).
     * Gates the onboarding flow so it shows once. Separate from [securitySetup]
     * so the AI-setup step is part of the same once-only flow.
     */
    val onboarding: OnboardingRepository =
        OnboardingRepository(encrypted("onboarding"))

    /**
     * On-device model provisioning (download-from-trusted-source → verify →
     * install) used by the onboarding AI step and the Settings entry. Implements
     * the sibling-owned `com.personalagent.shared.provisioning` contract; the
     * real fetch happens at runtime and needs a device + network.
     */
    val modelProvisioner: ModelProvisioner = AndroidModelProvisioner(appContext)

    /**
     * Robust, resumable model download (WorkManager foreground service). Survives
     * backgrounding / screen-lock / process death; the UI only observes it.
     */
    val modelDownloadController: com.personalagent.android.llm.ModelDownloadController =
        com.personalagent.android.llm.ModelDownloadController(appContext)

    /**
     * 🔒 CRISIS-CRITICAL (Step 7) — consent-first crisis-safety wiring. 🔒
     *
     * The user's pre-chosen trusted contacts, sealed at rest via the same encrypted
     * [KeyValueStorage] as everything else. The UI captures explicit consent up front
     * when a contact is added.
     */
    val trustedContactsStore: TrustedContactsStore =
        TrustedContactsStore(encrypted("trusted_contacts"))

    /**
     * 🔒 Placeholder crisis resources — clearly-labelled starting points pending a
     * crisis expert's review/localization. The support surface shows that caveat.
     */
    val crisisResourceProvider: CrisisResourceProvider = DefaultCrisisResourceProvider()

    /**
     * 🔒 Auto-detection is OFF by default. The canonical [KeywordCrisisRecognizer]
     * is wired here, but it is **never** connected to live/background scanning — it
     * is only consulted on an explicit, user-initiated check-in. The everyday entry
     * point is the "Support" tab, which surfaces support on demand without any
     * recognition at all. So there are no auto-triggers and no false alarms.
     * // TODO crisis-review: connecting the recognizer to any live text is gated
     * behind SECURITY_REVIEW Gate 2 sign-off.
     */
    val crisisRecognizer: CrisisRecognizer = KeywordCrisisRecognizer()

    /**
     * 🔒 Builds the supportive, consent-first surface. It assembles copy + resources
     * and contacts NO ONE — there is no autonomous path. NOT-FOR-REAL-USERS until
     * the crisis-expert gate is passed.
     */
    val crisisResponder: CrisisResponder =
        CrisisResponder(crisisResourceProvider)

    val reminderService: ReminderService =
        ReminderService(
            store = store,
            scheduler = AndroidReminderScheduler(appContext),
            clock = SystemClock,
        )

    /**
     * On-device, offline text embedder (Step 2 memory layer). Created lazily so
     * the (native) ONNX runtime only spins up if something actually embeds.
     * Requires the model asset to be installed — see [EmbedderFactory.isModelInstalled].
     */
    val embedder: Embedder by lazy { EmbedderFactory.create(appContext) }

    /** Whether the on-device embedding model asset is present in this build. */
    val isEmbeddingModelInstalled: Boolean
        get() = EmbedderFactory.isModelInstalled(appContext)

    /**
     * On-device, offline small LLM (Step 3). Created lazily so the (native)
     * MediaPipe runtime only spins up if something actually generates. Requires
     * the `.task` model to be provisioned on the device — see
     * [LlmModelProvisioning] and [isLlmModelInstalled].
     */
    val llm: OnDeviceLlm by lazy { LlmModelProvisioning.create(appContext) }

    /** Whether the on-device LLM model file is present on this device. */
    val isLlmModelInstalled: Boolean
        get() = LlmModelProvisioning.isModelInstalled(appContext)

    /**
     * Step-2 long-term memory engine (semantic retrieval + recording). The vector
     * index persists through its own **encrypted** store (the embeddings are
     * derived from user content, so they are sealed at rest like everything else).
     */
    val memoryService: MemoryService by lazy {
        MemoryService(
            embedder = embedder,
            index = InMemoryVectorIndex(encrypted("vector_index")),
            store = store,
        )
    }

    /**
     * 🔑 Bring-your-own-key cloud wallet (Stream 3). Persists, per provider, the
     * user's OWN developer API key + which provider is active — all sealed at rest
     * by the same encrypted [KeyValueStorage] as everything else (never plaintext,
     * never logged). The [CloudSettingsSection] UI reads/writes this.
     *
     * Billing note for the UI: the key is billed separately by Anthropic / OpenAI;
     * a Claude Pro / ChatGPT Plus consumer subscription cannot be used here. With
     * no key set, the app stays fully on-device.
     */
    val cloudKeyStore: CloudKeyStore =
        CloudKeyStore(encrypted("cloud_keys"))

    /**
     * Cloud escalation transport (Step 4 + Stream 3). **Default-OFF.**
     *   1. an explicit [cloudConfig] → [HttpCloudClient]; otherwise
     *   2. a [DynamicCloudClient] that re-reads the user's BYO-key selection from
     *      the encrypted [cloudKeyStore] **on every escalation**.
     *
     * 🔧 Resolving per-use (not once at construction) is deliberate: a key the user
     * saves in Settings takes effect IMMEDIATELY — no app restart. With no provider/
     * key set, the dynamic client throws on use, so the app stays fully on-device.
     * The key is read from the encrypted store at runtime — never hardcoded/logged.
     */
    val cloudClient: CloudClient =
        cloudConfig?.let { HttpCloudClient(it) }
            ?: DynamicCloudClient { cloudKeyStore.activeCloudClient() }

    /**
     * Step-4 conversation orchestrator. The real on-device anonymizer
     * ([DefaultPayloadPrep]) and the [HeuristicEscalationPolicy] are wired in; the
     * cloud call itself stays disabled until [cloudConfig] is supplied (see
     * [cloudClient]). So the escalate→anonymize→(cloud)→rehydrate path is live and
     * safe: it minimizes/tokenizes before anything would leave the device, and with
     * cloud off an escalation fails loudly rather than leaking.
     */
    /**
     * 🔒 On-device memory GRAPH (items about the user) — sealed at rest by the same
     * encrypted [KeyValueStorage] as everything else, and NEVER sent to the cloud.
     * It grounds the on-device model and is fully user-editable/exportable via the
     * Memory screen. Extraction uses the active LLM (with a heuristic fallback) and
     * the bundled embedder for similarity dedup.
     */
    val memoryGraphStore: com.personalagent.shared.graph.MemoryGraphStore by lazy {
        com.personalagent.shared.graph.PersistentMemoryGraphStore(encrypted("memory_graph"))
    }

    val memoryGraph: com.personalagent.shared.graph.MemoryGraphService by lazy {
        com.personalagent.shared.graph.MemoryGraphService(
            store = memoryGraphStore,
            embedder = embedder,
            extractor = com.personalagent.shared.graph.CompositeMemoryExtractor(llm),
            clock = SystemClock,
        )
    }

    val conversationService: ConversationService by lazy {
        ConversationService(
            llm = llm,
            memory = memoryService,
            escalationPolicy = HeuristicEscalationPolicy(),
            payloadPrep = DefaultPayloadPrep(),
            cloudClient = cloudClient,
            // 🔒 Local-only grounding from the on-device memory graph.
            userFacts = com.personalagent.shared.conversation.UserFactProvider { memoryGraph.retrieveFacts(it) },
        )
    }
}
