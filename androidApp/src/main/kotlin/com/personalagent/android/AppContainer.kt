package com.personalagent.android

import android.content.Context
import com.personalagent.android.notification.AndroidReminderScheduler
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
}
