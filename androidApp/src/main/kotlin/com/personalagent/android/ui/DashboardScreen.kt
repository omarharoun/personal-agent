package com.personalagent.android.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.personalagent.android.ui.theme.HermesText
import com.personalagent.shared.hermes.ReminderStatus
import com.personalagent.shared.hermes.ReminderView
import com.personalagent.shared.notes.Memo
import com.personalagent.shared.tasks.Task
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/** Navigation hooks the home cards + overflow fire. */
data class DashboardNav(
    val onChat: () -> Unit,
    val onReminders: () -> Unit,
    val onGoals: () -> Unit,
    val onReflection: () -> Unit,
    val onNotes: () -> Unit,
    /** The to-do list card. */
    val onTasks: (() -> Unit)? = null,
    /** The "Run a task" tool-use preview flow (secondary, in the overflow). */
    val onRunTask: () -> Unit = {},
    val onSkills: (() -> Unit)? = null,
)

private val reminderFmt = SimpleDateFormat("EEE d MMM, HH:mm", Locale.getDefault())
private val homeDateFmt = SimpleDateFormat("EEEE, MMMM d", Locale.getDefault())

/**
 * The home — a warm, personal life dashboard: a time-of-day greeting, then four
 * live "life area" cards (Goals · Tasks · Memos · Reminders), each previewing real
 * content and tapping into its full screen. Chat + Run-a-task are secondary (a FAB
 * and the overflow menu), so the home reads as a calm board, not a chat window.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    vm: DashboardViewModel,
    onOpenDrawer: () -> Unit,
    nav: DashboardNav,
) {
    val state by vm.state.collectAsStateWithLifecycle()
    var menuOpen by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onOpenDrawer) { Icon(Icons.Filled.Menu, "Menu") }
                },
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                        Box(
                            Modifier.size(7.dp).clip(CircleShape).background(
                                if (state.connected) MaterialTheme.colorScheme.tertiary
                                else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        )
                        Text(
                            if (state.connected) "Connected" else "Connecting…",
                            style = HermesText.mono.copy(fontSize = 11.5.sp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { menuOpen = true }) { Icon(Icons.Filled.MoreVert, "More") }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(text = { Text("Run a task") }, onClick = { menuOpen = false; nav.onRunTask() })
                        nav.onSkills?.let { open ->
                            DropdownMenuItem(text = { Text("Skills") }, onClick = { menuOpen = false; open() })
                        }
                        DropdownMenuItem(text = { Text("Refresh") }, onClick = { menuOpen = false; vm.refresh() })
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                text = { Text("Chat") },
                icon = { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null) },
                onClick = nav.onChat,
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { inner ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(inner),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                start = 20.dp, end = 20.dp, top = 8.dp, bottom = 96.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item { GreetingHeader(state.name) }

            item {
                GoalsCard(
                    goals = state.goals,
                    loading = state.goalsLoading,
                    onOpen = nav.onGoals,
                )
            }
            item {
                TasksCard(
                    tasks = state.tasks,
                    onToggle = { id, done -> vm.toggleTask(id, done) },
                    onOpen = { nav.onTasks?.invoke() },
                )
            }
            item { MemosCard(memos = state.memos, onOpen = nav.onNotes) }
            item { RemindersCard(reminders = state.reminders, onOpen = nav.onReminders) }
        }
    }
}

// --- Greeting ---------------------------------------------------------------

@Composable
private fun GreetingHeader(name: String?) {
    val hour = remember { Calendar.getInstance().get(Calendar.HOUR_OF_DAY) }
    val greeting = when {
        hour < 12 -> "Good morning"
        hour < 17 -> "Good afternoon"
        else -> "Good evening"
    }
    val today = remember { homeDateFmt.format(Date()) }
    Column(Modifier.padding(top = 8.dp, bottom = 2.dp)) {
        Text(
            greeting + (name?.let { ", $it" } ?: "") + ".",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            today,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

// --- Card scaffold ----------------------------------------------------------

/**
 * The shared look for a life card: a warm panel with an accent-tinted icon chip,
 * a title, a chevron, and body content. The whole card is tappable → [onOpen].
 */
