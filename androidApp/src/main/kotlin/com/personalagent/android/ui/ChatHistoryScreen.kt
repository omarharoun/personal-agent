package com.personalagent.android.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.personalagent.android.ui.theme.HermesText

/**
 * A browsable history of every saved conversation — the persistent counterpart to
 * the drawer's short "Recent" list. Threads are read from [ConversationViewModel]
 * (which mirrors them to a sealed-at-rest `ChatStore`), newest first, with a
 * preview + relative time; a cloud badge marks threads surfaced from Hermes
 * `/api/sessions`. Tapping one reopens it in the chat surface.
 *
 * A **Select** mode turns each row into a checkbox so several threads can be picked
 * and removed at once with a single red **Delete** button.
 */
@Composable
fun ChatHistoryScreen(
    vm: ConversationViewModel,
    onOpenChat: (Long) -> Unit,
) {
    val sessions by vm.sessions.collectAsStateWithLifecycle()

    // Only threads with real content; the empty "New chat" placeholder is hidden.
    val history = sessions
        .filter { it.messages.isNotEmpty() || it.fromHermes }
        .sortedByDescending { it.updatedAt }

    var selecting by remember { mutableStateOf(false) }
    val selected: SnapshotStateList<Long> = remember { mutableStateListOf() }

    if (history.isEmpty()) {
        HistoryEmptyState()
        return
    }

    // Drop any ids that no longer exist (e.g. after a delete).
    val liveIds = history.map { it.id }.toSet()
    selected.retainAll(liveIds)

    Column(Modifier.fillMaxSize()) {
        HistoryToolbar(
            count = history.size,
            selecting = selecting,
            selectedCount = selected.size,
            onToggleSelecting = {
                selecting = !selecting
                if (!selecting) selected.clear()
            },
            onSelectAll = { selected.clear(); selected.addAll(liveIds) },
            onDelete = {
                selected.toList().forEach { vm.deleteChat(it) }
                selected.clear()
                selecting = false
            },
        )
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                start = 12.dp, end = 12.dp, bottom = 12.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(history, key = { it.id }) { s ->
                val isSelected = s.id in selected
                HistoryRow(
                    title = s.title.ifBlank { "Untitled chat" },
                    preview = s.preview(),
                    time = relativeTime(s.updatedAt),
                    fromHermes = s.fromHermes,
                    selecting = selecting,
                    selected = isSelected,
                    onOpen = {
                        if (selecting) {
                            if (isSelected) selected.remove(s.id) else selected.add(s.id)
                        } else {
                            onOpenChat(s.id)
                        }
                    },
                    onLongPress = {
                        if (!selecting) { selecting = true }
                        if (s.id !in selected) selected.add(s.id)
                    },
                    onDelete = { vm.deleteChat(s.id) },
                )
            }
        }
    }
}

@Composable
private fun HistoryToolbar(
    count: Int,
    selecting: Boolean,
    selectedCount: Int,
    onToggleSelecting: () -> Unit,
    onSelectAll: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().padding(start = 14.dp, end = 8.dp, top = 12.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            if (selecting) "$selectedCount SELECTED"
            else "$count CONVERSATION${if (count == 1) "" else "S"} · SAVED ON THIS DEVICE",
            style = HermesText.displayLabel,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        if (selecting) {
            TextButton(onClick = onSelectAll) { Text("All") }
            Button(
                onClick = onDelete,
                enabled = selectedCount > 0,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError,
                ),
            ) {
                Icon(Icons.Filled.DeleteOutline, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.size(6.dp))
                Text("Delete")
            }
            TextButton(onClick = onToggleSelecting) { Text("Cancel") }
        } else {
            TextButton(onClick = onToggleSelecting) { Text("Select") }
        }
    }
}

private fun ChatSession.preview(): String =
    messages.lastOrNull { it.text.isNotBlank() }?.text?.take(90)?.trim().orEmpty()

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun HistoryRow(
    title: String,
    preview: String,
    time: String,
    fromHermes: Boolean,
    selecting: Boolean,
    selected: Boolean,
    onOpen: () -> Unit,
    onLongPress: () -> Unit,
    onDelete: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = if (selected) MaterialTheme.colorScheme.secondaryContainer
        else MaterialTheme.colorScheme.surface,
        border = BorderStroke(
            1.dp,
            if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
        ),
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickableRow(onClick = onOpen, onLongClick = onLongPress),
    ) {
        Row(
            Modifier.padding(start = 14.dp, end = 6.dp, top = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (selecting) {
                Icon(
                    if (selected) Icons.Filled.CheckCircle else Icons.Filled.RadioButtonUnchecked,
                    contentDescription = if (selected) "Selected" else "Not selected",
                    tint = if (selected) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(22.dp),
                )
            } else {
                Icon(
                    Icons.Filled.ChatBubbleOutline,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp),
                )
            }
            Spacer(Modifier.size(12.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (fromHermes) {
                        Spacer(Modifier.size(6.dp))
                        Icon(
                            Icons.Filled.CloudDone,
                            contentDescription = "From your Hermes",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(14.dp),
                        )
                    }
                }
                if (preview.isNotBlank()) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        preview,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.height(3.dp))
                Text(
                    time,
                    style = HermesText.mono,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (!selecting) {
                IconButton(onClick = onDelete) {
                    Icon(
                        Icons.Filled.DeleteOutline,
                        contentDescription = "Delete conversation",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

/** Row-level tap + long-press without pulling clickable imports into every call. */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
private fun Modifier.combinedClickableRow(onClick: () -> Unit, onLongClick: () -> Unit): Modifier =
    this.combinedClickable(onClick = onClick, onLongClick = onLongClick)

@Composable
private fun HistoryEmptyState() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(32.dp),
        ) {
            Icon(
                Icons.Filled.ChatBubbleOutline,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(40.dp),
            )
            Spacer(Modifier.height(14.dp))
            Text(
                "No saved conversations yet",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "Your chats are saved on this device as you talk, and stay here when you " +
                    "reopen the app.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
        }
    }
}

/** Coarse relative-time label from an epoch-millis timestamp. */
private fun relativeTime(epochMillis: Long): String {
    if (epochMillis <= 0L) return "—"
    val now = System.currentTimeMillis()
    val diff = now - epochMillis
    if (diff < 0) return "just now"
    val min = diff / 60_000
    val hr = diff / 3_600_000
    val day = diff / 86_400_000
    return when {
        min < 1 -> "just now"
        min < 60 -> "${min}m ago"
        hr < 24 -> "${hr}h ago"
        day < 7 -> "${day}d ago"
        day < 30 -> "${day / 7}w ago"
        else -> "${day / 30}mo ago"
    }
}
