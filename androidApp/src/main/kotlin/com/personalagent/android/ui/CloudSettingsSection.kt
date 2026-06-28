package com.personalagent.android.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Button
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.personalagent.android.AppContainer
import com.personalagent.shared.cloud.CloudKeyStore
import com.personalagent.shared.cloud.CloudProvider

/**
 * Self-contained Settings sub-view for the **bring-your-own-key cloud option**
 * (Stream 3). The integrator embeds this in the Settings surface — it does NOT
 * touch `AppScreen`.
 *
 * Lets the user:
 *   - pick the active provider (Anthropic / OpenAI),
 *   - paste an API key into a MASKED field,
 *   - Save (persists encrypted via [CloudKeyStore]) or Clear it.
 *
 * 🔒 The key is written straight into the encrypted [CloudKeyStore]; it is never
 * logged and the field is password-masked. With no key set the app stays fully
 * on-device. A newly-saved key takes effect **immediately** — the cloud client is
 * resolved per-use (see `DynamicCloudClient`), so no restart is required.
 */
@Composable
fun CloudSettingsSection(
    container: AppContainer,
    modifier: Modifier = Modifier,
) {
    CloudSettingsSection(keyStore = container.cloudKeyStore, modifier = modifier)
}

@Composable
fun CloudSettingsSection(
    keyStore: CloudKeyStore,
    modifier: Modifier = Modifier,
) {
    // Selected provider in the UI (defaults to the saved active provider, else Anthropic).
    var provider by remember {
        mutableStateOf(keyStore.activeProvider() ?: CloudProvider.ANTHROPIC)
    }
    // Masked key field. Pre-filled blank; we never echo a stored key back into the UI.
    var keyInput by remember { mutableStateOf("") }
    var status by remember { mutableStateOf<String?>(null) }

    // Reflect whether the *selected* provider currently has a saved key.
    var hasSavedKey by remember { mutableStateOf(keyStore.hasKey(provider)) }
    LaunchedEffect(provider) {
        hasSavedKey = keyStore.hasKey(provider)
        status = null
        keyInput = ""
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Cloud assist (your API key)", style = MaterialTheme.typography.titleMedium)

            Text(
                "Uses YOUR developer API key, billed separately by Anthropic/OpenAI. " +
                    "A Claude Pro or ChatGPT Plus consumer subscription CANNOT be used here. " +
                    "If no key is set, the app stays fully on-device.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            // Provider picker.
            Column(Modifier.selectableGroup()) {
                CloudProvider.entries.forEach { p ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .selectable(
                                selected = provider == p,
                                role = Role.RadioButton,
                                onClick = { provider = p },
                            )
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = provider == p, onClick = null)
                        Text(
                            text = p.displayName,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.padding(start = 8.dp),
                        )
                    }
                }
            }

            Text(
                if (hasSavedKey) {
                    "A key is saved for ${provider.displayName}. Enter a new one to replace it."
                } else {
                    "No key saved for ${provider.displayName}."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            // Masked API-key field.
            OutlinedTextField(
                value = keyInput,
                onValueChange = { keyInput = it },
                label = { Text("API key") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth(),
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = {
                        val trimmed = keyInput.trim()
                        if (trimmed.isEmpty()) {
                            status = "Enter a key to save."
                            return@Button
                        }
                        keyStore.setApiKey(provider, trimmed)
                        keyStore.setActiveProvider(provider)
                        keyInput = ""
                        hasSavedKey = true
                        status = "Saved — cloud assist is ready. No restart needed."
                    },
                ) { Text("Save") }

                OutlinedButton(
                    onClick = {
                        keyStore.clearApiKey(provider)
                        if (keyStore.activeProvider() == provider) {
                            keyStore.clearActiveProvider()
                        }
                        keyInput = ""
                        hasSavedKey = false
                        status = "Cleared. The app is fully on-device for ${provider.displayName}."
                    },
                ) { Text("Clear") }
            }

            status?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}