@Composable
private fun LifeCard(
    emoji: String,
    title: String,
    accent: Color,
    onOpen: () -> Unit,
    content: @Composable () -> Unit,
) {
    Surface(
        onClick = onOpen,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        shape = MaterialTheme.shapes.extraLarge,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(34.dp).clip(MaterialTheme.shapes.medium)
                        .background(accent.copy(alpha = 0.16f)),
                    contentAlignment = Alignment.Center,
                ) { Text(emoji, fontSize = 18.sp) }
                Spacer(Modifier.size(12.dp))
                Text(
                    title,
                    style = HermesText.displayLabel.copy(fontSize = 13.sp),
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
                Icon(
                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = "Open",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(14.dp))
            content()
        }
    }
}

@Composable
private fun EmptyLine(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

// --- Goals ------------------------------------------------------------------

@Composable
private fun GoalsCard(goals: List<String>, loading: Boolean, onOpen: () -> Unit) {
    LifeCard("🎯", "Goals", MaterialTheme.colorScheme.tertiary, onOpen) {
        when {
            loading && goals.isEmpty() -> LoadingRow()
            goals.isEmpty() -> EmptyLine("No goals yet — tap to set what “better” looks like for you.")
            else -> Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                goals.take(3).forEach { g ->
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Box(
                            Modifier.padding(top = 7.dp).size(6.dp).clip(CircleShape)
                                .background(MaterialTheme.colorScheme.tertiary)
                        )
                        Text(
                            g,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

// --- Tasks ------------------------------------------------------------------

@Composable
private fun TasksCard(
    tasks: List<Task>,
    onToggle: (String, Boolean) -> Unit,
    onOpen: () -> Unit,
) {
    LifeCard("✅", "Tasks", MaterialTheme.colorScheme.primary, onOpen) {
        if (tasks.isEmpty()) {
            EmptyLine("All clear — tap to add a to-do.")
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                tasks.take(3).forEach { t ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = t.done,
                            onCheckedChange = { onToggle(t.id, it) },
                            colors = CheckboxDefaults.colors(
                                checkedColor = MaterialTheme.colorScheme.primary,
                            ),
                        )
                        Text(
                            t.text,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                            textDecoration = if (t.done) TextDecoration.LineThrough else null,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
                if (tasks.size > 3) {
                    Text(
                        "+${tasks.size - 3} more",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 12.dp, top = 4.dp),
                    )
                }
            }
        }
    }
}

// --- Memos ------------------------------------------------------------------

@Composable
private fun MemosCard(memos: List<Memo>, onOpen: () -> Unit) {
    LifeCard("📝", "Memos", MaterialTheme.colorScheme.tertiary, onOpen) {
        if (memos.isEmpty()) {
            EmptyLine("Nothing saved yet — tap to jot something your agent should remember.")
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                memos.take(3).forEach { m ->
                    Column {
                        Text(
                            m.text,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            reminderFmt.format(Date(m.savedAt)),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 1.dp),
                        )
                    }
                }
            }
        }
    }
}

// --- Reminders --------------------------------------------------------------

@Composable
private fun RemindersCard(reminders: List<ReminderView>, onOpen: () -> Unit) {
    LifeCard("⏰", "Reminders", MaterialTheme.colorScheme.primary, onOpen) {
        if (reminders.isEmpty()) {
            EmptyLine("Nothing upcoming — tap to set a reminder.")
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                reminders.take(3).forEach { r ->
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        val due = r.status == ReminderStatus.DUE_NOW
                        Box(
                            Modifier.size(6.dp).clip(CircleShape).background(
                                if (due) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.tertiary
                            )
                        )
                        Column(Modifier.weight(1f)) {
                            Text(
                                r.text,
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                if (due) "Due now"
                                else r.whenMillis?.let { reminderFmt.format(Date(it)) } ?: "scheduled",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}

// --- shared bits ------------------------------------------------------------

@Composable
private fun LoadingRow() {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
        Text(
            "Loading…",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
