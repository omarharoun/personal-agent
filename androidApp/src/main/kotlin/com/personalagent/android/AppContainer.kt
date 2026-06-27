package com.personalagent.android

import android.content.Context
import com.personalagent.android.embedding.EmbedderFactory
import com.personalagent.android.llm.LlmModelProvisioning
import com.personalagent.android.notification.AndroidReminderScheduler
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
import com.personalagent.shared.reminder.ReminderService
import com.personalagent.shared.store.AndroidKeyValueStorage
import com.personalagent.shared.store.LocalStore
import com.personalagent.shared.store.PersistentLocalStore
import com.personalagent.shared.util.SystemClock

/**
 * Tiny manual DI container. Wires the shared business objects to their Android
 * implementations in one place. (Step 1 has no DI framework on purpose.)
 *
 * 🔒 Step 5 swap point: replace [AndroidKeyValueStorage] here with an encrypted
 * implementation; nothing else in the app changes.
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

    val store: LocalStore =
        PersistentLocalStore(AndroidKeyValueStorage(appContext))

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
     * index persists through its own [AndroidKeyValueStorage] file so it survives
     * restarts independently of the entity store.
     */
    val memoryService: MemoryService by lazy {
        MemoryService(
            embedder = embedder,
            index = InMemoryVectorIndex(AndroidKeyValueStorage(appContext, "vector_index")),
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
