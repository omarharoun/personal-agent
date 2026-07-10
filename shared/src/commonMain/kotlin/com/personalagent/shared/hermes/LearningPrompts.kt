package com.personalagent.shared.hermes

import com.personalagent.shared.learning.LearningGoal

/**
 * Phase 6 — Learning Guide prompt/interaction design on top of Hermes (mirrors
 * [LifePrompts]; no second AI). These messages make the user's own agent:
 *  1. remember a learning goal (Step 1),
 *  2. use its web_search/web_extract tools to recommend the next right FREE
 *     open-web resource, filtered against its memory of the person (Step 2), and
 *  3. record what happened and adapt (Step 3).
 *
 * Kept shared, pure and centralized so the wording is reviewable + unit-tested
 * and reused by Android + iOS.
 *
 * 🔒 REVIEW REQUIRED — the Step 2 prompt pulls in fetched web content. That
 * content is untrusted: the prompt tells the agent to treat page text as data,
 * never as instructions, and the app renders the resulting titles/URLs/summaries
 * as inert text (see [com.personalagent.shared.learning.LearningModels]).
 */
object LearningPrompts {

    /** The Hermes memory-tool toolset name and the web toolset the loop needs. */
    const val WEB_TOOLSET = "web"
    const val WEB_SEARCH_TOOL = "web_search"

    /**
     * Persist a learning goal into the agent's memory as the CURRENT focus. Kept
     * to a single compact line because Hermes' built-in memory is global and
     * char-limited (Step 0 finding) — the full history stays in the local store.
     */
    fun saveLearningGoal(goal: LearningGoal): String {
        val bits = buildString {
            append("Learning goal: \"").append(goal.topic.trim()).append("\"")
            goal.why?.trim()?.takeIf { it.isNotEmpty() }?.let { append("; why: ").append(it) }
            goal.level?.trim()?.takeIf { it.isNotEmpty() }?.let { append("; level: ").append(it) }
            goal.style?.trim()?.takeIf { it.isNotEmpty() }?.let { append("; prefers: ").append(it) }
        }
        return "Please remember this as my current learning focus so you can guide me over time. " +
            "$bits. Keep it concise in memory. Reply with a short, warm one-line acknowledgement — " +
            "do not search the web yet."
    }

    /** Ask the agent to list the learning goals it remembers (memory-grounded). */
    fun listLearningGoals(): String =
        "Based only on what you actually remember about me, list the things I'm currently trying to " +
            "learn as a short bulleted list (just the topics). If you don't have any learning goals " +
            "stored for me yet, say so plainly and briefly — don't invent any."
}
