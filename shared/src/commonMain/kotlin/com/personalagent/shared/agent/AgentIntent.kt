package com.personalagent.shared.agent

/**
 * The capability the user's free-text turn maps to in the single conversational
 * surface (UX Stream 1). The UI no longer has Notes/Reminders/Plan tabs; instead
 * an [IntentRouter] inspects each typed message and decides whether it should
 * quietly create a note / reminder / plan item, or be answered by the AI.
 *
 * This is intentionally a small, closed set — every branch maps to an action the
 * existing view-models already expose (addNote / scheduleReminder / addPlanItem /
 * conversationService.respond), so the surface stays a thin router over logic that
 * is already tested.
 */
sealed interface AgentIntent {

    /** "note: buy milk", "remember that the gate code is 1234", "take a note ...". */
    data class CreateNote(val title: String, val body: String) : AgentIntent

    /**
     * "remind me to call mom in 10 minutes". [whenMillisHint] is a best-effort
     * absolute trigger time (epoch millis) derived from a relative phrase like
     * "in N minutes/hours"; it is `null` when no time could be parsed, in which
     * case the UI should ask for / default a time rather than guess.
     */
    data class CreateReminder(val text: String, val whenMillisHint: Long?) : AgentIntent

    /** "add to my plan finish the report", "plan to ship v2", "todo water plants". */
    data class AddPlanItem(val title: String) : AgentIntent

    /** Anything else — hand the raw text to the conversational AI. */
    data class Ask(val text: String) : AgentIntent
}

/**
 * Heuristic, model-free intent parser for the single conversational surface.
 *
 * This is **best-effort by design**: it recognises a small set of conservative,
 * explicit lead-ins ("note:", "remind me", "add to my plan", "todo", …). Anything
 * that does not clearly match falls through to [AgentIntent.Ask], so the AI (or the
 * friendly "install a model" fallback) handles it. No false-positive capture: if in
 * doubt, we Ask. When an on-device model is available the app may later replace this
 * with model-driven routing, but the heuristics keep the surface useful with no model.
 *
 * All matching is case-insensitive and tolerant of leading whitespace. Times are
 * parsed only for simple relative phrases ("in N minute(s)/hour(s)"); everything
 * else yields a `null` time hint (documented on [AgentIntent.CreateReminder]).
 */
object IntentRouter {

    private const val MINUTE_MILLIS = 60_000L
    private const val HOUR_MILLIS = 60 * MINUTE_MILLIS

    // "in 10 minutes", "in 1 hour", "in 2 hrs", "in 5 min" — captures count + unit.
    private val RELATIVE_TIME = Regex(
        """\bin\s+(\d+)\s*(minute|minutes|min|mins|hour|hours|hr|hrs|h|m)\b""",
        RegexOption.IGNORE_CASE,
    )

    /**
     * Parse [input] into an [AgentIntent]. [nowMillis] is the current wall-clock
     * time, used only to turn a relative "in N minutes/hours" phrase into an
     * absolute trigger time. Blank input routes to [AgentIntent.Ask] with the
     * original (empty) text — callers should short-circuit blank sends anyway.
     */
    fun parse(input: String, nowMillis: Long): AgentIntent {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) return AgentIntent.Ask(trimmed)
        val lower = trimmed.lowercase()

        // --- Reminder ---------------------------------------------------------
        // "remind me to X", "remind me X", "set a reminder to X".
        reminderBody(trimmed, lower)?.let { body ->
            val whenHint = parseRelativeTime(lower, nowMillis)
            // Strip the matched time phrase out of the reminder text so the saved
            // title reads "call mom" not "call mom in 10 minutes".
            val cleaned = RELATIVE_TIME.replace(body, "").trim().trimEnd(',').trim()
            val text = cleaned.ifBlank { body }
            return AgentIntent.CreateReminder(text = text, whenMillisHint = whenHint)
        }

        // --- Note -------------------------------------------------------------
        noteBody(trimmed, lower)?.let { body ->
            return splitNote(body)
        }

        // --- Plan item --------------------------------------------------------
        planBody(trimmed, lower)?.let { title ->
            return AgentIntent.AddPlanItem(title)
        }

        // --- Otherwise: ask the AI -------------------------------------------
        return AgentIntent.Ask(trimmed)
    }

    /** Returns the reminder text (everything after the lead-in) or null. */
    private fun reminderBody(original: String, lower: String): String? {
        val leadIns = listOf(
            "remind me to ",
            "remind me ",
            "set a reminder to ",
            "set a reminder ",
            "reminder to ",
            "reminder: ",
        )
        for (lead in leadIns) {
            if (lower.startsWith(lead)) return original.substring(lead.length).trim()
        }
        return null
    }

    /** Returns the note body (everything after the lead-in) or null. */
    private fun noteBody(original: String, lower: String): String? {
        val leadIns = listOf(
            "note: ",
            "note ",
            "take a note: ",
            "take a note ",
            "make a note: ",
            "make a note ",
            "remember that ",
            "remember to ",
            "remember: ",
            "remember ",
        )
        for (lead in leadIns) {
            if (lower.startsWith(lead)) return original.substring(lead.length).trim()
        }
        return null
    }

    /** Returns the plan-item title (everything after the lead-in) or null. */
    private fun planBody(original: String, lower: String): String? {
        val leadIns = listOf(
            "add to my plan: ",
            "add to my plan ",
            "add to plan: ",
            "add to plan ",
            "plan to ",
            "todo: ",
            "todo ",
            "to-do: ",
            "to-do ",
        )
        for (lead in leadIns) {
            if (lower.startsWith(lead)) return original.substring(lead.length).trim()
        }
        return null
    }

    /**
     * Split a free-text note into a short title + body. If the body contains a
     * sentence/line break we use the first chunk as the title and the rest as the
     * body; otherwise the whole thing is the title with an empty body (matching how
     * [AppViewModel-style] note creation treats a single line).
     */
    private fun splitNote(body: String): AgentIntent.CreateNote {
        val text = body.trim()
        // Prefer a line break, then a sentence end, as the title boundary.
        val breakIdx = text.indexOfFirst { it == '\n' }
        if (breakIdx in 0 until text.length - 1) {
            val title = text.substring(0, breakIdx).trim()
            val rest = text.substring(breakIdx + 1).trim()
            return AgentIntent.CreateNote(title = title.ifBlank { rest }, body = rest)
        }
        return AgentIntent.CreateNote(title = text, body = "")
    }

    /**
     * Best-effort relative-time parse. Recognises only "in N minute(s)/hour(s)"
     * (and common abbreviations). Returns an absolute epoch-millis trigger time, or
     * `null` if no such phrase is present — callers must treat null as "no time
     * specified" and never invent one.
     */
    fun parseRelativeTime(lower: String, nowMillis: Long): Long? {
        val match = RELATIVE_TIME.find(lower) ?: return null
        val count = match.groupValues[1].toLongOrNull() ?: return null
        if (count <= 0) return null
        val unit = match.groupValues[2].lowercase()
        val deltaMillis = when (unit) {
            "hour", "hours", "hr", "hrs", "h" -> count * HOUR_MILLIS
            // minute, minutes, min, mins, m
            else -> count * MINUTE_MILLIS
        }
        return nowMillis + deltaMillis
    }
}
