// 🔒 CRISIS-CRITICAL (Step 7) — autonomous action DISABLED; requires human + crisis-response-expert review; NOT-FOR-REAL-USERS.
package com.personalagent.shared.safety

import com.personalagent.shared.store.InMemoryKeyValueStorage
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.fail

class CrisisSafetyTest {

    // --- Recognizer: conservative ---

    @Test
    fun ordinary_text_is_NONE() {
        val r = KeywordCrisisRecognizer()
        val ordinary = listOf(
            "What's the weather like tomorrow?",
            "I'm dying to see the new movie tonight!",
            "This traffic is killing me, I'll be late.",
            "Remind me to buy milk.",
            "I'm dead tired after that workout.",
            "",
            "   ",
        )
        for (text in ordinary) {
            assertEquals(
                CrisisLevel.NONE,
                r.assess(text).level,
                "Expected NONE for ordinary text: \"$text\"",
            )
        }
    }

    @Test
    fun clear_distress_is_POSSIBLE_DISTRESS() {
        val r = KeywordCrisisRecognizer()
        val distress = listOf(
            "I want to die.",
            "I don't want to live anymore.",
            "Sometimes I think about killing myself.",
            "There's no reason to live.",
            "I feel suicidal.",
        )
        for (text in distress) {
            assertEquals(
                CrisisLevel.POSSIBLE_DISTRESS,
                r.assess(text).level,
                "Expected POSSIBLE_DISTRESS for: \"$text\"",
            )
        }
    }

    @Test
    fun recognizer_rationale_does_not_echo_user_text() {
        // We must not repeat/amplify the user's distress back at them.
        val r = KeywordCrisisRecognizer()
        val assessment = r.assess("I want to die")
        assertFalse(
            assessment.rationale.lowercase().contains("want to die"),
            "Rationale must not echo the user's words back.",
        )
    }

    // --- Responder: consent-first, safe copy ---

    @Test
    fun no_response_for_NONE() {
        val responder = CrisisResponder(DefaultCrisisResourceProvider())
        val resp = responder.respond(CrisisAssessment(CrisisLevel.NONE, "ordinary"))
        assertNull(resp, "We never push an unsolicited crisis message on ordinary text.")
    }

    @Test
    fun response_encourages_self_reach_out_has_resources_and_offers_consent_help() {
        val responder = CrisisResponder(DefaultCrisisResourceProvider())
        val resp = responder.respond(
            CrisisAssessment(CrisisLevel.POSSIBLE_DISTRESS, "matched"),
            regionHint = "US",
        )
        assertNotNull(resp)
        val msg = resp.message.lowercase()

        // (a) encourages the user to reach out themselves to a trusted person
        assertTrue(
            msg.contains("reaching out to someone you trust") || msg.contains("reach out to someone you trust"),
            "Message should encourage self-reach-out to a trusted person.",
        )
        // (b) surfaces real resources
        assertTrue(resp.resources.isNotEmpty(), "Response must include crisis resources.")
        // (c) offers consent-based help to contact someone
        assertTrue(resp.offerToHelpContact, "Response must offer consent-based help to contact someone.")
        assertTrue(
            msg.contains("only if and when you want"),
            "The offer must be framed as available only with the user's clear consent.",
        )
    }

    @Test
    fun response_makes_no_confidentiality_or_no_authorities_claims() {
        val responder = CrisisResponder(DefaultCrisisResourceProvider())
        val resp = responder.respond(CrisisAssessment(CrisisLevel.POSSIBLE_DISTRESS, "matched"))
        assertNotNull(resp)
        val msg = resp.message.lowercase()
        val forbidden = listOf(
            "confidential",
            "stays between us",
            "between you and me",
            "won't tell anyone",
            "will not tell anyone",
            "no authorities",
            "authorities won't",
            "authorities will not",
            "won't be involved",
            "will not be involved",
            "this is private",
        )
        for (phrase in forbidden) {
            assertFalse(
                msg.contains(phrase),
                "Message must not make confidentiality / no-authorities claims: \"$phrase\"",
            )
        }
    }

    @Test
    fun default_resources_are_flagged_to_verify_and_localize() {
        // The default provider must not masquerade as production-ready.
        val resources = DefaultCrisisResourceProvider().resourcesFor("US")
        assertTrue(resources.isNotEmpty())
        assertTrue(
            resources.all { it.note.uppercase().contains("VERIFY") && it.note.uppercase().contains("LOCALIZE") },
            "Default resources must be loudly flagged VERIFY + LOCALIZE.",
        )
    }

    // --- Trusted contacts store: consent-first round-trip ---

    @Test
    fun trusted_contacts_roundtrip_with_consent_timestamp() = runTest {
        val storage = InMemoryKeyValueStorage()
        val store = TrustedContactsStore(storage)
        val contact = TrustedContact(
            id = "c1",
            name = "Alex",
            relationship = "sibling",
            phone = "+10000000000",
            consentedAt = 1_700_000_000_000L,
        )
        store.add(contact)

        assertEquals(listOf(contact), store.all())
        assertEquals(contact, store.get("c1"))
        // Persisted in the storage layer, not the object: a fresh store sees it.
        val reopened = TrustedContactsStore(storage)
        assertEquals(1_700_000_000_000L, reopened.get("c1")?.consentedAt)
    }

    @Test
    fun trusted_contacts_store_rejects_contact_without_consent() = runTest {
        val store = TrustedContactsStore(InMemoryKeyValueStorage())
        val noConsent = TrustedContact("c2", "Sam", "friend", null, consentedAt = 0L)
        try {
            store.add(noConsent)
            fail("Adding a contact without a consent timestamp must throw.")
        } catch (e: IllegalArgumentException) {
            // expected — consent must be captured up front
        }
    }

    @Test
    fun trusted_contacts_remove_works() = runTest {
        val store = TrustedContactsStore(InMemoryKeyValueStorage())
        store.add(TrustedContact("a", "A", "friend", null, 1L))
        store.add(TrustedContact("b", "B", "friend", null, 1L))
        store.remove("a")
        assertEquals(listOf("b"), store.all().map { it.id })
    }

    // --- DISABLED autonomous action: never acts, regardless of input ---

    @Test
    fun disabled_autonomous_action_never_acts_regardless_of_input() {
        val action = DisabledAutonomousCrisisAction()
        assertFalse(action.enabled, "Autonomous action must be disabled.")

        val inputs = listOf(
            AutonomousActionRequest(
                CrisisAssessment(CrisisLevel.POSSIBLE_DISTRESS, "x"),
                "I want to die",
                TrustedContact("c1", "Alex", "sibling", "+10000000000", 1L),
            ),
            AutonomousActionRequest(
                CrisisAssessment(CrisisLevel.NONE, "y"),
                "hello",
                null,
            ),
            AutonomousActionRequest(
                CrisisAssessment(CrisisLevel.POSSIBLE_DISTRESS, "z"),
                "",
                null,
            ),
        )
        for (req in inputs) {
            val outcome = action.attempt(req)
            // The ONLY possible outcome is Refused — there is no "acted" path.
            assertTrue(
                outcome is AutonomousActionOutcome.Refused,
                "Disabled autonomous action must always refuse; got $outcome",
            )
        }
    }
}
