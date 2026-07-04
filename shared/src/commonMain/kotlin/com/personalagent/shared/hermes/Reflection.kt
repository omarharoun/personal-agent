package com.personalagent.shared.hermes

import com.personalagent.shared.store.KeyValueStorage

/**
 * Phase 4 — optional periodic reflection. A gentle, personalized check-in
 * (weekly/monthly) that is **always easy to snooze or turn off** and **never
 * nags**. The reflection *content* comes from Hermes (grounded in memory, via
 * [LifePrompts.reflection]); this file owns only the cadence preference + the
 * pure due-logic. The preference is a schedule setting (not user content).
 */
enum class ReflectionCadence(val label: String, val intervalMillis: Long) {
    OFF("Off", 0L),
    WEEKLY("Weekly", 7L * 24 * 60 * 60 * 1000),
    MONTHLY("Monthly", 30L * 24 * 60 * 60 * 1000);

    /** The human word used in the reflection prompt ("weekly"/"monthly"). */
    val promptWord: String get() = if (this == MONTHLY) "monthly" else "weekly"
}

/** Immutable reflection settings + bookkeeping. */
data class ReflectionState(
    val cadence: ReflectionCadence = ReflectionCadence.OFF,
    /** When the last reflection was shown (or when the cadence was enabled). */
    val anchorMillis: Long = 0L,
    /** If the user snoozed, don't surface again until this time. */
    val snoozedUntilMillis: Long = 0L,
) {
    /** The next time a reflection is due, or null if reflections are off. */
    fun nextDueAt(): Long? {
        if (cadence == ReflectionCadence.OFF) return null
        val base = anchorMillis + cadence.intervalMillis
        return maxOf(base, snoozedUntilMillis)
    }

    /** Whether a reflection is due at [now]. Never due when off. */
    fun isDue(now: Long): Boolean {
        val due = nextDueAt() ?: return false
        return now >= due
    }
}

/**
 * Persists [ReflectionState] via the (encrypted) [KeyValueStorage]. Only a
 * cadence + two timestamps — no reflection content is ever stored here.
 */
class ReflectionStore(private val storage: KeyValueStorage) {

    fun load(): ReflectionState {
        val cadence = storage.get(KEY_CADENCE)
            ?.let { runCatching { ReflectionCadence.valueOf(it) }.getOrNull() }
            ?: ReflectionCadence.OFF
        val anchor = storage.get(KEY_ANCHOR)?.toLongOrNull() ?: 0L
        val snooze = storage.get(KEY_SNOOZE)?.toLongOrNull() ?: 0L
        return ReflectionState(cadence, anchor, snooze)
    }

    /** Set the cadence; anchors "now" so the first reflection is one interval out
     *  (no immediate nag on enabling). Turning OFF clears the schedule. */
    fun setCadence(cadence: ReflectionCadence, now: Long) {
        storage.put(KEY_CADENCE, cadence.name)
        storage.put(KEY_ANCHOR, now.toString())
        storage.put(KEY_SNOOZE, "0")
    }

    /** Record that a reflection was just shown (re-anchors the interval). */
    fun markShown(now: Long) {
        storage.put(KEY_ANCHOR, now.toString())
        storage.put(KEY_SNOOZE, "0")
    }

    /** Snooze the next reflection until [untilMillis]. */
    fun snoozeUntil(untilMillis: Long) {
        storage.put(KEY_SNOOZE, untilMillis.toString())
    }

    private companion object {
        const val KEY_CADENCE = "reflection.cadence"
        const val KEY_ANCHOR = "reflection.anchor"
        const val KEY_SNOOZE = "reflection.snooze_until"
    }
}
