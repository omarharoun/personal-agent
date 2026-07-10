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
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.personalagent.shared.learning.LearningGoal

/**
 * Phase 6 — the Learning Guide (Step 1: declare + list goals). The user says what
 * they want to learn in their own words; the agent remembers it and (Step 2) will
 * recommend the next right FREE open-web resource. Goals live in the local store
 * (authoritative, instant) and are mirrored to Hermes memory as the current focus.
 */
@Composable
fun LearningScreen(vm: LearningViewModel) {
    val state by vm.state.collectAsStateWithLifecycle()
    var topic by remember { mutableStateOf("") }
    var why by remember { mutableStateOf("") }
    var level by remember { mutableStateOf(vm.levels.first()) }
    var style by remember { mutableStateOf("") }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(top = 12.dp),
    ) {
        Text(
            "Tell your agent what you want to get better at. It draws on what it knows " +
                "about you and searches the free, open web to point you at the next right thing " +
                "to learn — one honest suggestion at a time, never a listicle.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        // Honest availability banner — never fail silently (Step 0 requirement).
        if (state.webAvailable == false) {
            Spacer(Modifier.height(12.dp))
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    "Web search is unavailable on your Hermes. You can still set goals, but to get " +
                        "recommendations, enable a web-search backend in your Hermes config.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.padding(12.dp),
                )
            }
        }

        Spacer(Modifier.height(16.dp))
        OutlinedTextField(
            value = topic,
            onValueChange = { topic = it },
            label = { Text("What do you want to learn?") },
            modifier = Modifier.fillMaxWidth(),
            minLines = 1,
        )
        OutlinedTextField(
            value = why,
            onValueChange = { why = it },
            label = { Text("Why does it matter to you? (optional)") },
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            minLines = 1,
        )

        Spacer(Modifier.height(10.dp))
        Text("Where are you starting from?", style = MaterialTheme.typography.labelLarge)
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(top = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            vm.levels.forEach { l ->
                FilterChip(selected = level == l, onClick = { level = l }, label = { Text(l) })
            }
        }

        OutlinedTextField(
            value = style,
            onValueChange = { style = it },
            label = { Text("How do you like to learn? (optional — e.g. videos, hands-on)") },
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            minLines = 1,
        )

        Button(
            onClick = {
                vm.addGoal(topic, why, level, style)
                topic = ""; why = ""; style = ""
            },
            enabled = topic.isNotBlank() && !state.saving,
            modifier = Modifier.padding(top = 10.dp),
        ) { Text("Add learning goal") }

        state.message?.let {
            Spacer(Modifier.height(6.dp))
            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        // --- Current learning goals --------------------------------------------
        Spacer(Modifier.height(24.dp))
        Text("What you're learning", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))

        val active = state.goals.filter { it.active }
        if (active.isEmpty()) {
            Text(
                "No learning goals yet — add one above.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            active.forEach { goal ->
                GoalCard(goal, onArchive = { vm.archiveGoal(goal.id) })
                Spacer(Modifier.height(10.dp))
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun GoalCard(goal: LearningGoal, onArchive: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp)) {
            Text(goal.topic, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            goal.why?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            val meta = listOfNotNull(
                goal.level?.let { "Level: $it" },
                goal.style?.let { "Prefers: $it" },
            ).joinToString("  ·  ")
            if (meta.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(meta, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onArchive) { Text("Archive") }
            }
        }
    }
}
