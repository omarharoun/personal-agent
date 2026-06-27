package com.personalagent.shared.util

/**
 * Trivial clock abstraction so all time-dependent logic (reminders, ids,
 * timestamps) is deterministic under test. The real app injects [SystemClock];
 * tests inject a fake that returns a fixed/controlled instant.
 */
fun interface Clock {
    /** Current time in epoch milliseconds (UTC). */
    fun nowMillis(): Long
}
