package com.personalagent.shared.cloud

import com.personalagent.shared.crypto.AeadSecretKeyProvider
import com.personalagent.shared.crypto.EncryptedKeyValueStorage
import com.personalagent.shared.crypto.JvmAead
import com.personalagent.shared.store.InMemoryKeyValueStorage
import com.personalagent.shared.store.KeyValueStorage
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Regression test for the device bug: "saved my Anthropic API key, restarted the
 * app, and it was gone."
 *
 * It reproduces the production path: a [CloudKeyStore] over an
 * [EncryptedKeyValueStorage] over a PERSISTENT backing, sealed by a STABLE data
 * key (which on Android is the AndroidKeyStore key that survives restarts —
 * simulated here by reusing the same key bytes). The save must survive a brand-new
 * store instance over the same backing (i.e. a fresh process) and still DECRYPT.
 */
class CloudKeyStorePersistenceTest {

    // Persistent backing shared across "process restarts" (fresh wrapper instances).
    private val backing: KeyValueStorage = InMemoryKeyValueStorage()
    // A STABLE 32-byte data key — stands in for the Android Keystore key that
    // persists across restarts (a fresh provider with the same bytes decrypts).
    private val keyBytes = ByteArray(32) { (it * 7 + 1).toByte() }

    /** A fresh encrypted store + CloudKeyStore over the SAME backing + SAME key —
     *  exactly what the app builds on a new process launch. */
    private fun freshKeyStore(): CloudKeyStore =
        CloudKeyStore(EncryptedKeyValueStorage(backing, AeadSecretKeyProvider(keyBytes, JvmAead())))

    @Test
    fun key_persists_and_decrypts_across_a_fresh_store_instance() {
        val secret = "sk-ant-test-abc123-DO-NOT-LOG"
        // Save through one instance...
        freshKeyStore().apply {
            setApiKey(CloudProvider.ANTHROPIC, secret)
            setActiveProvider(CloudProvider.ANTHROPIC)
        }
        // ...read through a BRAND-NEW instance over the same backing (new process).
        val reopened = freshKeyStore()
        assertEquals(secret, reopened.apiKey(CloudProvider.ANTHROPIC), "key must survive restart")
        assertTrue(reopened.hasKey(CloudProvider.ANTHROPIC))
        assertEquals(CloudProvider.ANTHROPIC, reopened.activeProvider())

        // And the active cloud client builds from the persisted key (cloud available
        // immediately, with no restart — mirrors DynamicCloudClient at use time).
        val client = reopened.activeCloudClient(
            engineFactory = { MockEngine { respond("ok", HttpStatusCode.OK) } },
        )
        assertNotNull(client, "a cloud client should build from the persisted key")
    }

    @Test
    fun stored_bytes_are_ciphertext_not_the_plaintext_key() {
        val secret = "sk-openai-zzz-secret"
        freshKeyStore().setApiKey(CloudProvider.OPENAI, secret)
        val raw = backing.get("cloud.apiKey.OPENAI")
        assertNotNull(raw)
        assertFalse(raw!!.contains(secret), "stored value must be ciphertext, not the plaintext key")
    }

    @Test
    fun clearing_a_key_persists_across_restart() {
        freshKeyStore().setApiKey(CloudProvider.ANTHROPIC, "sk-temp")
        freshKeyStore().clearApiKey(CloudProvider.ANTHROPIC)
        assertNull(freshKeyStore().apiKey(CloudProvider.ANTHROPIC))
        assertFalse(freshKeyStore().hasKey(CloudProvider.ANTHROPIC))
    }

    @Test
    fun per_provider_keys_are_isolated_across_restart() {
        freshKeyStore().setApiKey(CloudProvider.ANTHROPIC, "sk-ant-1")
        freshKeyStore().setApiKey(CloudProvider.OPENAI, "sk-oai-2")
        val r = freshKeyStore()
        assertEquals("sk-ant-1", r.apiKey(CloudProvider.ANTHROPIC))
        assertEquals("sk-oai-2", r.apiKey(CloudProvider.OPENAI))
    }
}
