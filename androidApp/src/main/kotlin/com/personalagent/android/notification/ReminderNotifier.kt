package com.personalagent.android.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.getSystemService
import com.personalagent.android.MainActivity

/**
 * Owns the reminder notification channel and posts the actual notification.
 * Tapping it deep-links into the app's Reminders view (see [MainActivity]).
 */
object ReminderNotifier {
    const val CHANNEL_ID = "reminders"
    private const val CHANNEL_NAME = "Reminders"

    fun ensureChannel(context: Context) {
        val channel = NotificationChannel(
            CHANNEL_ID,
            CHANNEL_NAME,
            NotificationManager.IMPORTANCE_HIGH,
        ).apply { description = "Time-based reminders from your Life Agent" }
        context.getSystemService<NotificationManager>()?.createNotificationChannel(channel)
    }

    /**
     * Post the reminder notification. Uses a stable int derived from the reminder
     * id so re-posting the same reminder replaces rather than stacks. Tapping opens
     * the app straight to Reminders. No-op if POST_NOTIFICATIONS isn't granted.
     */
    fun show(context: Context, reminderId: String, title: String, body: String) {
        ensureChannel(context)

        val openReminders = Intent(context, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
            putExtra(MainActivity.EXTRA_OPEN, MainActivity.DEST_REMINDERS)
        }
        val pending = PendingIntent.getActivity(
            context,
            reminderId.hashCode(),
            openReminders,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentTitle(title)
            .setContentText(body)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setContentIntent(pending)
            .build()

        val manager = NotificationManagerCompat.from(context)
        if (manager.areNotificationsEnabled()) {
            runCatching { manager.notify(reminderId.hashCode(), notification) }
        }
    }
}
