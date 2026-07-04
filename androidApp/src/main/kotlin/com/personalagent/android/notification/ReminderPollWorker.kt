package com.personalagent.android.notification

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.personalagent.android.PersonalAgentApp
import java.util.concurrent.TimeUnit

/**
 * Delivers reminders by POLLING the user's Hermes `/api/jobs` and raising a LOCAL
 * notification when one is due (the chosen delivery model — no server we control,
 * no push service). Hermes owns the schedule; this worker is the notifier.
 *
 * Scheduled two ways (see [ReminderScheduling]):
 *  - a periodic poll (every 15 min — WorkManager's floor) as a safety net, and
 *  - a one-time poll shortly after each reminder's due time for punctual delivery.
 *
 * The reminder *text* is read from Hermes at poll time and shown in the
 * notification; the app persists only opaque fire-keys to notify each firing once.
 */
class ReminderPollWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val app = applicationContext as? PersonalAgentApp ?: return Result.success()
        val pair = app.container.reminderPollerOrNull() ?: return Result.success() // not connected
        val (client, poller) = pair
        return try {
            poller.pollOnce { due ->
                ReminderNotifier.show(applicationContext, due.jobId, due.title, due.body)
            }
            Result.success()
        } catch (_: Throwable) {
            // Transient (Hermes unreachable, offline) — let WorkManager retry.
            Result.retry()
        } finally {
            client.close()
        }
    }
}

/** Registers/cancels the reminder polling work. */
object ReminderScheduling {
    private const val PERIODIC = "hermes_reminder_poll_periodic"
    private const val ONESHOT_PREFIX = "hermes_reminder_poll_at_"

    /** Ensure the every-15-min safety-net poll is running. Idempotent. */
    fun ensurePeriodic(context: Context) {
        val req = PeriodicWorkRequestBuilder<ReminderPollWorker>(15, TimeUnit.MINUTES).build()
        WorkManager.getInstance(context)
            .enqueueUniquePeriodicWork(PERIODIC, ExistingPeriodicWorkPolicy.KEEP, req)
    }

    /** Poll right now (e.g. on app open / after creating a reminder). */
    fun pollNow(context: Context) {
        val req = OneTimeWorkRequestBuilder<ReminderPollWorker>().build()
        WorkManager.getInstance(context)
            .enqueueUniqueWork("${ONESHOT_PREFIX}now", ExistingWorkPolicy.REPLACE, req)
    }

    /**
     * Schedule a punctual poll ~[atMillis]. WorkManager delays aren't exact
     * (Doze can defer them), but combined with the periodic net this delivers
     * reminders close to their time without any push infrastructure.
     */
    fun pollAt(context: Context, atMillis: Long, tag: String) {
        val delay = (atMillis - System.currentTimeMillis()).coerceAtLeast(0)
        val req = OneTimeWorkRequestBuilder<ReminderPollWorker>()
            .setInitialDelay(delay, TimeUnit.MILLISECONDS)
            .build()
        WorkManager.getInstance(context)
            .enqueueUniqueWork("$ONESHOT_PREFIX$tag", ExistingWorkPolicy.REPLACE, req)
    }

    fun cancelAll(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(PERIODIC)
    }
}
