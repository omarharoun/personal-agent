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
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.personalagent.shared.model.Note

@Composable
fun NotesScreen(state: UiState, vm: AppViewModel) {
    var editing by remember { mutableStateOf<Note?>(null) }
    var title by remember { mutableStateOf("") }
    var body by remember { mutableStateOf("") }

    fun reset() { editing = null; title = ""; body = "" }

    Column(Modifier.fillMaxWidth().padding(top = 12.dp)) {
        OutlinedTextField(
            value = title,
            onValueChange = { title = it },
            label = { Text("Title") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = body,
            onValueChange = { body = it },
            label = { Text("Note") },
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        )
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = {
                    val current = editing
                    if (current == null) vm.addNote(title, body) else vm.editNote(current, title, body)
                    reset()
                },
            ) { Text(if (editing == null) "Add note" else "Save") }
            if (editing != null) {
                OutlinedButton(onClick = { reset() }) { Text("Cancel") }
            }
        }

        LazyColumn(Modifier.fillMaxWidth().padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(state.notes, key = { it.id }) { note ->
                Card(Modifier.fillMaxWidth()) {
                    Row(
                        Modifier.fillMaxWidth().padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(note.title, fontWeight = FontWeight.SemiBold)
                            if (note.body.isNotBlank()) {
                                Text(note.body, modifier = Modifier.padding(top = 2.dp))
                            }
                        }
                        IconButton(onClick = {
                            editing = note; title = note.title; body = note.body
                        }) { Icon(Icons.Filled.Edit, contentDescription = "Edit") }
                        IconButton(onClick = { vm.deleteNote(note.id) }) {
                            Icon(Icons.Filled.Delete, contentDescription = "Delete")
                        }
                    }
                }
            }
        }
    }
}
