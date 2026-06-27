// 🔒 CRISIS-CRITICAL (Step 7) — consent-first only; autonomous action disabled; NOT-FOR-REAL-USERS until human + crisis-expert review.
package com.personalagent.shared.safety

/**
 * 🔒 CRISIS-CRITICAL (Step 7) — shared crisis-safety contract. 🔒
 *
 * NOTE ON OWNERSHIP: this package (`com.personalagent.shared.safety`) is owned by
 * the `feat/step7-shared` sibling. The types below are the **agreed contract** the
 * Android and iOS UIs build against. This file is the Android worktree's local
 * mirror so `:androidApp` compiles in isolation; at merge time the sibling's
 * authoritative version takes precedence (reconcile, don't duplicate).
 *
 * SAFETY POSTURE (all of it deliberate):
 *  - There is **no autonomous action anywhere in this contract**. Nothing here can
 *    place a call, send a message, or notify anyone. The only "contact" path is a
 *    UI affordance that opens the device dialer / SMS composer *pre-filled* for the
 *    user to review and send themselves (see the Android/iOS surfaces).
 *  - Recognition is coarse and conservative: a single [CrisisSignal.POSSIBLE_DISTRESS]
 *    that only ever *offers* support. No diagnosis, no severity scoring, no logging.
 *  - Copy is warm, brief, non-clinical and **not alarmist**, and makes **no
 *    confidentiality promises**.
 *
 * This whole feature is NOT-FOR-REAL-USERS until SECURITY_REVIEW Gate 2 (crisis
 * autonomous action) is designed and signed off by a human + crisis expert.
 */

/**
 * The single, coarse signal the on-device recognizer may report. Intentionally
 * binary and conservative — the product only ever *offers* support, so finer
 * gradations would add false precision and risk being alarmist.
 */
enum class CrisisSignal {
    /** Nothing detected. The support surface is not auto-shown. */
    NONE,

    /**
     * The recognizer saw language that *might* indicate the user is struggling.
     * This is a gentle "offer help" trigger — never a diagnosis or an alarm.
     */
    POSSIBLE_DISTRESS,
}

/**
 * On-device, offline recognizer. Owned by the sibling; the real implementation is
 * gated behind the crisis-expert review. Pure function of text → coarse signal,
 * with no side effects, no logging, and no network.
 */
fun interface CrisisRecognizer {
    fun assess(text: String): CrisisSignal
}

/**
 * Supplies the crisis **resources** shown on the support surface (helplines,
 * directories, emergency-number reminder). This is the swap point for vetted,
 * **localized** resources — the default implementation ships only clearly-labelled
 * starting points that a crisis expert must review/localize before any real user.
 */
fun interface CrisisResourceProvider {
    fun resources(): List<CrisisResource>
}

/**
 * Turns a [CrisisSignal] into the supportive, consent-first [CrisisResponse] the UI
 * renders. It assembles copy + resources + the user's pre-chosen trusted contacts.
 *
 * It does **not** contact anyone — there is no method here that can. The autonomous
 * path described in the original brief is intentionally absent.
 */
interface CrisisResponder {
    fun respond(signal: CrisisSignal): CrisisResponse
}

/**
 * The safe default recognizer: it recognizes nothing. 🔒
 *
 * Until the sibling's real recognizer is wired *and* the crisis-expert gate is
 * passed, the app ships with auto-detection switched OFF — the support surface is
 * still reachable on demand (the user can always tap to find support), but nothing
 * is ever auto-triggered. This makes "no false alarms, ever" the default.
 */
object DisabledCrisisRecognizer : CrisisRecognizer {
    override fun assess(text: String): CrisisSignal = CrisisSignal.NONE
}
