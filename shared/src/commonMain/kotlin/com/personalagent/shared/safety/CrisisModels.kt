// 🔒 CRISIS-CRITICAL (Step 7) — consent-first only; autonomous action disabled; NOT-FOR-REAL-USERS until human + crisis-expert review.
package com.personalagent.shared.safety

import kotlinx.serialization.Serializable

/**
 * 🔒 SHARED CRISIS-SAFETY CONTRACT (Step 7).
 *
 * ⚠️ OWNERSHIP / RECONCILE-AT-MERGE: the canonical version of this contract is
 * owned by the `feat/step7-shared` sibling. It is duplicated here, deliberately
 * minimal and matching the agreed API (`CrisisRecognizer` / `CrisisResponse` /
 * `CrisisResource` / `TrustedContact`), so the iOS UI subtask
 * (`feat/step7-ios-ui`) compiles and is reviewable in isolation. Reconcile the
 * two at merge — do not ship both copies.
 *
 * Design rules baked into these types:
 *  - There is **no autonomous action**. The recognizer only *classifies*; it
 *    never calls, messages, or notifies anyone. Reaching a trusted contact is
 *    always an explicit, user-initiated, user-confirmed tap in the UI.
 *  - Tone is supportive and non-clinical. Nothing here makes a confidentiality
 *    promise or a diagnosis.
 */

/**
 * The single, intentionally coarse safety signal. We deliberately do NOT model
 * graded severities here: the only supported response is the calm, consent-first
 * support surface. Anything finer would invite alarmist or clinical UX that this
 * project is not qualified to ship.
 */
enum class CrisisSignal {
    /** Nothing suggesting distress was detected. No support surface is shown. */
    NONE,

    /**
     * Language that *might* indicate the user is struggling. This is a low-bar,
     * non-diagnostic hint — never a conclusion about the person. It only gates a
     * gentle, optional support view.
     */
    POSSIBLE_DISTRESS,
}

/**
 * A crisis-support resource shown to the user. Sourced ONLY from the shared
 * [CrisisResourceProvider] so the copy/numbers live in one reviewed place.
 *
 * All contact affordances are optional and, in the UI, are user-tapped — never
 * dialed or sent automatically.
 *
 * ⚠️ The default resource list is US-centric and unreviewed. Real localization
 * (region detection + locale-correct hotlines) is a crisis-expert + product
 * requirement before any real user sees this.
 */
@Serializable
data class CrisisResource(
    /** Display name, e.g. "988 Suicide & Crisis Lifeline". */
    val name: String,
    /** One warm, plain-language line about what this resource is. */
    val description: String,
    /** Optional phone number for a user-initiated `tel:` call. */
    val phoneNumber: String? = null,
    /** Optional SMS number for a user-initiated `sms:` message. */
    val smsNumber: String? = null,
    /** Optional pre-filled SMS body (e.g. "HOME" for a text line). */
    val smsBody: String? = null,
    /** Optional web URL the user can open. */
    val url: String? = null,
    /** Human availability note, e.g. "Call or text, 24/7". */
    val availability: String = "",
)

/**
 * The result of [CrisisRecognizer.assess]. Pure data — holds the signal, a
 * supportive (non-alarmist, non-clinical) message to show, and the resources to
 * offer. It carries **no action** and triggers nothing on its own.
 */
@Serializable
data class CrisisResponse(
    val signal: CrisisSignal,
    /** Short, warm, supportive copy. Empty when [signal] is [CrisisSignal.NONE]. */
    val supportiveMessage: String,
    val resources: List<CrisisResource>,
) {
    /** Convenience for callers/UI gating. */
    val isDistress: Boolean get() = signal == CrisisSignal.POSSIBLE_DISTRESS
}

/**
 * A person the user has *explicitly* chosen, up front and with consent, that
 * they might want help reaching in a hard moment. Stored encrypted at rest like
 * everything else.
 *
 * A [TrustedContact] only ever exists because the user added it by hand in the
 * setup view; it is never inferred from contacts/messages/anywhere.
 */
@Serializable
data class TrustedContact(
    val id: String,
    val name: String,
    /** Optional — without it, "Help me contact" can't open the dialer/Messages. */
    val phoneNumber: String? = null,
    /** Free-form relation label the user typed, e.g. "Sister", "Friend". */
    val relation: String = "",
    /** When the user added this contact (their up-front act of consent). */
    val addedAtMillis: Long,
)
