package com.personalagent.android.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.personalagent.android.AppContainer
import com.personalagent.android.ui.onboarding.AiModelSetupScreen
import com.personalagent.android.ui.onboarding.ModelSetupMode
import com.personalagent.android.ui.onboarding.ModelSetupViewModel

/**
 * Settings tab. For now its single job is the on-device AI model entry: users
 * who skipped onboarding (or want to change models) can provision, replace, or
 * delete the model here.
 */
@Composable
fun SettingsScreen(container: AppContainer, modifier: Modifier = Modifier) {
    val vm: ModelSetupViewModel =
        viewModel(factory = ModelSetupViewModel.Factory(container))
    AiModelSetupScreen(
        vm = vm,
        mode = ModelSetupMode.SETTINGS,
        modifier = modifier.fillMaxSize(),
    )
}
