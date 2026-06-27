// 🔒 SECURITY-CRITICAL (Step 5) — pending human security review; do NOT ship to a
// real user until reviewed. Uses vetted standard crypto.
package com.personalagent.shared.crypto

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * ============================================================================
 *  RECOVERY — DUAL-WRAP ENVELOPE ENCRYPTION
 * ============================================================================
 *
 * The local wallet is encrypted with a single random **data-encryption key (DEK)**.
 * The DEK itself is never persisted in the clear. Instead it is **wrapped (encrypted)
 * twice**, and only the two wraps are stored on the device:
 *
 *   1. **Hardware wrap** — `hardwareKey.encrypt(DEK)`. The key-encryption key lives
 *      in the platform Keystore / Secure Enclave and never leaves hardware. This is
 *      the normal unlock path (device present, user authenticated).
 *
 *   2. **Recovery wrap** — `AES-GCM(KDF(recoveryCode, salt), DEK)`. The
 *      key-encryption key is derived on demand from a **high-entropy recovery code
 *      that only the user holds**. This is the recovery path (new device, hardware
 *      key lost/wiped).
 *
 *  ┌────────────┐        wrap under hardware KEK         ┌──────────────────┐
 *  │            │ ─────────────────────────────────────▶│  hardwareWrap     │
 *  │    DEK     │                                        ├──────────────────┤  stored
 *  │  (random)  │ ─────────────────────────────────────▶│  recoveryWrap     │  on device
 *  └────────────┘        wrap under KDF(recoveryCode)    │  + salt + KdfParams
 *                                                        └──────────────────┘
 *
 * 🔒 INVARIANT — NO COMPANY-SIDE KEY PATH (the whole point):
 *   The data is recoverable with the **device hardware key OR the user's recovery
 *   code — and NOTHING ELSE.** The company / server:
 *     - never sees or stores the hardware key (it is sealed in Keystore/Enclave);
 *     - never sees, stores, transmits, escrows, or can regenerate the recovery code
 *       (it is generated on-device from a CSPRNG, shown to the user once, and the
 *       only thing persisted is the *wrap* it produces — not the code);
 *     - therefore has **no path** to the DEK and cannot decrypt the user's data, and
 *       cannot reset/recover access on the user's behalf.
 *   If the user loses BOTH the device key AND their recovery code, the data is
 *   **unrecoverable by design**. This is a deliberate, documented trade-off — do not
 *   "fix" it by adding a server-held key or a derivable code; that would silently
 *   destroy the guarantee.
 *
 * VERIFY DURING REVIEW: confirm the recovery code (and the KDF-derived key) is never
 * serialized into [WrappedDataKey], never logged, and never sent to any CloudClient
 * or persisted; only [WrappedDataKey] (the wraps + salt + non-secret params) is stored.
 */

/**
 * The on-device, persistable envelope: the DEK wrapped under both KEKs, plus the
 * non-secret salt and KDF parameters needed to redo the recovery derivation.
 *
 * Contains **no plaintext key material**: `hardwareWrap` and `recoveryWrap` are
 * ciphertexts, `recoverySalt` is a public salt, and [kdf] holds only cost
 * parameters. Safe to store next to the encrypted data (it is useless without the
 * hardware key or the recovery code). It is **NOT** safe to call this "the key".
 */
@Serializable
data class WrappedDataKey(
    val version: Int = 1,
    /** Base64 of `hardwareKey.encrypt(DEK)` — AEAD with nonce prepended. */
    val hardwareWrap: String,
    /** Base64 of `AES-GCM(KDF(recoveryCode, salt), DEK)` — AEAD with nonce prepended. */
    val recoveryWrap: String,
    /** Base64 of the random per-wallet KDF salt (public). */
    val recoverySalt: String,
    /** Self-describing KDF cost parameters used for the recovery wrap. */
    val kdf: KdfParams = KdfParams.Pbkdf2Default,
) {
    fun toJson(): String = Json.encodeToString(serializer(), this)

    companion object {
        fun fromJson(json: String): WrappedDataKey = Json.decodeFromString(serializer(), json)
    }
}

/** Thrown when an unwrap fails (wrong recovery code, tampered blob, or wrong key). */
class RecoveryException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * Generates the user-held recovery code, derives the recovery KEK, and performs the
 * dual-wrap / unwrap of the DEK. Holds no long-lived secret state itself.
 *
 * @param aead vetted AES-256-GCM used for the recovery wrap (and for the DEK→data
 *   provider via [AeadSecretKeyProvider]).
 * @param kdf vetted slow KDF turning the recovery code into a 256-bit KEK.
 * @param random CSPRNG used for the DEK, the KDF salt, and the recovery code.
 * @param kdfParams cost parameters for new wraps (existing wraps carry their own).
 */
