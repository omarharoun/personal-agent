package com.personalagent.android.ui.onboarding

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.personalagent.shared.provisioning.ModelOption
import com.personalagent.shared.provisioning.ProvisionState

/**
 * The "Set up your AI" surface. Shows the curated, trusted model options
 * (name, size, quant, license + link, gated badge), a Wi-Fi-only toggle
 * (default on), the trusted-source/verified note, and drives provisioning with a
 * real progress bar. Used both in onboarding (with a "Skip for now") and from
 * Settings (with Replace / Delete once a model is installed).
 *
 * @param mode ONBOARDING shows Skip + Done affordances; SETTINGS shows manage
 *   actions for an already-set-up model.
 * @param onSkip onboarding only — proceed without AI (the app fully works).
 * @param onDone advance once a model is installed (onboarding) / dismiss (settings).
 */
enum class ModelSetupMode { ONBOARDING, SETTINGS }

@Composable
fun AiModelSetupScreen(
    vm: ModelSetupViewModel,
    mode: ModelSetupMode,
    onSkip: () -> Unit = {},
    onDone: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val ps = state.provisionState

    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 16.dp),
    ) {
        Text("Set up your AI", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(8.dp))
        Text(
            "The app works fully without AI. To turn on on-device features, install " +
                "a small language model. It runs entirely on your phone — your data " +
                "stays on-device.",
            style = MaterialTheme.typography.bodyMedium,
        )

        Spacer(Modifier.height(12.dp))
        TrustedSourceNote()

        Spacer(Modifier.height(16.dp))
        Text("Choose a model", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        state.options.forEach { option ->
            ModelOptionCard(
                option = option,
                selected = option.id == state.selected.id,
                installed = option.id == state.installedOptionId,
                enabled = !state.isWorking,
                onSelect = { vm.selectOption(option) },
            )
            Spacer(Modifier.height(8.dp))
        }

        Spacer(Modifier.height(8.dp))
        WifiToggle(
            wifiOnly = state.wifiOnly,
            enabled = !state.isWorking,
            onToggle = vm::setWifiOnly,
        )

        Spacer(Modifier.height(8.dp))
        // Honest, size-stated download note.
        Text(
            "This model is about ${formatBytes(state.selected.sizeBytes)}. It downloads once and " +
                "is stored on your device. The download is verified before the model " +
                "is used." +
                if (state.selected.requiresProviderAuth) {
                    "\n\nThis is a gated model: you must accept the provider's license " +
                        "(“${state.selected.licenseName}”) on their site before downloading."
                } else "",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(Modifier.height(16.dp))
        ProvisionStatus(ps)

        Spacer(Modifier.height(16.dp))
        Actions(
            state = state,
            mode = mode,
            onDownload = vm::download,
            onRetry = vm::retry,
            onCancel = vm::cancel,
            onDelete = vm::delete,
            onSkip = onSkip,
            onDone = onDone,
        )
    }
}

@Composable
private fun TrustedSourceNote() {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
        ),
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Verified, contentDescription = null)
            Spacer(Modifier.width(12.dp))
            Text(
                "Models are downloaded from a trusted source and verified " +
                    "(checksum) before they're ever used.",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
private fun ModelOptionCard(
    option: ModelOption,
    selected: Boolean,
    installed: Boolean,
    enabled: Boolean,
    onSelect: () -> Unit,
) {
    val context = LocalContext.current
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = selected, enabled = enabled, onClick = onSelect),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) {
                MaterialTheme.colorScheme.surfaceVariant
            } else {
                MaterialTheme.colorScheme.surface
            },
        ),
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            RadioButton(selected = selected, enabled = enabled, onClick = onSelect)
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        option.displayName,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    if (installed) {
                        Spacer(Modifier.width(8.dp))
                        Icon(
                            Icons.Filled.CheckCircle,
                            contentDescription = "Installed",
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
                Text(
                    "${formatBytes(option.sizeBytes)} · ${option.quant}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (option.requiresProviderAuth) {
                        AssistChip(
                            onClick = {},
                            enabled = false,
                            leadingIcon = {
                                Icon(Icons.Filled.Lock, contentDescription = null)
                            },
                            label = { Text("License required") },
                        )
                        Spacer(Modifier.width(8.dp))
                    }
                    TextButton(onClick = {
                        context.startActivity(
                            Intent(Intent.ACTION_VIEW, Uri.parse(option.licenseUrl)),
                        )
                    }) {
                        Text(option.licenseName)
                    }
                }
            }
        }
    }
}

