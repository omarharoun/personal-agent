package com.personalagent.android.ui

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.personalagent.android.ui.theme.HermesText
import com.personalagent.shared.hermes.ReminderStatus
import com.personalagent.shared.hermes.ReminderView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val fmt = SimpleDateFormat("EEE d MMM, HH:mm", Locale.getDefault())

/**
 * Reminders, backed by the user's Hermes (`/api/jobs`) + a local history so
 * past/fired reminders stay visible with a clear status. Create one here or just
 * ask in chat — both land here, delivered as a local notification when due.
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
                    ReminderCard(r, onDismiss = { vm.dismiss(r) })
                }
            }
        }
    }
}

@Composable
private fun ReminderCard(r: ReminderView, onDismiss: () -> Unit) {
    val done = r.status == ReminderStatus.DONE
    Card(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatusBadge(r.status)
                    Text(
                        r.text,
                        fontWeight = FontWeight.SemiBold,
                        color = if (done) MaterialTheme.colorScheme.onSurfaceVariant
                        else MaterialTheme.colorScheme.onSurface,
                    )
                }
                val whenText = r.whenMillis?.let { fmt.format(Date(it)) } ?: "scheduled"
                Text(
                    whenText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            IconButton(onClick = onDismiss) {
                Icon(
                    Icons.Filled.Close,
                    contentDescription = if (r.live) "Cancel reminder" else "Clear from history",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun StatusBadge(status: ReminderStatus) {
    val (label, color) = when (status) {
        ReminderStatus.UPCOMING -> "UPCOMING" to MaterialTheme.colorScheme.tertiary
        ReminderStatus.DUE_NOW -> "DUE NOW" to MaterialTheme.colorScheme.primary
        ReminderStatus.DONE -> "DONE" to MaterialTheme.colorScheme.onSurfaceVariant
    }
    Surface(
        color = Color.Transparent,
        border = BorderStroke(1.dp, color.copy(alpha = 0.5f)),
        shape = MaterialTheme.shapes.small,
    ) {
        Text(
            label,
            style = HermesText.displayLabel.copy(fontSize = 10.sp),
            color = color,
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
        )
    }
}
