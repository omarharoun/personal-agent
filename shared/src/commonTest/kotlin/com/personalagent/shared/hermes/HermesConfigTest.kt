package com.personalagent.shared.hermes

import com.personalagent.shared.store.InMemoryKeyValueStorage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class HermesConfigTest {

    @Test
    fun normalize_adds_scheme_and_strips_v1_and_slashes() {
        assertEquals("http://host:8642", HermesConfig.normalizeBaseUrl("host:8642"))
        assertEquals("http://host:8642", HermesConfig.normalizeBaseUrl("http://host:8642/"))
        assertEquals("http://host:8642", HermesConfig.normalizeBaseUrl("http://host:8642/v1"))
        assertEquals("http://host:8642", HermesConfig.normalizeBaseUrl("http://host:8642/v1/"))
        assertEquals("https://h.example.com", HermesConfig.normalizeBaseUrl("  https://h.example.com/V1  "))
        assertNull(HermesConfig.normalizeBaseUrl("   "))
    }

    @Test
    fun session_key_is_scoped_and_bounded() {
        val k = HermesConfig.newSessionKey()
        assertTrue(k.startsWith("lifeagent:user-"))
        assertTrue(k.length <= 256)
        assertFalse(k.any { it == '\n' || it == '\r' })
        // Two mints differ.
        assertTrue(k != HermesConfig.newSessionKey())
    }

    @Test
    fun plaintext_remote_detection() {
        assertFalse(HermesConfig("http://127.0.0.1:8642", "k", "s").isPlaintextRemote)
        assertFalse(HermesConfig("http://192.168.1.5:8642", "k", "s").isPlaintextRemote)
        assertFalse(HermesConfig("http://10.0.2.2:8642", "k", "s").isPlaintextRemote)
        assertFalse(HermesConfig("https://example.com", "k", "s").isPlaintextRemote)
        assertTrue(HermesConfig("http://example.com:8642", "k", "s").isPlaintextRemote)
    }

    @Test
    fun endpoints_are_built_from_root() {
        val c = HermesConfig("http://host:8642", "k", "s")
        assertEquals("http://host:8642/health", c.health)
        assertEquals("http://host:8642/v1/chat/completions", c.chatCompletions)
        assertEquals("http://host:8642/api/jobs", c.jobs)
        assertEquals("http://host:8642/api/jobs/abc", c.job("abc"))
    }

    @Test
    fun store_persists_and_reuses_session_key() {
        val storage = InMemoryKeyValueStorage()
        val store = HermesConfigStore(storage)
        assertFalse(store.isConfigured())

        val cfg = store.save("http://host:8642", "secret-key")
        assertTrue(store.isConfigured())
        assertEquals("http://host:8642", cfg.baseUrl)
        assertTrue(cfg.sessionKey.startsWith("lifeagent:user-"))

        // Reload keeps the SAME session key (memory continuity).
        val reloaded = store.load()
        assertNotNull(reloaded)
        assertEquals(cfg.sessionKey, reloaded.sessionKey)

        // Disconnect (default) clears creds but keeps the memory scope.
        store.disconnect()
        assertFalse(store.isConfigured())
        assertEquals(cfg.sessionKey, store.sessionKey())

        // Full reset forgets the scope too.
        store.disconnect(forgetMemoryScope = true)
        assertTrue(store.sessionKey() != cfg.sessionKey)
    }
}
