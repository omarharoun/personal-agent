// 🔒 SECURITY-CRITICAL (Step 4) — pending human security review; do not ship to a
// real user until reviewed. A subtle mistake in this file silently defeats the
// whole cloud-escalation privacy guarantee (data leaves the device that should not).
package com.personalagent.shared.cloud

/**
 * 🔒 SECURITY-CRITICAL — the REAL on-device payload-prep for cloud escalation.
 *
 * ## Why this exists
 * When a question is escalated to a remote model, the raw on-device text would
 * otherwise be sent verbatim. This class stands between the device and the cloud
 * and applies two defenses, in priority order.
 *
 * ### 1. MINIMIZE FIRST — the PRIMARY defense ([minimize], applied before anything else)
 * The single most important protection is **sending as little as possible**: reduce
 * the payload to the *shape of the question* and drop detail the cloud doesn't need.
 *
 * This matters because **context re-identifies people even with every proper noun
 * removed.** "My manager at the Springfield plant who I carpool with on Tuesdays"
 * names nobody, yet may identify exactly one human. No scrubber can fix that — only
 * *not sending it* can. So minimization, NOT scrubbing, is why the guarantee holds.
 *
 * The minimizer here is intentionally **conservative** (whitespace + greeting/sign-off
 * removal); aggressive task-specific minimization — deciding *what* to even ask the
 * cloud — happens upstream at the escalation decision. This is the floor, not the
 * ceiling, of minimization.
 *
 * ### 2. ANONYMIZE — the SECONDARY defense ([anonymize])
 * Whatever detail remains is scanned for identifying specifics — emails, phones,
 * names, locations — which are replaced with stable placeholder tokens
 * (`<EMAIL_1>`, `<PHONE_1>`, `<PERSON_1>`, `<LOCATION_1>`). The placeholder→real
 * mapping is recorded in a [RehydrationMap] that **never leaves the device**.
 *
 * ## ⚠️ HONESTY: this anonymizer is imperfect by construction
 * PII detection here is **regex + heuristic**. It WILL miss things (lowercased
 * names, novel formats, misspellings, foreign scripts, indirect references) and it
 * WILL over-match (capitalized common nouns). Treat tokenization as **best-effort
 * defense in depth, not a guarantee.** Do not tell a user "your data is removed
 * before it's sent" — the truthful claim is "we send as little as possible, and
 * scrub the obvious identifiers we can detect from what's left."
 *
 * ## Rehydration
 * The cloud answers in terms of the tokens; [rehydrate] substitutes the real values
 * back in locally, using the [RehydrationMap]. The map is the secret key to the
 * anonymization and must never be transmitted alongside the payload — see the
 * invariant documented on [RehydrationMap].
 */
class DefaultPayloadPrep : PayloadPrep {

    override fun prepare(text: String, contextHints: List<String>): PreparedPayload {
        // PRIMARY defense first: shrink the payload before we even look at PII, so
        // that any detail minimization removes is never anonymized — it is simply
        // never sent.
        val minimized = minimize(text)
        // SECONDARY defense: tokenize the identifiers that survive minimization.
        return anonymize(minimized, contextHints)
    }

    override fun rehydrate(cloudAnswer: String, mapping: RehydrationMap): String {
        // Longest token first so "<PERSON_1>" cannot partially shadow "<PERSON_11>".
        var restored = cloudAnswer
        for (token in mapping.tokensLongestFirst()) {
            val real = mapping.realForToken(token) ?: continue
            restored = restored.replace(token, real)
        }
        return restored
    }

    // ---------------------------------------------------------------------------
    // PRIMARY DEFENSE — minimization
    // ---------------------------------------------------------------------------

    /**
     * Conservative payload minimization (the PRIMARY privacy defense).
     *
     * Public (not part of [PayloadPrep]) so it can be unit-tested in isolation and
     * reused by the escalation decision. It removes content that adds length but no
     * question-shape — social greetings and sign-offs — and collapses redundant
     * whitespace. It deliberately does NOT paraphrase or drop sentences, because
     * doing so safely requires understanding the question; that heavier minimization
     * is the caller's responsibility upstream.
     *
     * Honest scope: this guarantees the payload is no *larger* than the input and
     * usually smaller; it does not guarantee the payload is *minimal*.
     */
    fun minimize(text: String): String {
        var t = text
        for (filler in FILLER_PHRASES) {
            t = filler.replace(t, " ")
        }
        // Collapse whitespace runs and tidy punctuation left dangling by removal.
        t = WHITESPACE.replace(t, " ")
        t = DANGLING_PUNCT.replace(t, "$1")   // drop the stray space, keep the punctuation
        return t.trim()
    }

    // ---------------------------------------------------------------------------
    // SECONDARY DEFENSE — anonymization
    // ---------------------------------------------------------------------------