class RecoveryManager(
    private val aead: Aead,
    private val kdf: Kdf,
    private val random: SecureRandomBytes,
    private val kdfParams: KdfParams = KdfParams.Pbkdf2Default,
) {
    /** Fresh random 256-bit data-encryption key. Caller wraps it immediately and keeps it only in memory. */
    fun generateDataKey(): ByteArray = random.nextBytes(32)

    /**
     * Generate a **high-entropy, user-held recovery code**.
     *
     * Format: [RecoveryCode.ENTROPY_BITS]-bit CSPRNG output, RFC 4648 Base32 (A–Z,
     * 2–7 — no padding, case-insensitive on entry), grouped in 4-char blocks joined
     * by '-' for legibility, e.g. `K3M9-7QXZ-...`. There is **no checksum and no
     * structure to guess**: every code is uniformly random. The company cannot
     * regenerate it — losing it forfeits the recovery path.
     */
    fun generateRecoveryCode(): String = RecoveryCode.generate(random)

    /**
     * Dual-wrap [dataKey] under the [hardwareKey] (normal unlock) and the
     * [recoveryCode] (recovery path). Returns the persistable [WrappedDataKey].
     *
     * [recoveryCode] is normalized ([RecoveryCode.normalize]) so the user may type it
     * with any casing/spacing/dashes. The code is used transiently to derive the KEK
     * and is **not** stored anywhere in the result.
     */
    fun wrap(dataKey: ByteArray, hardwareKey: SecretKeyProvider, recoveryCode: String): WrappedDataKey {
        require(dataKey.size == 32) { "DEK must be 32 bytes (AES-256)" }
        hardwareKey.ensureKey()
        val hardwareWrap = hardwareKey.encrypt(dataKey)

        val salt = random.nextBytes(16)
        val kek = deriveRecoveryKek(recoveryCode, salt, kdfParams)
        val recoveryWrap = try {
            aead.seal(kek, dataKey)
        } finally {
            kek.fill(0)
        }
        return WrappedDataKey(
            hardwareWrap = Base64.encode(hardwareWrap),
            recoveryWrap = Base64.encode(recoveryWrap),
            recoverySalt = Base64.encode(salt),
            kdf = kdfParams,
        )
    }

    /** Normal unlock: recover the DEK using the hardware key. Throws [RecoveryException] on failure. */
    fun unwrapWithHardware(wrapped: WrappedDataKey, hardwareKey: SecretKeyProvider): ByteArray =
        try {
            hardwareKey.decrypt(Base64.decode(wrapped.hardwareWrap))
        } catch (t: Throwable) {
            throw RecoveryException("Hardware unwrap failed (key absent, rotated, or blob tampered)", t)
        }

    /**
     * Recovery unlock: recover the DEK using the user's [recoveryCode]. Throws
     * [RecoveryException] for a wrong code or tampered blob — GCM authentication
     * makes a wrong code indistinguishable from a tamper (both fail the tag), so no
     * oracle leaks whether the code was "close".
     */
    fun unwrapWithRecoveryCode(wrapped: WrappedDataKey, recoveryCode: String): ByteArray {
        val salt = Base64.decode(wrapped.recoverySalt)
        val kek = deriveRecoveryKek(recoveryCode, salt, wrapped.kdf)
        return try {
            aead.open(kek, Base64.decode(wrapped.recoveryWrap))
        } catch (t: Throwable) {
            throw RecoveryException("Recovery unwrap failed (wrong recovery code or tampered blob)", t)
        } finally {
            kek.fill(0)
        }
    }

    /**
     * Rotate the recovery code: unwrap with the OLD code, re-wrap under a NEW code
     * (fresh salt) while keeping the same hardware wrap semantics. The DEK is
     * unchanged so existing ciphertext stays valid. Returns the new blob and code.
     */
    fun rotateRecoveryCode(
        wrapped: WrappedDataKey,
        oldRecoveryCode: String,
        hardwareKey: SecretKeyProvider,
        newRecoveryCode: String = generateRecoveryCode(),
    ): Pair<WrappedDataKey, String> {
        val dek = unwrapWithRecoveryCode(wrapped, oldRecoveryCode)
        return try {
            wrap(dek, hardwareKey, newRecoveryCode) to newRecoveryCode
        } finally {
            dek.fill(0)
        }
    }

    private fun deriveRecoveryKek(recoveryCode: String, salt: ByteArray, params: KdfParams): ByteArray {
        val normalized = RecoveryCode.normalize(recoveryCode)
        require(normalized.isNotEmpty()) { "Recovery code is empty after normalization" }
        val chars = normalized.toCharArray()
        return try {
            kdf.deriveKey(chars, salt, params)
        } finally {
            chars.fill(' ')
        }
    }
}
