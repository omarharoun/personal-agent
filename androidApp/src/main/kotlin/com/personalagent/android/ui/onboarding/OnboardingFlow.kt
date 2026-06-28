package com.personalagent.android.ui.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.personalagent.android.AppContainer
import com.personalagent.android.ui.RecoverySetupScreen

/**
 * First-run onboarding flow, shown ONCE (gated by [AppContainer.onboarding]).
 *
 * Order: Welcome → Recovery-code setup (existing Step-5 screen) → Set up your AI
 * (new) → Done. A progress indicator runs across the steps. The AI step is fully
 * skippable — the app works without it.
 *
 * @param onFinished called after the user completes the final step; the caller
 *   marks onboarding complete and enters the app.
 */
@Composable
fun OnboardingFlow(container: AppContainer, onFinished: () -> Unit) {
    val steps = remember { OnboardingStep.entries }
    var index by rememberSaveable { mutableIntStateOf(0) }
    val step = steps[index]

    fun next() { if (index < steps.lastIndex) index++ else onFinished() }

    Scaffold(
        topBar = {
            OnboardingProgress(current = index, total = steps.size, title = step.title)
        },
    ) { inner ->
        Column(Modifier.fillMaxSize().padding(inner)) {
            when (step) {
                OnboardingStep.WELCOME -> WelcomeStep(onContinue = ::next)
                OnboardingStep.RECOVERY -> RecoverySetupScreen(onConfirmed = { code ->
                    container.securitySetup.complete(code)
                    next()
                })
                OnboardingStep.AI_SETUP -> {
                    val vm: ModelSetupViewModel =
                        viewModel(factory = ModelSetupViewModel.Factory(container))
                    AiModelSetupScreen(
                        vm = vm,
                        mode = ModelSetupMode.ONBOARDING,
                        onSkip = ::next,
                        onDone = ::next,
                    )
                }
                OnboardingStep.DONE -> DoneStep(onFinish = onFinished)
            }
        }
    }
}

private enum class OnboardingStep(val title: String) {
    WELCOME("Welcome"),
    RECOVERY("Recovery code"),
    AI_SETUP("Set up your AI"),
    DONE("All set"),
}

@Composable
private fun OnboardingProgress(current: Int, total: Int, title: String) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp)) {
        Text(
            "Step ${current + 1} of $total · $title",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
        LinearProgressIndicator(
            progress = { (current + 1).toFloat() / total },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun WelcomeStep(onContinue: () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text("Welcome to Personal Agent", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(16.dp))
        Text(
            "Your private, on-device assistant for notes, reminders, and plans. " +
                "Your data is encrypted and stays on your phone.\n\n" +
                "Next we'll set up a recovery code, then you can optionally install " +
                "an on-device AI model.",
            style = MaterialTheme.typography.bodyLarge,
        )
        Spacer(Modifier.height(32.dp))
        Button(onClick = onContinue, modifier = Modifier.fillMaxWidth()) {
            Text("Get started")
        }
    }
}

@Composable
private fun DoneStep(onFinish: () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            Icons.Filled.CheckCircle,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.height(16.dp))
        Text("You're all set", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(16.dp))
        Text(
            "You can install or change the on-device AI model any time from " +
                "Settings.",
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(32.dp))
        Button(onClick = onFinish, modifier = Modifier.fillMaxWidth()) {
            Text("Enter app")
        }
    }
}
