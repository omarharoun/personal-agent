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

    override fun put(key: String, value: String) {
        prefs.edit().putString(key, value).apply()
    }

    override fun remove(key: String) {
        prefs.edit().remove(key).apply()
    }

    override fun keys(): Set<String> = prefs.all.keys.toSet()
}
