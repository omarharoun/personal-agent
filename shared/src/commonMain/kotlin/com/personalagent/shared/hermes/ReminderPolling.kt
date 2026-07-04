package com.personalagent.shared.hermes

/** A reminder that has come due and should raise a local notification now. */
data class DueReminder(
    val jobId: String,
    val fireKey: String,
    val title: String,
    val body: String,
)

/**
 * Pure decision logic for the polling reminder deliverer: given the jobs Hermes
 * reports, the current time, and which firings we've already notified, decide
 * which reminders to surface *now*.
 *
 * Kept platform-free so it's unit-tested once and reused by the Android worker
 * (and later iOS). No content is stored — the caller persists only the returned
 * [DueReminder.fireKey]s to avoid re-notifying the same firing.
 */
object ReminderPolling {

    /** Ignore firings older than this — avoids a notification storm the first
     *  time the app polls a Hermes that already has long-past jobs. */
    const val DEFAULT_STALE_AFTER_MS = 24L * 60 * 60 * 1000

    fun dueNow(
        jobs: List<HermesJob>,
        nowMillis: Long,
        alreadyNotified: Set<String>,
        staleAfterMillis: Long = DEFAULT_STALE_AFTER_MS,
    ): List<DueReminder> {
        val floor = nowMillis - staleAfterMillis
        return jobs.mapNotNull { job ->
            if (!job.isActive) return@mapNotNull null
            val runAt = job.nextRunAtMillis ?: return@mapNotNull null
            if (runAt > nowMillis) return@mapNotNull null      // not due yet
            if (runAt < floor) return@mapNotNull null          // too old to surface
            if (job.fireKey in alreadyNotified) return@mapNotNull null
            DueReminder(
                jobId = job.id,
                fireKey = job.fireKey,
                title = "Reminder",
                body = job.label,
            )
        }
    }
}
