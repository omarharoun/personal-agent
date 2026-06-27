package com.personalagent.shared.store

import platform.Foundation.NSUserDefaults

/**
 * NSUserDefaults-backed storage for iOS.
 *
 * // TODO Step 5: swap for encrypted wallet. This is the UNENCRYPTED
 * // placeholder — NSUserDefaults is plaintext in the app container. Replace
 * // with a Keychain / encrypted-file (or wallet) implementation without
 * // touching LocalStore or any caller.
 */
class IosKeyValueStorage(
    private val suiteName: String = "personal_agent_store",
) : KeyValueStorage {
    private val defaults = NSUserDefaults(suiteName = suiteName)

    override fun get(key: String): String? = defaults.stringForKey(key)

    override fun put(key: String, value: String) {
        defaults.setObject(value, forKey = key)
    }

    override fun remove(key: String) {
        defaults.removeObjectForKey(key)
    }

    @Suppress("UNCHECKED_CAST")
    override fun keys(): Set<String> =
        (defaults.dictionaryRepresentation().keys as Set<String>)
}
