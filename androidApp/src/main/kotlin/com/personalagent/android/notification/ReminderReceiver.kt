package com.personalagent.android.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.personalagent.android.PersonalAgentApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Receives the exact-alarm broadcast and (1) posts the notification the user
 * sees, (2) flips the reminder's persisted status to FIRED so app state stays
 * truthful. This is the moment a reminder "actually fires".
 */
class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getStringExtra(ReminderIntents.EXTRA_ID) ?: return
        val title = intent.getStringExtra(ReminderIntents.EXTRA_TITLE) ?: "Reminder"
        val body = intent.getStringExtra(ReminderIntents.EXTRA_BODY).orEmpty()

        ReminderNotifier.show(context, id, title, body)

        // Persist FIRED off the main thread; keep the receiver alive while we do.
        val pending = goAsync()
        val container = (context.applicationContext as PersonalAgentApp).container
        CoroutineScope(Dispatchers.Default).launch {
            try {
                container.reminderService.markFired(id)
            } finally {
                pending.finish()
            }
        }
    }
}
