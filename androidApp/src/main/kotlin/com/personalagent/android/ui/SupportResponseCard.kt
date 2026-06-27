// 🔒 CRISIS-CRITICAL (Step 7) — consent-first only; autonomous action disabled; NOT-FOR-REAL-USERS until human + crisis-expert review.
package com.personalagent.android.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AssistChip
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

/**
 * 🔒 CRISIS-CRITICAL (Step 7) — the supportive surface. 🔒
 *
 * A calm, warm card shown when the shared layer reports possible distress (or when
 * the user opens it on demand). Tone is brief, kind, non-clinical and not alarmist.
 * It (a) gently encourages reaching out, (b) lists crisis resources, and (c) offers
 * — only on an explicit tap (= the user's choice) — to help reach a trusted contact
 * by opening the dialer / SMS composer pre-filled. Nothing here contacts anyone
 * automatically.
 *
 * @param onContactMissingApp surfaced when no dialer/SMS/browser app can handle a tap.
 */
@Composable
fun SupportResponseCard(
    response: CrisisResponse,
    onDismiss: () -> Unit,
    onContactMissingApp: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
        ),
    ) {
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
        ) {
            // Honesty banner — this feature is pending expert review.
            Text(
                text = response.reviewNotice,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
            Spacer(Modifier.height(16.dp))

            Text(
                text = response.headline,
                style = MaterialTheme.typography.titleLarge,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = response.message,
                style = MaterialTheme.typography.bodyLarge,
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = response.encourageReachOut,
                style = MaterialTheme.typography.bodyMedium,
            )

            // --- Trusted contacts (user-initiated reach-out) ---
            if (response.trustedContacts.isNotEmpty()) {
                Spacer(Modifier.height(20.dp))
                Text("People you trust", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                response.trustedContacts.forEach { contact ->
                    TrustedContactRow(
                        contact = contact,
                        onCall = {
                            if (!ContactIntents.openDialer(context, it)) onContactMissingApp()
                        },
                        onText = {
                            val body = "Hi ${contact.name.substringBefore(' ')}, " +
                                "I'm having a really hard time and could use someone to talk to."
                            if (!ContactIntents.openSms(context, it, body)) onContactMissingApp()
                        },
                    )
                    Spacer(Modifier.height(8.dp))
                }
            }

            // --- Crisis resources ---
            Spacer(Modifier.height(20.dp))
            Text("Ways to get support", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            response.resources.forEach { resource ->
                CrisisResourceRow(
                    resource = resource,
                    onCall = { if (!ContactIntents.openDialer(context, it)) onContactMissingApp() },
                    onText = { num, body ->
                        if (!ContactIntents.openSms(context, num, body)) onContactMissingApp()
                    },
                    onOpenUrl = { if (!ContactIntents.openUrl(context, it)) onContactMissingApp() },
                )
                Spacer(Modifier.height(12.dp))
            }

            Spacer(Modifier.height(4.dp))
            TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                Text("Close")
            }
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
            text = contact.relationship?.let { "${contact.name} · $it" } ?: contact.name,
            style = MaterialTheme.typography.bodyLarge,
        )
        val phone = contact.phone
        if (phone.isNullOrBlank()) {
            Text(
                "No number saved for this person.",
                style = MaterialTheme.typography.bodySmall,
            )
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

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun CrisisResourceRow(
    resource: CrisisResource,
    onCall: (String) -> Unit,
    onText: (String, String) -> Unit,
    onOpenUrl: (String) -> Unit,
) {
    Column(Modifier.fillMaxWidth()) {
        Text(resource.name, style = MaterialTheme.typography.bodyLarge)
        Text(resource.description, style = MaterialTheme.typography.bodyMedium)
        resource.availability?.let {
            Text("Available: $it", style = MaterialTheme.typography.bodySmall)
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            resource.phone?.takeIf { it.isNotBlank() }?.let { phone ->
                AssistChip(onClick = { onCall(phone) }, label = { Text("Call $phone") })
            }
            resource.sms?.takeIf { it.isNotBlank() }?.let { sms ->
                AssistChip(
                    onClick = { onText(sms, "") },
                    label = { Text("Text $sms") },
                )
            }
            resource.url?.takeIf { it.isNotBlank() }?.let { url ->
                AssistChip(onClick = { onOpenUrl(url) }, label = { Text("Open website") })
            }
        }
        HorizontalDivider(Modifier.padding(top = 4.dp))
    }
}
