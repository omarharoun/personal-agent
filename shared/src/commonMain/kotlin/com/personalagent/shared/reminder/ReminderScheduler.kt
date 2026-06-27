package com.personalagent.shared.reminder

import com.personalagent.shared.model.Reminder

/**
 * Platform seam for turning a [Reminder] into a real OS-level alarm/notification.
 *
 * Implementations:
 *   - Android: AlarmManager.setExactAndAllowWhileIdle(...) -> a BroadcastReceiver
 *     posts a notification. (see androidApp)
 *   - iOS: UNUserNotificationCenter with a UNCalendarNotificationTrigger.
 *     (see iosApp / iosMain)
 *   - JVM/tests: [NoopReminderScheduler] records calls without touching an OS.
 *
 * Kept as a plain interface (not expect/actual) so it is trivially injectable
 * and fakeable in shared tests.
 */
interface ReminderScheduler {
    /** Register the OS alarm that will fire at [Reminder.triggerAtMillis]. */
    fun schedule(reminder: Reminder)

    /** Cancel a previously scheduled OS alarm by reminder id. */
    fun cancel(reminderId: String)
}

/**
 * No-op scheduler: used on the JVM target and in unit tests. It records what
 * it was asked to do so tests can assert scheduling behaviour without an OS.
 */
class NoopReminderScheduler : ReminderScheduler {
    val scheduled = mutableListOf<Reminder>()
    val cancelled = mutableListOf<String>()

    override fun schedule(reminder: Reminder) { scheduled += reminder }
    override fun cancel(reminderId: String) { cancelled += reminderId }
}