    private fun anonymize(text: String, contextHints: List<String>): PreparedPayload {
        val map = RehydrationMap()
        val counters = HashMap<String, Int>()

        fun tokenize(real: String, category: String): String {
            val trimmed = real.trim()
            map.tokenForReal(trimmed)?.let { return it }       // same entity → same token
            val n = (counters[category] ?: 0) + 1
            counters[category] = n
            val token = "<${category}_$n>"
            map.record(token, trimmed)
            return token
        }

        var t = text

        // Most specific patterns first, so their digits/letters aren't re-grabbed by
        // a looser pattern downstream. Emails before phones (emails contain no phone),
        // structured PII before free-text proper nouns.
        t = EMAIL.replace(t) { tokenize(it.value, "EMAIL") }
        t = PHONE.replace(t) { m ->
            if (m.value.count { it.isDigit() } in 7..15) tokenize(m.value, "PHONE") else m.value
        }

        // Authoritative caller-supplied hints (known-sensitive literals from local
        // memory). Longest first so "Alice Johnson" wins over "Alice".
        for (hint in contextHints.map { it.trim() }.filter { it.isNotEmpty() }.sortedByDescending { it.length }) {
            val category = classifyHint(hint)
            val rx = Regex("(?<![\\w])" + Regex.escape(hint) + "(?![\\w])", RegexOption.IGNORE_CASE)
            t = rx.replace(t) { tokenize(hint, category) }
        }

        // Heuristic proper-noun pass for identifiers the caller didn't hint. This is
        // the weakest, most error-prone layer — see the honesty note on the class.
        // The regex is greedy and will swallow a sentence-initial capitalized word
        // ("Email Alice" → one match), so we trim leading/trailing stopwords and
        // tokenize only the entity core, preserving the dropped words verbatim.
        t = PROPER_NOUN.replace(t) { m ->
            val words = m.value.split(WHITESPACE)
            var start = 0
            var end = words.size
            while (start < end && isStopword(words[start])) start++
            while (end > start && isStopword(words[end - 1])) end--
            if (start >= end) return@replace m.value           // nothing but stopwords
            val core = words.subList(start, end).joinToString(" ")
            val token = tokenize(core, if (looksLikeLocation(core)) "LOCATION" else "PERSON")
            (words.subList(0, start) + token + words.subList(end, words.size)).joinToString(" ")
        }

        return PreparedPayload(anonymizedText = t, mapping = map)
    }

    private fun classifyHint(hint: String): String = when {
        hint.contains('@') -> "EMAIL"
        hint.count { it.isDigit() } >= 7 -> "PHONE"
        looksLikeLocation(hint) -> "LOCATION"
        else -> "PERSON"
    }

    private fun looksLikeLocation(phrase: String): Boolean =
        phrase.split(Regex("\\s+")).any { it.lowercase().trim('.', ',') in LOCATION_GAZETTEER }

    private fun isStopword(word: String): Boolean =
        word.lowercase().trim('.', ',') in PROPER_NOUN_STOPWORDS

    private companion object {
        // ---- minimization ----
        // Pure-noise social wrappers. Conservative on purpose; documented as the floor.
        val FILLER_PHRASES: List<Regex> = listOf(
            Regex("\\b(hi|hello|hey)\\b[\\s,!.]*", RegexOption.IGNORE_CASE),
            Regex("\\b(dear)\\b[\\s,]*", RegexOption.IGNORE_CASE),
            Regex("\\b(best|kind|warm)\\s+regards\\b[\\s,!.]*", RegexOption.IGNORE_CASE),
            Regex("\\b(regards|sincerely|cheers|thanks|thank you)\\b[\\s,!.]*", RegexOption.IGNORE_CASE),
        )
        val WHITESPACE = Regex("\\s+")
        val DANGLING_PUNCT = Regex("\\s+([,.;:!?])")

        // ---- anonymization patterns ----
        val EMAIL = Regex("[A-Za-z0-9._%+\\-]+@[A-Za-z0-9.\\-]+\\.[A-Za-z]{2,}")
        // Loose phone shape; the digit-count check in anonymize() rejects stray runs.
        val PHONE = Regex("\\+?\\(?\\d[\\d\\s().\\-]{5,}\\d")
        // One or more Capitalized words; tokens like <PERSON_1> are ALL-CAPS so the
        // required trailing lowercase keeps the pass from re-matching its own output.
        val PROPER_NOUN = Regex("\\b[A-Z][a-z]+(?:[.\\-'’]?\\s*[A-Z][a-z]+)*\\b")

        // Tiny illustrative gazetteer — a real build would use a proper on-device
        // place list / NER model. Lowercased; only used to TYPE a detected proper noun.
        val LOCATION_GAZETTEER = setOf(
            "springfield", "london", "paris", "berlin", "tokyo", "york", "boston",
            "seattle", "austin", "dublin", "madrid", "rome", "chicago", "denver",
        )

        // Common capitalized non-entities, so sentence-initial words and labels don't
        // become spurious <PERSON_n> tokens. Far from exhaustive — over/under-matching
        // is expected (see the honesty note).
        val PROPER_NOUN_STOPWORDS = setOf(
            "i", "the", "a", "an", "my", "me", "we", "you", "it", "is", "are", "was",
            "please", "could", "would", "can", "should", "email", "call", "tell",
            "remind", "ask", "and", "or", "but", "to", "from", "in", "at", "on",
            "with", "about", "for", "of", "what", "when", "where", "who", "how", "why",
        )
    }
}
