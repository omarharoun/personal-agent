package com.personalagent.shared.hermes

import kotlinx.datetime.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Wire models + helpers for Hermes' reminder/scheduling surface — `/api/jobs`
 * (verified live in Phase 0; the exact job shape is captured in docs/PHASE2.md).
 *
 * Reminders are Hermes cron jobs. The app is the *delivery* mechanism (the user's
 * decision): it POLLS `/api/jobs` and raises a LOCAL notification when a job is
 * due. Hermes is the source of truth — the app keeps no second copy of the
 * reminder text, only a dedup marker (job id + run time) so it notifies once.
 */

@Serializable
data class HermesJob(
    val id: String,
    val name: String? = null,
    val prompt: String? = null,
    @SerialName("schedule_display") val scheduleDisplay: String? = null,
    @SerialName("next_run_at") val nextRunAt: String? = null,
    @SerialName("last_run_at") val lastRunAt: String? = null,
    val state: String? = null,
    val enabled: Boolean = true,
    val deliver: String? = null,
) {
    /** The best human label for this reminder. */
    val label: String get() = name?.takeIf { it.isNotBlank() } ?: prompt?.takeIf { it.isNotBlank() } ?: "Reminder"

    /** [nextRunAt] parsed to epoch millis, or null if absent/unparseable. */
    val nextRunAtMillis: Long? get() = parseIsoMillis(nextRunAt)

    /** True when this job is active (not paused/disabled). */
    val isActive: Boolean get() = enabled && (state == null || state.equals("scheduled", ignoreCase = true))

    /** A stable key identifying THIS scheduled firing, for notify-once dedup. */
    val fireKey: String get() = "$id@${nextRunAt ?: scheduleDisplay ?: ""}"
}

@Serializable
data class HermesJobsList(val jobs: List<HermesJob> = emptyList())

@Serializable
data class HermesJobEnvelope(val job: HermesJob? = null)

@Serializable
data class HermesCreateJobRequest(
    val name: String,
    val schedule: String,
    val prompt: String,
    val deliver: String = "local",
)

/** Parse an ISO-8601 instant (with offset) to epoch millis; null on failure. */
internal fun parseIsoMillis(iso: String?): Long? {
    if (iso.isNullOrBlank()) return null
    return runCatching { Instant.parse(iso).toEpochMilliseconds() }.getOrNull()
}

/**
 * Build the `schedule` string for a one-shot reminder at [targetMillis], given
 * [nowMillis]. We use a duration ("<N>m") rather than an ISO timestamp so we
 * never have to reason about the server's timezone — the server schedules it at
 * `now + N minutes`, and the app's own poll compares the returned `next_run_at`
 * to the device clock. Minimum 1 minute (the server rejects past/zero one-shots).
 */
fun oneShotScheduleMinutes(nowMillis: Long, targetMillis: Long): String {
    val deltaMs = targetMillis - nowMillis
    val minutes = ((deltaMs + 59_999) / 60_000).coerceAtLeast(1) // ceil, min 1
    return "${minutes}m"
}
