package com.personalagent.android.notification

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.os.Build
import androidx.core.content.getSystemService
import com.personalagent.shared.model.Reminder
import com.personalagent.shared.reminder.ReminderScheduler

/**
 * Android implementation of the shared [ReminderScheduler] seam.
 *
 * Registers an exact alarm with [AlarmManager]; when it fires the OS sends a
 * broadcast to [ReminderReceiver], which posts the notification. This is what
 * makes a reminder actually go off even if the app is backgrounded/closed.
 */
class AndroidReminderScheduler(
    private val context: Context,
) : ReminderScheduler {

    private val alarmManager: AlarmManager? = context.getSystemService()

    override fun schedule(reminder: Reminder) {
        val am = alarmManager ?: return
        val pending = pendingIntent(reminder, mutable = false)

        // On API 31+ exact alarms may require user permission; fall back to an
        // inexact alarm rather than crashing if it isn't granted. Pre-31 they
        // are always allowed.
        val canExact = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            am.canScheduleExactAlarms()
        } else {
            true
        }
        if (canExact) {
            am.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                reminder.triggerAtMillis,
                pending,
            )
        } else {
            am.setAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                reminder.triggerAtMillis,
                pending,
            )
        }
    }

    override fun cancel(reminderId: String) {
        val am = alarmManager ?: return
        // Rebuild an equivalent PendingIntent to target the same alarm.
        val intent = android.content.Intent(context, ReminderReceiver::class.java).apply {
            action = ReminderIntents.ACTION_FIRE
        }
        val pending = PendingIntent.getBroadcast(
            context,
            ReminderIntents.requestCode(reminderId),
            intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
        )
        if (pending != null) {
            am.cancel(pending)
            pending.cancel()
        }
    }

    private fun pendingIntent(reminder: Reminder, mutable: Boolean): PendingIntent {
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or
            if (mutable) PendingIntent.FLAG_MUTABLE else PendingIntent.FLAG_IMMUTABLE
        return PendingIntent.getBroadcast(
            context,
            ReminderIntents.requestCode(reminder.id),
            ReminderIntents.fireIntent(context, reminder),
            flags,
        )
    }
}
