// 🔒 CRISIS-CRITICAL (Step 7) — autonomous action DISABLED; requires human + crisis-response-expert review; NOT-FOR-REAL-USERS.
package com.personalagent.shared.safety

import kotlinx.serialization.Serializable

/**
 * The Step-7 crisis-safety contract.
 *
 * 🔒 THIS WHOLE PACKAGE IS A HUMAN-REVIEW-REQUIRED GATE (Gate 2 in
 * docs/SECURITY_REVIEW.md). Someone in genuine distress may be the user. The
 * SAFE spine here — conservative recognition + a consent-first supportive
 * response + configurable resources + consented trusted contacts — must NOT
 * reach a real user until it has been reviewed by someone with crisis-response
 * expertise. The autonomous "contact help for the user" capability is built only
 * as a DISABLED, review-gated seam (see [AutonomousCrisisAction]).
 *
 * These types are the exact, shared contract the Android and iOS crisis-UI
 * slices build against; do not change signatures without coordinating.
 */

/**
 * A person the user has *chosen, up front and with consent*, that the app may
 * later (only with a fresh, explicit go-ahead) help them reach out to.
 *
 * [consentedAt] (epoch millis, UTC) is when the user consented to having this
 * person stored as a trusted contact. It is captured at add-time and is not
 * optional by design — there is no path to store a contact without consent.
 * `@Serializable` so it can persist through the encrypted [com.personalagent
 * .shared.store.KeyValueStorage] seam; the data-class signature is unchanged.
 */
@Serializable
data class TrustedContact(
    val id: String,
    val name: String,
    val relationship: String,
    val phone: String?,
    val consentedAt: Long,
)

/**
 * Deliberately coarse and conservative. We only ever distinguish "no signal"
 * from "there might be distress here". We intentionally do NOT model severity,
 * imminence, or intent — inferring those from text is unreliable and a wrong
 * confident label is harmful. The coarseness is a safety choice, not a
 * limitation we plan to remove.
 */
enum class CrisisLevel {
    /** No clear distress signal. The default and the overwhelmingly common case. */
    NONE,

    /**
     * A clear-enough signal that distress is *possible*. This gates ONLY whether
     * a gentle, supportive, consent-first response is *offered*. It never, by
     * itself, triggers any action toward anyone.
     */
    POSSIBLE_DISTRESS,
}

/** The recognizer's read of a single piece of user text, with a short rationale. */
data class CrisisAssessment(
    val level: CrisisLevel,
    val rationale: String,
)

/**
 * Recognition seam. Implementations must bias hard toward [CrisisLevel.NONE].
 *
 * 🔒 Recognizing genuine distress from text is *hard and unreliable*. Any
 * implementation will both miss real distress (false negatives) and flag
 * ordinary text (false positives). Because of that, a positive assessment must
 * never drive an autonomous action — it only gates whether support is offered.
 */
interface CrisisRecognizer {
    fun assess(userText: String): CrisisAssessment
}

/** A single, real, verify-before-use crisis resource (e.g. a helpline). */
data class CrisisResource(
    val name: String,
    val contact: String,
    val note: String,
)

/**
 * The supportive, consent-first response. Note what it is and is NOT:
 *  - [message] gently encourages the user to reach out *themselves* to someone
 *    they trust. It is warm, brief, non-clinical, and does NOT reflect/repeat
 *    the user's distress back at them, and makes NO confidentiality or
 *    "authorities won't be involved" promises.
 *  - [resources] are real, region-aware, verify-before-use crisis resources.
 *  - [offerToHelpContact] only signals that help reaching a trusted contact is
 *    *available if the user clearly asks for it*. It is an offer, never an act.
 */
data class CrisisResponse(
    val message: String,
    val resources: List<CrisisResource>,
    val offerToHelpContact: Boolean,
)
