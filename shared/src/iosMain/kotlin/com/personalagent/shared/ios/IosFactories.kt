package com.personalagent.shared.ios

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

    fun systemClock(): Clock = SystemClock
}
