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
    val persona: String = DEFAULT_PERSONA,
) {

    /**
     * Build a multi-turn **ChatML** prompt for the on-device (MediaPipe) models,
     * which are all ChatML (SmolLM / Qwen2.5). This is what gives the local model
     * short-term conversation memory:
     *
     * ```
     * <|im_start|>system
     * <persona>
     *
     * [Relevant memory]
     * - …                       (or "(no relevant memory)")
     * <|im_end|>
     * <|im_start|>user
     * <prior user turn><|im_end|>
     * <|im_start|>assistant
     * <prior assistant turn><|im_end|>
     * …                          (recent [history], oldest first)
     * <|im_start|>user
     * <current userText><|im_end|>
     * <|im_start|>assistant
     * ```
     *
     * The trailing open assistant turn is where the model continues; the on-device
     * runtime stops on `<|im_end|>` and the [OutputSanitizer] strips any leak.
     *
     * @param history recent prior turns of THIS chat, oldest first (already
     *   windowed by the caller). The current [userText] is appended after them.
     */
    fun buildChatMl(
        userText: String,
        context: List<MemoryEntry>,
        history: List<ConversationTurn> = emptyList(),
    ): String = buildString {
        append(IM_START).append("system\n")
        append(persona.trim()).append("\n\n")
        append(SECTION_CONTEXT).append('\n')
        if (context.isEmpty()) {
            append(NO_CONTEXT)
        } else {
            for (entry in context) append("- ").append(entry.content.trim()).append('\n')
            deleteAt(length - 1) // drop trailing newline from the loop
        }
        append(IM_END).append('\n')

        for (turn in history) {
            val role = if (turn.role == ChatRole.USER) "user" else "assistant"
            append(IM_START).append(role).append('\n')
            append(turn.text.trim()).append(IM_END).append('\n')
        }

        append(IM_START).append("user\n")
        append(userText.trim()).append(IM_END).append('\n')
        // Open the assistant turn; the model continues from here.
        append(IM_START).append("assistant\n")
    }

    /**
     * Build the full prompt (legacy bracket format).
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

        // ChatML control tokens (the on-device catalog models are all ChatML).
        const val IM_START = "<|im_start|>"
        const val IM_END = "<|im_end|>"
    }
}
