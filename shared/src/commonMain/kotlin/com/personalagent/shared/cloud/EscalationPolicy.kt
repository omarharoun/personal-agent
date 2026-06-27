package com.personalagent.shared.cloud

/**
 * Decides whether one turn should be answered locally (on-device) or **escalated**
 * to a [CloudClient].
 *
 * 🤝 SHARED CONTRACT — Step 4. `ConversationService` consults this at the decision
 * point that was a no-op stub in Step 3.
 *
 * @receiver localContext the already-retrieved on-device context for the turn
 *   (memory snippets), as plain strings — a policy may read it for confidence
 *   signals but must not need anything off-device to decide.
 */
interface EscalationPolicy {
    fun shouldEscalate(userText: String, localContext: List<String>): Boolean
}

/**
 * The safe default: **never** escalate. Keeps `ConversationService` local-only and
 * network-free unless a caller deliberately opts into a different policy.
 */
object LocalOnlyEscalationPolicy : EscalationPolicy {
    override fun shouldEscalate(userText: String, localContext: List<String>): Boolean = false
}

/**
 * Tuning knobs for [HeuristicEscalationPolicy]. All thresholds are configurable so
 * the heuristic is easy to unit-test and to tighten/loosen per product needs.
 *
 * @param enabled master switch; when false the policy never escalates.
 * @param explicitEscalationPhrases substrings (matched case-insensitively) that
 *   signal the user explicitly wants deeper/cloud reasoning ("think hard",
 *   "do deep research", …). Any match escalates.
 * @param lowConfidenceMarkers substrings that, if present in [localContext],
 *   signal the on-device path is unsure (e.g. a retrieval tagged low-confidence).
 *   Any match escalates.
 * @param minWordsForComplexPlanning a turn must be at least this long (in words)
 *   before length+constraint complexity can trigger escalation.
 * @param minConstraintsForComplex how many constraint signals a long turn must
 *   carry before it counts as complex multi-constraint planning.
 * @param constraintMarkers substrings counted as constraint signals.
 */
data class HeuristicConfig(
    val enabled: Boolean = true,
    val explicitEscalationPhrases: List<String> = DEFAULT_EXPLICIT_PHRASES,
    val lowConfidenceMarkers: List<String> = DEFAULT_LOW_CONFIDENCE_MARKERS,
    val minWordsForComplexPlanning: Int = 60,
    val minConstraintsForComplex: Int = 3,
    val constraintMarkers: List<String> = DEFAULT_CONSTRAINT_MARKERS,
) {
    companion object {
        val DEFAULT_EXPLICIT_PHRASES: List<String> = listOf(
            "think hard",
            "think harder",
            "think deeply",
            "deep research",
            "do research",
            "research this",
            "in-depth analysis",
            "in depth analysis",
            "thorough analysis",
            "reason carefully",
            "use the cloud",
            "escalate to the cloud",
        )

        val DEFAULT_LOW_CONFIDENCE_MARKERS: List<String> = listOf(
            "[low-confidence]",
            "[low confidence]",
            "[uncertain]",
        )

        // Counted as constraint signals; the leading/trailing spaces keep matches
        // word-ish so we don't count substrings inside other words.
        val DEFAULT_CONSTRAINT_MARKERS: List<String> = listOf(
            " and ", " but ", " also ", " must ", " without ", " before ", " after ",
            " then ", " however ", " whereas ", " constraint", " requirement",
            ",", ";",
        )
    }
}

/**
 * A conservative, configurable [EscalationPolicy]. **Defaults to LOCAL** and only
 * escalates when a turn genuinely exceeds on-device capability:
 *
 *  1. **Explicit ask** — the user asked for deeper reasoning / research / cloud
 *     ([HeuristicConfig.explicitEscalationPhrases]).
 *  2. **Low local confidence** — the retrieved [localContext] is tagged with a
 *     low-confidence marker ([HeuristicConfig.lowConfidenceMarkers]).
 *  3. **Complex multi-constraint planning** — the turn is both long
 *     (≥ [HeuristicConfig.minWordsForComplexPlanning] words) and carries many
 *     constraint signals (≥ [HeuristicConfig.minConstraintsForComplex]).
 *
 * Anything else stays local. The double gate on (3) (long **and** many
 * constraints) keeps ordinary chatter — even wordy chatter — on-device.
 */
class HeuristicEscalationPolicy(
    private val config: HeuristicConfig = HeuristicConfig(),
) : EscalationPolicy {

    override fun shouldEscalate(userText: String, localContext: List<String>): Boolean {
        if (!config.enabled) return false

        val text = userText.lowercase()

        // (1) explicit user ask for deeper/cloud reasoning.
        if (config.explicitEscalationPhrases.any { it.isNotEmpty() && text.contains(it.lowercase()) }) {
            return true
        }

        // (2) low local confidence signalled by the retrieved context.
        if (localContext.any { ctx ->
                val c = ctx.lowercase()
                config.lowConfidenceMarkers.any { it.isNotEmpty() && c.contains(it.lowercase()) }
            }
        ) {
            return true
        }

        // (3) complex multi-constraint planning: long AND many constraints.
        val wordCount = userText.split(WHITESPACE).count { it.isNotBlank() }
        if (wordCount >= config.minWordsForComplexPlanning) {
            // Pad so leading/trailing markers (e.g. a final clause) still match.
            val padded = " $text "
            val constraintHits = config.constraintMarkers.sumOf { marker ->
                if (marker.isEmpty()) 0 else countOccurrences(padded, marker.lowercase())
            }
            if (constraintHits >= config.minConstraintsForComplex) return true
        }

        return false
    }

    private fun countOccurrences(haystack: String, needle: String): Int {
        if (needle.isEmpty()) return 0
        var count = 0
        var idx = haystack.indexOf(needle)
        while (idx >= 0) {
            count++
            idx = haystack.indexOf(needle, idx + needle.length)
        }
        return count
    }

    private companion object {
        val WHITESPACE = Regex("\\s+")
    }
}
