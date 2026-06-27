package com.personalagent.shared.store

import com.personalagent.shared.crypto.SecretKeyProvider
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/**
 * 🔒 SECURITY-CRITICAL (Step 5) — the real encrypted-at-rest [KeyValueStorage]. 🔒
 *
 * This is the Step-5 replacement for the plaintext placeholders
 * ([InMemoryKeyValueStorage], `AndroidKeyValueStorage`, …). It is a thin
 * envelope: it owns no keys and touches no disk. Every value is sealed by
 * [crypto] (a keystore-backed [SecretKeyProvider]) and the resulting ciphertext
 * is handed to the unchanged [delegate] for actual persistence. Nothing above
 * this class (LocalStore, the app, callers) changes — this is the swap point the
 * earlier steps were built around.
 *
 * Layout per entry:
 *   - The **value** is `encrypt(plaintext, aad = key)` then Base64 for storage.
 *     A fresh random IV is generated per write by [crypto] and framed into the
 *     blob, so re-writing the same value yields different ciphertext.
 *   - The **key** is stored in the clear (it is the lookup handle). It is also
 *     passed as AAD so a ciphertext is cryptographically bound to its key — an
 *     attacker who swaps two stored values (or moves one to another key) makes
 *     decryption fail rather than silently returning the wrong record.
 *
 * Keys are NOT encrypted. They are opaque app-chosen strings (e.g. "notes"),
 * not user content; treat them as non-sensitive metadata. If a future key space
 * embeds user data, hash/tokenize keys before this layer.
 *
 * Migration: this class does not auto-migrate data written by the plaintext
 * placeholders. A one-time migration (read plaintext → write encrypted → wipe)
 * is required before real data exists; see SECURITY_REVIEW Gate 1.
 */
@OptIn(ExperimentalEncodingApi::class)
class EncryptedKeyValueStorage(
    private val delegate: KeyValueStorage,
    private val crypto: SecretKeyProvider,
) : KeyValueStorage {

    init {
        // Ensure the keystore key exists before any read/write touches it.
        crypto.ensureKey()
    }

    override fun get(key: String): String? {
        val stored = delegate.get(key) ?: return null
        val blob = Base64.decode(stored)
        val plain = crypto.decrypt(blob, aad = key.encodeToByteArray())
        return plain.decodeToString()
    }

    override fun put(key: String, value: String) {
        val blob = crypto.encrypt(value.encodeToByteArray(), aad = key.encodeToByteArray())
        delegate.put(key, Base64.encode(blob))
    }

    override fun remove(key: String) = delegate.remove(key)

    override fun keys(): Set<String> = delegate.keys()
}
