// 🔒 CRISIS-CRITICAL (Step 7) — consent-first only; autonomous action disabled; NOT-FOR-REAL-USERS until human + crisis-expert review.
package com.personalagent.shared.safety

/**
 * 🔒 SHARED CRISIS-SAFETY CONTRACT (Step 7) — see [CrisisModels] for the
 * ownership / reconcile-at-merge note (canonical copy lives in
 * `feat/step7-shared`).
 *
 * Provides crisis-support [CrisisResource]s. The UI lists resources ONLY from
 * here so the numbers and copy live in one reviewed place.
 */
interface CrisisResourceProvider {
    fun resources(): List<CrisisResource>
}

/**
 * Conservative, placeholder resource list.
 *
 * ⚠️ NOT-FOR-REAL-USERS: US-centric and unreviewed. Real builds need region
 * detection + locale-correct, crisis-expert-vetted resources. This exists so the
 * support surface has something concrete to render in review.
 */
class DefaultCrisisResourceProvider : CrisisResourceProvider {
    override fun resources(): List<CrisisResource> = listOf(
        CrisisResource(
            name = "988 Suicide & Crisis Lifeline",
            description = "Free, confidential support for people in distress.",
            phoneNumber = "988",
            smsNumber = "988",
            availability = "Call or text, 24/7 (US)",
        ),
        CrisisResource(
            name = "Crisis Text Line",
            description = "Text with a trained crisis counselor.",
            smsNumber = "741741",
            smsBody = "HOME",
            availability = "Text, 24/7 (US)",
        ),
        CrisisResource(
            name = "Emergency services",
            description = "If you or someone else may be in immediate danger.",
            phoneNumber = "911",
            availability = "24/7 (US)",
        ),
    )
}

/**
 * Classifies a piece of user text into a [CrisisResponse]. **Classification
 * only** — a recognizer never contacts anyone, schedules anything, or notifies
 * anyone. The autonomous pathway is intentionally absent at this layer.
 */
interface CrisisRecognizer {
    fun assess(text: String): CrisisResponse
}

/**
 * Deliberately simple, transparent keyword recognizer.
 *
 * ⚠️ NOT-FOR-REAL-USERS: this is a placeholder, not a validated classifier. A
 * real implementation must be designed and reviewed with crisis experts (recall
 * vs. false-positives, non-English, sarcasm/quoting, etc.). It is intentionally
 * conservative and explainable rather than clever: it only ever returns
 * [CrisisSignal.POSSIBLE_DISTRESS] (a gentle hint) or [CrisisSignal.NONE]. It
 * runs fully on device and stores nothing.
 */
class KeywordCrisisRecognizer(
    private val resourceProvider: CrisisResourceProvider = DefaultCrisisResourceProvider(),
    /** Copy is centralized so it can be reviewed/translated in one place. */
    private val supportiveMessage: String = DEFAULT_SUPPORTIVE_MESSAGE,
    private val phrases: List<String> = DISTRESS_PHRASES,
) : CrisisRecognizer {

    override fun assess(text: String): CrisisResponse {
        val hay = text.lowercase()
        val hit = phrases.any { hay.contains(it) }
        return if (hit) {
            CrisisResponse(
                signal = CrisisSignal.POSSIBLE_DISTRESS,
                supportiveMessage = supportiveMessage,
                resources = resourceProvider.resources(),
            )
        } else {
            CrisisResponse(CrisisSignal.NONE, "", emptyList())
        }
    }

    companion object {
        /**
         * Warm, brief, non-clinical, non-alarmist. Makes NO confidentiality
         * promise. ⚠️ Placeholder copy pending crisis-expert review.
         */
        const val DEFAULT_SUPPORTIVE_MESSAGE: String =
            "It sounds like things might be really hard right now. You don't have to go " +
                "through this alone — reaching out to someone you trust, or to a trained " +
                "listener, can help."

        /**
         * Intentionally small and explainable. Real coverage is an expert task;
         * this is only enough to demonstrate the consent-first surface in review.
         */
        val DISTRESS_PHRASES: List<String> = listOf(
            "i want to die",
            "kill myself",
            "end my life",
            "don't want to be here",
            "want to disappear",
            "can't go on",
            "hurt myself",
            "no reason to live",
            "hopeless",
        )
    }
}
