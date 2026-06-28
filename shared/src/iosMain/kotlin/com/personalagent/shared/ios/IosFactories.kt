package com.personalagent.shared.ios

import com.personalagent.shared.cloud.CloudClient
import com.personalagent.shared.cloud.CloudConfig
import com.personalagent.shared.cloud.CloudKeyStore
import com.personalagent.shared.cloud.DefaultPayloadPrep
import com.personalagent.shared.cloud.HeuristicEscalationPolicy
import com.personalagent.shared.cloud.HttpCloudClient
import com.personalagent.shared.cloud.UnavailableCloudClient
import com.personalagent.shared.conversation.ConversationService
import com.personalagent.shared.conversation.GenOptions
import com.personalagent.shared.crypto.IosNativeKeyStore
import com.personalagent.shared.crypto.IosSecretKeyProvider
import com.personalagent.shared.crypto.SecretKeyProvider
import com.personalagent.shared.llm.IosLlmAdapter
import com.personalagent.shared.llm.IosNativeLlm
import com.personalagent.shared.conversation.OnDeviceLlm
import com.personalagent.shared.memory.Embedder
import com.personalagent.shared.memory.InMemoryVectorIndex
import com.personalagent.shared.memory.IosEmbedderAdapter
import com.personalagent.shared.memory.IosNativeEmbedder
import com.personalagent.shared.memory.MemoryService
import com.personalagent.shared.provisioning.IosModelProvisioningAdapter
import com.personalagent.shared.provisioning.IosNativeModelProvisioner
import com.personalagent.shared.provisioning.ModelOption
import com.personalagent.shared.provisioning.ModelProvisioner
import com.personalagent.shared.provisioning.ProvisionState
import com.personalagent.shared.reminder.ReminderScheduler
import com.personalagent.shared.reminder.ReminderService
import com.personalagent.shared.safety.CrisisRecognizer
import com.personalagent.shared.safety.CrisisResourceProvider
import com.personalagent.shared.safety.CrisisResponder
import com.personalagent.shared.safety.DefaultCrisisResourceProvider
import com.personalagent.shared.safety.KeywordCrisisRecognizer
import com.personalagent.shared.safety.TrustedContactsStore
import com.personalagent.shared.crypto.EncryptedKeyValueStorage
import com.personalagent.shared.store.IosKeyValueStorage
import com.personalagent.shared.store.LocalStore
import com.personalagent.shared.store.PersistentLocalStore
import com.personalagent.shared.util.Clock
import com.personalagent.shared.util.SystemClock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

/**
 * Clean Swift-facing entry points into the shared stack, so the SwiftUI app
 * never has to construct Kotlin objects with default arguments (which don't
 * cross the ObjC/Swift bridge).
 *
 * The reminder scheduler is provided FROM Swift (it uses UNUserNotificationCenter),
 * which is why [createReminderService] takes it as a parameter.
 *
 * 🔒 Step 5 (DONE for iOS): [createLocalStore]/[createMemoryService] now wrap the
 * plaintext [IosKeyValueStorage] containers in an [EncryptedKeyValueStorage]
 * backed by [IosSecretKeyProvider] (Keychain + Secure Enclave + CryptoKit
 * AES-GCM). Everything above the [KeyValueStorage] seam is unchanged; the SwiftUI
 * app only has to provide the Swift key store (see [createSecretKeyProvider]).
 */
object IosFactories {
    /**
     * 🔒 Wrap the Swift-provided secure key store ([IosNativeKeyStore], implemented
     * by `IosSecretKeyStore` over Keychain + Secure Enclave + CryptoKit) as the
     * shared [SecretKeyProvider]. Provided FROM Swift for the same reason as the
     * embedder/scheduler/LLM — the crypto lives in native Apple frameworks.
     */
    fun createSecretKeyProvider(native: IosNativeKeyStore): SecretKeyProvider =
        IosSecretKeyProvider(native)

    /**
     * 🔒 The REAL, encrypted-at-rest store for iOS. JSON blobs are AES-GCM sealed
     * by [crypto] before they ever reach the (plaintext) NSUserDefaults container.
     */
    fun createLocalStore(crypto: SecretKeyProvider): LocalStore =
        PersistentLocalStore(EncryptedKeyValueStorage(IosKeyValueStorage(), crypto))

    fun createReminderService(store: LocalStore, scheduler: ReminderScheduler): ReminderService =
        ReminderService(store, scheduler, SystemClock)

    /**
     * Wrap the Swift-provided on-device embedder ([IosNativeEmbedder], implemented
     * by `IosEmbedder` over Apple NaturalLanguage) as the shared [Embedder].
     *
     * The embedder is provided FROM Swift for the same reason the reminder
     * scheduler is — it uses a native (NaturalLanguage) API that lives on the
     * Swift side. See [createReminderService] for the mirror pattern.
     *
     * 🔗 Step 2 wiring: once the memory-layer sibling lands `MemoryService`, add a
     *    `createMemoryService(store, embedder)` factory here that mirrors
     *    [createReminderService], so SwiftUI never constructs Kotlin objects with
     *    default args. The [Embedder] returned here is exactly what it will take.
     */
    fun createEmbedder(native: IosNativeEmbedder): Embedder =
        IosEmbedderAdapter(native)

