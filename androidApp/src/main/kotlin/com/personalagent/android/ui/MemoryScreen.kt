package com.personalagent.android.ui

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.personalagent.android.AppContainer
import com.personalagent.shared.graph.MemoryNode
import kotlinx.coroutines.launch

/**
 * The Memory screen: everything the agent remembers ABOUT THE USER, grouped by
 * type, with relationships, full edit/delete, and JSON export/import. Makes the
 * privacy posture explicit: stored encrypted on-device, never sent to the cloud.
 */
@Composable
fun MemoryScreen(container: AppContainer, modifier: Modifier = Modifier) {
    val vm: MemoryViewModel = viewModel(factory = MemoryViewModel.Factory(container))
    val state by vm.state.collectAsStateWithLifecycle()
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()

    var editing by remember { mutableStateOf<MemoryNode?>(null) }

    // SAF: write the exported JSON to a user-chosen file.
    val exporter = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri ->
        if (uri != null) scope.launch {
            val text = vm.exportJson()
            runCatching {
                ctx.contentResolver.openOutputStream(uri)?.use { it.write(text.encodeToByteArray()) }
            }
            Toast.makeText(ctx, "Memory exported", Toast.LENGTH_SHORT).show()
        }
    }
    // SAF: read a JSON file and import it.
    val importer = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            val text = runCatching {
                ctx.contentResolver.openInputStream(uri)?.use { it.readBytes().decodeToString() }
            }.getOrNull()
            if (text == null) {
                Toast.makeText(ctx, "Couldn't read that file", Toast.LENGTH_SHORT).show()
            } else {
                vm.import(text) { count ->
                    val msg = if (count < 0) "Import failed: not a valid memory file" else "Imported $count items"
                    Toast.makeText(ctx, msg, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    Column(modifier.fillMaxSize()) {
        Text(
            "🔒 Stored encrypted on this device and never sent to the cloud. This is " +
                "what the on-device assistant knows about you — you're in full control.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(vertical = 8.dp),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { exporter.launch("memory-export.json") }) { Text("Export") }
            OutlinedButton(onClick = { importer.launch(arrayOf("application/json", "text/*")) }) { Text("Import") }
        }
        Spacer(Modifier.height(8.dp))

        if (!state.loading && state.total == 0) {
            Text(
                "Nothing yet. As you chat with the on-device model, I'll remember durable " +
                    "facts about you here (people, preferences, goals…).",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 16.dp),
            )
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 8.dp),
        ) {
            state.byType.forEach { (type, items) ->
                item(key = "header-$type") {
                    Text(
                        "${type.name} (${items.size})",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
                items(items, key = { it.node.id }) { item ->
                    MemoryItemCard(item, onEdit = { editing = item.node }, onDelete = { vm.delete(item.node.id) })
                }
            }
        }
    }

    editing?.let { node ->
        EditMemoryDialog(
            node = node,
            onDismiss = { editing = null },
            onSave = { label, attrs -> vm.edit(node.id, label, attrs); editing = null },
        )
    }
}

@Composable
private fun MemoryItemCard(item: MemoryItem, onEdit: () -> Unit, onDelete: () -> Unit) {
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(item.node.label, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            if (item.node.attributes.isNotEmpty()) {
                Text(
                    item.node.attributes.entries.joinToString(", ") { "${it.key}: ${it.value}" },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            item.relations.forEach { rel ->
                Text("• $rel", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onEdit) { Text("Edit") }
                TextButton(onClick = onDelete) { Text("Delete") }
            }
        }
    }
}

@Composable
private fun EditMemoryDialog(node: MemoryNode, onDismiss: () -> Unit, onSave: (String, Map<String, String>) -> Unit) {
    var label by remember { mutableStateOf(node.label) }
    // Edit attributes as simple "key: value" lines.
    var attrsText by remember {
        mutableStateOf(node.attributes.entries.joinToString("\n") { "${it.key}: ${it.value}" })
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit memory") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = label,
                    onValueChange = { label = it },
                    label = { Text("Label") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                )
                OutlinedTextField(
                    value = attrsText,
                    onValueChange = { attrsText = it },
                    label = { Text("Attributes (one 'key: value' per line)") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                )
            }
        },
        confirmButton = {
            Button(onClick = {
                val attrs = attrsText.lines().mapNotNull { line ->
                    val i = line.indexOf(':')
                    if (i <= 0) null else line.substring(0, i).trim() to line.substring(i + 1).trim()
                }.filter { it.first.isNotEmpty() }.toMap()
                onSave(label.trim(), attrs)
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
