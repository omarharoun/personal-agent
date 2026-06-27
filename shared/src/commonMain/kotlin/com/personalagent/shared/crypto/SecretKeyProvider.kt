// 🔒 SECURITY-CRITICAL (Step 5) — pending human security review; do NOT ship to a
// real user until reviewed. Uses vetted standard crypto.
package com.personalagent.shared.crypto

/**
 * Hardware-backed AEAD provider. The platform Keystore (Android) / Secure Enclave
 * (iOS) holds the actual key material; **the key never leaves hardware** — callers
 * hand plaintext in and get ciphertext out, but can never read the key bytes.
 *
 * The byte layout of [encrypt]/[decrypt] is **AES-GCM with the 12-byte nonce
 * prepended** to the ciphertext+tag:  `nonce(12) || ciphertext || tag(16)`.
 * Each [encrypt] call MUST use a fresh random nonce (GCM nonce reuse under one key
 * is catastrophic — it breaks confidentiality and authentication).
 *
 * 🤝 SHARED CONTRACT — the production platform implementations are provided by the
 * sibling slices:
 *   - Android: `AndroidKeystoreSecretKeyProvider` (`feat/step5-android`)
 *   - iOS:     `SecureEnclaveSecretKeyProvider`   (`feat/step5-ios`)
 * This slice ships the testable core ([EncryptedKeyValueStorage], [RecoveryManager],
 * [AeadSecretKeyProvider]) plus a software stand-in in test source so the whole
 * layer is unit-testable without a device.
 *
 * ⚠️ HONESTY: real hardware-backed key isolation is only verifiable **on-device**
 * (Keystore/Secure Enclave attestation). This common layer cannot prove it; that is
 * part of the pending human security review (see docs/SECURITY_REVIEW.md Gate 1).
 */
interface SecretKeyProvider {
    /** True if the data-encryption key already exists in hardware. */
    fun hasKey(): Boolean

    /** Create the data-encryption key in hardware if absent. Idempotent. */
    fun ensureKey()

    /**
     * AES-GCM seal [plaintext] under the hardware key, binding [aad] (additional
     * authenticated data — authenticated but not encrypted). Returns
     * `nonce(12) || ciphertext || tag(16)` with a fresh random nonce each call.
     */
    fun encrypt(plaintext: ByteArray, aad: ByteArray = ByteArray(0)): ByteArray

    /**
     * AES-GCM open a [ciphertext] produced by [encrypt], verifying [aad]. Throws if
     * the tag fails (tampering, wrong key, or wrong/over-short input). The same
     * [aad] supplied to [encrypt] MUST be supplied here or authentication fails.
     */
    fun decrypt(ciphertext: ByteArray, aad: ByteArray = ByteArray(0)): ByteArray
}

/**
 * A [SecretKeyProvider] backed by an in-memory raw key and an injected [Aead].
 *
 * This is **not** hardware-backed — it is the production glue for the *envelope*
 * unlock path: once the data-encryption key (DEK) has been unwrapped (via the
 * hardware key OR the recovery code, see [RecoveryManager]), wrap the recovered
 * DEK bytes in one of these and hand it to [EncryptedKeyValueStorage]. The DEK
 * lives only in process memory while unlocked and is never persisted in the clear.
 *
 * @param key 32-byte (AES-256) data-encryption key. Defensively copied.
 */
class AeadSecretKeyProvider(
    key: ByteArray,
    private val aead: Aead,
) : SecretKeyProvider {
    private val key: ByteArray = key.copyOf()

    init {
        require(this.key.size == 32) { "DEK must be 32 bytes (AES-256), was ${this.key.size}" }
    }

    override fun hasKey(): Boolean = true
    override fun ensureKey() { /* key is supplied at construction */ }

    override fun encrypt(plaintext: ByteArray, aad: ByteArray): ByteArray =
        aead.seal(key, plaintext, aad)

    override fun decrypt(ciphertext: ByteArray, aad: ByteArray): ByteArray =
        aead.open(key, ciphertext, aad)
}
