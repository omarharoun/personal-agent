package com.personalagent.android.notification

import android.content.Context
import android.content.Intent
import com.personalagent.shared.model.Reminder

/** Shared keys/factory for the broadcast that fires a reminder. */
object ReminderIntents {
    const val ACTION_FIRE = "com.personalagent.android.ACTION_FIRE_REMINDER"
    const val EXTRA_ID = "reminder_id"
    const val EXTRA_TITLE = "reminder_title"
    const val EXTRA_BODY = "reminder_body"

    fun fireIntent(context: Context, reminder: Reminder): Intent =
        Intent(context, ReminderReceiver::class.java).apply {
            action = ACTION_FIRE
            putExtra(EXTRA_ID, reminder.id)
            putExtra(EXTRA_TITLE, reminder.title)
            putExtra(EXTRA_BODY, reminder.note)
        }

    /** Stable request code per reminder id, for PendingIntent identity. */
    fun requestCode(reminderId: String): Int = reminderId.hashCode()
}
