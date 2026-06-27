package com.personalagent.android.onboarding

import com.personalagent.shared.store.KeyValueStorage

/**
 * 🔒 SECURITY-CRITICAL (Step 5) — first-run security setup state. 🔒
 *
 * Tracks whether the user has been shown and has confirmed saving their recovery
 * code, and persists the salted PBKDF2 verifier (never the code). Backed by the
 * **encrypted** [KeyValueStorage] so the verifier is sealed at rest like all
 * other data.
 */
class SecuritySetupRepository(
    private val storage: KeyValueStorage,
) {
    fun isComplete(): Boolean = storage.get(KEY_COMPLETE) == "true"

    /** Persist the verifier and mark setup complete after the user confirms they saved the code. */
    fun complete(recoveryCode: String) {
        storage.put(KEY_VERIFIER, RecoveryCode.makeVerifier(recoveryCode))
        storage.put(KEY_COMPLETE, "true")
    }

    /** Verify a re-entered recovery code against the stored verifier (for a future restore/confirm flow). */
    fun verify(recoveryCode: String): Boolean {
        val verifier = storage.get(KEY_VERIFIER) ?: return false
        return RecoveryCode.verify(recoveryCode, verifier)
    }

    private companion object {
        const val KEY_COMPLETE = "security_setup_complete"
        const val KEY_VERIFIER = "recovery_code_verifier"
    }
}
