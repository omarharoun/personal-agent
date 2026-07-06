package com.personalagent.shared.profile

import com.personalagent.shared.store.KeyValueStorage

/**
 * The user's display name, used only for the warm personal greeting on the home
 * screen ("Good evening, Omar."). Two sources, in priority order:
 *   1. [userName] — what the user typed in Settings (authoritative).
 *   2. [derivedName] — a first name the agent recalled from its own memory,
 *      fetched at most once and cached here.
 * If neither is known the greeting is shown with no name — never a fabricated one.
 *
 * This is a display preference, not a credential, but it's kept in the same
 * sealed [KeyValueStorage] as everything else for consistency.
 */
class ProfileStore(private val storage: KeyValueStorage) {

    /** The name the user typed in Settings, or null. */
    fun userName(): String? = storage.get(KEY_USER_NAME)?.trim()?.takeIf { it.isNotBlank() }

    fun setUserName(name: String) {
        val n = name.trim()
        if (n.isBlank()) storage.remove(KEY_USER_NAME) else storage.put(KEY_USER_NAME, n)
    }

    /** A first name the agent recalled from memory (cached), or null. */
    fun derivedName(): String? = storage.get(KEY_DERIVED_NAME)?.trim()?.takeIf { it.isNotBlank() }

    fun setDerivedName(name: String) = storage.put(KEY_DERIVED_NAME, name.trim())

    /**
     * True once we've already asked Hermes for the name (whether or not it knew),
     * so the home screen makes that lightweight query only once per install.
     */
    fun derivedAttempted(): Boolean = storage.get(KEY_DERIVED_TRIED) == "1"

    fun markDerivedAttempted() = storage.put(KEY_DERIVED_TRIED, "1")

    /** The name to greet with (user-set wins over derived), or null if unknown. */
    fun displayName(): String? = userName() ?: derivedName()

    private companion object {
        const val KEY_USER_NAME = "profile.user_name"
        const val KEY_DERIVED_NAME = "profile.derived_name"
        const val KEY_DERIVED_TRIED = "profile.derived_tried"
    }
}
