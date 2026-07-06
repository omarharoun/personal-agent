// 🔒 CRISIS-CRITICAL (Step 7) — consent-first only; autonomous action disabled; NOT-FOR-REAL-USERS until human + crisis-expert review.
package com.personalagent.android.safety

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri

/**
 * 🔒 CRISIS-CRITICAL (Step 7) — user-initiated, user-confirmed contact only. 🔒
 *
 * Every helper here opens a system composer **pre-filled** and then stops. The
 * OS dialer / SMS app is what actually places the call or sends the text, and only
 * after the user taps the call/send button themselves. Specifically:
 *
 *  - Calls use [Intent.ACTION_DIAL] (NOT `ACTION_CALL`). This opens the dialer with
 *    the number entered; it can never auto-dial, and the app holds no `CALL_PHONE`
 *    permission. A device check is needed to confirm the dialer opens as expected.
 *  - Texts use [Intent.ACTION_SENDTO] with an `smsto:` URI and a pre-filled body.
 *    The user reviews and sends; nothing is sent programmatically.
 *
 * There is no autonomous path here by construction — no function sends or calls.
 *
 * @return true if a composer was opened; false if no app could handle it (the
 *   caller surfaces a gentle fallback rather than crashing).
 */
object ContactIntents {

    /** Open the dialer pre-filled with [phone]. Does NOT place the call. */
    fun openDialer(context: Context, phone: String): Boolean =
        launch(context, Intent(Intent.ACTION_DIAL, Uri.parse("tel:${phone.trim()}")))

    /**
     * Open the SMS composer to [phone] pre-filled with [body]. Does NOT send.
     * The user reviews the message and taps send themselves.
     */
    fun openSms(context: Context, phone: String, body: String): Boolean =
        launch(
            context,
            Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:${phone.trim()}"))
                .putExtra("sms_body", body),
        )

    /** Open a URL (e.g. a helpline directory) in the browser. */
    fun openUrl(context: Context, url: String): Boolean =
        launch(context, Intent(Intent.ACTION_VIEW, Uri.parse(url.trim())))

    private fun launch(context: Context, intent: Intent): Boolean = try {
        // FLAG_ACTIVITY_NEW_TASK so this works when launched from a non-Activity context.
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        true
    } catch (_: ActivityNotFoundException) {
        // No app can handle it — the caller shows a gentle fallback.
        false
    } catch (_: Throwable) {
        // Any other failure (e.g. a SecurityException from an OEM dialer, a
        // malformed URI) must NOT crash the crisis path — degrade to the fallback.
        false
    }
}
