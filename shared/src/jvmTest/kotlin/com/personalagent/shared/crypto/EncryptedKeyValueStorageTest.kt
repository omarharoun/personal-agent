package com.personalagent.shared.crypto

import com.personalagent.shared.store.InMemoryKeyValueStorage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** [EncryptedKeyValueStorage] behaviour: ciphertext at rest, correct round-trip, AAD binding. */
class EncryptedKeyValueStorageTest {

    private fun newStore(): Pair<EncryptedKeyValueStorage, InMemoryKeyValueStorage> {
        val delegate = InMemoryKeyValueStorage()
        val enc = EncryptedKeyValueStorage(delegate, SoftwareSecretKeyProvider())
        return enc to delegate
    }

    @Test
    fun put_then_get_roundtrips() {
        val (enc, _) = newStore()
        enc.put("note:1", "my private body")
        assertEquals("my private body", enc.get("note:1"))
    }

    @Test
    fun delegate_holds_ciphertext_never_plaintext() {
        val (enc, delegate) = newStore()
        val secret = "SUPER-SECRET-VALUE"
        enc.put("k", secret)
        val stored = delegate.get("k")
        assertNotNull(stored)
        assertTrue(stored.isNotEmpty())
        assertNotEquals(secret, stored)
        assertTrue(!stored.contains(secret), "plaintext must not appear in the delegate")
    }

    @Test
    fun missing_key_returns_null() {
        val (enc, _) = newStore()
        assertNull(enc.get("absent"))
    }

    @Test
    fun remove_deletes() {
        val (enc, delegate) = newStore()
        enc.put("k", "v")
        enc.remove("k")
        assertNull(enc.get("k"))
        assertNull(delegate.get("k"))
    }

    @Test
    fun same_value_encrypts_differently_each_write() {
        val (enc, delegate) = newStore()
        enc.put("a", "dup")
        val first = delegate.get("a")
        enc.put("a", "dup")
        val second = delegate.get("a")
        assertNotEquals(first, second, "fresh nonce per write must change ciphertext")
        assertEquals("dup", enc.get("a"))
    }

    @Test
    fun ciphertext_is_bound_to_its_key_swap_fails() {
        val delegate = InMemoryKeyValueStorage()
        val enc = EncryptedKeyValueStorage(delegate, SoftwareSecretKeyProvider())
        enc.put("key-a", "value-a")
        // Attacker copies key-a's ciphertext under key-b in the underlying store.
        delegate.put("key-b", delegate.get("key-a")!!)
        // Reading key-b must fail authentication: AAD = "key-b" != "key-a".
        assertFailsWith<Throwable> { enc.get("key-b") }
    }

    @Test
    fun tampered_stored_value_fails_to_read() {
        val (enc, delegate) = newStore()
        enc.put("k", "v")
        val sealed = Base64.decode(delegate.get("k")!!)
        sealed[sealed.size - 1] = (sealed[sealed.size - 1].toInt() xor 0x01).toByte()
        delegate.put("k", Base64.encode(sealed))
        assertFailsWith<Throwable> { enc.get("k") }
    }

    @Test
    fun storage_key_transform_makes_delegate_keys_opaque() {
        val delegate = InMemoryKeyValueStorage()
        // Opaque (hashed) storage keys; AAD binding still uses the logical key.
        val enc = EncryptedKeyValueStorage(delegate, SoftwareSecretKeyProvider()) { logical ->
            Base64.encode(JvmKdf().deriveKey(logical.toCharArray(), "fixed-salt".encodeToByteArray(), KdfParams(iterations = 1_000)))
        }
        enc.put("plain-key", "v")
        assertTrue(delegate.keys().none { it == "plain-key" }, "logical key must not appear in delegate")
        assertEquals("v", enc.get("plain-key"))
    }
}
