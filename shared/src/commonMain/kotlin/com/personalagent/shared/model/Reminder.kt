package com.personalagent.shared.model

import kotlinx.serialization.Serializable

/**
 * A time-based reminder. [triggerAtMillis] is the epoch-millis instant the
 * local notification should fire. Platform schedulers (AlarmManager on
 * Android, UNUserNotificationCenter on iOS) turn this into an OS-level alarm.
 */
@Serializable
data class Reminder(
    val id: String,
    val title: String,
    val note: String = "",
    val triggerAtMillis: Long,
    val status: ReminderStatus = ReminderStatus.SCHEDULED,
    val createdAt: Long,
) {
    companion object {
        fun create(title: String, triggerAtMillis: Long, nowMillis: Long, note: String = ""): Reminder =
            Reminder(
                id = Ids.next(nowMillis),
                title = title.trim(),
                note = note,
                triggerAtMillis = triggerAtMillis,
                status = ReminderStatus.SCHEDULED,
                createdAt = nowMillis,
            )
    }

    /** True when this reminder's fire time is at or before [nowMillis]. */
    fun isDue(nowMillis: Long): Boolean =
        status == ReminderStatus.SCHEDULED && triggerAtMillis <= nowMillis
}

enum class ReminderStatus {
    SCHEDULED,
    FIRED,
    CANCELLED,
}
