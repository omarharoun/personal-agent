// 🔒 CRISIS-CRITICAL (Step 7) — autonomous action DISABLED; requires human + crisis-response-expert review; NOT-FOR-REAL-USERS.
package com.personalagent.shared.safety

/**
 * 🔒🔒🔒 THE DANGEROUS PART — BUILT AS A SEAM, DELIBERATELY NOT IMPLEMENTED. 🔒🔒🔒
 *
 * This is the seam for the app *autonomously* contacting someone on the user's
 * behalf in an emergency (e.g. reaching a trusted contact or emergency services
 * **without** waiting for the user's in-the-moment consent).
 *
 * The hard judgment this would require — deciding WHEN an emergency is grave
 * enough to override a person's consent and act for them — is NOT something to
 * settle with an `if` statement in this codebase. It is an ethical, legal, and
 * clinical decision. So this capability is left as an explicitly DISABLED,
 * UNIMPLEMENTED, review-gated seam. It cannot be enabled without sign-off from a
 * human AND a crisis-response expert (see docs/SECURITY_REVIEW.md, Gate 2).
 *
 * The only shipped implementation is [DisabledAutonomousCrisisAction], which
 * always refuses and never acts.
 */
interface AutonomousCrisisAction {
    /**
     * Whether autonomous action is enabled. 🔒 This is hard-wired `false` in the
     * only implementation and must stay that way until the Gate-2 review.
     */
    val enabled: Boolean

    /**
     * "Attempt" an autonomous action for the given request. The only permitted
     * outcome today is [AutonomousActionOutcome.Refused] — see that type for why
     * there is intentionally no "acted" outcome.
     */
    fun attempt(request: AutonomousActionRequest): AutonomousActionOutcome
}

/** Inputs an autonomous action *would* consider. Inert — nothing consumes these to act. */
data class AutonomousActionRequest(
    val assessment: CrisisAssessment,
    val userText: String,
    val contact: TrustedContact?,
)

/**
 * The result of an autonomous-action attempt.
 *
 * 🔒 There is deliberately only ONE outcome: [Refused]. There is NO `Acted`
 * variant anywhere in this codebase. Adding one — i.e. actually wiring autonomous
 * contact and the "override consent in an emergency" judgment — is the gated work
 * that must not happen without human + crisis-expert review.
 */
sealed interface AutonomousActionOutcome {
    /** The capability is disabled; nothing was done. */
    data class Refused(val reason: String) : AutonomousActionOutcome
}

/**
 * 🔒 The shipped, SAFE default: ALWAYS refuses, NEVER acts, NEVER contacts
 * anyone — regardless of input. It exists so the rest of the app can depend on
 * the [AutonomousCrisisAction] seam while the dangerous capability stays off.
 *
 * Enabling autonomous action is intentionally NOT a flag you flip here. It
 * requires building the (currently absent) judgment behind a human + crisis-
 * response-expert review. [enabled] is hard-coded `false`.
 */
class DisabledAutonomousCrisisAction : AutonomousCrisisAction {

    override val enabled: Boolean = false

    override fun attempt(request: AutonomousActionRequest): AutonomousActionOutcome =
        AutonomousActionOutcome.Refused(
            "Autonomous crisis action is DISABLED. The decision to override a " +
                "user's consent and act for them is gated behind human + crisis-" +
                "response-expert review (SECURITY_REVIEW Gate 2) and is not " +
                "implemented. No contact was made.",
        )
}
