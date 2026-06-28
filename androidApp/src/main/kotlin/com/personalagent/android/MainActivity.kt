package com.personalagent.android

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewmodel.compose.viewModel
import com.personalagent.android.ui.AppScreen
import com.personalagent.android.ui.AppViewModel
import com.personalagent.android.ui.SafetyViewModel
import com.personalagent.android.ui.onboarding.OnboardingFlow

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val container = (application as PersonalAgentApp).container

        setContent {
            MaterialTheme {
                // Ask for notification permission once (Android 13+/API 33).
                val permissionLauncher = rememberLauncherForActivityResult(
                    ActivityResultContracts.RequestPermission(),
                ) { /* result handled by the OS; notifier no-ops if denied */ }

                LaunchedEffect(Unit) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                }

                // First-run onboarding: Welcome → recovery-code setup (Step 5) →
                // AI model setup (new) → Done. Shown once; gates app entry. The
                // recovery-code step inside the flow is still the Step-5 security
                // gate (it persists the verifier before the AI step runs).
                var onboardingComplete by remember {
                    mutableStateOf(container.onboarding.isComplete())
                }
                if (!onboardingComplete) {
                    OnboardingFlow(container = container, onFinished = {
                        container.onboarding.complete()
                        onboardingComplete = true
                    })
                } else {
                    val vm: AppViewModel = viewModel(factory = AppViewModel.Factory(container))
                    // 🔒 Step 7: consent-first crisis-safety surface (autonomous action disabled).
                    val safetyVm: SafetyViewModel =
                        viewModel(factory = SafetyViewModel.Factory(container))
                    AppScreen(vm, safetyVm, container)
                }
            }
        }
    }
}
