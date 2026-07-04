package com.personalagent.android.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.personalagent.shared.hermes.LifePrompts

/**
 * Life-improvement layer: the user defines what "better" means to them (goals),
 * and the agent — drawing on its real memory of the user — offers a personalized
 * nudge. This is interaction design over Hermes (see [LifePrompts]); the agent is
 * the brain and holds the memory.
 */
@Composable
fun GoalsScreen(vm: GoalsViewModel) {
    val state by vm.state.collectAsStateWithLifecycle()
    var category by remember { mutableStateOf(LifePrompts.GOAL_CATEGORIES.first()) }
    var draft by remember { mutableStateOf("") }

    LaunchedEffect(Unit) { vm.refreshGoals() }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(top = 12.dp),
    ) {
        Text(
            "What does \"better\" look like for you? Set a goal and your agent will keep " +
                "it in mind and help you toward it over time.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(Modifier.height(12.dp))
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            LifePrompts.GOAL_CATEGORIES.forEach { c ->
                FilterChip(
                    selected = category == c,
                    onClick = { category = c },
                    label = { Text(c) },
                )
            }
        }

        OutlinedTextField(
            value = draft,
            onValueChange = { draft = it },
            label = { Text("Your goal") },
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            minLines = 2,
        )
        Button(
            onClick = { vm.addGoal(category, draft); draft = "" },
            enabled = draft.isNotBlank() && !state.saving,
            modifier = Modifier.padding(top = 8.dp),
        ) { Text("Add goal") }

        // --- Personalized nudge -------------------------------------------------
        Spacer(Modifier.height(20.dp))
        Text("Encouragement", style = MaterialTheme.typography.titleMedium)
        Text(
            "A nudge grounded in what your agent actually remembers about you.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedButton(
            onClick = { vm.getNudge() },
            enabled = !state.loadingNudge,
            modifier = Modifier.padding(top = 8.dp),
        ) { Text(if (state.loadingNudge) "Thinking…" else "Get a nudge") }

        if (state.nudge.isNotBlank()) {
            Card(
                Modifier.fillMaxWidth().padding(top = 8.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
            ) {
                MarkdownText(
                    text = state.nudge,
                    modifier = Modifier.padding(16.dp),
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            }
        }

        // --- Current goals ------------------------------------------------------
        Spacer(Modifier.height(24.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Your goals", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            if (state.loadingGoals) CircularProgressIndicator(Modifier.height(18.dp))
        }
        Spacer(Modifier.height(8.dp))
        if (state.goalsSummary.isNotBlank()) {
            MarkdownText(text = state.goalsSummary, modifier = Modifier.fillMaxWidth())
        } else if (!state.loadingGoals) {
            Text(
                "No goals yet — add one above.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(24.dp))
    }
}
