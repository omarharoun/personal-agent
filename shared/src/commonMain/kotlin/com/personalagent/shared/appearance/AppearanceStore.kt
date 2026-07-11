package com.personalagent.shared.appearance

import com.personalagent.shared.store.KeyValueStorage

/**
 * Persists the user's appearance choices — currently the selected ACCENT color id
 * (one of [AccentPalette.OPTIONS]). Kept in the same sealed [KeyValueStorage] as
 * every other store, for consistency (this is a display preference, not a secret,
 * but it rides the same encrypted-at-rest path so we never scatter storage seams).
 *
 * The dark/light/system MODE toggle stays where it already lives (a platform UI
 * pref); this store only owns the accent, which is new and shared across platforms.
 */
class AppearanceStore(private val storage: KeyValueStorage) {

    /** The chosen accent id, defaulting to the neutral [AccentPalette.DEFAULT_ID]. */
    fun accentId(): String =
        storage.get(KEY_ACCENT)?.takeIf { it.isNotBlank() } ?: AccentPalette.DEFAULT_ID

    /** The resolved option (falls back to the default for an unknown/blank id). */
    fun accent(): AccentOption = AccentPalette.byId(accentId())

    /** Persist a new accent id. Unknown ids are ignored (keeps the store honest). */
    fun setAccentId(id: String) {
        if (AccentPalette.OPTIONS.none { it.id == id }) return
        storage.put(KEY_ACCENT, id)
    }

    private companion object {
        const val KEY_ACCENT = "appearance.accent_id"
    }
}
