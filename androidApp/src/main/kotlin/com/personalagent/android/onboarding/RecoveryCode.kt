package com.personalagent.android.onboarding

import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/**
 * 🔒 SECURITY-CRITICAL (Step 5) — user-held recovery code. 🔒
 *
 * Generates the high-entropy, user-held recovery code shown once at first-run
 * setup, and a salted PBKDF2 *verifier* so the app can later confirm the user
 * re-entered the same code without storing the code itself.
 *
 * 🔒 IMPORTANT SCOPE NOTE (read before assuming this "recovers" data):
 *   The at-rest key is the device-bound AndroidKeyStore key
 *   ([com.personalagent.shared.crypto.AndroidSecretKeyProvider]); it is
 *   non-exportable and cannot leave the device. This recovery code is the
 *   user-held secret captured at onboarding and the verifier proves possession,
 *   but the cryptographic escrow that would let this code re-derive access on a
 *   *new* device (recovery-code-derived key wrapping an exported data key, backed
 *   up off device) is NOT implemented in this subtask. Hence the setup warning is
 *   literally true today: lose the device/key and the data is unrecoverable.
 *   Wiring the escrow is the documented Gate-1 follow-up. NOT-FOR-REAL-USERS.
 */
@OptIn(ExperimentalEncodingApi::class)
object RecoveryCode {

    /** Crockford Base32 alphabet (no I/L/O/U — unambiguous when written by hand). */
    private const val ALPHABET = "0123456789ABCDEFGHJKMNPQRSTVWXYZ"
    private const val GROUPS = 4
    private const val CHARS_PER_GROUP = 5            // 20 chars * 5 bits ≈ 100 bits entropy
    private const val PBKDF2_ITERATIONS = 150_000
    private const val PBKDF2_KEY_BITS = 256
    private const val SALT_BYTES = 16

    private val random = SecureRandom()

    /**
     * A freshly generated code, e.g. `K7P2Q-9XRTV-3M8WH-ZB4NC`. Hyphens are
     * display-only; [normalize] strips them before verification.
     */
    fun generate(): String {
        val sb = StringBuilder()
        repeat(GROUPS) { g ->
            if (g > 0) sb.append('-')
            repeat(CHARS_PER_GROUP) {
                sb.append(ALPHABET[random.nextInt(ALPHABET.length)])
            }
        }
        return sb.toString()
    }

    /** Upper-cases and removes separators/whitespace so display formatting never affects matching. */
    fun normalize(code: String): String =
        code.uppercase().filter { it != '-' && !it.isWhitespace() }

    /**
     * Salted PBKDF2-HMAC-SHA256 verifier for [code], serialized as
     * `pbkdf2$<iterations>$<base64(salt)>$<base64(hash)>`. Store this, never the code.
     */
    fun makeVerifier(code: String): String {
        val salt = ByteArray(SALT_BYTES).also { random.nextBytes(it) }
        val hash = pbkdf2(normalize(code), salt, PBKDF2_ITERATIONS)
        return "pbkdf2\$$PBKDF2_ITERATIONS\$${Base64.encode(salt)}\$${Base64.encode(hash)}"
    }

    /** Constant-time check of [code] against a [verifier] produced by [makeVerifier]. */
    fun verify(code: String, verifier: String): Boolean {
        val parts = verifier.split('$')
        if (parts.size != 4 || parts[0] != "pbkdf2") return false
        val iterations = parts[1].toIntOrNull() ?: return false
        val salt = runCatching { Base64.decode(parts[2]) }.getOrNull() ?: return false
        val expected = runCatching { Base64.decode(parts[3]) }.getOrNull() ?: return false
        val actual = pbkdf2(normalize(code), salt, iterations)
        return constantTimeEquals(expected, actual)
    }

    private fun pbkdf2(code: String, salt: ByteArray, iterations: Int): ByteArray {
        val spec = PBEKeySpec(code.toCharArray(), salt, iterations, PBKDF2_KEY_BITS)
        try {
            return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
                .generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
    }

    private fun constantTimeEquals(a: ByteArray, b: ByteArray): Boolean {
        if (a.size != b.size) return false
        var diff = 0
        for (i in a.indices) diff = diff or (a[i].toInt() xor b[i].toInt())
        return diff == 0
    }
}
