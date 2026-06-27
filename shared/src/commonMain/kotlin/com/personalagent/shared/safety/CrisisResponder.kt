// 🔒 CRISIS-CRITICAL (Step 7) — autonomous action DISABLED; requires human + crisis-response-expert review; NOT-FOR-REAL-USERS.
package com.personalagent.shared.safety

/**
 * Turns a [CrisisAssessment] into a SAFE, consent-first [CrisisResponse] — or
 * nothing at all when there is no signal.
 *
 * 🔒 Wellbeing rules baked into the copy below (a crisis-response expert must
 * review these — Gate 2):
 *  - It gently ENCOURAGES the user to reach out *themselves* to someone they
 *    trust. The app does not insert itself between the user and that person.
 *  - It surfaces real, region-aware [CrisisResource]s (via [resourceProvider]).
 *  - It OFFERS to help them contact someone — framed strictly as *available if
 *    the user clearly asks*. It is an offer, never an action.
 *  - It does NOT do reflective listening that repeats/amplifies the user's
 *    distress back at them.
 *  - It makes NO confidentiality promises and NO "authorities won't be involved"
 *    claims — we cannot and must not promise either.
 *  - It is warm, brief, and non-clinical. It does not diagnose or assess risk.
 */
class CrisisResponder(
    private val resourceProvider: CrisisResourceProvider,
) {
    /**
     * @return a supportive response when [assessment] is [CrisisLevel
     *   .POSSIBLE_DISTRESS]; `null` for [CrisisLevel.NONE] so we never push an
     *   unsolicited crisis message onto ordinary conversation.
     * @param regionHint passed through to the resource provider so resources are
     *   localized to the user's region.
     */
    fun respond(assessment: CrisisAssessment, regionHint: String? = null): CrisisResponse? {
        if (assessment.level == CrisisLevel.NONE) return null
        return CrisisResponse(
            message = SUPPORT_MESSAGE,
            resources = resourceProvider.resourcesFor(regionHint),
            // We make help *available* on the user's clear ask. The flag signals
            // the offer; acting on it always requires the user to say yes, and is
            // never autonomous (see AutonomousCrisisAction — disabled).
            offerToHelpContact = true,
        )
    }

    companion object {
        /**
         * 🔒 Reviewed copy. Encourages self-reach-out, points to resources, and
         * offers consent-based help — with no reflection of distress, no
         * confidentiality/"no authorities" claims, warm and brief.
         */
        const val SUPPORT_MESSAGE: String =
            "I'm really glad you told me, and you don't have to carry this on your " +
            "own. If you can, reaching out to someone you trust — a friend, family " +
            "member, or someone close to you — can really help right now. Below are " +
            "some places you can contact for support too. If you'd like, I can help " +
            "you reach out to one of your trusted contacts — only if and when you " +
            "want me to."
    }
}
