package com.personalagent.android.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.personalagent.shared.tasks.Task

/**
 * A simple to-do list. Tasks live on THIS device (checked off instantly, offline)
 * — for anything time-based that should notify you, use Reminders instead.
 */
@Composable
fun TasksScreen(vm: TasksViewModel) {
    val state by vm.state.collectAsStateWithLifecycle()
    var draft by remember { mutableStateOf("") }

    val open = state.tasks.filter { !it.done }
    val done = state.tasks.filter { it.done }

    Column(Modifier.fillMaxSize().padding(top = 12.dp)) {
        Text(
            "Quick to-dos kept on this device. For anything you want to be reminded " +
                "about at a set time, use Reminders.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedTextField(
            value = draft,
            onValueChange = { draft = it },
            label = { Text("Add a task…") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
        )
        Button(
            onClick = { vm.add(draft); draft = "" },
            enabled = draft.isNotBlank(),
            modifier = Modifier.padding(top = 8.dp),
        ) { Text("Add task") }

        if (state.tasks.isEmpty()) {
            Text(
                "No tasks yet — add one above.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 24.dp),
            )
        } else {
            LazyColumn(
                Modifier.fillMaxWidth().padding(top = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(open, key = { it.id }) { t -> TaskRow(t, vm) }
                if (done.isNotEmpty()) {
                    item {
                        Text(
                            "Done",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 12.dp, bottom = 2.dp),
                        )
                    }
                    items(done, key = { it.id }) { t -> TaskRow(t, vm) }
                }
            }
        }
    }
}

@Composable
private fun TaskRow(task: Task, vm: TasksViewModel) {
    Card(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Checkbox(
                checked = task.done,
                onCheckedChange = { vm.toggle(task.id, it) },
            )
            Text(
                task.text,
                style = MaterialTheme.typography.bodyLarge,
                color = if (task.done) MaterialTheme.colorScheme.onSurfaceVariant
                else MaterialTheme.colorScheme.onSurface,
                textDecoration = if (task.done) TextDecoration.LineThrough else null,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = { vm.remove(task.id) }) {
                Icon(
                    Icons.Filled.Close,
                    contentDescription = "Delete task",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
