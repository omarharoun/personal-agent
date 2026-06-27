package com.personalagent.android.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.personalagent.android.PersonalAgentApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Exact alarms do not survive a reboot. On BOOT_COMPLETED we re-arm every
 * still-scheduled future reminder so nothing is silently lost.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val pending = goAsync()
        val container = (context.applicationContext as PersonalAgentApp).container
        CoroutineScope(Dispatchers.Default).launch {
            try {
                container.reminderService.rescheduleAll()
            } finally {
                pending.finish()
            }
        }
    }
}
