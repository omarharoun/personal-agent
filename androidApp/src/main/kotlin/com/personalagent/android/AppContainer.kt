package com.personalagent.android

import android.content.Context
import com.personalagent.android.embedding.EmbedderFactory
import com.personalagent.android.llm.AndroidModelProvisioner
import com.personalagent.android.llm.LlmModelProvisioning
import com.personalagent.android.notification.AndroidReminderScheduler
import com.personalagent.android.onboarding.OnboardingRepository
import com.personalagent.android.onboarding.SecuritySetupRepository
import com.personalagent.shared.provisioning.ModelProvisioner
import com.personalagent.shared.cloud.CloudClient
import com.personalagent.shared.cloud.CloudConfig
import com.personalagent.shared.cloud.DefaultPayloadPrep
import com.personalagent.shared.cloud.HeuristicEscalationPolicy
import com.personalagent.shared.cloud.HttpCloudClient
import com.personalagent.shared.cloud.UnavailableCloudClient
import com.personalagent.shared.conversation.ConversationService
import com.personalagent.shared.conversation.OnDeviceLlm
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
     * 🔒 First-run recovery-code setup state (verifier sealed at rest). The UI
     * gates app entry on [SecuritySetupRepository.isComplete].
     */
    val securitySetup: SecuritySetupRepository =
        SecuritySetupRepository(encrypted("security_setup"))

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
     * Cloud escalation transport (Step 4). **Default-OFF:** with no [cloudConfig]
     * this is [UnavailableCloudClient], which throws if ever reached — so escalation
     * prep is wired but no data can leave the device until a provider is configured.
     *
     * configure a zero-retention provider + key to enable cloud escalation
     * (pass a [CloudConfig] to [AppContainer]); the API key must come from runtime
     * config, never source.
     */
    val cloudClient: CloudClient =
        cloudConfig?.let { HttpCloudClient(it) } ?: UnavailableCloudClient

    /**
     * Step-4 conversation orchestrator. The real on-device anonymizer
     * ([DefaultPayloadPrep]) and the [HeuristicEscalationPolicy] are wired in; the
     * cloud call itself stays disabled until [cloudConfig] is supplied (see
     * [cloudClient]). So the escalate→anonymize→(cloud)→rehydrate path is live and
     * safe: it minimizes/tokenizes before anything would leave the device, and with
     * cloud off an escalation fails loudly rather than leaking.
     */
    val conversationService: ConversationService by lazy {
        ConversationService(
            llm = llm,
            memory = memoryService,
            escalationPolicy = HeuristicEscalationPolicy(),
            payloadPrep = DefaultPayloadPrep(),
            cloudClient = cloudClient,
        )
    }
}
