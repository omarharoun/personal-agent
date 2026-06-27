// 🔒 CRISIS-CRITICAL (Step 7) — consent-first only; autonomous action disabled; NOT-FOR-REAL-USERS until human + crisis-expert review.
package com.personalagent.android.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.personalagent.android.AppContainer
import com.personalagent.shared.safety.CrisisRecognizer
import com.personalagent.shared.safety.CrisisResponder
import com.personalagent.shared.safety.CrisisResponse
import com.personalagent.shared.safety.CrisisSignal
import com.personalagent.shared.safety.TrustedContact
import com.personalagent.shared.safety.TrustedContactsStore
import com.personalagent.shared.util.SystemClock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * 🔒 CRISIS-CRITICAL (Step 7) — consent-first crisis-safety state holder. 🔒
 *
 * Owns the trusted-contacts list and the (optional) support surface. It performs no
 * autonomous action: it never contacts anyone. The only outward actions are the
 * user-initiated dialer/SMS intents fired from the composables themselves.
 *
 * NOT-FOR-REAL-USERS until SECURITY_REVIEW Gate 2 (crisis) is signed off.
 */
data class SafetyUiState(
    val contacts: List<TrustedContact> = emptyList(),
    /** Non-null when the support surface is being shown. */
    val support: CrisisResponse? = null,
    val message: String? = null,
)

class SafetyViewModel(
    private val contactsStore: TrustedContactsStore,
    private val responder: CrisisResponder,
    private val recognizer: CrisisRecognizer,
) : ViewModel() {

    private val _state = MutableStateFlow(SafetyUiState())
    val state: StateFlow<SafetyUiState> = _state.asStateFlow()

    init { refreshContacts() }

    fun refreshContacts() =
        _state.update { it.copy(contacts = contactsStore.all()) }

    fun consumeMessage() = _state.update { it.copy(message = null) }

    /** Surface a transient message (e.g. a gentle fallback when no dialer/SMS app exists). */
    fun showMessage(text: String) = _state.update { it.copy(message = text) }

    // --- Trusted contacts (explicit consent captured by the caller) ---

    /**
     * Add a trusted contact. The screen only enables this once the user has ticked
     * the up-front consent box, so reaching here *is* the consent; we stamp the
     * acknowledgement time on the record.
     */
    fun addContact(name: String, phone: String, relationship: String) {
        if (name.isBlank()) {
            _state.update { it.copy(message = "Please enter a name") }
            return
        }
        contactsStore.add(
            TrustedContact.create(
                name = name,
                phone = phone,
                relationship = relationship,
                nowMillis = SystemClock.nowMillis(),
            ),
        )
        refreshContacts()
        _state.update { it.copy(message = "Saved") }
    }

    fun removeContact(id: String) {
        contactsStore.remove(id)
        refreshContacts()
        _state.update { it.copy(message = "Removed") }
    }

    // --- Support surface ---

    /**
     * Show the supportive surface on demand. This is the always-available, non-
     * alarmist entry point: the user can choose to look for support at any time.
     * Building the response contacts no one — it only assembles copy + resources +
     * the user's chosen contacts.
     */
    fun showSupport() = _state.update {
        it.copy(support = responder.respond(CrisisSignal.POSSIBLE_DISTRESS))
    }

    fun dismissSupport() = _state.update { it.copy(support = null) }

    /**
     * The shared-layer-reported path: if the recognizer flags [CrisisSignal.POSSIBLE_DISTRESS]
     * for some observed text, *offer* the support surface. Auto-detection is OFF by
     * default (the wired recognizer always returns NONE) and this is intentionally
     * NOT connected to the live conversation pending the crisis-expert gate.
     */
    fun onObservedText(text: String) {
        if (recognizer.assess(text) == CrisisSignal.POSSIBLE_DISTRESS) showSupport()
    }

    /** Factory so Compose can build this VM from the app container. */
    class Factory(private val container: AppContainer) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            SafetyViewModel(
                container.trustedContactsStore,
                container.crisisResponder,
                container.crisisRecognizer,
            ) as T
    }
}