@Composable
private fun WifiToggle(wifiOnly: Boolean, enabled: Boolean, onToggle: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(Modifier.weight(1f)) {
            Text("Download over Wi-Fi only", style = MaterialTheme.typography.bodyLarge)
            Text(
                "Avoids using mobile data for the large download.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked = wifiOnly, onCheckedChange = onToggle, enabled = enabled)
    }
}

@Composable
private fun ProvisionStatus(ps: ProvisionState) {
    when (ps) {
        is ProvisionState.Idle -> Unit
        is ProvisionState.Downloading -> {
            val frac: Float? =
                if (ps.totalBytes > 0L) {
                    (ps.downloadedBytes.toFloat() / ps.totalBytes).coerceIn(0f, 1f)
                } else {
                    null
                }
            Text(
                if (frac != null) {
                    "Downloading… ${(frac * 100).toInt()}%"
                } else {
                    "Downloading…"
                },
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(Modifier.height(8.dp))
            if (frac != null) {
                LinearProgressIndicator(
                    progress = { frac },
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                LinearProgressIndicator(Modifier.fillMaxWidth())
            }
        }
        is ProvisionState.Verifying -> {
            Text("Verifying…", style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(8.dp))
            LinearProgressIndicator(Modifier.fillMaxWidth())
        }
        is ProvisionState.Installed -> {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Filled.CheckCircle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    "Installed and verified. On-device AI is ready.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
        is ProvisionState.Failed -> {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                ),
            ) {
                Text(
                    ps.reason,
                    modifier = Modifier.padding(12.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
            }
        }
    }
}

@Composable
private fun Actions(
    state: ModelSetupUiState,
    mode: ModelSetupMode,
    onDownload: () -> Unit,
    onRetry: () -> Unit,
    onCancel: () -> Unit,
    onDelete: () -> Unit,
    onSkip: () -> Unit,
    onDone: () -> Unit,
) {
    val ps = state.provisionState
    when {
        state.isWorking -> {
            OutlinedButton(onClick = onCancel, modifier = Modifier.fillMaxWidth()) {
                Text("Cancel")
            }
        }
        ps is ProvisionState.Failed -> {
            Button(onClick = onRetry, modifier = Modifier.fillMaxWidth()) {
                Text("Retry download")
            }
            if (mode == ModelSetupMode.ONBOARDING) {
                Spacer(Modifier.height(8.dp))
                TextButton(onClick = onSkip, modifier = Modifier.fillMaxWidth()) {
                    Text("Skip for now")
                }
            }
        }
        state.isInstalled -> {
            if (mode == ModelSetupMode.ONBOARDING) {
                Button(onClick = onDone, modifier = Modifier.fillMaxWidth()) {
                    Text("Continue")
                }
            } else {
                Button(onClick = onDownload, modifier = Modifier.fillMaxWidth()) {
                    Text("Replace model")
                }
                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = onDelete, modifier = Modifier.fillMaxWidth()) {
                    Text("Delete model")
                }
            }
        }
        else -> {
            Button(onClick = onDownload, modifier = Modifier.fillMaxWidth()) {
                Text("Download")
            }
            if (mode == ModelSetupMode.ONBOARDING) {
                Spacer(Modifier.height(8.dp))
                TextButton(onClick = onSkip, modifier = Modifier.fillMaxWidth()) {
                    Text("Skip for now")
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    "You can set this up later in Settings.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/**
 * Human-readable size for a [ModelOption.sizeBytes] (decimal MB/GB, matching how
 * download sizes are usually shown). The canonical contract carries raw bytes, so
 * the UI formats them here.
 */
private fun formatBytes(bytes: Long): String {
    val mb = bytes.toDouble() / 1_000_000.0
    return if (mb >= 1000.0) {
        "%.1f GB".format(mb / 1000.0)
    } else {
        "%.0f MB".format(mb)
    }
}
