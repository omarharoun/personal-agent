// 🔒 CRISIS-CRITICAL (Step 7) — consent-first only; autonomous action disabled; NOT-FOR-REAL-USERS until human + crisis-expert review.
package com.personalagent.shared.safety

/**
 * 🔒 CRISIS-CRITICAL (Step 7). 🔒
 *
 * Everything the support surface needs to render, assembled by a [CrisisResponder].
 * It is purely descriptive data — rendering it has no side effects and contacts
 * no one.
 *
 * The copy is intentionally warm, brief, non-clinical and not alarmist, and makes
 * no confidentiality promises.
 */
data class CrisisResponse(
    val signal: CrisisSignal,
    /** Short, gentle title for the card. */
    val headline: String,
    /** One or two warm sentences acknowledging the moment. */
    val message: String,
    /** A gentle nudge to reach out to someone they trust. */
    val encourageReachOut: String,
    /** Vetted/localized-pending resources to offer. */
    val resources: List<CrisisResource>,
    /** The user's pre-chosen trusted contacts (may be empty). */
    val trustedContacts: List<TrustedContact>,
    /** Visible reminder that this feature is pending expert review. */
    val reviewNotice: String,
)

/**
 * Default [CrisisResponder]. 🔒
 *
 * Assembles the supportive surface from the [resourceProvider] and the user's
 * [contactsStore]. It never contacts anyone — there is deliberately no method here
 * that could. The copy below is the reviewed-tone source of truth.
 */
class DefaultCrisisResponder(
    private val resourceProvider: CrisisResourceProvider,
    private val contactsStore: TrustedContactsStore,
) : CrisisResponder {

    override fun respond(signal: CrisisSignal): CrisisResponse = CrisisResponse(
        signal = signal,
        headline = "You don't have to go through this alone",
        message = "It sounds like things might be really hard right now. " +
            "Whatever you're feeling is okay, and you deserve support.",
        encourageReachOut = "If you can, reaching out to someone you trust can help — " +
            "a friend, family member, or one of the people you chose below.",
        resources = resourceProvider.resources(),
        trustedContacts = contactsStore.all(),
        reviewNotice = REVIEW_NOTICE,
    )

    companion object {
        /**
         * 🔒 Honesty banner shown on the surface. This build is not for real users
         * until a crisis expert has reviewed the recognition, copy, and resources.
         */
        const val REVIEW_NOTICE: String =
            "This support feature is still being reviewed by safety experts and the " +
                "resources below may not be right for your area yet."
    }
}
