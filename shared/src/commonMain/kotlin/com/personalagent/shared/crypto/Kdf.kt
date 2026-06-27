// 🔒 SECURITY-CRITICAL (Step 5) — pending human security review; do NOT ship to a
// real user until reviewed. Uses vetted standard crypto.
package com.personalagent.shared.crypto

import kotlinx.serialization.Serializable

/**
 * Password-based key derivation. Implementations MUST use a **vetted, standard,
 * deliberately-slow** KDF (Argon2id / scrypt / PBKDF2) — never a plain hash — to
 * turn a user-held [RecoveryManager] recovery code into a key-encryption key (KEK).
 *
 * The JVM impl (`JvmKdf`, jvmMain) uses **PBKDF2-HMAC-SHA256** from `javax.crypto`,
 * which is FIPS-vetted and available with no extra dependency. PBKDF2 is the
 * conservative floor; Argon2id/scrypt are preferable where a vetted KMP/platform
 * impl is available, and a platform impl can be injected here without touching the
 * common layer. **KDF algorithm + parameters are part of the pending human review**
 * (see docs/SECURITY_REVIEW.md Gate 1).
 */
interface Kdf {
    /**
     * Derive a [params].keyLengthBits/8-byte key from [passphrase] and [salt] using
     * [params]. [passphrase] is a `CharArray` so the caller can zero it after use.
     * Implementations MUST honor the algorithm named in [params] (and fail loudly if
     * they cannot provide it) so a wrapped blob never silently decrypts under a
     * weaker KDF than it was created with.
     */
    fun deriveKey(passphrase: CharArray, salt: ByteArray, params: KdfParams): ByteArray
}

/**
 * Self-describing KDF parameters, persisted alongside the wrapped key so unwrap uses
 * exactly the same derivation. Stored in the on-device recovery blob only.
 *
 * @param algorithm vetted KDF id, e.g. `"PBKDF2-HMAC-SHA256"`, `"argon2id"`, `"scrypt"`.
 * @param iterations PBKDF2 iteration count (or Argon2 time-cost / scrypt N). Higher =
 *   slower = more brute-force resistant. PBKDF2-HMAC-SHA256 default targets the OWASP
 *   2023 guidance of 600,000.
 * @param keyLengthBits derived KEK length; 256 to match AES-256-GCM.
 * @param memoryKib memory cost for memory-hard KDFs (Argon2id/scrypt); ignored by PBKDF2.
 * @param parallelism lanes for Argon2id; ignored by PBKDF2.
 */
@Serializable
data class KdfParams(
    val algorithm: String = PBKDF2_HMAC_SHA256,
    val iterations: Int = 600_000,
    val keyLengthBits: Int = 256,
    val memoryKib: Int = 0,
    val parallelism: Int = 1,
) {
    init {
        require(iterations > 0) { "iterations must be > 0" }
        require(keyLengthBits % 8 == 0 && keyLengthBits >= 128) { "keyLengthBits must be a byte multiple >= 128" }
    }

    companion object {
        const val PBKDF2_HMAC_SHA256 = "PBKDF2-HMAC-SHA256"

        /** OWASP-2023-aligned PBKDF2 default (used in production). */
        val Pbkdf2Default = KdfParams()
    }
}
