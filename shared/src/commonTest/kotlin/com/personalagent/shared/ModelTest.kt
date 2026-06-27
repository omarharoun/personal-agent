package com.personalagent.shared

import com.personalagent.shared.model.MemoryEntry
import com.personalagent.shared.model.Note
import com.personalagent.shared.model.PlanItem
import com.personalagent.shared.model.Reminder
import com.personalagent.shared.model.ReminderStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ModelTest {

    @Test
    fun note_create_trims_title_and_sets_timestamps() {
        val n = Note.create(title = "  Groceries  ", body = "milk", nowMillis = 1_000L)
        assertEquals("Groceries", n.title)
        assertEquals("milk", n.body)
        assertEquals(1_000L, n.createdAt)
        assertEquals(1_000L, n.updatedAt)
        assertTrue(n.id.isNotBlank())
    }

    @Test
    fun note_edit_updates_only_updatedAt() {
        val n = Note.create("a", "b", nowMillis = 1_000L)
        val e = n.edited("a2", "b2", nowMillis = 2_000L)
        assertEquals(n.id, e.id)
        assertEquals(n.createdAt, e.createdAt)
        assertEquals(2_000L, e.updatedAt)
        assertEquals("a2", e.title)
    }

    @Test
    fun ids_are_unique() {
        val a = Note.create("a", "", 1L)
        val b = Note.create("b", "", 1L)
        assertNotEquals(a.id, b.id)
    }

    @Test
    fun reminder_isDue_respects_status_and_time() {
        val r = Reminder.create("ping", triggerAtMillis = 100L, nowMillis = 0L)
        assertFalse(r.isDue(99L))
        assertTrue(r.isDue(100L))
        assertTrue(r.isDue(101L))
        assertFalse(r.copy(status = ReminderStatus.CANCELLED).isDue(101L))
        assertFalse(r.copy(status = ReminderStatus.FIRED).isDue(101L))
    }

    @Test
    fun planItem_toggle_flips_done() {
        val p = PlanItem.create("write report", nowMillis = 1L)
        assertFalse(p.done)
        assertTrue(p.toggled().done)
        assertFalse(p.toggled().toggled().done)
    }

    @Test
    fun memoryEntry_embedding_is_null_by_default() {
        val m = MemoryEntry.create("user likes metric units", nowMillis = 1L)
        assertNull(m.embedding) // DEFERRED: populated by on-device model in a later step
        assertEquals("user likes metric units", m.content)
    }
}
