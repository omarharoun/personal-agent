package com.personalagent.shared.cloud

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PayloadPrepTest {

    // --- RehydrationMap round-trip -----------------------------------------

    @Test
    fun rehydration_map_put_and_lookup_round_trip() {
        val map = RehydrationMap()
            .put("<NAME_1>", "Dr. Lee")
            .put("<PHONE_1>", "555-0100")

        assertEquals("Dr. Lee", map.lookup("<NAME_1>"))
        assertEquals("555-0100", map.lookup("<PHONE_1>"))
        assertNull(map.lookup("<NAME_2>"))
        assertEquals(2, map.size)
        assertFalse(map.isEmpty())
    }

    @Test
    fun rehydration_map_toString_is_redacted() {
        val map = RehydrationMap().put("<NAME_1>", "Top Secret Person")
        val s = map.toString()
        assertFalse(s.contains("Top Secret Person"), "toString must never leak real values")
        assertTrue(s.contains("REDACTED"))
    }

    @Test
    fun rehydrate_replaces_tokens_with_real_values() {
        // RehydrationMap.rehydrate is internal but reachable via PassthroughPayloadPrep.
        val prep = PassthroughPayloadPrep()
        val map = RehydrationMap().put("<NAME_1>", "Dr. Lee")
        assertEquals(
            "Booked with Dr. Lee at 9am",
            prep.rehydrate("Booked with <NAME_1> at 9am", map),
        )
    }

    // --- PassthroughPayloadPrep --------------------------------------------

    @Test
    fun passthrough_prepare_returns_text_unchanged_with_empty_mapping() {
        val prep = PassthroughPayloadPrep()
        val prepared = prep.prepare("call Dr. Lee at 555-0100")

        assertEquals("call Dr. Lee at 555-0100", prepared.anonymizedText)
        assertTrue(prepared.mapping.isEmpty())
    }

    @Test
    fun passthrough_rehydrate_with_empty_mapping_is_identity() {
        val prep = PassthroughPayloadPrep()
        val empty = prep.prepare("anything").mapping
        assertEquals("the cloud answer", prep.rehydrate("the cloud answer", empty))
    }
}
