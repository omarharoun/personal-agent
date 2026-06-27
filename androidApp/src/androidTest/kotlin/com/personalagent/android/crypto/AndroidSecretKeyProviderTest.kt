package com.personalagent.android.crypto

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.personalagent.shared.crypto.AndroidSecretKeyProvider
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.security.KeyStore

/**
 * 🔒 On-device test for [AndroidSecretKeyProvider]. Runs only on a real
 * device/emulator (it exercises the AndroidKeyStore + hardware-backed Cipher).
 * It **self-skips** when the `AndroidKeyStore` provider is unavailable, so it
 * never fails in a plain JVM environment.
 *
 * NOTE: hardware backing and StrongBox can ONLY be confirmed on real hardware;
 * the round-trip below is correctness, not a hardware attestation.
 */
@RunWith(AndroidJUnit4::class)
class AndroidSecretKeyProviderTest {

    private val alias = "test_key_${System.nanoTime()}"
    private lateinit var provider: AndroidSecretKeyProvider

    @Before
    fun setUp() {
        assumeTrue(
            "AndroidKeyStore not available — skipping (not on a device/emulator)",
            keystoreAvailable(),
        )
        provider = AndroidSecretKeyProvider(alias)
    }

    @Test
    fun ensureKey_is_idempotent_and_creates_the_key() {
        assertFalse(provider.hasKey())
        provider.ensureKey()
        assertTrue(provider.hasKey())
        provider.ensureKey() // second call must not throw or replace
        assertTrue(provider.hasKey())
    }

    @Test
    fun encrypt_then_decrypt_round_trips() {
        provider.ensureKey()
        val plaintext = "the quick brown fox éü—🔒".encodeToByteArray()
        val blob = provider.encrypt(plaintext)
        assertArrayEquals(plaintext, provider.decrypt(blob))
    }

    @Test
    fun each_encrypt_uses_a_fresh_iv_so_ciphertext_differs() {
        provider.ensureKey()
        val plaintext = "same input".encodeToByteArray()
        val a = provider.encrypt(plaintext)
        val b = provider.encrypt(plaintext)
        // Same plaintext, different blobs (fresh random IV prepended each call).
        assertFalse(a.contentEquals(b))
        // Both still decrypt back to the original.
        assertArrayEquals(plaintext, provider.decrypt(a))
        assertArrayEquals(plaintext, provider.decrypt(b))
    }

    @Test
    fun aad_must_match_or_decrypt_fails() {
        provider.ensureKey()
        val plaintext = "bound to its key".encodeToByteArray()
        val blob = provider.encrypt(plaintext, aad = "key-A".encodeToByteArray())
        assertArrayEquals(plaintext, provider.decrypt(blob, aad = "key-A".encodeToByteArray()))
        try {
            provider.decrypt(blob, aad = "key-B".encodeToByteArray())
            fail("decrypt with wrong AAD must throw (GCM tag mismatch)")
        } catch (expected: Exception) {
            // AEADBadTagException / GeneralSecurityException — tampering/wrong AAD rejected.
        }
    }

    @Test
    fun tampered_ciphertext_is_rejected() {
        provider.ensureKey()
        val blob = provider.encrypt("integrity".encodeToByteArray())
        blob[blob.size - 1] = (blob[blob.size - 1].toInt() xor 0x01).toByte()
        try {
            provider.decrypt(blob)
            fail("tampered ciphertext must throw (GCM tag mismatch)")
        } catch (expected: Exception) {
            // expected
        }
    }

    private fun keystoreAvailable(): Boolean = try {
        KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        true
    } catch (e: Exception) {
        false
    }
}
