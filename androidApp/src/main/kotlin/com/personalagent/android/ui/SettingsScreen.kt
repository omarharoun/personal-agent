package com.personalagent.android.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.personalagent.android.AppContainer
import com.personalagent.android.ui.theme.ThemeMode

/**
 * Settings for the Hermes client. Sections:
 *  1. **Your Hermes** — the connected address + a way to change/disconnect it.
 *  2. **Appearance** — Dark (default) / Light / System.
 *  3. **About** — build/version + the privacy posture (data lives on your Hermes).
 */
@Composable
fun SettingsScreen(
    container: AppContainer,
    themeMode: ThemeMode,
    onThemeModeChange: (ThemeMode) -> Unit,
    onDisconnect: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        HermesSection(container, onDisconnect)
        HorizontalDivider()
        AppearanceSection(themeMode, onThemeModeChange)
        HorizontalDivider()
        AboutSection()
    }
}

@Composable
private fun HermesSection(container: AppContainer, onDisconnect: () -> Unit) {
    val baseUrl = remember { container.hermesConfigStore.load()?.baseUrl }
    Column(Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
        Text("Your Hermes", style = MaterialTheme.typography.titleMedium)
        Text(
            baseUrl ?: "Not connected",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp),
        )
        Text(
            "Your conversations, notes, and reminders live on this server — not on any " +
                "server we control. This device stores only the connection (encrypted).",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 6.dp),
        )
        OutlinedButton(onClick = onDisconnect, modifier = Modifier.padding(top = 10.dp)) {
            Text("Change / disconnect")
        }
    }
}

@Composable
private fun AppearanceSection(themeMode: ThemeMode, onThemeModeChange: (ThemeMode) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
        Text("Appearance", style = MaterialTheme.typography.titleMedium)
        Row(
            Modifier.fillMaxWidth().padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            data class Opt(val mode: ThemeMode, val label: String)
            listOf(
                Opt(ThemeMode.DARK, "Dark"),
                Opt(ThemeMode.LIGHT, "Light"),
                Opt(ThemeMode.SYSTEM, "System"),
            ).forEach { opt ->
                FilterChip(
                    selected = themeMode == opt.mode,
                    onClick = { onThemeModeChange(opt.mode) },
                    label = { Text(opt.label) },
                )
            }
        }
    }
}

@Composable
private fun AboutSection() {
    val ctx = LocalContext.current
    val version = remember {
        runCatching {
            val pi = ctx.packageManager.getPackageInfo(ctx.packageName, 0)
            "v${pi.versionName} (build ${pi.longVersionCode})"
        }.getOrDefault("version unavailable")
    }
    Column(Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
        Text("About", style = MaterialTheme.typography.titleMedium)
        Text(
            "Life Agent — $version",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}
