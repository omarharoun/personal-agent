// 🔒 CRISIS-CRITICAL (Step 7) — consent-first only; autonomous action disabled; NOT-FOR-REAL-USERS until human + crisis-expert review.
package com.personalagent.android.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.personalagent.android.safety.ContactIntents
import com.personalagent.shared.safety.CrisisResource
import com.personalagent.shared.safety.CrisisResponse
import com.personalagent.shared.safety.TrustedContact

/** Honesty banner — this whole feature is pending crisis-expert review (Gate 2). */
private const val REVIEW_NOTICE: String =
    "Support feature — pending review by a crisis-response expert. Resources below " +
        "are placeholders to verify/localize. If you're in immediate danger, contact " +
        "your local emergency services."

/**
 * 🔒 CRISIS-CRITICAL (Step 7) — the supportive surface. 🔒
 *
 * A calm, warm card shown when the user opens support on demand. Tone is brief,
 * kind, non-clinical and not alarmist. It (a) shows the consent-first support
 * [CrisisResponse.message], (b) lists crisis [CrisisResponse.resources], and (c)
 * — only when [CrisisResponse.offerToHelpContact] and the user has trusted
 * [contacts] — offers, on an explicit tap, to help reach one by opening the dialer
 * / SMS composer pre-filled. Nothing here contacts anyone automatically.
 *
 * @param contacts the user's pre-chosen trusted contacts (from app state, NOT from
 *   the response — the response never carries personal contacts).
 * @param onContactMissingApp surfaced when no dialer/SMS app can handle a tap.
 */
@Composable
fun SupportResponseCard(
    response: CrisisResponse,
    contacts: List<TrustedContact>,
    onDismiss: () -> Unit,
    onContactMissingApp: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
        ),
    ) {
        // The card OWNS the only scroll here. The body must never add its own
        // scroll — nesting two same-direction scrolls measures the inner one with
        // an infinite max-height constraint and throws at layout time (this was the
        // "Find support" crash). See [SupportResourcesBody].
        SupportResourcesBody(
            response = response,
            contacts = contacts,
            onClose = onDismiss,
            onContactMissingApp = onContactMissingApp,
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
        )
    }
}

/**
 * The supportive content itself — with **no scroll of its own**. The CALLER
 * provides the scroll (via [modifier]) so this can be dropped into either the
 * chat [SupportResponseCard] or the full-screen Support Resources view without
 * ever nesting two scrollables (the crash). Everything here is pure rendering; it
 * cannot throw. Outward taps only open the dialer/SMS composer, guarded so a
 * device with no handler shows a gentle fallback instead of crashing.
 */
@Composable
fun SupportResourcesBody(
    response: CrisisResponse,
    contacts: List<TrustedContact>,
    onClose: () -> Unit,
    onContactMissingApp: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    Column(modifier = modifier) {
        Text(
            text = REVIEW_NOTICE,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(16.dp))

        Text(
            text = response.message,
            style = MaterialTheme.typography.bodyLarge,
        )

        // --- Crisis resources (placeholder, verify/localize before real users) ---
        Spacer(Modifier.height(20.dp))
        Text("Ways to get support", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        response.resources.forEach { resource ->
            CrisisResourceRow(resource)
            Spacer(Modifier.height(12.dp))
        }

        // --- Trusted contacts (user-initiated reach-out, only if offered) ---
        if (response.offerToHelpContact && contacts.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Text("People you trust", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            contacts.forEach { contact ->
                TrustedContactRow(
                    contact = contact,
                    onCall = { if (!ContactIntents.openDialer(context, it)) onContactMissingApp() },
                    onText = { phone ->
                        val firstName = contact.name.substringBefore(' ')
                        val body = "Hi $firstName, I'm having a really hard time and " +
                            "could use someone to talk to."
                        if (!ContactIntents.openSms(context, phone, body)) onContactMissingApp()
                    },
                )
                Spacer(Modifier.height(8.dp))
            }
        }

        Spacer(Modifier.height(4.dp))
        TextButton(onClick = onClose, modifier = Modifier.fillMaxWidth()) {
            Text("Close")
        }
    }
}

@Composable
private fun TrustedContactRow(
    contact: TrustedContact,
    onCall: (String) -> Unit,
    onText: (String) -> Unit,
) {
    Column(Modifier.fillMaxWidth()) {
        Text(
            text = if (contact.relationship.isNotBlank()) {
                "${contact.name} · ${contact.relationship}"
            } else {
                contact.name
            },
            style = MaterialTheme.typography.bodyLarge,
        )
        val phone = contact.phone
        if (phone.isNullOrBlank()) {
            Text("No number saved for this person.", style = MaterialTheme.typography.bodySmall)
        } else {
            Row {
                // The labels make it explicit this just *helps* the user reach out —
                // it opens the dialer/composer; the user calls or sends themselves.
                OutlinedButton(onClick = { onCall(phone) }) {
                    Text("Help me call ${contact.name.substringBefore(' ')}")
                }
                Spacer(Modifier.width(8.dp))
                OutlinedButton(onClick = { onText(phone) }) {
                    Text("Help me text")
                }
            }
        }
    }
}

@Composable
private fun CrisisResourceRow(resource: CrisisResource) {
    Column(Modifier.fillMaxWidth()) {
        Text(resource.name, style = MaterialTheme.typography.bodyLarge)
        Text(resource.contact, style = MaterialTheme.typography.bodyMedium)
        if (resource.note.isNotBlank()) {
            Text(resource.note, style = MaterialTheme.typography.bodySmall)
        }
        HorizontalDivider(Modifier.padding(top = 4.dp))
    }
}
