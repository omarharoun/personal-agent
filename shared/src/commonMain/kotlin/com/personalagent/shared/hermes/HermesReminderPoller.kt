package com.personalagent.shared.hermes

/**
 * Orchestrates one reminder poll: ask Hermes for the current jobs, work out
 * which are newly due, hand them to [notify], and record them so they don't fire
 * again. Platform-free (the [notify] callback does the actual OS notification),
 * so the Android worker and a future iOS task share this logic.
 *
 * The user's chosen delivery model: the app is the notifier. Hermes owns the
 * schedule; we poll `/api/jobs` and raise a LOCAL notification when one is due.
 */
class HermesReminderPoller(
    private val client: HermesClient,
    private val notified: NotifiedReminderStore,
    private val now: () -> Long,
) {
    /**
     * Run one poll. Returns the reminders surfaced this pass (for logging/tests).
     * Never throws for the "Hermes unreachable" case is the caller's concern — a
     * background poll should catch and retry later.
     */
    suspend fun pollOnce(notify: (DueReminder) -> Unit): List<DueReminder> {
        val jobs = client.listJobs()
        val due = ReminderPolling.dueNow(jobs, now(), notified.all())
        due.forEach(notify)
        notified.markNotified(due.map { it.fireKey })
        // Forget markers for reminders Hermes no longer has, so the store stays small.
        notified.retainOnly(jobs.map { it.fireKey }.toSet())
        return due
    }
}
