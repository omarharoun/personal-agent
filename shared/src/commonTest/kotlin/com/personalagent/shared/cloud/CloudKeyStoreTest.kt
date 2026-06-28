package com.personalagent.shared.cloud

import com.personalagent.shared.store.InMemoryKeyValueStorage
import io.ktor.client.engine.mock.MockEngine
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Round-trip tests for [CloudKeyStore] over an [InMemoryKeyValueStorage] (the same
 * seam the encrypted store implements in the app). Verifies per-provider key
 * storage, active-provider selection, and that [CloudKeyStore.activeCloudClient]
 * is null without a key and non-null with one — and that keys never leak into logs
 * or [toString].
 */
class CloudKeyStoreTest {

    private fun store() = CloudKeyStore(InMemoryKeyValueStorage())

    @Test
    fun api_key_round_trips_per_provider() {
        val s = store()
        assertFalse(s.hasKey(CloudProvider.ANTHROPIC))
        assertNull(s.apiKey(CloudProvider.ANTHROPIC))

        s.setApiKey(CloudProvider.ANTHROPIC, "sk-ant-123")
        s.setApiKey(CloudProvider.OPENAI, "sk-oai-456")

        assertEquals("sk-ant-123", s.apiKey(CloudProvider.ANTHROPIC))
        assertEquals("sk-oai-456", s.apiKey(CloudProvider.OPENAI))
        assertTrue(s.hasKey(CloudProvider.ANTHROPIC))
        assertTrue(s.hasKey(CloudProvider.OPENAI))
    }

    @Test
    fun set_blank_key_clears_and_trims() {
        val s = store()
        s.setApiKey(CloudProvider.OPENAI, "  sk-padded  ")
        assertEquals("sk-padded", s.apiKey(CloudProvider.OPENAI))

        s.setApiKey(CloudProvider.OPENAI, "   ")
        assertFalse(s.hasKey(CloudProvider.OPENAI))
        assertNull(s.apiKey(CloudProvider.OPENAI))
    }

    @Test
    fun clear_key_removes_only_that_provider() {
        val s = store()
        s.setApiKey(CloudProvider.ANTHROPIC, "a")
        s.setApiKey(CloudProvider.OPENAI, "b")

        s.clearApiKey(CloudProvider.ANTHROPIC)

        assertFalse(s.hasKey(CloudProvider.ANTHROPIC))
        assertTrue(s.hasKey(CloudProvider.OPENAI))
    }

    @Test
    fun active_provider_round_trips() {
        val s = store()
        assertNull(s.activeProvider())

        s.setActiveProvider(CloudProvider.ANTHROPIC)
        assertEquals(CloudProvider.ANTHROPIC, s.activeProvider())

        s.setActiveProvider(CloudProvider.OPENAI)
        assertEquals(CloudProvider.OPENAI, s.activeProvider())

        s.clearActiveProvider()
        assertNull(s.activeProvider())
    }

    @Test
    fun active_cloud_client_is_null_without_active_provider_or_key() {
        val s = store()
        // No active provider at all.
        assertNull(s.activeCloudClient { MockEngine { error("must not be reached") } })

        // Active provider chosen but no key for it.
        s.setActiveProvider(CloudProvider.ANTHROPIC)
        assertNull(s.activeCloudClient { MockEngine { error("must not be reached") } })
    }

    @Test
    fun active_cloud_client_is_non_null_once_active_provider_has_a_key() {
        val s = store()
        s.setApiKey(CloudProvider.ANTHROPIC, "sk-ant-123")
        s.setActiveProvider(CloudProvider.ANTHROPIC)

        val client = s.activeCloudClient { MockEngine { error("not invoked in this test") } }
        assertNotNull(client)
        // Name carries only the model id (no secret).
        assertTrue(client.name.contains(CloudProvider.ANTHROPIC.defaultModel))
        assertFalse(client.name.contains("sk-ant-123"))
    }

    @Test
    fun toString_redacts_keys() {
        val s = store()
        s.setApiKey(CloudProvider.OPENAI, "super-secret-key")
        assertFalse(s.toString().contains("super-secret-key"))
        assertTrue(s.toString().contains("REDACTED"))
    }
}
