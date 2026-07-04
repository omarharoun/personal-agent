package com.personalagent.android.ui.connect

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/**
 * The "Connect to your Hermes" screen — the front door of a bring-your-own-Hermes
 * (Path A) client. The user points the app at the Hermes instance *they* run.
 *
 * 🔒 REVIEW REQUIRED — trust boundary + credential entry. This is the only place a
 * backend is chosen; there is no default/hidden server. The key is stored (on a
 * successful health check) via the hardware-sealed store, never in plaintext.
 */
@Composable
fun ConnectScreen(
    vm: ConnectViewModel,
    onConnected: () -> Unit,
    onOpenSetupGuide: (() -> Unit)? = null,
) {
    val state by vm.state.collectAsStateWithLifecycle()
    var keyVisible by remember { mutableStateOf(false) }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .imePadding()
            .padding(horizontal = 24.dp, vertical = 32.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            "Connect your Life Agent",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "This app is the front door to your own Hermes agent — the private \"brain\" " +
                "that remembers your notes, reminders, and reflections. Run Hermes on your " +
                "computer or a small server, then point the app at it. Your data stays on " +
                "your server; we never see it.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(24.dp))

        OutlinedTextField(
            value = state.baseUrl,
            onValueChange = vm::onBaseUrlChange,
            label = { Text("Hermes address") },
            placeholder = { Text("http://192.168.1.20:8642") },
            singleLine = true,
            enabled = !state.testing,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Next),
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(4.dp))
        Text(
            "The address where your Hermes API server is running (default port 8642).",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(Modifier.height(16.dp))
        OutlinedTextField(
            value = state.apiKey,
            onValueChange = vm::onApiKeyChange,
            label = { Text("API key") },
            singleLine = true,
            enabled = !state.testing,
            visualTransformation = if (keyVisible) VisualTransformation.None else PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
            trailingIcon = {
                TextButton(onClick = { keyVisible = !keyVisible }) {
                    Text(if (keyVisible) "Hide" else "Show")
                }
            },
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(
                Icons.Filled.Lock,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.height(14.dp),
            )
            Text(
                "The API_SERVER_KEY you set on your Hermes. Stored encrypted on this device only.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        state.plaintextWarning?.let { warn ->
            Spacer(Modifier.height(16.dp))
            InfoBox(text = warn, tone = InfoTone.WARNING)
        }

        state.error?.let { err ->
            Spacer(Modifier.height(16.dp))
            InfoBox(text = err, tone = InfoTone.ERROR)
        }

        Spacer(Modifier.height(24.dp))
        Button(
            onClick = { vm.testAndConnect(onConnected) },
            enabled = !state.testing,
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (state.testing) {
                CircularProgressIndicator(
                    modifier = Modifier.height(18.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary,
                )
                Spacer(Modifier.height(0.dp))
                Text("  Testing…")
            } else {
                Text("Test & Connect")
            }
        }

        if (onOpenSetupGuide != null) {
            Spacer(Modifier.height(8.dp))
            TextButton(onClick = onOpenSetupGuide, modifier = Modifier.fillMaxWidth()) {
                Text("Don't have Hermes yet? Setup guide")
            }
        }
    }
}

private enum class InfoTone { WARNING, ERROR }

@Composable
private fun InfoBox(text: String, tone: InfoTone) {
    val container = when (tone) {
        InfoTone.WARNING -> MaterialTheme.colorScheme.secondaryContainer
        InfoTone.ERROR -> MaterialTheme.colorScheme.errorContainer
    }
    val onContainer = when (tone) {
        InfoTone.WARNING -> MaterialTheme.colorScheme.onSecondaryContainer
        InfoTone.ERROR -> MaterialTheme.colorScheme.onErrorContainer
    }
    Surface(color = container, shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(14.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(Icons.Filled.Info, contentDescription = null, tint = onContainer, modifier = Modifier.height(20.dp))
            Text(text, style = MaterialTheme.typography.bodyMedium, color = onContainer)
        }
    }
}
