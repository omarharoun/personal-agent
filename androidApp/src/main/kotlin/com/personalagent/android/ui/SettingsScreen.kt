package com.personalagent.android.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.personalagent.android.AppContainer
import com.personalagent.android.ui.onboarding.AiModelSetupScreen
import com.personalagent.android.ui.onboarding.ModelSetupMode
import com.personalagent.android.ui.onboarding.ModelSetupViewModel
import com.personalagent.android.ui.theme.ThemeMode

/**
 * Settings, opened from the drawer. Sections, top to bottom:
 *  1. **Appearance** — Dark (default) / Light / System.
 *  2. **Cloud (bring-your-own API key)** — Anthropic/OpenAI + a key (stored
 *     encrypted). Off by default → the app stays fully on-device.
 *  3. **On-device AI model** — provision / replace / delete the local model
 *     (takes the remaining space and scrolls internally).
 *  4. **About** — the build/version label, so the running build is identifiable.
 */
@Composable
fun SettingsScreen(
    container: AppContainer,
    themeMode: ThemeMode,
    onThemeModeChange: (ThemeMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    val vm: ModelSetupViewModel =
        viewModel(factory = ModelSetupViewModel.Factory(container))

    Column(modifier.fillMaxSize()) {
        AppearanceSection(themeMode, onThemeModeChange)
        HorizontalDivider()
        CloudSettingsSection(container)
        HorizontalDivider()
        AiModelSetupScreen(
            vm = vm,
            mode = ModelSetupMode.SETTINGS,
            modifier = Modifier.weight(1f).fillMaxWidth(),
        )
        HorizontalDivider()
        AboutSection()
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
            "Personal Agent — $version",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}
