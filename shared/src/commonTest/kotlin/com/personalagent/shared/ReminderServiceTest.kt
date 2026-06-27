package com.personalagent.shared

import com.personalagent.shared.model.ReminderStatus
import com.personalagent.shared.reminder.NoopReminderScheduler
import com.personalagent.shared.reminder.ReminderService
import com.personalagent.shared.reminder.ScheduleResult
import com.personalagent.shared.store.InMemoryKeyValueStorage
import com.personalagent.shared.store.PersistentLocalStore
import com.personalagent.shared.util.Clock
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** Mutable fake clock so tests control "now" exactly. */
private class FakeClock(var now: Long) : Clock {
    override fun nowMillis(): Long = now
}

class ReminderServiceTest {

    private fun service(now: Long): Triple<ReminderService, NoopReminderScheduler, PersistentLocalStore> {
        val store = PersistentLocalStore(InMemoryKeyValueStorage())
        val scheduler = NoopReminderScheduler()
        val svc = ReminderService(store, scheduler, FakeClock(now))
        return Triple(svc, scheduler, store)
    }

    @Test
    fun schedule_future_reminder_persists_and_arms_alarm() = runTest {
        val (svc, scheduler, store) = service(now = 1_000L)
        val result = svc.schedule(title = "Take meds", triggerAtMillis = 5_000L)

        val scheduled = assertIs<ScheduleResult.Scheduled>(result)
        assertEquals("Take meds", scheduled.reminder.title)
        // persisted
        assertEquals(1, store.allReminders().size)
        // OS alarm armed exactly once with the same reminder
        assertEquals(1, scheduler.scheduled.size)
        assertEquals(scheduled.reminder.id, scheduler.scheduled.first().id)
    }

    @Test
    fun schedule_rejects_blank_title() = runTest {
        val (svc, scheduler, store) = service(now = 1_000L)
        val result = svc.schedule(title = "   ", triggerAtMillis = 5_000L)
        val rejected = assertIs<ScheduleResult.Rejected>(result)
        assertEquals(ScheduleResult.Rejected.Reason.BLANK_TITLE, rejected.reason)
        assertTrue(store.allReminders().isEmpty())
        assertTrue(scheduler.scheduled.isEmpty())
    }

    @Test
    fun schedule_rejects_past_trigger() = runTest {
        val (svc, scheduler, _) = service(now = 1_000L)
        val past = assertIs<ScheduleResult.Rejected>(svc.schedule("x", triggerAtMillis = 1_000L))
        assertEquals(ScheduleResult.Rejected.Reason.TRIGGER_IN_PAST, past.reason)
        assertTrue(scheduler.scheduled.isEmpty())
    }

    @Test
    fun cancel_marks_cancelled_and_drops_alarm() = runTest {
        val (svc, scheduler, store) = service(now = 1_000L)
        val r = assertIs<ScheduleResult.Scheduled>(svc.schedule("x", 5_000L)).reminder
        svc.cancel(r.id)
        assertEquals(ReminderStatus.CANCELLED, store.getReminder(r.id)?.status)
        assertEquals(listOf(r.id), scheduler.cancelled)
    }

    @Test
    fun markFired_transitions_scheduled_to_fired() = runTest {
        val (svc, _, store) = service(now = 1_000L)
        val r = assertIs<ScheduleResult.Scheduled>(svc.schedule("x", 5_000L)).reminder
        svc.markFired(r.id)
        assertEquals(ReminderStatus.FIRED, store.getReminder(r.id)?.status)
    }

    @Test
    fun dueReminders_returns_past_scheduled_sorted() = runTest {
        val clock = FakeClock(1_000L)
        val store = PersistentLocalStore(InMemoryKeyValueStorage())
        val svc = ReminderService(store, NoopReminderScheduler(), clock)
        svc.schedule("later", 9_000L)
        svc.schedule("soonest", 2_000L)
        svc.schedule("middle", 3_000L)

        // advance time so the first two are due, the 9000 one is not
        clock.now = 5_000L
        val due = svc.dueReminders()
        assertEquals(listOf("soonest", "middle"), due.map { it.title })
    }

    @Test
    fun rescheduleAll_rearms_only_future_scheduled() = runTest {
        val clock = FakeClock(1_000L)
        val store = PersistentLocalStore(InMemoryKeyValueStorage())
        val scheduler = NoopReminderScheduler()
        val svc = ReminderService(store, scheduler, clock)
        val future = assertIs<ScheduleResult.Scheduled>(svc.schedule("future", 8_000L)).reminder
        val alsoFuture = assertIs<ScheduleResult.Scheduled>(svc.schedule("future2", 9_000L)).reminder
        svc.cancel(alsoFuture.id)

        scheduler.scheduled.clear() // simulate reboot: OS alarms are gone
        clock.now = 2_000L
        svc.rescheduleAll()

        // only the still-SCHEDULED future reminder is re-armed
        assertEquals(listOf(future.id), scheduler.scheduled.map { it.id })
    }
}
