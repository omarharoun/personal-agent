// 🔒 SECURITY-CRITICAL (Step 5) — TEST stand-in for the hardware provider; pending
// human security review. Uses vetted standard crypto (javax.crypto). NOT for real users.
package com.personalagent.shared.crypto

import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Software, in-memory [SecretKeyProvider] standing in for the hardware-backed
 * providers (Android Keystore / iOS Secure Enclave) so the encryption + recovery
 * layer is unit-testable without a device.
 *
 * It mimics the hardware contract: an AES-256 key created lazily by [ensureKey] and
 * held only in process memory (the stand-in for "in hardware"), AES-256-GCM with a
 * fresh random 12-byte nonce prepended, and AAD support. The crucial difference from
 * a real provider — and why this is TEST-ONLY — is that here the key bytes live in
 * the heap and never get hardware isolation/attestation.
 *
 * @param fixedKey optionally inject a known key so a "wrong key" test can construct a
 *   second provider with a different key.
 */
class SoftwareSecretKeyProvider(
    fixedKey: ByteArray? = null,
    private val random: SecureRandom = SecureRandom(),
) : SecretKeyProvider {

    private var key: ByteArray? = fixedKey?.copyOf()

    override fun hasKey(): Boolean = key != null

    override fun ensureKey() {
        if (key == null) key = ByteArray(32).also { random.nextBytes(it) }
    }

    override fun encrypt(plaintext: ByteArray, aad: ByteArray): ByteArray {
        ensureKey()
        val nonce = ByteArray(12).also { random.nextBytes(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, nonce))
        if (aad.isNotEmpty()) cipher.updateAAD(aad)
        return nonce + cipher.doFinal(plaintext)
    }

    override fun decrypt(ciphertext: ByteArray, aad: ByteArray): ByteArray {
        val k = key ?: error("no key")
        require(ciphertext.size >= 12 + 16) { "ciphertext too short" }
        val nonce = ciphertext.copyOfRange(0, 12)
        val body = ciphertext.copyOfRange(12, ciphertext.size)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(k, "AES"), GCMParameterSpec(128, nonce))
        if (aad.isNotEmpty()) cipher.updateAAD(aad)
        return cipher.doFinal(body)
    }
}
