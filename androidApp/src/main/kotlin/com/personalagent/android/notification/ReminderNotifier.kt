package com.personalagent.android.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.getSystemService

/**
 * Owns the reminder notification channel and posts the actual notification.
 * This is what the user sees when a reminder fires.
 */
object ReminderNotifier {
    const val CHANNEL_ID = "reminders"
    private const val CHANNEL_NAME = "Reminders"

    fun ensureChannel(context: Context) {
        val channel = NotificationChannel(
            CHANNEL_ID,
            CHANNEL_NAME,
            NotificationManager.IMPORTANCE_HIGH,
        ).apply { description = "Time-based reminders you set in Personal Agent" }
        context.getSystemService<NotificationManager>()?.createNotificationChannel(channel)
    }

    /**
     * Post the reminder notification. Uses a stable int derived from the
     * reminder id so re-posting the same reminder replaces rather than stacks.
     * No-op if the user has not granted POST_NOTIFICATIONS (API 33+).
     */
    fun show(context: Context, reminderId: String, title: String, body: String) {
        ensureChannel(context)
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentTitle(title)
            .setContentText(body)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .build()

        val manager = NotificationManagerCompat.from(context)
        if (manager.areNotificationsEnabled()) {
            runCatching { manager.notify(reminderId.hashCode(), notification) }
        }
    }
}
