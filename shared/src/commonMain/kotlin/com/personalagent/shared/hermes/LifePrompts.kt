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

    /**
     * Steering injected as a `system` turn ONLY when the user is asking to schedule
     * something. It fixes the failure the user hit: the agent tried to *push* a
     * scheduled result via `send()`, which the Hermes API server doesn't support,
     * so nothing was delivered. This client delivers on the user's own terms —
     * IN-APP, by polling the schedule — with no external messaging or push. So we
     * tell the agent exactly that, and forbid the channels the user doesn't want.
     */
    fun schedulingSteer(): String =
        "SCHEDULING CONTRACT for this client: this app delivers scheduled tasks and reminders " +
            "IN-APP by polling your schedule (/api/jobs) — it shows the user the result the next " +
            "time they're in the app. You CANNOT push messages yourself: do NOT call send(), and " +
            "do NOT use any external messaging, email, chat, webhook, or OS push notification. " +
            "When the user asks to schedule or automate something, create the job with LOCAL " +
            "delivery only, then confirm it in plain text (what, and when it will run). If a task " +
            "produces content (e.g. a daily digest), say that its result will appear here in the " +
            "app when it runs. Never claim you messaged or notified them through any outside channel."

    /**
     * True when [text] reads like a request to schedule / automate / recur
     * something — the only case where [schedulingSteer] is injected. Deliberately
     * broad on scheduling verbs, but requires an automation/recurrence cue so plain
     * one-off "remind me" chatter (already handled locally) isn't over-steered.
     */
    fun looksLikeScheduling(text: String): Boolean {
        val t = text.lowercase()
        val verb = listOf("schedule", "automate", "set up a task", "recurring", "cron", "run this", "run it")
            .any { t.contains(it) }
        val recurrence = listOf(
            "every day", "each day", "every morning", "each morning", "every week", "each week",
            "every night", "daily", "weekly", "monthly", "each night", "every hour", "hourly",
            "every month", "at 0", "at 1", "at 2", "at 3", "at 4", "at 5", "at 6", "at 7",
            "at 8", "at 9", "a.m.", "p.m.", " am", " pm",
        ).any { t.contains(it) }
        return verb || (t.contains("remind") && recurrence)
    }

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
