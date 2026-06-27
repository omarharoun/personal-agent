// 🔒 CRISIS-CRITICAL (Step 7) — autonomous action DISABLED; requires human + crisis-response-expert review; NOT-FOR-REAL-USERS.
package com.personalagent.shared.safety

/**
 * A deliberately **coarse, keyword/phrase-based** [CrisisRecognizer].
 *
 * 🔒 HONEST LIMITATIONS — read before trusting this.
 * Real recognition of distress is genuinely hard and this class does not solve
 * it. It is a crude substring matcher over a small list of *unambiguous,
 * multi-word* phrases. By design it will:
 *   - MISS most genuine distress (indirect language, metaphor, lowercased or
 *     novel wording, anything not on the list, languages other than English);
 *   - and still occasionally FALSE-ALARM on idioms it can't disambiguate.
 * That tradeoff is acceptable ONLY because a positive result does nothing on its
 * own: it merely gates whether a gentle, consent-first *offer* of support is
 * shown. It must NEVER gate an autonomous action. The false-alarm / false-
 * negative balance here is one of the things a crisis-response expert must
 * review (see docs/SECURITY_REVIEW.md, Gate 2).
 *
 * Bias: hard toward [CrisisLevel.NONE]. We match only whole phrases that, in
 * plain reading, clearly express that someone may be in a hard, unsafe moment.
 * Matching whole phrases (not single words like "die"/"kill"/"dead") avoids the
 * most common idioms ("dying to see it", "this traffic is killing me").
 */
class KeywordCrisisRecognizer(
    /**
     * Phrases that clearly enough signal possible distress. Lowercase, matched as
     * substrings of normalized text. Intentionally small and conservative —
     * adding looser terms trades false-negatives for false-positives and must be
     * a reviewed decision, not a casual edit.
     */
    private val distressPhrases: List<String> = DEFAULT_DISTRESS_PHRASES,
) : CrisisRecognizer {

    override fun assess(userText: String): CrisisAssessment {
        val normalized = normalize(userText)
        if (normalized.isBlank()) {
            return CrisisAssessment(CrisisLevel.NONE, "Empty input.")
        }
        val hit = distressPhrases.firstOrNull { normalized.contains(it) }
        return if (hit != null) {
            CrisisAssessment(
                level = CrisisLevel.POSSIBLE_DISTRESS,
                // Rationale deliberately does NOT echo the user's words back —
                // we don't repeat/amplify distress. It names the mechanism only.
                rationale = "Matched a conservative distress phrase. " +
                    "Coarse signal only — gates a supportive offer, nothing more.",
            )
        } else {
            CrisisAssessment(
                level = CrisisLevel.NONE,
                rationale = "No conservative distress phrase matched.",
            )
        }
    }

    /**
     * Lowercase, collapse whitespace, and strip punctuation that would split
     * phrases. Apostrophes are *deleted* (not turned into spaces) so contractions
     * collapse to their bare form ("don't" -> "dont"), which is how the phrase
     * list is written; all other punctuation becomes a word boundary.
     */
    private fun normalize(text: String): String =
        text.lowercase()
            .filterNot { it == '\'' || it == '’' }
            .map { if (it.isLetterOrDigit() || it == ' ') it else ' ' }
            .joinToString("")
            .split(' ')
            .filter { it.isNotEmpty() }
            .joinToString(" ")

    companion object {
        /**
         * 🔒 Curated, conservative phrase list. These are whole-phrase, plain-
         * reading expressions of someone possibly being unsafe. This list is NOT
         * exhaustive and is NOT a substitute for human/clinical judgment — it is
         * the floor for *offering* support, reviewed under Gate 2.
         */
        val DEFAULT_DISTRESS_PHRASES: List<String> = listOf(
            "kill myself",
            "killing myself",
            "want to die",
            "wish i was dead",
            "wish i were dead",
            "end my life",
            "ending my life",
            "take my own life",
            "dont want to live",
            "do not want to live",
            "no reason to live",
            "nothing to live for",
            "better off dead",
            "better off without me",
            "hurt myself",
            "harm myself",
            "cant go on",
            "cannot go on",
            "suicidal",
        )
    }
}
