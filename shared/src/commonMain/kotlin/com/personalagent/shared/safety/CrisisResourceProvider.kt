// 🔒 CRISIS-CRITICAL (Step 7) — autonomous action DISABLED; requires human + crisis-response-expert review; NOT-FOR-REAL-USERS.
package com.personalagent.shared.safety

/**
 * Supplies the crisis [CrisisResource]s shown to the user.
 *
 * 🔒 Resources MUST be configurable and region-aware. Hard-coding a single phone
 * number is actively dangerous: numbers change, are country-specific, and a
 * stale or wrong-country number can send someone in crisis nowhere. Implementers
 * should resolve to the *user's own region's* services and confirm the numbers
 * are current at build/config time.
 *
 * The app injects a real, verified, localized provider. [DefaultCrisisResource
 * Provider] is only a clearly-marked placeholder so the spine is testable.
 */
interface CrisisResourceProvider {
    /**
     * @param regionHint optional region/locale hint (e.g. an ISO country code).
     *   Implementations should prefer the user's own region's services. `null`
     *   means "region unknown" — return guidance to find local services rather
     *   than a possibly-wrong specific number.
     */
    fun resourcesFor(regionHint: String?): List<CrisisResource>
}

/**
 * 🔒 PLACEHOLDER ONLY — **VERIFY + LOCALIZE current crisis resources before real
 * users.** These entries are intentionally generic and carry loud notes telling
 * the integrator to replace them. They must NOT ship as-is.
 *
 * IMPORTANT (build-time gate): before this app is put in front of any real user,
 * the integrator MUST confirm — for every region the app serves — that each
 * resource here is replaced with a current, accurate, locally-correct service,
 * verified at build time. Numbers, names, and availability all drift; this code
 * cannot and does not vouch for any specific number being live or correct.
 */
class DefaultCrisisResourceProvider : CrisisResourceProvider {

    override fun resourcesFor(regionHint: String?): List<CrisisResource> {
        val region = regionHint?.trim().takeUnless { it.isNullOrEmpty() }
        // Prefer pointing the user at *their own region's* services. We never
        // assert a specific helpline number here, because we cannot verify one
        // for the user's actual location from inside this layer.
        val regionLine = if (region != null) {
            "Look up the official crisis/emergency services for your region ($region)."
        } else {
            "Look up the official crisis/emergency services for your country/region."
        }
        return listOf(
            CrisisResource(
                name = "Your local emergency number",
                contact = regionLine,
                note = "PLACEHOLDER — VERIFY + LOCALIZE before real users. If someone " +
                    "is in immediate danger, contacting local emergency services is " +
                    "appropriate. Replace with the correct number for the user's region.",
            ),
            CrisisResource(
                name = "A crisis / suicide-prevention helpline in your region",
                contact = regionLine,
                note = "PLACEHOLDER — VERIFY + LOCALIZE before real users. Replace with " +
                    "the current, official helpline for the user's region (e.g. via " +
                    "findahelpline.com or the region's health authority), confirmed " +
                    "current at build time.",
            ),
        )
    }
}
