// 🔒 CRISIS-CRITICAL (Step 7) — consent-first only; autonomous action disabled; NOT-FOR-REAL-USERS until human + crisis-expert review.
package com.personalagent.shared.safety

import kotlinx.serialization.Serializable

/**
 * 🔒 CRISIS-CRITICAL (Step 7). 🔒
 *
 * A single crisis-support resource shown on the support surface. Any of [phone],
 * [sms] or [url] may be present; the UI turns each into a *user-initiated* tap
 * (dialer / SMS composer / browser) — never an automatic action.
 *
 * @param availability free-text like "24/7" when known; never implies a promise.
 */
@Serializable
data class CrisisResource(
    val name: String,
    val description: String,
    val phone: String? = null,
    val sms: String? = null,
    val url: String? = null,
    val availability: String? = null,
)

/**
 * Default [CrisisResourceProvider]. 🔒
 *
 * These are **starting points only** and must be reviewed + localized by a crisis
 * expert before this feature reaches anyone — the UI surfaces that caveat too. The
 * production build replaces this with a region-aware, vetted provider.
 */
class DefaultCrisisResourceProvider : CrisisResourceProvider {
    override fun resources(): List<CrisisResource> = listOf(
        CrisisResource(
            name = "If you're in immediate danger",
            description = "Call your local emergency number (for example 911, 112, or 999).",
            availability = "24/7",
        ),
        CrisisResource(
            name = "Find a Helpline",
            description = "A free directory of crisis helplines around the world.",
            url = "https://findahelpline.com",
        ),
        CrisisResource(
            name = "988 Suicide & Crisis Lifeline (US & Canada)",
            description = "Call or text 988 to reach a trained counselor. Free to contact.",
            phone = "988",
            sms = "988",
            availability = "24/7",
        ),
    )
}
