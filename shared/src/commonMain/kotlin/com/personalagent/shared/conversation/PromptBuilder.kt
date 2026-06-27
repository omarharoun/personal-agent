package com.personalagent.shared.conversation

import com.personalagent.shared.model.MemoryEntry

/**
 * Assembles the grounded prompt for one everyday turn: persona + retrieved
 * memory context + the user's turn. Pure and deterministic — given the same
 * inputs it produces the same string — so prompt assembly is unit-testable on
 * its own, with no model and no network.
 *
 * The layout is intentionally simple and section-delimited so a small on-device
 * model can follow it, and so tests can assert that both the context and the
 * user turn are present.
 */
class PromptBuilder(
    private val persona: String = DEFAULT_PERSONA,
) {

    /**
     * Build the full prompt.
     *
     * @param userText the current user turn.
     * @param context relevant memories retrieved for this turn, best-first. May be
     *   empty, in which case an explicit "(no relevant memory)" marker is used so
     *   the model isn't left guessing whether context was omitted or absent.
     */
    fun build(userText: String, context: List<MemoryEntry>): String = buildString {
        append(SECTION_SYSTEM).append('\n')
        append(persona.trim()).append("\n\n")

        append(SECTION_CONTEXT).append('\n')
        if (context.isEmpty()) {
            append(NO_CONTEXT)
        } else {
            for (entry in context) {
                append("- ").append(entry.content.trim()).append('\n')
            }
            // drop the trailing newline left by the loop
            deleteAt(length - 1)
        }
        append("\n\n")

        append(SECTION_USER).append('\n')
        append(userText.trim()).append("\n\n")

        // The model continues from here.
        append(SECTION_ASSISTANT)
    }

    companion object {
        /**
         * System persona for the private personal assistant. Scope is deliberately
         * narrow: notes, reminders, and light planning. "Stay in scope" is explicit
         * so the local model declines unrelated requests rather than wandering.
         */
        const val DEFAULT_PERSONA: String =
            "You are a private, on-device personal assistant. You help the user with " +
                "their notes, reminders, and light day-to-day planning. You are concise, " +
                "practical, and friendly. Use the relevant memory below when it helps, and " +
                "say so when you don't have enough information. Stay in scope: politely " +
                "decline requests outside of notes, reminders, and planning."

        const val SECTION_SYSTEM = "[System]"
        const val SECTION_CONTEXT = "[Relevant memory]"
        const val SECTION_USER = "[User]"
        const val SECTION_ASSISTANT = "[Assistant]"
        const val NO_CONTEXT = "(no relevant memory)"
    }
}
