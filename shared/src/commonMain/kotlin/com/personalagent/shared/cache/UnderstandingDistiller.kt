package com.personalagent.shared.cache

import com.personalagent.shared.conversation.GenOptions
import com.personalagent.shared.conversation.OnDeviceLlm

/**
 * The distilled result of one interaction: a stable [topic] label and a [summary]
 * of *understanding* — durable facts / what's been figured out about the topic or
 * the user — explicitly **not** the verbatim reply.
 */
data class DistilledUnderstanding(
    val topic: String,
    val summary: String,
)

/**
 * Turns a finished conversation turn into cached **understanding**.
 *
 * Step 6 thesis: the system gets cheaper *and* more personal over time by caching
 * what it has *figured out*, not the answers it gave. After a turn — especially one
 * that hit the cloud or surfaced a new fact about a topic or the user — the
 * distiller asks the on-device [OnDeviceLlm] to extract the durable understanding
 * (a topic + a few facts) and stores that in the [SemanticCache]. A later, similar
 * turn can then retrieve the understanding and be answered locally, so cloud usage
 * falls with use (see [com.personalagent.shared.cache.CloudUsageStats]).
 *
 * We cache understanding, **never canned answers**: the prompt forbids copying the
 * reply, and [distill] is a no-op for empty/trivial turns so the cache fills only
 * with signal.
 *
 * Fully on-device and deterministic-testable: inject the Step-3
 * `FakeOnDeviceLlm` to script the summary, with no model and no network.
 *
 * 🤝 SHARED CONTRACT — constructor is `UnderstandingDistiller(llm)`. The
 * [SemanticCache] is supplied per call ([distillInto]) because the cache instance
 * is owned and wired by the sibling/coordinator, not by the distiller.
 *
 * @param llm the on-device model used to summarize the interaction into facts.
 * @param minTurnWords a turn whose user text has fewer than this many words is
 *   treated as too trivial to hold durable understanding and is skipped.
 */
class UnderstandingDistiller(
    private val llm: OnDeviceLlm,
    private val minTurnWords: Int = DEFAULT_MIN_TURN_WORDS,
) {

    /**
     * Distill one interaction into [DistilledUnderstanding], or `null` when there's
     * nothing worth caching (blank/too-short turn, no model available, or the model
     * produced no usable understanding).
     *
     * The reply is provided only as *evidence* for the model to reason over; the
     * prompt instructs it to extract facts, never to echo the answer.
     *
     * @param userText the user's turn.
     * @param assistantReply the reply that was given (cloud or local).
     * @param options decoding options; defaults bias toward determinism (low temp).
     */
    suspend fun distill(
        userText: String,
        assistantReply: String,
        options: GenOptions = DISTILL_OPTIONS,
    ): DistilledUnderstanding? {
        if (!llm.isAvailable) return null

        val turn = userText.trim()
        val reply = assistantReply.trim()
        if (!isWorthDistilling(turn)) return null

        val raw = llm.generate(buildPrompt(turn, reply), options).trim()
        return parse(raw)
    }

    /**
     * [distill] the interaction and, when it yields understanding, [SemanticCache.store]
     * it. Returns what was stored, or `null` when nothing was distilled (so callers
     * can branch / count). The cache is only touched on success.
     */
    suspend fun distillInto(
        cache: SemanticCache,
        userText: String,
        assistantReply: String,
        options: GenOptions = DISTILL_OPTIONS,
    ): DistilledUnderstanding? {
        val understanding = distill(userText, assistantReply, options) ?: return null
        cache.store(understanding.topic, understanding.summary)
        return understanding
    }

    /** A turn is worth distilling only if it carries enough signal to hold a fact. */
    private fun isWorthDistilling(turn: String): Boolean {
        if (turn.isEmpty()) return false
        val words = turn.split(WHITESPACE).count { it.isNotBlank() }
        return words >= minTurnWords
    }

    private fun buildPrompt(turn: String, reply: String): String = buildString {
        append(INSTRUCTION).append("\n\n")
        append("USER TURN:\n").append(turn).append("\n\n")
        append("ASSISTANT REPLY (evidence only — do NOT copy it):\n")
        append(reply.ifEmpty { "(none)" }).append("\n\n")
        append(OUTPUT_FORMAT)
    }

    /**
     * Parse the model output into [DistilledUnderstanding].
     *
     * Primary path reads the `TOPIC:` / `SUMMARY:` markers the prompt asks for.
     * If the model ignored the format, fall back to first-line-as-topic and the
     * remainder (or whole text) as summary, so a sloppy model still yields usable
     * understanding rather than nothing. Returns `null` only when there's no text
     * at all to file.
     */
    private fun parse(raw: String): DistilledUnderstanding? {
        if (raw.isBlank()) return null

        val topic = extractField(raw, TOPIC_MARKER)
        val summary = extractField(raw, SUMMARY_MARKER)

        if (topic != null && summary != null && topic.isNotBlank() && summary.isNotBlank()) {
            return DistilledUnderstanding(topic.trim(), summary.trim())
        }

        // Fallback: no usable markers — derive a best-effort topic + summary.
        val lines = raw.lines().map { it.trim() }.filter { it.isNotEmpty() }
        if (lines.isEmpty()) return null
        val fallbackTopic = (topic?.takeIf { it.isNotBlank() } ?: lines.first())
            .take(MAX_TOPIC_CHARS)
        val fallbackSummary = (summary?.takeIf { it.isNotBlank() }
            ?: lines.joinToString(" ")).trim()
        if (fallbackSummary.isBlank()) return null
        return DistilledUnderstanding(fallbackTopic.trim(), fallbackSummary)
    }

    /** Read the text after a `MARKER:` up to the next line, case-insensitively. */
    private fun extractField(raw: String, marker: String): String? {
        for (line in raw.lines()) {
            val trimmed = line.trim()
            if (trimmed.length >= marker.length &&
                trimmed.substring(0, marker.length).equals(marker, ignoreCase = true)
            ) {
                return trimmed.substring(marker.length).trim()
            }
        }
        return null
    }

    companion object {
        const val DEFAULT_MIN_TURN_WORDS: Int = 3
        const val MAX_TOPIC_CHARS: Int = 80

        private const val TOPIC_MARKER = "TOPIC:"
        private const val SUMMARY_MARKER = "SUMMARY:"

        private val WHITESPACE = Regex("\\s+")

        /** Low temperature: distillation should be stable, not creative. */
        private val DISTILL_OPTIONS = GenOptions(maxTokens = 256, temperature = 0.1f)

        private const val INSTRUCTION =
            "You distill a conversation turn into durable UNDERSTANDING for a personal " +
                "assistant's long-term cache. Extract the lasting facts this turn revealed " +
                "about the user or the subject — what has been figured out — so a future " +
                "similar turn can be answered without re-asking. Do NOT restate or copy the " +
                "assistant's reply; capture knowledge, not the answer. Be concise and factual."

        private const val OUTPUT_FORMAT =
            "Respond in exactly this format:\nTOPIC: <short stable label>\n" +
                "SUMMARY: <one or two sentences of durable facts / understanding>"
    }
}
