// 🔒 SECURITY-CRITICAL (Step 5) — pending human security review; do NOT ship to a
// real user until reviewed. Uses vetted standard crypto.
package com.personalagent.shared.crypto

import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Vetted JVM crypto: AES-256-GCM ([JvmAead]), PBKDF2-HMAC-SHA256 ([JvmKdf]), and a
 * CSPRNG ([JvmSecureRandom]) — all from `javax.crypto` / `java.security`, no
 * hand-rolled primitives. Used by the JVM/desktop build and by the shared unit
 * tests so the encryption + recovery layer is fully exercisable without hardware.
 *
 * NOTE: these are *software* keys (the AES key is an in-memory `byte[]`). True
 * hardware key isolation is provided only by the platform [SecretKeyProvider]
 * implementations (Android Keystore / iOS Secure Enclave) and is verifiable only
 * on-device — part of the pending Gate-1 human review.
 */

private const val GCM_NONCE_BYTES = 12
private const val GCM_TAG_BITS = 128

/** AES-256-GCM, `nonce(12) || ciphertext || tag(16)`, fresh random nonce per [seal]. */
class JvmAead(
    private val random: SecureRandom = SecureRandom(),
) : Aead {

    override fun seal(key: ByteArray, plaintext: ByteArray, aad: ByteArray): ByteArray {
        require(key.size == 32) { "AES-256 key must be 32 bytes, was ${key.size}" }
        val nonce = ByteArray(GCM_NONCE_BYTES).also { random.nextBytes(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(GCM_TAG_BITS, nonce))
        if (aad.isNotEmpty()) cipher.updateAAD(aad)
        val body = cipher.doFinal(plaintext)
        return nonce + body
    }

    override fun open(key: ByteArray, sealed: ByteArray, aad: ByteArray): ByteArray {
        require(key.size == 32) { "AES-256 key must be 32 bytes, was ${key.size}" }
        require(sealed.size >= GCM_NONCE_BYTES + GCM_TAG_BITS / 8) { "ciphertext too short" }
        val nonce = sealed.copyOfRange(0, GCM_NONCE_BYTES)
        val body = sealed.copyOfRange(GCM_NONCE_BYTES, sealed.size)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(GCM_TAG_BITS, nonce))
        if (aad.isNotEmpty()) cipher.updateAAD(aad)
        return cipher.doFinal(body) // throws AEADBadTagException on tamper / wrong key / wrong aad
    }
}

/** PBKDF2-HMAC-SHA256 KDF. Honors [KdfParams.algorithm]; refuses anything else. */
class JvmKdf : Kdf {
    override fun deriveKey(passphrase: CharArray, salt: ByteArray, params: KdfParams): ByteArray {
        require(params.algorithm == KdfParams.PBKDF2_HMAC_SHA256) {
            "JvmKdf only provides ${KdfParams.PBKDF2_HMAC_SHA256}; blob requests '${params.algorithm}'"
        }
        val spec = PBEKeySpec(passphrase, salt, params.iterations, params.keyLengthBits)
        try {
            return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
    }
}

/** CSPRNG backed by `java.security.SecureRandom`. */
class JvmSecureRandom(
    private val random: SecureRandom = SecureRandom(),
) : SecureRandomBytes {
    override fun nextBytes(size: Int): ByteArray = ByteArray(size).also { random.nextBytes(it) }
}
