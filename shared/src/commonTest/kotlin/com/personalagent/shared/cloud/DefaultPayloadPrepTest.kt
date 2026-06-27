package com.personalagent.shared.cloud

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 🔒 SECURITY-CRITICAL tests for [DefaultPayloadPrep].
 *
 * These assert the two defenses hold: (1) minimization shrinks the payload, and
 * (2) detectable identifiers are tokenized, never appear in the outbound text, map
 * stably, round-trip back to their real values, and the mapping never leaks into
 * the payload. They do NOT — and cannot — assert the anonymizer catches *all* PII;
 * that is impossible with heuristics and is why minimization is the primary defense.
 */
class DefaultPayloadPrepTest {

    private val prep = DefaultPayloadPrep()

    @Test
    fun emails_phones_names_locations_are_tokenized_and_absent_from_payload() {
        val payload = prep.prepare(
            "Email Alice Johnson at alice@example.com or call 555-123-4567; she lives in Springfield."
        )
        val out = payload.anonymizedText

        // Each category produced a token.
        assertTrue(out.contains("<EMAIL_1>"), "email not tokenized: $out")
        assertTrue(out.contains("<PHONE_1>"), "phone not tokenized: $out")
        assertTrue(out.contains("<PERSON_1>"), "name not tokenized: $out")
        assertTrue(out.contains("<LOCATION_1>"), "location not tokenized: $out")

        // None of the real identifiers survive in the outbound text.
        assertFalse(out.contains("alice@example.com"), "email leaked: $out")
        assertFalse(out.contains("555-123-4567"), "phone leaked: $out")
        assertFalse(out.contains("Alice Johnson"), "name leaked: $out")
        assertFalse(out.contains("Springfield"), "location leaked: $out")
    }

    @Test
    fun the_same_entity_always_maps_to_the_same_token() {
        val payload = prep.prepare("Tell Sarah that Sarah should call.")
        val out = payload.anonymizedText

        assertTrue(out.contains("<PERSON_1>"), "expected a person token: $out")
        assertFalse(out.contains("<PERSON_2>"), "same name got two different tokens: $out")
        assertEquals(2, "<PERSON_1>".toRegex().findAll(out).count(), "both mentions should reuse the token: $out")
        assertFalse(out.contains("Sarah"), "name leaked: $out")
    }

    @Test
    fun rehydrate_restores_the_real_values_in_a_full_round_trip() {
        val payload = prep.prepare("Email Bob at bob@x.com about Denver.")

        // Simulate the cloud answering purely in terms of the tokens it received.
        val cloudAnswer = "I drafted a note to <PERSON_1> at <EMAIL_1> regarding <LOCATION_1>."
        val rehydrated = prep.rehydrate(cloudAnswer, payload.mapping)

        assertEquals("I drafted a note to Bob at bob@x.com regarding Denver.", rehydrated)
        // Real values are back; no token residue.
        assertFalse(rehydrated.contains("<PERSON_1>"))
        assertFalse(rehydrated.contains("<EMAIL_1>"))
        assertFalse(rehydrated.contains("<LOCATION_1>"))
    }

    @Test
    fun minimization_reduces_the_payload() {
        val verbose = "Hello,   I    really    need   the    quarterly   report.    Thanks,   Sam"
        val minimized = prep.minimize(verbose)

        assertTrue(minimized.length < verbose.length, "minimize did not shrink: '$minimized'")
        // Pure-noise social wrappers are gone; the question survives.
        assertFalse(minimized.contains("Hello"), "greeting not minimized: '$minimized'")
        assertFalse(minimized.contains("Thanks"), "sign-off not minimized: '$minimized'")
        assertTrue(minimized.contains("quarterly report"), "question content lost: '$minimized'")
    }

    @Test
    fun minimization_runs_before_anonymization_in_prepare() {
        // prepare() must shrink before it tokenizes, so removed detail is never sent.
        val raw = "Hello,    please    summarize    this    for    me."
        val payload = prep.prepare(raw)
        assertTrue(payload.anonymizedText.length < raw.length, "prepare did not minimize: ${payload.anonymizedText}")
        assertFalse(payload.anonymizedText.contains("Hello"))
    }

    @Test
    fun context_hints_catch_identifiers_the_heuristics_would_miss() {
        // A lowercased name no capitalization heuristic would flag — but the caller
        // knows it is sensitive and passes it as an authoritative hint.
        val payload = prep.prepare("my buddy alice is visiting", contextHints = listOf("alice"))
        val out = payload.anonymizedText

        assertTrue(out.contains("<PERSON_1>"), "hinted entity not tokenized: $out")
        assertFalse(out.contains("alice"), "hinted entity leaked: $out")
    }

    @Test
    fun the_mapping_is_never_leaked_into_the_anonymized_text() {
        val payload = prep.prepare(
            "Email Alice Johnson at alice@example.com or call 555-123-4567 in Springfield."
        )
        val out = payload.anonymizedText

        // No real value recorded in the mapping may appear in the outbound text.
        for (token in payload.mapping.tokensLongestFirst()) {
            val real = payload.mapping.realForToken(token)!!
            assertFalse(out.contains(real), "real value '$real' leaked into payload: $out")
        }
        // And the mapping's own toString must not expose its contents (log-safety).
        assertFalse(payload.mapping.toString().contains("alice@example.com"))
        assertTrue(payload.mapping.toString().contains("REDACTED"))
    }

    @Test
    fun rehydrate_disambiguates_token_prefixes() {
        // Build a mapping with >10 person tokens so "<PERSON_1>" is a prefix of
        // "<PERSON_11>"; longest-first restoration must not corrupt the longer token.
        val names = (1..11).joinToString(" and ") { "Name${('A' + it - 1)}x" }
        val payload = prep.prepare(names)
        // Echo every token back, then rehydrate; nothing should be partially replaced.
        val restored = prep.rehydrate(payload.anonymizedText, payload.mapping)
        assertEquals(names, restored, "prefix collision corrupted rehydration: $restored")
    }
}
