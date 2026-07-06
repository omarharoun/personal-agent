package com.personalagent.android.ui

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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

    if (history.isEmpty()) {
        HistoryEmptyState()
        return
    }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Text(
                "${history.size} CONVERSATION${if (history.size == 1) "" else "S"} · SAVED ON THIS DEVICE",
                style = HermesText.displayLabel,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 2.dp),
            )
        }
        items(history, key = { it.id }) { s ->
            HistoryRow(
                title = s.title.ifBlank { "Untitled chat" },
                preview = s.preview(),
                time = relativeTime(s.updatedAt),
                fromHermes = s.fromHermes,
                onOpen = { onOpenChat(s.id) },
                onDelete = { vm.deleteChat(s.id) },
            )
        }
    }
}

private fun ChatSession.preview(): String =
    messages.lastOrNull { it.text.isNotBlank() }?.text?.take(90)?.trim().orEmpty()

@Composable
private fun HistoryRow(
    title: String,
    preview: String,
    time: String,
    fromHermes: Boolean,
    onOpen: () -> Unit,
    onDelete: () -> Unit,
) {
    Surface(
        onClick = onOpen,
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier.padding(start = 14.dp, end = 6.dp, top = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Filled.ChatBubbleOutline,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp),
            )
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
