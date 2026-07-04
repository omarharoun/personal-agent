package com.personalagent.shared.hermes

/**
 * The life-improvement + reflection layer is **prompt/interaction design on top
 * of Hermes**, not a second AI (per the spec's global rule). These are the
 * carefully-framed messages the app sends to the user's agent so it pulls on its
 * *accumulated memory of this user* to produce grounded, personal responses —
 * never generic advice.
 *
 * Kept here (shared, pure) so the wording is centralized, reviewable, and unit-
 * tested, and reused by Android + iOS.
 */
object LifePrompts {

    /** Categories the user can frame a goal under (what "better" means to them). */
    val GOAL_CATEGORIES = listOf("Health", "Relationships", "Learning", "Habits", "Work", "Other")

    /** Save a goal into the agent's memory. */
    fun saveGoal(category: String, goal: String): String =
        "I want to set a personal goal. Category: $category. Goal: \"$goal\". " +
            "Please remember this as one of my active goals so you can support me with it over time. " +
            "Reply with a short, warm acknowledgement."

    /** Ask the agent to list the goals it remembers for this user. */
    fun listGoals(): String =
        "Based only on what you actually remember about me, list my current personal goals as a short " +
            "bulleted list. If you don't have any goals stored for me yet, say so plainly and briefly — " +
            "don't invent any."

    /**
     * The core "personalized nudge": explicitly grounds the agent in its real
     * memory of the user and forbids generic filler. This is what makes the nudge
     * reference the user's *actual* history.
     */
    fun personalizedNudge(): String =
        "Give me one short, encouraging nudge toward my goals — grounded ONLY in what you actually " +
            "remember about me and my history. Reference something specific you know about me or a " +
            "pattern you've noticed. If you genuinely don't have enough memory about me yet to be " +
            "specific, say that honestly and invite me to tell you more, rather than giving generic " +
            "advice. Keep it warm, brief, and non-preachy."

    /**
     * A gentle periodic reflection (Phase 4). [cadence] is a human word like
     * "weekly" or "monthly". Personalized via memory, explicitly easy to decline,
     * never nagging.
     */
    fun reflection(cadence: String): String =
        "It's time for a gentle $cadence reflection. Drawing ONLY on what you actually remember about " +
            "me — recent things I've mentioned, how I've seemed, my goals — offer one warm, low-pressure " +
            "reflection or question to help me check in with myself. It should feel like a friend " +
            "checking in, not a task. One short paragraph. If you don't remember enough about me yet, " +
            "just say a brief, kind hello and invite me to share what's on my mind."
}
