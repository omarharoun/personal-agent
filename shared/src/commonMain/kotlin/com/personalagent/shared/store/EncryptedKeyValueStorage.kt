package com.personalagent.shared.store

import com.personalagent.shared.crypto.SecretKeyProvider
import kotlin.io.encoding.Base64

/**
 * 🔒 SECURITY-CRITICAL (Step 5 — encryption at rest). 🔒
 *
 * The encrypted realization of the [KeyValueStorage] seam that Step 1 reserved
 * for exactly this swap (see the comment in [KeyValueStorage]). It is a thin
 * decorator: it delegates the actual byte persistence to an [inner]
 * [KeyValueStorage] (NSUserDefaults / SharedPreferences / file — all plaintext
 * containers) but stores only **authenticated ciphertext** there. Plaintext
 * values exist solely in memory.
 *
 * NOTHING ABOVE THIS CLASS CHANGES: [PersistentLocalStore] and every caller are
 * unaffected; encryption is purely an implementation of the storage seam.
 *
 * Design:
 *  - Values are encrypted with [crypto] ([SecretKeyProvider], AES-GCM on iOS via
 *    CryptoKit). The returned `nonce ‖ ciphertext ‖ tag` blob is Base64-encoded
 *    to a String so it round-trips through string-only containers.
 *  - The **storage key is used as AAD**, binding each ciphertext to its slot:
 *    an attacker who can write the container cannot move ciphertext from one key
 *    to another without the authenticated-decryption check failing.
 *  - Keys themselves are left in the clear (they are fixed schema names like
 *    "notes"/"reminders", not user content). If key confidentiality is ever
 *    required, hash them here — callers are unaffected.
 *  - A decrypt/authentication failure THROWS (propagated from [crypto.decrypt]);
 *    we never silently return unverified or partial plaintext. There is
 *    intentionally no plaintext→ciphertext migration: this is a fresh Step-5
 *    store (NOT-FOR-REAL-USERS; no production data to migrate yet).
 *
 * ── PROVENANCE / MERGE NOTE ─────────────────────────────────────────────────
 * Like [SecretKeyProvider], this is a SHARED Step-5 component conceptually owned
 * by `feat/step5-shared`. It is included here so the iOS subtask wires a real
 * encrypted store and compiles in isolation. On merge, dedupe against the
 * sibling's canonical copy (keep one).
 */
class EncryptedKeyValueStorage(
    private val inner: KeyValueStorage,
    private val crypto: SecretKeyProvider,
) : KeyValueStorage {

    init {
        // Ensure a device key exists before any read/write touches the store.
        crypto.ensureKey()
    }

    override fun get(key: String): String? {
        val stored = inner.get(key) ?: return null
        val blob = Base64.decode(stored)
        // Throws on tamper / wrong key / AAD mismatch — surfaced to the caller.
        val plaintext = crypto.decrypt(blob, aad = key.encodeToByteArray())
        return plaintext.decodeToString()
    }

    override fun put(key: String, value: String) {
        val blob = crypto.encrypt(value.encodeToByteArray(), aad = key.encodeToByteArray())
        inner.put(key, Base64.encode(blob))
    }

    override fun remove(key: String) = inner.remove(key)

    override fun keys(): Set<String> = inner.keys()
}
