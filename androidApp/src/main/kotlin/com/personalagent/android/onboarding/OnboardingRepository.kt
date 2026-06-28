package com.personalagent.android.onboarding

import com.personalagent.shared.store.KeyValueStorage

/**
 * Tracks whether the user has completed first-run onboarding (Welcome →
 * recovery-code setup → AI model setup → Done) so the flow shows ONCE.
 *
 * This is intentionally separate from [SecuritySetupRepository]: the recovery
 * code is the security gate (and may be required independently), while this flag
 * records that the user has been *through the whole flow* — including the chance
 * to set up (or skip) the on-device model. Backed by the same encrypted
 * [KeyValueStorage] as everything else.
 */
class OnboardingRepository(
    private val storage: KeyValueStorage,
) {
    fun isComplete(): Boolean = storage.get(KEY_COMPLETE) == "true"

    fun complete() = storage.put(KEY_COMPLETE, "true")

    private companion object {
        const val KEY_COMPLETE = "onboarding_complete"
    }
}
