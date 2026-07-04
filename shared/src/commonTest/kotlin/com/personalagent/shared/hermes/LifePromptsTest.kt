package com.personalagent.shared.hermes

import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Locks the intent of the life-improvement prompts: every one must ground the
 * agent in its REAL memory of the user and forbid generic/invented output — that
 * is what keeps nudges personal rather than fortune-cookie advice.
 */
class LifePromptsTest {

    @Test
    fun nudge_demands_memory_grounding_and_forbids_generic() {
        val p = LifePrompts.personalizedNudge().lowercase()
        assertTrue(p.contains("remember"), "must reference real memory")
        assertTrue(p.contains("generic"), "must explicitly forbid generic advice")
    }

    @Test
    fun list_goals_forbids_invention() {
        val p = LifePrompts.listGoals().lowercase()
        assertTrue(p.contains("only") && (p.contains("don't invent") || p.contains("dont invent") || p.contains("invent any")))
    }

    @Test
    fun save_goal_embeds_category_and_text() {
        val p = LifePrompts.saveGoal("Health", "walk daily")
        assertTrue(p.contains("Health"))
        assertTrue(p.contains("walk daily"))
        assertTrue(p.lowercase().contains("remember"))
    }

    @Test
    fun reflection_is_low_pressure_and_memory_grounded() {
        val p = LifePrompts.reflection("weekly").lowercase()
        assertTrue(p.contains("weekly"))
        assertTrue(p.contains("remember"))
        assertTrue(p.contains("friend") || p.contains("low-pressure") || p.contains("not a task"))
    }
}
