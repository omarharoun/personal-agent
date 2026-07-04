package com.personalagent.android.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val fmt = SimpleDateFormat("EEE d MMM, HH:mm", Locale.getDefault())

/**
 * Reminders, backed by the user's Hermes (`/api/jobs`). Create a one-shot
 * reminder here or just ask in chat ("remind me to call my sister Sunday") — both
 * land in the same place, and the app delivers a local notification when due.
 */
@Composable
fun RemindersScreen(vm: RemindersViewModel) {
    val state by vm.state.collectAsStateWithLifecycle()
    var title by remember { mutableStateOf("") }
    var minutesFromNow by remember { mutableLongStateOf(60L) }

    val choices = listOf(
        "1 min" to 1L,
        "1 hour" to 60L,
        "3 hours" to 180L,
        "Tomorrow" to 24L * 60L,
        "Next week" to 7L * 24L * 60L,
    )

    Column(Modifier.fillMaxSize().padding(top = 12.dp)) {
        OutlinedTextField(
            value = title,
            onValueChange = { title = it },
            label = { Text("Remind me to…") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        Row(
            Modifier.fillMaxWidth().padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            choices.forEach { (label, m) ->
                AssistChip(
                    onClick = { minutesFromNow = m },
                    label = { Text(label) },
                    colors = if (minutesFromNow == m)
                        AssistChipDefaults.assistChipColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        )
                    else AssistChipDefaults.assistChipColors(),
                )
            }
        }

        Button(
            onClick = { vm.create(title, minutesFromNow); title = "" },
            enabled = title.isNotBlank(),
            modifier = Modifier.padding(top = 12.dp),
        ) { Text("Set reminder") }

        state.error?.let {
            Text(
                it,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 12.dp),
            )
        }

        if (state.loading && state.reminders.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else if (state.reminders.isEmpty() && state.error == null) {
            Text(
                "No reminders yet. Set one above, or just ask your agent in chat.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 24.dp),
            )
        } else {
            LazyColumn(
                Modifier.fillMaxWidth().padding(top = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(state.reminders, key = { it.id }) { r ->
                    Card(Modifier.fillMaxWidth()) {
                        Row(
                            Modifier.fillMaxWidth().padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(r.label, fontWeight = FontWeight.SemiBold)
                                val whenText = r.nextRunAtMillis?.let { fmt.format(Date(it)) }
                                    ?: r.scheduleDisplay ?: "scheduled"
                                Text(
                                    whenText,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(top = 2.dp),
                                )
                            }
                            IconButton(onClick = { vm.delete(r.id) }) {
                                Icon(Icons.Filled.Delete, contentDescription = "Cancel reminder")
                            }
                        }
                    }
                }
            }
        }
    }
}
