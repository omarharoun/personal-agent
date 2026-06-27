package com.personalagent.android.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.List
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

private enum class Tab(val label: String) { NOTES("Notes"), REMINDERS("Reminders"), PLAN("Plan") }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppScreen(vm: AppViewModel) {
    val state by vm.state.collectAsStateWithLifecycle()
    var tab by remember { mutableStateOf(Tab.NOTES) }
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(state.message) {
        state.message?.let {
            snackbar.showSnackbar(it)
            vm.consumeMessage()
        }
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Personal Agent") }) },
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = tab == Tab.NOTES,
                    onClick = { tab = Tab.NOTES },
                    icon = { Icon(Icons.Filled.List, contentDescription = null) },
                    label = { Text(Tab.NOTES.label) },
                )
                NavigationBarItem(
                    selected = tab == Tab.REMINDERS,
                    onClick = { tab = Tab.REMINDERS },
                    icon = { Icon(Icons.Filled.DateRange, contentDescription = null) },
                    label = { Text(Tab.REMINDERS.label) },
                )
                NavigationBarItem(
                    selected = tab == Tab.PLAN,
                    onClick = { tab = Tab.PLAN },
                    icon = { Icon(Icons.Filled.CheckCircle, contentDescription = null) },
                    label = { Text(Tab.PLAN.label) },
                )
            }
        },
    ) { inner ->
        Column(Modifier.fillMaxSize().padding(inner).padding(horizontal = 16.dp)) {
            when (tab) {
                Tab.NOTES -> NotesScreen(state, vm)
                Tab.REMINDERS -> RemindersScreen(state, vm)
                Tab.PLAN -> PlanScreen(state, vm)
            }
        }
    }
}
