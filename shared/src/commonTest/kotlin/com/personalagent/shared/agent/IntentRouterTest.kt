package com.personalagent.shared.agent

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

/**
 * Covers each [IntentRouter] branch + the best-effort relative-time parse. The
 * router is model-free and conservative: only explicit lead-ins capture an action,
 * everything else falls through to [AgentIntent.Ask].
 *
 * Uses [assertIs] (a contract assertion) so the matched intent is smart-cast and
 * its fields can be asserted directly.
 */
class IntentRouterTest {

    // Fixed "now" so relative-time assertions are deterministic.
    private val now = 1_000_000_000_000L
    private val minute = 60_000L
    private val hour = 60 * minute

    // --- Reminder ------------------------------------------------------------

    @Test
    fun remindMe_to_withRelativeMinutes() {
        val intent = assertIs<AgentIntent.CreateReminder>(
            IntentRouter.parse("Remind me to call mom in 10 minutes", now),
        )
        // The matched time phrase is stripped from the saved text.
        assertEquals("call mom", intent.text)
        assertEquals(now + 10 * minute, intent.whenMillisHint)
    }

    @Test
    fun remindMe_withRelativeHours() {
        val intent = assertIs<AgentIntent.CreateReminder>(
            IntentRouter.parse("remind me to take medicine in 2 hours", now),
        )
        assertEquals("take medicine", intent.text)
        assertEquals(now + 2 * hour, intent.whenMillisHint)
    }

    @Test
    fun remindMe_withoutTime_hasNullHint() {
        val intent = assertIs<AgentIntent.CreateReminder>(
            IntentRouter.parse("remind me to water the plants", now),
        )
        assertEquals("water the plants", intent.text)
        assertNull(intent.whenMillisHint)
    }

    @Test
    fun setAReminder_leadIn() {
        val intent = assertIs<AgentIntent.CreateReminder>(
            IntentRouter.parse("Set a reminder to pay rent in 5 min", now),
        )
        assertEquals("pay rent", intent.text)
        assertEquals(now + 5 * minute, intent.whenMillisHint)
    }

    // --- Note ----------------------------------------------------------------

    @Test
    fun note_colon_prefix() {
        val intent = assertIs<AgentIntent.CreateNote>(
            IntentRouter.parse("note: gate code is 1234", now),
        )
        assertEquals("gate code is 1234", intent.title)
        assertEquals("", intent.body)
    }

    @Test
    fun takeANote_prefix() {
        val intent = assertIs<AgentIntent.CreateNote>(
            IntentRouter.parse("Take a note buy milk and eggs", now),
        )
        assertEquals("buy milk and eggs", intent.title)
    }

    @Test
    fun rememberThat_prefix() {
        val intent = assertIs<AgentIntent.CreateNote>(
            IntentRouter.parse("Remember that the wifi password is hunter2", now),
        )
        assertEquals("the wifi password is hunter2", intent.title)
    }

    @Test
    fun note_multiline_splitsTitleAndBody() {
        val intent = assertIs<AgentIntent.CreateNote>(
            IntentRouter.parse("note: Groceries\nmilk, eggs, bread", now),
        )
        assertEquals("Groceries", intent.title)
        assertEquals("milk, eggs, bread", intent.body)
    }

    // --- Plan ----------------------------------------------------------------

    @Test
    fun addToMyPlan_prefix() {
        val intent = assertIs<AgentIntent.AddPlanItem>(
            IntentRouter.parse("Add to my plan finish the quarterly report", now),
        )
        assertEquals("finish the quarterly report", intent.title)
    }

    @Test
    fun planTo_prefix() {
        val intent = assertIs<AgentIntent.AddPlanItem>(
            IntentRouter.parse("plan to ship v2", now),
        )
        assertEquals("ship v2", intent.title)
    }

    @Test
    fun todo_prefix() {
        val intent = assertIs<AgentIntent.AddPlanItem>(
            IntentRouter.parse("todo: water plants", now),
        )
        assertEquals("water plants", intent.title)
    }

    // --- Ask (fall-through) --------------------------------------------------

    @Test
    fun plainQuestion_fallsThroughToAsk() {
        val intent = assertIs<AgentIntent.Ask>(
            IntentRouter.parse("What's the weather like today?", now),
        )
        assertEquals("What's the weather like today?", intent.text)
    }

    @Test
    fun conversational_notMistakenForNote() {
        // "remembering" should NOT trigger the "remember " note lead-in (the word
        // boundary differs), so it stays a normal Ask.
        assertIs<AgentIntent.Ask>(
            IntentRouter.parse("I keep remembering old memories", now),
        )
    }

    @Test
    fun blankInput_isAsk() {
        val intent = assertIs<AgentIntent.Ask>(IntentRouter.parse("   ", now))
        assertEquals("", intent.text)
    }

    // --- Relative-time helper directly ---------------------------------------

    @Test
    fun parseRelativeTime_variants() {
        assertEquals(now + 15 * minute, IntentRouter.parseRelativeTime("in 15 minutes", now))
        assertEquals(now + 1 * minute, IntentRouter.parseRelativeTime("in 1 min", now))
        assertEquals(now + 3 * hour, IntentRouter.parseRelativeTime("in 3 hrs", now))
        assertEquals(now + 1 * hour, IntentRouter.parseRelativeTime("in 1 h", now))
        assertNull(IntentRouter.parseRelativeTime("tomorrow afternoon", now))
        assertNull(IntentRouter.parseRelativeTime("in 0 minutes", now))
    }
}
