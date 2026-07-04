package com.personalagent.shared.hermes

import com.personalagent.shared.store.InMemoryKeyValueStorage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ReflectionTest {

    private val week = ReflectionCadence.WEEKLY.intervalMillis

    @Test
    fun off_is_never_due() {
        val s = ReflectionState(ReflectionCadence.OFF, anchorMillis = 0L)
        assertFalse(s.isDue(Long.MAX_VALUE))
        assertNull(s.nextDueAt())
    }

    @Test
    fun weekly_due_one_interval_after_anchor() {
        val anchor = 1_000_000_000_000L
        val s = ReflectionState(ReflectionCadence.WEEKLY, anchorMillis = anchor)
        assertFalse(s.isDue(anchor + week - 1))       // not yet
        assertTrue(s.isDue(anchor + week))            // due
        assertEquals(anchor + week, s.nextDueAt())
    }

    @Test
    fun snooze_pushes_due_time_out() {
        val anchor = 1_000_000_000_000L
        val snooze = anchor + week + 3L * 24 * 60 * 60 * 1000
        val s = ReflectionState(ReflectionCadence.WEEKLY, anchorMillis = anchor, snoozedUntilMillis = snooze)
        assertFalse(s.isDue(anchor + week))           // suppressed by snooze
        assertTrue(s.isDue(snooze))
    }

    @Test
    fun store_roundtrips_and_enabling_avoids_immediate_nag() {
        val store = ReflectionStore(InMemoryKeyValueStorage())
        val now = 1_000_000_000_000L
        store.setCadence(ReflectionCadence.WEEKLY, now)
        val loaded = store.load()
        assertEquals(ReflectionCadence.WEEKLY, loaded.cadence)
        assertFalse(loaded.isDue(now))                // not due the moment you enable it
        assertTrue(loaded.isDue(now + week))

        store.markShown(now + week)
        assertFalse(store.load().isDue(now + week))    // re-anchored, not due again yet
    }
}
