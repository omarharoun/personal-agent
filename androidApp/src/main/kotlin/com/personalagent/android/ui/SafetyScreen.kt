// 🔒 CRISIS-CRITICAL (Step 7) — consent-first only; autonomous action disabled; NOT-FOR-REAL-USERS until human + crisis-expert review.
package com.personalagent.android.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
 *  1. An always-available "Find support" entry that opens the dedicated
 *     [SupportResourcesScreen] with the warm [SupportResponseCard] (real crisis
 *     resources + gentle guidance). Auto-detection stays OFF until the crisis-
 *     expert gate — see [SafetyViewModel].
 *  2. The [TrustedContactsScreen] for choosing trusted people in advance, with consent.
 *
 * No autonomous action anywhere: every outward action is a user tap that opens the
 * dialer/SMS composer for the user to send themselves.
 *
 * @param onFindSupport navigate to the Support Resources view. Kept as navigation
 *   (a separate screen) rather than inline expansion because the resources card
 *   scrolls, and nesting it inside this scrolling column crashed the layout.
 */
@Composable
fun SafetyScreen(
    vm: SafetyViewModel,
    snackbar: SnackbarHostState,
    onFindSupport: () -> Unit,
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
        Button(
            onClick = onFindSupport,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(Icons.Filled.FavoriteBorder, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("Find support")
        }
        Spacer(Modifier.height(8.dp))
        Text(
            "If you're going through a hard time, tap above for gentle support, crisis " +
                "resources, and ways to reach someone.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(24.dp))

        TrustedContactsScreen(state = state, vm = vm)
    }
}

/**
 * 🔒 CRISIS-CRITICAL (Step 7) — the dedicated Support Resources view. 🔒
 *
 * Opened from "Find support". Builds the supportive surface on entry (contacts no
 * one — it only assembles copy + resources) and shows it full-screen, so the
 * resources card owns the only scroll on screen. The resource list is still a
 * placeholder pending crisis-expert verification/localization (see the card's
 * REVIEW notice and [DefaultCrisisResourceProvider]).
 */
@Composable
fun SupportResourcesScreen(
    vm: SafetyViewModel,
    snackbar: SnackbarHostState,
    onClose: () -> Unit,
) {
    val state by vm.state.collectAsStateWithLifecycle()

    // Assemble the supportive response as soon as this screen appears.
    LaunchedEffect(Unit) { vm.showSupport() }

    LaunchedEffect(state.message) {
        state.message?.let {
            snackbar.showSnackbar(it)
            vm.consumeMessage()
        }
    }

    val support = state.support
    if (support != null) {
        SupportResponseCard(
            response = support,
            contacts = state.contacts,
            onDismiss = onClose,
            onContactMissingApp = {
                vm.showMessage("This device doesn't have an app to do that.")
            },
            modifier = Modifier.fillMaxSize().padding(vertical = 12.dp),
        )
    }
}
