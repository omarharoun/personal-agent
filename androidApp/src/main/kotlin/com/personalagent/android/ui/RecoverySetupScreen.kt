package com.personalagent.android.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.personalagent.android.onboarding.RecoveryCode

/**
 * 🔒 SECURITY-CRITICAL (Step 5) — first-run recovery-code setup. 🔒
 *
 * Shown ONCE before the user can use the app. Generates the user-held recovery
 * code, displays it with the plain-language no-recovery warning, and requires an
 * explicit "I have saved it" confirmation before continuing. The code is shown
 * only here — there is no later screen that reveals it again.
 *
 * @param onConfirmed invoked with the generated code once the user confirms they
 *   saved it; the caller persists the verifier + marks setup complete.
 */
@Composable
fun RecoverySetupScreen(onConfirmed: (String) -> Unit) {
    // Generate once and survive recomposition / rotation so the displayed code
    // never silently changes under the user mid-setup.
    val recoveryCode = rememberSaveable { RecoveryCode.generate() }
    var saved by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = "Save your recovery code",
            style = MaterialTheme.typography.headlineSmall,
        )
        Spacer(Modifier.height(16.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
            ),
        ) {
            Text(
                text = recoveryCode,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                textAlign = TextAlign.Center,
                fontFamily = FontFamily.Monospace,
                fontSize = 24.sp,
                letterSpacing = 2.sp,
                style = MaterialTheme.typography.headlineMedium,
            )
        }

        Spacer(Modifier.height(20.dp))

        // 🔒 Exact-requirement warning copy. Losing the code = losing the data, and
        // the company cannot recover or reset it. Surfaced verbatim via RECOVERY_WARNING.
        Text(
            text = RECOVERY_WARNING,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.error,
        )

        Spacer(Modifier.height(24.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = saved, onCheckedChange = { saved = it })
            Spacer(Modifier.width(8.dp))
            Text(
                text = "I have written down my recovery code and stored it somewhere safe.",
                style = MaterialTheme.typography.bodyMedium,
            )
        }

        Spacer(Modifier.height(16.dp))

        Button(
            onClick = { onConfirmed(recoveryCode) },
            enabled = saved,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Continue")
        }
    }
}

/**
 * The exact plain-language warning required by the brief. Kept as a constant so it
 * is verifiable in tests and reused anywhere the recovery code is presented.
 */
const val RECOVERY_WARNING: String =
    "This is your recovery code. Write it down and keep it somewhere safe.\n\n" +
        "If you lose this code, you lose your data. We cannot recover it or reset " +
        "it for you — not even the company that makes this app. There is no backup " +
        "and no way to get your data back without it."
