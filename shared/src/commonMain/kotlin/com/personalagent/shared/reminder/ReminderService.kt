package com.personalagent.shared.reminder

import com.personalagent.shared.model.Reminder
import com.personalagent.shared.model.ReminderStatus
import com.personalagent.shared.store.LocalStore
import com.personalagent.shared.util.Clock

/** Outcome of trying to schedule a reminder — pure data, easy to assert. */
sealed interface ScheduleResult {
    data class Scheduled(val reminder: Reminder) : ScheduleResult
    data class Rejected(val reason: Reason) : ScheduleResult {
        enum class Reason { BLANK_TITLE, TRIGGER_IN_PAST }
    }
}

/**
 * Coordinates reminder lifecycle across persistence ([LocalStore]) and the OS
 * alarm ([ReminderScheduler]). The validation/decision logic lives here and is
 * fully unit-tested with a fake clock + fake store + [NoopReminderScheduler];
 * the platform pieces stay dumb.
 */
class ReminderService(
    private val store: LocalStore,
    private val scheduler: ReminderScheduler,
    private val clock: Clock,
) {

    /**
     * Validate, persist, and schedule a new reminder. A reminder whose trigger
     * is not strictly in the future is rejected (the OS would otherwise fire it
     * immediately or drop it).
     */
    suspend fun schedule(title: String, triggerAtMillis: Long, note: String = ""): ScheduleResult {
        val now = clock.nowMillis()
        if (title.isBlank()) {
            return ScheduleResult.Rejected(ScheduleResult.Rejected.Reason.BLANK_TITLE)
        }
        if (triggerAtMillis <= now) {
            return ScheduleResult.Rejected(ScheduleResult.Rejected.Reason.TRIGGER_IN_PAST)
        }
        val reminder = Reminder.create(
            title = title,
            triggerAtMillis = triggerAtMillis,
            nowMillis = now,
            note = note,
        )
        store.upsertReminder(reminder)
        scheduler.schedule(reminder)
        return ScheduleResult.Scheduled(reminder)
    }

    /** Cancel a scheduled reminder: mark it CANCELLED and drop the OS alarm. */
    suspend fun cancel(reminderId: String) {
        val existing = store.getReminder(reminderId) ?: return
        store.upsertReminder(existing.copy(status = ReminderStatus.CANCELLED))
        scheduler.cancel(reminderId)
    }

    /**
     * Mark a reminder FIRED. Called when the OS notification is delivered so the
     * persisted state stays in sync with what the user actually saw.
     */
    suspend fun markFired(reminderId: String) {
        val existing = store.getReminder(reminderId) ?: return
        if (existing.status == ReminderStatus.SCHEDULED) {
            store.upsertReminder(existing.copy(status = ReminderStatus.FIRED))
        }
    }

    /**
     * Reminders that should have fired by [now] but are still SCHEDULED — e.g.
     * because the device was off when the alarm was due. The app calls this on
     * launch/boot to re-fire or reconcile missed reminders.
     */
    suspend fun dueReminders(now: Long = clock.nowMillis()): List<Reminder> =
        store.allReminders()
            .filter { it.isDue(now) }
            .sortedBy { it.triggerAtMillis }

    /** Re-arm every still-scheduled future reminder (used after device reboot). */
    suspend fun rescheduleAll(now: Long = clock.nowMillis()) {
        store.allReminders()
            .filter { it.status == ReminderStatus.SCHEDULED && it.triggerAtMillis > now }
            .forEach { scheduler.schedule(it) }
    }
}
