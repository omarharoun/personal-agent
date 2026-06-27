package com.personalagent.shared.crypto

/**
 * 🔒 SECURITY-CRITICAL (Step 5 — encryption at rest). 🔒
 *
 * The single, narrow seam between "we need authenticated encryption of some
 * bytes" and "where the key lives / what crypto primitive does the work".
 *
 * Everything above this interface (e.g. [com.personalagent.shared.store.EncryptedKeyValueStorage])
 * is key-management-agnostic: it hands plaintext + AAD in and gets ciphertext
 * out, never seeing key material. Each platform supplies an implementation whose
 * key is generated on-device, stored in the platform secure store, and **never
 * exported off the device**:
 *   - Android: Android Keystore / StrongBox-backed AES key  (sibling subtask)
 *   - iOS:     Keychain + Secure Enclave (this subtask — see iosMain
 *              `IosSecretKeyProvider` over Swift `IosSecretKeyStore` + CryptoKit)
 *
 * CONTRACT
 *  - [encrypt] MUST use a fresh random nonce per call and produce an
 *    authenticated ciphertext (AEAD, e.g. AES-GCM) that self-describes its
 *    nonce (nonce prepended to the returned bytes). [aad] is authenticated but
 *    NOT encrypted; the same [aad] must be supplied to [decrypt].
 *  - [decrypt] MUST fail (throw) on any authentication failure — wrong key,
 *    tampered ciphertext, or mismatched [aad]. It must never return unverified
 *    plaintext.
 *  - Key material never leaves the device / secure store.
 *
 * ── PROVENANCE / MERGE NOTE ─────────────────────────────────────────────────
 * This interface is the SHARED Step-5 contract owned by the `feat/step5-shared`
 * sibling. It is reproduced here verbatim so this iOS subtask compiles in
 * isolation (its branch is cut from `main`, before the shared sibling landed).
 * The signatures are identical, so the merge is a no-op dedupe: when
 * `feat/step5-shared` lands the canonical file, delete this copy and keep theirs.
 */
interface SecretKeyProvider {
    /** True once a usable key exists in the platform secure store. */
    fun hasKey(): Boolean

    /** Idempotently create the key if absent. Safe to call repeatedly. */
    fun ensureKey()

    /**
     * Authenticated encryption. Returns `nonce ‖ ciphertext ‖ tag`. A fresh
     * random nonce is generated per call. [aad] is authenticated, not encrypted.
     */
    fun encrypt(plaintext: ByteArray, aad: ByteArray = ByteArray(0)): ByteArray

    /**
     * Authenticated decryption of bytes produced by [encrypt]. Throws on any
     * authentication failure (wrong key / tampering / [aad] mismatch).
     */
    fun decrypt(ciphertext: ByteArray, aad: ByteArray = ByteArray(0)): ByteArray
}
