package com.personalagent.shared.ios

import com.personalagent.shared.cloud.CloudConfig
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
import com.personalagent.shared.reminder.ReminderScheduler
import com.personalagent.shared.reminder.ReminderService
import com.personalagent.shared.crypto.EncryptedKeyValueStorage
import com.personalagent.shared.store.IosKeyValueStorage
import com.personalagent.shared.store.LocalStore
import com.personalagent.shared.store.PersistentLocalStore
import com.personalagent.shared.util.Clock
import com.personalagent.shared.util.SystemClock

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

    fun systemClock(): Clock = SystemClock
}
