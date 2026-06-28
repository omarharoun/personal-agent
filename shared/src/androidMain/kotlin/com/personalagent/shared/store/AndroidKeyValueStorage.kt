package com.personalagent.shared.store

import android.content.Context

/**
 * SharedPreferences-backed storage for Android.
 *
 * // TODO Step 5: swap for encrypted wallet. This is the UNENCRYPTED
 * // placeholder — SharedPreferences is plaintext on disk. Replace with an
 * // EncryptedSharedPreferences / Keystore-wrapped implementation (or a wallet)
 * // without touching LocalStore or any caller.
 */
class AndroidKeyValueStorage(
    context: Context,
    fileName: String = "personal_agent_store",
) : KeyValueStorage {
    private val prefs = context.applicationContext
        .getSharedPreferences(fileName, Context.MODE_PRIVATE)

    override fun get(key: String): String? = prefs.getString(key, null)

    // NOTE: use commit() (synchronous), NOT apply() (async). apply() schedules the
    // disk write on a background thread and does NOT guarantee it lands before the
    // process dies — so a value saved right before the app is killed/restarted can
    // be LOST. That was the root cause of "saved my API key, restarted, it was
    // gone." commit() blocks until the write is durably persisted, which is what we
    // need at save time (these writes are infrequent, so the cost is negligible).
    override fun put(key: String, value: String) {
        prefs.edit().putString(key, value).commit()
    }

    override fun remove(key: String) {
        prefs.edit().remove(key).commit()
    }

    override fun keys(): Set<String> = prefs.all.keys.toSet()
}
