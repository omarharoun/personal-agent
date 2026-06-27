package com.personalagent.shared.ios

import com.personalagent.shared.memory.Embedder
import com.personalagent.shared.memory.IosEmbedderAdapter
import com.personalagent.shared.memory.IosNativeEmbedder
import com.personalagent.shared.reminder.ReminderScheduler
import com.personalagent.shared.reminder.ReminderService
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
 * 🔒 Step 5 swap point: replace [IosKeyValueStorage] in [createLocalStore] with
 * an encrypted (Keychain-backed) implementation; the SwiftUI app is unaffected.
 */
object IosFactories {
    fun createLocalStore(): LocalStore =
        PersistentLocalStore(IosKeyValueStorage())

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

    fun systemClock(): Clock = SystemClock
}
