package com.personalagent.android

import android.content.Context
import com.personalagent.android.embedding.EmbedderFactory
import com.personalagent.android.notification.AndroidReminderScheduler
import com.personalagent.shared.memory.Embedder
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
 */
class AppContainer(context: Context) {
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
}
