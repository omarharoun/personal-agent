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
import com.personalagent.android.ui.RecoverySetupScreen
import com.personalagent.android.ui.SafetyViewModel

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

                // 🔒 Step 5: gate the app behind first-run recovery-code setup. Until
                // the user has been shown and confirmed their recovery code, the only
                // screen available is RecoverySetupScreen.
                var setupComplete by remember { mutableStateOf(container.securitySetup.isComplete()) }
                if (!setupComplete) {
                    RecoverySetupScreen(onConfirmed = { code ->
                        container.securitySetup.complete(code)
                        setupComplete = true
                    })
                } else {
                    val vm: AppViewModel = viewModel(factory = AppViewModel.Factory(container))
                    // 🔒 Step 7: consent-first crisis-safety surface (autonomous action disabled).
                    val safetyVm: SafetyViewModel =
                        viewModel(factory = SafetyViewModel.Factory(container))
                    AppScreen(vm, safetyVm)
                }
            }
        }
    }
}
