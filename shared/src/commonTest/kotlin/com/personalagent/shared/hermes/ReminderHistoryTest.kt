package com.personalagent.shared.hermes

import com.personalagent.shared.store.InMemoryKeyValueStorage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ReminderHistoryTest {

    private fun iso(millis: Long) =
        kotlinx.datetime.Instant.fromEpochMilliseconds(millis).toString()

    private val now = 1_000_000_000_000L
    private val min = 60_000L

    @Test
    fun live_upcoming_and_due_and_done_statuses() {
        val live = listOf(
            HermesJob(id = "up", name = "Future", nextRunAt = iso(now + 60 * min), state = "scheduled"),
            HermesJob(id = "due", name = "Now", nextRunAt = iso(now - min), state = "scheduled"),
            HermesJob(id = "done", name = "Fired", nextRunAt = iso(now - 5 * min), lastRunAt = iso(now - 5 * min), state = "scheduled"),
        )
        val views = ReminderHistory.merge(live, emptyList(), now)
        val byId = views.associateBy { it.id }
        assertEquals(ReminderStatus.UPCOMING, byId["up"]!!.status)
        assertEquals(ReminderStatus.DUE_NOW, byId["due"]!!.status)
        assertEquals(ReminderStatus.DONE, byId["done"]!!.status)
    }

    @Test
    fun fired_job_gone_from_hermes_survives_via_history() {
        // History has a reminder whose target has passed; Hermes no longer lists it.
        val history = listOf(ReminderRecord("gone", "Call sister", now - 10 * min))
        val views = ReminderHistory.merge(emptyList(), history, now)
        assertEquals(1, views.size)
        assertEquals("Call sister", views[0].text)
        assertEquals(ReminderStatus.DONE, views[0].status)
        assertTrue(!views[0].live)
    }

    @Test
    fun upcoming_sorts_before_done() {
        val history = listOf(ReminderRecord("old", "Past", now - min))
        val live = listOf(HermesJob(id = "soon", name = "Soon", nextRunAt = iso(now + min), state = "scheduled"))
        val views = ReminderHistory.merge(live, history, now)
        assertEquals("soon", views.first().id)   // upcoming first
        assertEquals("old", views.last().id)     // done last
    }

    @Test
    fun store_upsert_dedups_by_id_and_remove_works() {
        val store = ReminderHistoryStore(InMemoryKeyValueStorage())
        store.upsert(ReminderRecord("a", "one", now))
        store.upsert(ReminderRecord("a", "one-updated", now + min))
        assertEquals(1, store.all().size)
        assertEquals("one-updated", store.all()[0].text)
        store.remove("a")
        assertTrue(store.all().isEmpty())
    }
}
