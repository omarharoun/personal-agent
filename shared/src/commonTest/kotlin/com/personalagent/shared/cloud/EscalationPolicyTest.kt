package com.personalagent.shared.cloud

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class EscalationPolicyTest {

    private val policy = HeuristicEscalationPolicy()

    // --- escalate: explicit user asks --------------------------------------

    @Test
    fun explicit_think_hard_escalates() {
        assertTrue(policy.shouldEscalate("Think hard about my career options", emptyList()))
    }

    @Test
    fun explicit_deep_research_escalates() {
        assertTrue(policy.shouldEscalate("Please do deep research on EV tax credits", emptyList()))
    }

    @Test
    fun explicit_use_the_cloud_escalates() {
        assertTrue(policy.shouldEscalate("Use the cloud for this one", emptyList()))
    }

    // --- escalate: low local confidence ------------------------------------

    @Test
    fun low_confidence_context_marker_escalates() {
        assertTrue(
            policy.shouldEscalate(
                "what did I decide?",
                listOf("note: maybe relevant [low-confidence] retrieval"),
            ),
        )
    }

    // --- escalate: complex multi-constraint planning -----------------------

    @Test
    fun long_multi_constraint_planning_escalates() {
        val hard = buildString {
            append("Plan a detailed five-day travel itinerary for my upcoming trip that fits a tight budget, ")
            append("and works around all of my afternoon meetings each day, but also leaves enough time for the gym, ")
            append("then books a dinner restaurant near each of the places we stop at without any seafood on the menu, ")
            append("before the busy holiday weekend when hotel and flight prices spike sharply across every single city ")
            append("we plan to visit along the way during this particular stretch of the long summer season this year.")
        }
        assertTrue(hard.split(Regex("\\s+")).size >= 60, "test fixture must be long enough")
        assertTrue(policy.shouldEscalate(hard, emptyList()))
    }

    // --- stay LOCAL (default behaviour) ------------------------------------

    @Test
    fun ordinary_short_turn_stays_local() {
        assertFalse(policy.shouldEscalate("what time is my dentist appointment?", emptyList()))
    }

    @Test
    fun greeting_stays_local() {
        assertFalse(policy.shouldEscalate("hey, good morning!", emptyList()))
    }

    @Test
    fun long_but_simple_turn_stays_local() {
        // Wordy but not many constraints — must NOT escalate (the double gate).
        val wordyButSimple = ("please remind me to call my mom sometime this evening when i get a quiet chance " +
            "because i keep forgetting to do it for her she has been waiting to hear back from me for quite a long while now " +
            "so it would really help me out quite a lot here if you could simply give me a gentle little nudge later on about it " +
            "at some reasonable point during the rest of my day today whenever that happens to work out fine for you okay")
        assertTrue(wordyButSimple.split(Regex("\\s+")).size >= 60)
        assertFalse(policy.shouldEscalate(wordyButSimple, emptyList()))
    }

    @Test
    fun normal_context_does_not_escalate() {
        assertFalse(
            policy.shouldEscalate("remind me about the dentist", listOf("the user's dentist is Dr. Lee")),
        )
    }

    // --- configurability ----------------------------------------------------

    @Test
    fun disabled_config_never_escalates() {
        val off = HeuristicEscalationPolicy(HeuristicConfig(enabled = false))
        assertFalse(off.shouldEscalate("think hard and do deep research", emptyList()))
    }

    @Test
    fun custom_explicit_phrase_is_honoured() {
        val custom = HeuristicEscalationPolicy(
            HeuristicConfig(explicitEscalationPhrases = listOf("ask the big model")),
        )
        assertTrue(custom.shouldEscalate("ASK THE BIG MODEL please", emptyList()))
        // The built-in phrase is no longer configured, so it stays local.
        assertFalse(custom.shouldEscalate("think hard", emptyList()))
    }

    // --- LocalOnlyEscalationPolicy -----------------------------------------

    @Test
    fun local_only_policy_never_escalates() {
        assertFalse(LocalOnlyEscalationPolicy.shouldEscalate("think hard, use the cloud!", emptyList()))
        assertFalse(LocalOnlyEscalationPolicy.shouldEscalate("anything", listOf("[low-confidence]")))
    }
}
