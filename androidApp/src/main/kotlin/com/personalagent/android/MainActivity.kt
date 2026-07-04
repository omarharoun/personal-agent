package com.personalagent.android

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import com.personalagent.android.ui.theme.PersonalAgentTheme
import com.personalagent.android.ui.theme.ThemeMode
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import com.personalagent.android.ui.AppScreen
import com.personalagent.android.ui.AppViewModel
import com.personalagent.android.ui.SafetyViewModel
import com.personalagent.android.ui.connect.ConnectScreen
import com.personalagent.android.ui.connect.ConnectViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val container = (application as PersonalAgentApp).container

        setContent {
            // Appearance preference (dark by default). Persisted in a tiny prefs file
            // so the choice survives restarts; read once, then held in state.
            val ctx = LocalContext.current
            val uiPrefs = remember { ctx.getSharedPreferences("ui_prefs", MODE_PRIVATE) }
            var themeMode by remember {
                mutableStateOf(
                    runCatching { ThemeMode.valueOf(uiPrefs.getString("theme_mode", null) ?: "DARK") }
                        .getOrDefault(ThemeMode.DARK),
                )
            }
            val dark = when (themeMode) {
                ThemeMode.DARK -> true
                ThemeMode.LIGHT -> false
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
            }
            val onThemeModeChange: (ThemeMode) -> Unit = { mode ->
                themeMode = mode
                uiPrefs.edit().putString("theme_mode", mode.name).apply()
            }

            PersonalAgentTheme(darkTheme = dark) {
                // Ask for notification permission once (Android 13+/API 33).
                val permissionLauncher = rememberLauncherForActivityResult(
                    ActivityResultContracts.RequestPermission(),
                ) { /* result handled by the OS; notifier no-ops if denied */ }

                LaunchedEffect(Unit) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                }

                // 🔒 Path A (bring-your-own-Hermes): the app trusts ONLY the Hermes
                // the user configures. Until they've connected one, the Connect
                // screen is the entire app — there is no default/hidden backend.
                var connected by remember {
                    mutableStateOf(container.isHermesConfigured)
                }
                if (!connected) {
                    val connectVm: ConnectViewModel =
                        viewModel(factory = ConnectViewModel.Factory(container))
                    ConnectScreen(vm = connectVm, onConnected = {
                        connected = true
                        // Start delivering reminders now that we have a Hermes.
                        com.personalagent.android.notification.ReminderScheduling.ensurePeriodic(this@MainActivity)
                    })
                } else {
                    val vm: AppViewModel = viewModel(factory = AppViewModel.Factory(container))
                    // 🔒 Step 7: consent-first crisis-safety surface (autonomous action disabled).
                    val safetyVm: SafetyViewModel =
                        viewModel(factory = SafetyViewModel.Factory(container))
                    AppScreen(vm, safetyVm, container, themeMode, onThemeModeChange)
                }
            }
        }
    }
}