    /**
     * Wrap the Swift-provided on-device LLM ([IosNativeLlm], implemented by
     * `IosOnDeviceLlm` over MLX Swift) as the shared [OnDeviceLlm] (Step 3).
     *
     * Provided FROM Swift for the same reason as the embedder/scheduler — it uses
     * native (MLX / Metal) APIs that live on the Swift side. The returned
     * [OnDeviceLlm] satisfies the `suspend`/`Flow` contract; the Swift seam itself
     * stays synchronous. See [createEmbedder] for the mirror pattern.
     */
    fun createOnDeviceLlm(native: IosNativeLlm): OnDeviceLlm =
        IosLlmAdapter(native)

    /**
     * Build [GenOptions] from Swift. Kotlin default arguments don't cross the
     * ObjC/Swift bridge, so SwiftUI can't write `GenOptions()`; this factory
     * supplies the Step-3 defaults (maxTokens=512, temperature=0.7, no stops).
     */
    fun defaultGenOptions(): GenOptions = GenOptions()

    /**
     * Step-2 memory engine for iOS. The vector index persists through its own
     * [IosKeyValueStorage] suite so it survives restarts independently of the
     * entity store.
     *
     * 🔒 Step 5: the embedding vectors are derived from user content, so the index
     * suite is also wrapped in [EncryptedKeyValueStorage] with the same [crypto].
     */
    fun createMemoryService(store: LocalStore, embedder: Embedder, crypto: SecretKeyProvider): MemoryService =
        MemoryService(
            embedder = embedder,
            index = InMemoryVectorIndex(
                EncryptedKeyValueStorage(IosKeyValueStorage("vector_index"), crypto),
            ),
            store = store,
        )

    /**
     * Step-4 conversation orchestrator for iOS. Wires the real on-device
     * anonymizer ([DefaultPayloadPrep]) + [HeuristicEscalationPolicy].
     *
     * Cloud is **default-OFF**: pass [cloudConfig] = null (the SwiftUI default)
     * and escalation uses [UnavailableCloudClient] (throws if reached), so nothing
     * leaves the device. To enable cloud escalation, configure a **zero-retention
     * provider** [CloudConfig] (base URL + model + API key supplied at runtime —
     * never hardcoded) and it is used via [HttpCloudClient] (Ktor Darwin engine).
     */
    fun createConversationService(
        llm: OnDeviceLlm,
        store: LocalStore,
        embedder: Embedder,
        crypto: SecretKeyProvider,
        cloudConfig: CloudConfig?,
    ): ConversationService = ConversationService(
        llm = llm,
        memory = createMemoryService(store, embedder, crypto),
        escalationPolicy = HeuristicEscalationPolicy(),
        payloadPrep = DefaultPayloadPrep(),
        cloudClient = cloudConfig?.let { HttpCloudClient(it) } ?: UnavailableCloudClient,
    )

    /**
     * 🔑 The bring-your-own-key cloud wallet (Stream 3) for iOS, encrypted at rest
     * with the SAME [crypto] (Keychain + Secure Enclave + AES-GCM) as every other
     * entity. Persists, per provider, the user's OWN developer API key + which
     * provider is active. Stored in its own [IosKeyValueStorage] suite. Keys are
     * never logged. `CloudSettingsView` reads/writes this.
     */
    fun createCloudKeyStore(crypto: SecretKeyProvider): CloudKeyStore =
        CloudKeyStore(
            EncryptedKeyValueStorage(IosKeyValueStorage("cloud_keys"), crypto),
        )

    /**
     * Stream-3 variant of [createConversationService] that derives the cloud client
     * from the user's BYO-key selection in [cloudKeyStore]. The cloud client is
     * non-null only when the user has chosen an active provider AND set its key;
     * otherwise escalation uses [UnavailableCloudClient] and nothing leaves the
     * device. The key is read from the encrypted store at runtime — never logged.
     *
     * (Resolved once when the service is built; the Settings view notes that a
     * newly-saved key applies after relaunch.)
     */
    fun createConversationService(
        llm: OnDeviceLlm,
        store: LocalStore,
        embedder: Embedder,
        crypto: SecretKeyProvider,
        cloudKeyStore: CloudKeyStore,
    ): ConversationService {
        val cloudClient: CloudClient = cloudKeyStore.activeCloudClient() ?: UnavailableCloudClient
        return ConversationService(
            llm = llm,
            memory = createMemoryService(store, embedder, crypto),
            escalationPolicy = HeuristicEscalationPolicy(),
            payloadPrep = DefaultPayloadPrep(),
            cloudClient = cloudClient,
        )
    }

