package com.personalagent.android.ui

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
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.personalagent.shared.hermes.ReflectionCadence

/**
 * Phase 4 — gentle, optional reflection. Pick a cadence (or Off), get a warm,
 * memory-grounded reflection on demand, and snooze in one tap. Designed to feel
 * like a friend checking in — never a nag; opting out is a single tap.
 */
@Composable
fun ReflectionScreen(vm: ReflectionViewModel) {
    val state by vm.state.collectAsStateWithLifecycle()

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(top = 12.dp),
    ) {
        Text(
            "A gentle, private check-in with yourself — personalized by what your agent " +
                "remembers about you. Entirely optional, and easy to pause any time.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(Modifier.height(16.dp))
        Text("How often?", style = MaterialTheme.typography.titleMedium)
        Row(
            Modifier.fillMaxWidth().padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ReflectionCadence.entries.forEach { c ->
                FilterChip(
                    selected = state.cadence == c,
                    onClick = { vm.setCadence(c) },
                    label = { Text(c.label) },
                )
            }
        }

        Spacer(Modifier.height(24.dp))
        Button(
            onClick = { vm.reflectNow() },
            enabled = !state.loading,
            modifier = Modifier.fillMaxWidth(),
        ) { Text(if (state.loading) "Reflecting…" else "Reflect now") }

        // Surface any failure so a tap never silently does nothing.
        state.message?.let { msg ->
            Text(
                msg,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(top = 12.dp),
            )
        }

        if (state.reflection.isNotBlank()) {
            Card(
                Modifier.fillMaxWidth().padding(top = 16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
            ) {
                Column(Modifier.padding(16.dp)) {
                    MarkdownText(
                        text = state.reflection,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                }
            }
        }

        if (state.cadence != ReflectionCadence.OFF) {
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = { vm.snooze(7) }) { Text("Snooze a week") }
                TextButton(onClick = { vm.setCadence(ReflectionCadence.OFF) }) { Text("Turn off") }
            }
        }
    }
}
