package com.personalagent.shared.store

import com.personalagent.shared.crypto.SecretKeyProvider
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Device-free behaviour test for [EncryptedKeyValueStorage] using a fake
 * [SecretKeyProvider]. Verifies the envelope wiring (round-trip, ciphertext is
 * not plaintext, fresh-IV-per-write, and that the storage key is bound as AAD).
 * The real cipher (AndroidKeyStore) is exercised by the on-device test.
 */
class EncryptedKeyValueStorageTest {

    /**
     * Fake provider: frames `[counter | aadLen | aad | plaintext]`. The counter
     * simulates a fresh IV per call (so equal plaintext → different blobs); the
     * embedded AAD lets decrypt enforce that the same AAD is supplied back.
     */
    private class FakeSecretKeyProvider : SecretKeyProvider {
        private var created = false
        private var counter = 0
        override fun hasKey() = created
        override fun ensureKey() { created = true }

        override fun encrypt(plaintext: ByteArray, aad: ByteArray): ByteArray {
            check(created) { "ensureKey() not called" }
            val header = byteArrayOf(counter++.toByte(), aad.size.toByte())
            return header + aad + plaintext
        }

        override fun decrypt(ciphertext: ByteArray, aad: ByteArray): ByteArray {
            val aadLen = ciphertext[1].toInt()
            val storedAad = ciphertext.copyOfRange(2, 2 + aadLen)
            require(storedAad.contentEquals(aad)) { "AAD mismatch" }
            return ciphertext.copyOfRange(2 + aadLen, ciphertext.size)
        }
    }

    private fun newStore(delegate: KeyValueStorage = InMemoryKeyValueStorage()) =
        EncryptedKeyValueStorage(delegate, FakeSecretKeyProvider())

    @Test
    fun put_then_get_round_trips() {
        val store = newStore()
        store.put("notes", "hello world")
        assertEquals("hello world", store.get("notes"))
        assertNull(store.get("missing"))
    }

    @Test
    fun stored_value_is_not_plaintext() {
        val delegate = InMemoryKeyValueStorage()
        val store = EncryptedKeyValueStorage(delegate, FakeSecretKeyProvider())
        store.put("k", "super secret")
        val raw = delegate.get("k")!!
        assertTrue("plaintext leaked into delegate") { !raw.contains("super secret") }
    }

    @Test
    fun rewriting_same_value_produces_different_ciphertext() {
        val delegate = InMemoryKeyValueStorage()
        val store = EncryptedKeyValueStorage(delegate, FakeSecretKeyProvider())
        store.put("k", "same")
        val first = delegate.get("k")
        store.put("k", "same")
        val second = delegate.get("k")
        assertTrue("fresh IV should change ciphertext") { first != second }
    }

    @Test
    fun ciphertext_is_bound_to_its_key_via_aad() {
        // Move a value stored under "a" to key "b" in the raw delegate: decrypt
        // under "b" must fail because the AAD (the key) no longer matches.
        val delegate = InMemoryKeyValueStorage()
        val store = EncryptedKeyValueStorage(delegate, FakeSecretKeyProvider())
        store.put("a", "value")
        delegate.put("b", delegate.get("a")!!)
        assertFailsWith<IllegalArgumentException> { store.get("b") }
    }

    @Test
    fun keys_and_remove_pass_through() {
        val store = newStore()
        store.put("a", "1")
        store.put("b", "2")
        assertEquals(setOf("a", "b"), store.keys())
        store.remove("a")
        assertEquals(setOf("b"), store.keys())
    }
}