    fun systemClock(): Clock = SystemClock

    // 🔒 CRISIS-CRITICAL (Step 7) — consent-first only; autonomous action disabled; NOT-FOR-REAL-USERS until human + crisis-expert review.
    //
    // Step-7 crisis-safety seams for iOS. These mirror the rest of this object:
    // SwiftUI never constructs Kotlin objects with default args, so it goes
    // through these factories.
    //
    // The shared safety contract (CrisisRecognizer / CrisisResource /
    // CrisisResponse / TrustedContact) is owned by the `feat/step7-shared`
    // sibling; reconcile at merge (see com.personalagent.shared.safety).

    /**
     * Conservative, on-device, classification-ONLY recognizer (no autonomous
     * action). ⚠️ Placeholder, not a validated classifier — see
     * [KeywordCrisisRecognizer].
     */
    fun createCrisisRecognizer(): CrisisRecognizer = KeywordCrisisRecognizer()

    /** Single, reviewed source of crisis resources the support view lists from. */
    fun createCrisisResourceProvider(): CrisisResourceProvider = DefaultCrisisResourceProvider()

    /**
     * 🔒 Builds the supportive, consent-first response from a [CrisisAssessment].
     * Contacts NO ONE — there is no autonomous path. Provided as a factory so
     * SwiftUI doesn't construct Kotlin objects with default args.
     */
    fun createCrisisResponder(): CrisisResponder =
        CrisisResponder(createCrisisResourceProvider())

    /**
     * 🔒 The user's hand-curated trusted contacts, encrypted at rest with the
     * SAME [crypto] (Keychain + Secure Enclave + AES-GCM) as every other entity.
     * Every entry is added explicitly by the user in the setup view — never
     * inferred. Stored in its own [IosKeyValueStorage] suite.
     */
    fun createTrustedContactsStore(crypto: SecretKeyProvider): TrustedContactsStore =
        TrustedContactsStore(
            EncryptedKeyValueStorage(IosKeyValueStorage("trusted_contacts"), crypto),
        )

    // MARK: On-device model provisioning (the "Set up your AI" onboarding step + Settings).
    //
    // The provisioning contract (com.personalagent.shared.provisioning) is owned
    // by the `feat/model-provisioning-shared` sibling; the copy on this branch is
    // a flagged stand-in for isolated compilation (see ModelProvisioning.kt). The
    // bridge below mirrors the rest of this object: SwiftUI never constructs Kotlin
    // objects with default args, and Swift never *produces* a Kotlin Flow — it
    // implements the synchronous [IosNativeModelProvisioner] seam and consumes
    // progress through [startProvision]'s callback.

    /**
     * Wrap the Swift-provided native provisioner ([IosNativeModelProvisioner],
     * implemented by `IosModelProvisioner` over URLSession + CryptoKit) as the
     * shared [ModelProvisioner]. Provided FROM Swift for the same reason as the
     * embedder/LLM — the network + on-disk install live in native Apple APIs.
     */
    fun createModelProvisioner(native: IosNativeModelProvisioner): ModelProvisioner =
        IosModelProvisioningAdapter(native)

    /**
     * Start provisioning [option] through the shared [ModelProvisioner] contract,
     * delivering every [ProvisionState] (Downloading → Verifying → Installed, or
     * Failed) to [onState] on the main thread so SwiftUI can update `@Published`
     * state directly. There is NO auto-download — Swift calls this only when the
     * user taps Download/Retry.
     *
     * Returns an [IosProvisionHandle]; call [IosProvisionHandle.cancel] to abort an
     * in-flight download (the underlying transfer is aborted via the seam's
     * `isCancelled` poll). Swift can't collect a Kotlin [kotlinx.coroutines.flow.Flow]
     * ergonomically, so this exposes the flow as a cancellable callback — the
     * supported interop direction (Swift *calls* Kotlin).
     */
    fun startProvision(
        provisioner: ModelProvisioner,
        option: ModelOption,
        wifiOnly: Boolean,
        onState: (ProvisionState) -> Unit,
    ): IosProvisionHandle {
        // Collect on Main so onState fires on the UI thread; the adapter already
        // shifts the blocking download to Dispatchers.Default via flowOn.
        val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
        val job = scope.launch {
            provisioner.provision(option, wifiOnly).collect { state -> onState(state) }
        }
        return IosProvisionHandle(job)
    }
}

/**
 * Swift-facing handle to an in-flight provisioning run started by
 * [IosFactories.startProvision]. Holds the collecting [Job]; [cancel] aborts the
 * collection (and, through the seam's `isCancelled` poll, the native download).
 */
class IosProvisionHandle internal constructor(private val job: Job) {
    fun cancel() {
        job.cancel()
    }
}
