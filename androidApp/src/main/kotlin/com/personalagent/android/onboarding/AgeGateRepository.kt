package com.personalagent.android.onboarding

import com.personalagent.shared.store.KeyValueStorage

/**
 * Records that the user has confirmed they meet the app's **18+** age requirement,
 * so the age gate is shown ONCE. Backed by the same encrypted [KeyValueStorage]
 * as the rest of onboarding.
 *
 * Only a positive confirmation is ever persisted — there is no "under 18" state to
 * store, because an under-18 user is blocked and never proceeds.
 */
class AgeGateRepository(
    private val storage: KeyValueStorage,
) {
    fun isConfirmed(): Boolean = storage.get(KEY_CONFIRMED) == "true"

    fun confirm() = storage.put(KEY_CONFIRMED, "true")

    private companion object {
        const val KEY_CONFIRMED = "age_gate_18plus_confirmed"
    }
}
