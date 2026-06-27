package com.personalagent.shared.crypto

/**
 * 🔒 SECURITY-CRITICAL (Step 5) — encryption-at-rest contract. 🔒
 *
 * The single seam between "we have some bytes to protect" and "how those bytes
 * are sealed with a key that never leaves the platform's secure hardware".
 * Platform implementations:
 *   - Android: [com.personalagent.shared.crypto.AndroidSecretKeyProvider]
 *     (AndroidKeyStore AES-256-GCM, hardware/StrongBox-backed where available).
 *   - iOS: Keychain / Secure Enclave (sibling subtask).
 *   - JVM: file/dev key (test + desktop; NOT for real users).
 *
 * Contract notes:
 *   - [encrypt] MUST generate a fresh random IV per call and self-frame it into
 *     the returned blob; [decrypt] reads it back. Callers never see the IV.
 *   - [aad] (additional authenticated data) is authenticated but NOT encrypted;
 *     the same value must be supplied to [decrypt] or it fails. Use it to bind a
 *     ciphertext to its context (e.g. the storage key) so blobs can't be swapped.
 *   - The key itself MUST be generated and held inside the platform keystore and
 *     never be exportable. Implementations only move *ciphertext* in and out.
 *
 * NOTE: this is the shared contract the platform providers implement. A sibling
 * subtask owns the canonical copy under the same package + signature; keep this
 * file byte-identical to that contract so the two converge on merge.
 */
interface SecretKeyProvider {
    /** True if the backing key already exists in the keystore. */
    fun hasKey(): Boolean

    /** Create the backing key if absent. Idempotent. */
    fun ensureKey()

    /** Seal [plaintext] (fresh IV prepended, [aad] authenticated). */
    fun encrypt(plaintext: ByteArray, aad: ByteArray = ByteArray(0)): ByteArray

    /** Open a blob produced by [encrypt]; [aad] must match. */
    fun decrypt(ciphertext: ByteArray, aad: ByteArray = ByteArray(0)): ByteArray
}
