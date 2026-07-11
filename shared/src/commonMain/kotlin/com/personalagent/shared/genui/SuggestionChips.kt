package com.personalagent.shared.genui

/**
 * The FIXED, curated suggestion chips the app ships (shared copy, so Android and
 * iOS use identical wording — like `LearningStatusText.TAP_OPTIONS`). Chips are
 * shortcuts to the same prompts a user could type; they are NOT model-generated,
 * so they can't be spoofed and are testable/localizable. Each maps to a canonical
 * prompt and a *preferred* view (a hint the agent may override).
 */
data class SuggestionChip(
    val label: String,
    val prompt: String,
    val preferredView: String,
)

object SuggestionChips {

    val ALL: List<SuggestionChip> = listOf(
        SuggestionChip("How's my week?", "How's my week going?", "week-pulse"),
        SuggestionChip("Plan my evening", "Plan my evening", "plan"),
        SuggestionChip("What should I learn next?", "What should I learn next?", "resource-rec"),
        SuggestionChip("Summarize my day", "Summarize my day", "day-recap"),
        SuggestionChip("How am I doing on my goals?", "How am I doing on my goals?", "stat-grid"),
    )

    /**
     * A light keyword heuristic: does this free-text turn look like a request for a
     * composed view, and if so which view is preferred? Returns null when the text
     * is ordinary chat (so it flows through the normal streaming path untouched).
     */
    fun preferredViewFor(text: String): String? {
        val t = text.lowercase()
        // An exact/near match to a chip's canonical prompt wins first.
        ALL.firstOrNull { t.contains(it.prompt.lowercase()) }?.let { return it.preferredView }
        return when {
            hasAny(t, "how's my week", "how is my week", "my week", "this week") -> "week-pulse"
            hasAny(t, "summarize my day", "my day", "recap", "how was today", "how's today") -> "day-recap"
            hasAny(t, "plan my", "plan the", "plan tonight", "my evening", "tonight", "agenda for") -> "plan"
            hasAny(t, "what should i learn", "learn next", "next resource", "study next", "keep learning") -> "resource-rec"
            hasAny(t, "my goals", "on my goals", "goal progress", "how am i doing") -> "stat-grid"
            else -> null
        }
    }

    private fun hasAny(t: String, vararg needles: String) = needles.any { t.contains(it) }
}
