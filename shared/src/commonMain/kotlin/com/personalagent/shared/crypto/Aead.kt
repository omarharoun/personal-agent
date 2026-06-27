// 🔒 SECURITY-CRITICAL (Step 5) — pending human security review; do NOT ship to a
// real user until reviewed. Uses vetted standard crypto.
package com.personalagent.shared.crypto

/**
 * Raw-key authenticated encryption (AEAD). Implementations MUST use a **vetted,
 * standard** AES-256-GCM construction (never a hand-rolled cipher) with:
 *   - a 256-bit key,
 *   - a **fresh random 96-bit (12-byte) nonce per [seal]** (GCM nonce reuse under
 *     one key is catastrophic),
 *   - a 128-bit (16-byte) authentication tag,
 *   - output layout `nonce(12) || ciphertext || tag(16)` (nonce prepended).
 *
 * This is the symmetric primitive shared by [AeadSecretKeyProvider] (DEK → data)
 * and [RecoveryManager] (recovery-derived key → wrapped DEK). It is a plain
 * injected interface (not `expect`/`actual`) so the common layer stays fully
 * unit-testable on the JVM; the JVM impl ([com.personalagent.shared.crypto] jvmMain
 * `JvmAead`) uses `javax.crypto`. Platform-hardware AEAD is a separate concern
 * exposed via [SecretKeyProvider].
 */
interface Aead {
    /** AES-256-GCM seal [plaintext] under [key], authenticating [aad]. */
    fun seal(key: ByteArray, plaintext: ByteArray, aad: ByteArray = ByteArray(0)): ByteArray

    /** AES-256-GCM open [sealed] under [key], verifying [aad]. Throws on auth failure. */
    fun open(key: ByteArray, sealed: ByteArray, aad: ByteArray = ByteArray(0)): ByteArray
}

/**
 * Cryptographically secure random bytes. JVM impl wraps `java.security.SecureRandom`.
 * Injected (not `expect`/`actual`) so tests can supply a deterministic source if
 * needed — though the real provider must always be a CSPRNG.
 */
fun interface SecureRandomBytes {
    /** Fill a fresh [size]-byte array with cryptographically secure random bytes. */
    fun nextBytes(size: Int): ByteArray
}
