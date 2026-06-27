package com.personalagent.shared.crypto

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** AEAD round-trip + negative tests over the vetted JVM AES-256-GCM and the provider. */
class AeadAndProviderTest {

    private val aead = JvmAead()
    private fun key() = JvmSecureRandom().nextBytes(32)

    @Test
    fun encrypt_then_decrypt_roundtrips() {
        val k = key()
        val msg = "hello, encrypted world".encodeToByteArray()
        val sealed = aead.seal(k, msg)
        assertContentEquals(msg, aead.open(k, sealed))
    }

    @Test
    fun roundtrips_with_aad() {
        val k = key()
        val msg = "bound".encodeToByteArray()
        val aad = "context-key".encodeToByteArray()
        val sealed = aead.seal(k, msg, aad)
        assertContentEquals(msg, aead.open(k, sealed, aad))
    }

    @Test
    fun wrong_aad_fails() {
        val k = key()
        val sealed = aead.seal(k, "x".encodeToByteArray(), "aad-1".encodeToByteArray())
        assertFailsWith<Throwable> { aead.open(k, sealed, "aad-2".encodeToByteArray()) }
    }

    @Test
    fun wrong_key_fails() {
        val sealed = aead.seal(key(), "secret".encodeToByteArray())
        assertFailsWith<Throwable> { aead.open(key(), sealed) }
    }

    @Test
    fun tampered_ciphertext_fails() {
        val k = key()
        val sealed = aead.seal(k, "secret".encodeToByteArray())
        sealed[sealed.size - 1] = (sealed[sealed.size - 1].toInt() xor 0x01).toByte()
        assertFailsWith<Throwable> { aead.open(k, sealed) }
    }

    @Test
    fun fresh_nonce_makes_ciphertext_nondeterministic() {
        val k = key()
        val msg = "same".encodeToByteArray()
        val a = aead.seal(k, msg)
        val b = aead.seal(k, msg)
        assertFalse(a.contentEquals(b), "GCM must use a fresh nonce per call")
    }

    @Test
    fun aead_secret_key_provider_roundtrips_and_binds_aad() {
        val provider = AeadSecretKeyProvider(key(), aead)
        assertTrue(provider.hasKey())
        val sealed = provider.encrypt("v".encodeToByteArray(), "k".encodeToByteArray())
        assertContentEquals("v".encodeToByteArray(), provider.decrypt(sealed, "k".encodeToByteArray()))
        assertFailsWith<Throwable> { provider.decrypt(sealed, "other".encodeToByteArray()) }
    }

    @Test
    fun software_provider_lazily_creates_key() {
        val provider = SoftwareSecretKeyProvider()
        assertFalse(provider.hasKey())
        provider.ensureKey()
        assertTrue(provider.hasKey())
        val sealed = provider.encrypt("data".encodeToByteArray())
        assertContentEquals("data".encodeToByteArray(), provider.decrypt(sealed))
    }

    @Test
    fun base64_roundtrips_arbitrary_bytes() {
        for (len in 0..40) {
            val bytes = JvmSecureRandom().nextBytes(len)
            assertContentEquals(bytes, Base64.decode(Base64.encode(bytes)))
        }
    }
}
