package com.personalagent.android.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.personalagent.android.ui.theme.HermesText
import com.personalagent.shared.hermes.HermesHealthDetailed
import com.personalagent.shared.hermes.HermesSessionCard
import com.personalagent.shared.hermes.HermesToolset
import com.personalagent.shared.hermes.UsageSummary

/** Navigation hooks the dashboard's quick actions fire. Tasks/Skills are optional
 *  so each build phase can light up its own action without a placeholder. */
data class DashboardNav(
    val onChat: () -> Unit,
    val onReminders: () -> Unit,
    val onGoals: () -> Unit,
    val onReflection: () -> Unit,
    val onNotes: () -> Unit,
    val onTasks: (() -> Unit)? = null,
    val onSkills: (() -> Unit)? = null,
)

/**
 * The dashboard home — a living board of cards built from REAL Hermes endpoints
 * (`/health/detailed`, `/api/sessions`, `/v1/toolsets`). Chat is one tap away; the
 * home is now activity + capabilities, not a chat window.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    vm: DashboardViewModel,
    onOpenDrawer: () -> Unit,
    nav: DashboardNav,
) {
    val state by vm.state.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onOpenDrawer) { Icon(Icons.Filled.Menu, "Menu") }
                },
                title = {
                    Text(
                        "LIFE AGENT",
                        style = HermesText.displayLabel.copy(fontSize = 15.sp),
                        color = MaterialTheme.colorScheme.primary,
                    )
                },
                actions = {
                    IconButton(onClick = { vm.refresh() }) { Icon(Icons.Filled.Refresh, "Refresh") }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { inner ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(inner),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item { StatusCard(state.health, state.loading) }
            state.usage?.let { u -> item { UsageCard(u) } }
            item { SectionLabel("QUICK ACTIONS") }
            item { QuickActions(nav) }

            if (state.toolsets.isNotEmpty()) {
                item { SectionLabel("CAPABILITIES") }
                item { CapabilitiesCard(state.toolsets) }
            }

            item { SectionLabel("RECENT ACTIVITY") }
            if (state.loading && state.sessions.isEmpty()) {
                item { Box(Modifier.fillMaxWidth().padding(24.dp), Alignment.Center) { CircularProgressIndicator() } }
            } else if (state.sessions.isEmpty()) {
                item {
                    Text(
                        state.error ?: "No agent activity yet. Start a chat or run a task.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                items(state.sessions, key = { it.id }) { s -> SessionCard(s) }
            }
            item { Spacer(Modifier.height(8.dp)) }
        }
    }
}

// --- cards ------------------------------------------------------------------

@Composable
private fun HermesCard(content: @Composable () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        shape = MaterialTheme.shapes.large,
        modifier = Modifier.fillMaxWidth(),
    ) { Box(Modifier.padding(14.dp)) { content() } }
}

@Composable
private fun SectionLabel(text: String) {
    Text(text, style = HermesText.displayLabel, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun StatusCard(health: HermesHealthDetailed?, loading: Boolean) {
    HermesCard {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            val ok = health?.isOk == true
            val dot = when {
                health == null && loading -> MaterialTheme.colorScheme.onSurfaceVariant
                ok -> MaterialTheme.colorScheme.tertiary
                else -> MaterialTheme.colorScheme.error
            }
            Box(Modifier.size(10.dp).clip(CircleShape).background(dot))
            Column(Modifier.weight(1f)) {
                Text(
                    if (health == null) (if (loading) "Connecting…" else "Not reachable") else "Hermes connected",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                health?.let {
                    Text(
                        "v${it.version ?: "?"} · gateway ${it.gatewayState ?: "?"} · ${it.activeAgents} active",
                        style = HermesText.mono,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun UsageCard(u: UsageSummary) {
    HermesCard {
        Column {
            SectionLabel("USAGE")
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Stat(u.sessionCount.toString(), "SESSIONS")
                Stat(fmtTokens(u.totalTokens), "TOKENS")
                Stat(u.totalToolCalls.toString(), "TOOL CALLS")
                Stat((if (u.costIsEstimated) "~" else "") + fmtCost(u.totalCostUsd), "COST")
            }
        }
    }
}

@Composable
private fun Stat(value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = HermesText.displayLarge.copy(fontSize = 22.sp), color = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(2.dp))
        Text(label, style = HermesText.displayLabel.copy(fontSize = 9.sp), color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun QuickActions(nav: DashboardNav) {
    val actions = buildList {
        add(Triple("💬", "Chat", nav.onChat))
        nav.onTasks?.let { add(Triple("⚡", "Run task", it)) }
        nav.onSkills?.let { add(Triple("✨", "Skills", it)) }
        add(Triple("⏰", "Reminders", nav.onReminders))
        add(Triple("⚑", "Goals", nav.onGoals))
        add(Triple("🌱", "Reflect", nav.onReflection))
        add(Triple("📝", "Notes", nav.onNotes))
    }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        actions.chunked(2).forEach { rowItems ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                rowItems.forEach { (emoji, label, onClick) ->
                    Surface(
                        onClick = onClick,
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                        shape = MaterialTheme.shapes.large,
                        modifier = Modifier.weight(1f).height(60.dp),
                    ) {
                        Row(
                            Modifier.padding(horizontal = 14.dp).fillMaxSize(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Text(emoji, fontSize = 20.sp)
                            Text(label, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
                        }
                    }
                }
                if (rowItems.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun CapabilitiesCard(toolsets: List<HermesToolset>) {
    val enabled = toolsets.filter { it.enabled }
    HermesCard {
        Column {
            Text(
                "${toolsets.size} toolsets · ${enabled.size} enabled",
                style = HermesText.mono,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(10.dp))
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                enabled.take(14).forEach { t ->
                    Surface(
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        shape = RoundedCornerShape(8.dp),
                    ) {
                        Text(
                            t.label.ifBlank { t.name },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            maxLines = 1,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SessionCard(s: HermesSessionCard) {
    HermesCard {
        Column {
            if (s.isFork) {
                Text("⑂ forked", style = HermesText.displayLabel.copy(fontSize = 9.sp), color = MaterialTheme.colorScheme.tertiary)
                Spacer(Modifier.height(2.dp))
            }
            Text(
                s.displayTitle,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(6.dp))
            val meta = buildString {
                append(s.model ?: "?")
                append("  ·  ${s.messageCount} msgs")
                if (s.toolCallCount > 0) append("  ·  ${s.toolCallCount} tools")
                append("  ·  ${fmtTokens(s.totalTokens)} tok")
                s.costUsd?.let { append("  ·  ${fmtCost(it)}") }
            }
            Text(meta, style = HermesText.mono.copy(fontSize = 11.sp), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

// --- format helpers ---------------------------------------------------------

private fun fmtTokens(n: Long): String = when {
    n >= 1_000_000 -> "%.1fM".format(n / 1_000_000.0)
    n >= 1_000 -> "%.1fk".format(n / 1_000.0)
    else -> n.toString()
}

private fun fmtCost(d: Double): String = when {
    d <= 0.0 -> "$0"
    d < 0.01 -> "<\$0.01"
    else -> "$%.2f".format(d)
}
