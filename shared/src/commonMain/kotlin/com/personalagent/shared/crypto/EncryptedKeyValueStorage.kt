// 🔒 SECURITY-CRITICAL (Step 5) — pending human security review; do NOT ship to a
// real user until reviewed. Uses vetted standard crypto.
package com.personalagent.shared.crypto

import com.personalagent.shared.store.KeyValueStorage

/**
 * The **Step-5 encryption swap**. Drops into the existing
 * [com.personalagent.shared.store.KeyValueStorage] seam (the one Step 1 marked
 * `// TODO Step 5`) without any caller change: it wraps a plaintext [delegate]
 * store and encrypts every value on write / decrypts on read using a
 * [SecretKeyProvider]. The encrypted wallet now covers the *whole* local store —
 * memory, notes, reminders, everything that goes through `KeyValueStorage`.
 *
 * **Value encryption.** Each value is sealed with AES-GCM via [crypto] and stored
 * Base64-encoded in the delegate, so the delegate only ever holds ciphertext —
 * plaintext values never touch disk/SharedPreferences/NSUserDefaults. A fresh nonce
 * per write (inside [crypto]) means writing the same value twice yields different
 * ciphertext.
 *
 * **Key/value binding (AAD).** The logical key is passed as AES-GCM *additional
 * authenticated data*. This cryptographically binds each ciphertext to its key, so
 * an attacker with write access to the delegate cannot move/copy ciphertext from one
 * key to another (a "swap" attack) without failing authentication on read.
 *
 * **Storage-key handling.** By default the logical key is also the delegate key
 * (transparent, `keys()` works). Callers that want the delegate to be opaque too may
 * pass a [storageKeyTransform] (e.g. a salted SHA-256) — then keys are stored hashed
 * and `keys()` returns those opaque digests. The AAD binding always uses the
 * *logical* key regardless, so security does not depend on the transform.
 *
 * @param delegate the underlying (plaintext) store; receives only ciphertext.
 * @param crypto AEAD provider holding the data-encryption key (hardware-backed in
 *   production, or an [AeadSecretKeyProvider] over a recovery-unwrapped DEK).
 * @param storageKeyTransform maps a logical key to the delegate key. Identity by
 *   default. MUST be deterministic.
 */
class EncryptedKeyValueStorage(
    private val delegate: KeyValueStorage,
    private val crypto: SecretKeyProvider,
    private val storageKeyTransform: (String) -> String = { it },
) : KeyValueStorage {

    init {
        crypto.ensureKey()
    }

    override fun get(key: String): String? {
        val stored = delegate.get(storageKeyTransform(key)) ?: return null
        val plaintext = crypto.decrypt(Base64.decode(stored), aad(key))
        return plaintext.decodeToString()
    }

    override fun put(key: String, value: String) {
        val sealed = crypto.encrypt(value.encodeToByteArray(), aad(key))
        delegate.put(storageKeyTransform(key), Base64.encode(sealed))
    }

    override fun remove(key: String) {
        delegate.remove(storageKeyTransform(key))
    }

    /**
     * Returns the **delegate's** stored keys. With the identity transform these equal
     * the logical keys; with a hashing transform they are opaque digests (logical keys
     * are unrecoverable by design). Documented so callers don't assume reversibility.
     */
    override fun keys(): Set<String> = delegate.keys()

    /** AAD = the logical key's UTF-8 bytes, binding ciphertext to its key. */
    private fun aad(key: String): ByteArray = key.encodeToByteArray()
}
