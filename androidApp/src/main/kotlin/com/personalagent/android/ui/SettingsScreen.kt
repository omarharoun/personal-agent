package com.personalagent.android.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.personalagent.android.AppContainer
import com.personalagent.android.ui.theme.ThemeMode
import com.personalagent.shared.appearance.AccentPalette

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
    accentId: String,
    onAccentChange: (String) -> Unit,
    onDisconnect: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        HermesSection(container, onDisconnect)
        HorizontalDivider()
        AppearanceSection(themeMode, onThemeModeChange, accentId, onAccentChange)
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AppearanceSection(
    themeMode: ThemeMode,
    onThemeModeChange: (ThemeMode) -> Unit,
    accentId: String,
    onAccentChange: (String) -> Unit,
) {
    val dark = when (themeMode) {
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
        ThemeMode.SYSTEM -> androidx.compose.foundation.isSystemInDarkTheme()
    }
    Column(Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
        Text("Appearance", style = MaterialTheme.typography.titleMedium)

        Text(
            "Mode",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 10.dp),
        )
        Row(
            Modifier.fillMaxWidth().padding(top = 6.dp),
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

        Text(
            "Accent color",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 16.dp),
        )
        Text(
            "Applies to highlights and active states, in both light and dark.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 2.dp),
        )
        FlowRow(
            Modifier.fillMaxWidth().padding(top = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            AccentPalette.OPTIONS.forEach { opt ->
                val rgb = if (dark) opt.darkRgb else opt.lightRgb
                val swatch = Color(0xFF000000L or (rgb and 0xFFFFFF))
                val selected = opt.id == accentId
                Box(
                    Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(swatch)
                        .border(
                            width = if (selected) 3.dp else 1.dp,
                            color = if (selected) MaterialTheme.colorScheme.onBackground
                            else MaterialTheme.colorScheme.outline,
                            shape = CircleShape,
                        )
                        .clickable { onAccentChange(opt.id) }
                        .semantics { contentDescription = opt.name + if (selected) ", selected" else "" },
                    contentAlignment = Alignment.Center,
                ) {
                    if (selected) {
                        Box(
                            Modifier.size(10.dp).clip(CircleShape)
                                .background(Color(0xFF000000L or (opt.let { if (dark) it.onDarkRgb else it.onLightRgb } and 0xFFFFFF))),
                        )
                    }
                }
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
