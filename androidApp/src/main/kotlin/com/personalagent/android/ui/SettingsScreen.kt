package com.personalagent.android.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.personalagent.android.AppContainer
import com.personalagent.android.ui.onboarding.AiModelSetupScreen
import com.personalagent.android.ui.onboarding.ModelSetupMode
import com.personalagent.android.ui.onboarding.ModelSetupViewModel

/**
 * Settings, opened from the gear in the conversational surface. Two things:
 *  1. **Cloud (bring-your-own API key)** — choose Anthropic/OpenAI + paste a key
 *     (stored encrypted). Off by default → the app stays fully on-device.
 *  2. **On-device AI model** — provision / replace / delete the local model.
 *
 * The cloud section is short and sits up top; the model-setup screen takes the
 * remaining space and scrolls internally (so the two don't nest scrolls).
 */
@Composable
fun SettingsScreen(container: AppContainer, modifier: Modifier = Modifier) {
    val vm: ModelSetupViewModel =
        viewModel(factory = ModelSetupViewModel.Factory(container))

    Column(modifier.fillMaxSize()) {
        CloudSettingsSection(container)
        HorizontalDivider()
        AiModelSetupScreen(
            vm = vm,
            mode = ModelSetupMode.SETTINGS,
            modifier = Modifier.weight(1f).fillMaxWidth(),
        )
    }
}
