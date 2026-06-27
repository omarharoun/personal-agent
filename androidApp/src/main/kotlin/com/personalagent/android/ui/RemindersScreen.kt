package com.personalagent.android.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import com.personalagent.shared.model.ReminderStatus
import com.personalagent.shared.util.SystemClock
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val fmt = SimpleDateFormat("EEE d MMM, HH:mm", Locale.getDefault())

@Composable
fun RemindersScreen(state: UiState, vm: AppViewModel) {
    var title by remember { mutableStateOf("") }
    var minutesFromNow by remember { mutableLongStateOf(1L) }

    Column(Modifier.fillMaxWidth().padding(top = 12.dp)) {
        OutlinedTextField(
            value = title,
            onValueChange = { title = it },
            label = { Text("Reminder title") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        // Quick "fire in N minutes" choices — keeps Step 1 simple while still
        // letting you verify a reminder actually fires (pick 1 min).
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(1L, 5L, 60L, 24L * 60L).forEach { m ->
                AssistChip(
                    onClick = { minutesFromNow = m },
                    label = { Text(if (m >= 60) "${m / 60}h" else "${m}m") },
                )
            }
        }
        Text(
            "Fires in $minutesFromNow min",
            modifier = Modifier.padding(top = 6.dp),
        )

        Button(
            onClick = {
                val triggerAt = SystemClock.nowMillis() + minutesFromNow * 60_000L
                vm.scheduleReminder(title, triggerAt)
                title = ""
            },
            modifier = Modifier.padding(top = 8.dp),
        ) { Text("Set reminder") }

        LazyColumn(Modifier.fillMaxWidth().padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(state.reminders, key = { it.id }) { r ->
                Card(Modifier.fillMaxWidth()) {
                    Row(
                        Modifier.fillMaxWidth().padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(r.title, fontWeight = FontWeight.SemiBold)
                            Text(
                                fmt.format(Date(r.triggerAtMillis)) + "  •  " + r.status.label(),
                                modifier = Modifier.padding(top = 2.dp),
                            )
                        }
                        if (r.status == ReminderStatus.SCHEDULED) {
                            IconButton(onClick = { vm.cancelReminder(r.id) }) {
                                Icon(Icons.Filled.Delete, contentDescription = "Cancel")
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun ReminderStatus.label(): String = when (this) {
    ReminderStatus.SCHEDULED -> "Scheduled"
    ReminderStatus.FIRED -> "Fired"
    ReminderStatus.CANCELLED -> "Cancelled"
}
