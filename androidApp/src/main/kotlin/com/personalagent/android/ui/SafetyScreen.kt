// 🔒 CRISIS-CRITICAL (Step 7) — consent-first only; autonomous action disabled; NOT-FOR-REAL-USERS until human + crisis-expert review.
package com.personalagent.android.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/**
 * 🔒 CRISIS-CRITICAL (Step 7) — the consent-first crisis-safety surface. 🔒
 *
 * Two things, both opt-in and non-alarmist:
 *  1. An always-available "Find support" entry that reveals the warm [SupportResponseCard]
 *     on demand (this is also the surface the shared layer would show on POSSIBLE_DISTRESS;
 *     auto-detection itself stays OFF until the crisis-expert gate — see [SafetyViewModel]).
 *  2. The [TrustedContactsScreen] for choosing trusted people in advance, with consent.
 *
 * No autonomous action anywhere: every outward action is a user tap that opens the
 * dialer/SMS composer for the user to send themselves.
 */
@Composable
fun SafetyScreen(
    vm: SafetyViewModel,
    snackbar: SnackbarHostState,
) {
    val state by vm.state.collectAsStateWithLifecycle()

    LaunchedEffect(state.message) {
        state.message?.let {
            snackbar.showSnackbar(it)
            vm.consumeMessage()
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(vertical = 16.dp),
    ) {
        val support = state.support
        if (support != null) {
            SupportResponseCard(
                response = support,
                onDismiss = vm::dismissSupport,
                onContactMissingApp = {
                    // Gentle fallback if the device has no dialer/SMS/browser app.
                    vm.showMessage("This device doesn't have an app to do that.")
                },
            )
            Spacer(Modifier.height(24.dp))
        } else {
            OutlinedButton(
                onClick = vm::showSupport,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Find support")
            }
            Spacer(Modifier.height(8.dp))
            Text(
                "If you're going through a hard time, tap above for gentle support and " +
                    "ways to reach someone.",
                style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
            )
            Spacer(Modifier.height(24.dp))
        }

        TrustedContactsScreen(state = state, vm = vm)
    }
}
