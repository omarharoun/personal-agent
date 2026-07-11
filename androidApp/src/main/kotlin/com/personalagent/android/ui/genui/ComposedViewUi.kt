package com.personalagent.android.ui.genui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.outlined.Circle
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.personalagent.shared.genui.ComposedView
import com.personalagent.shared.genui.PlanRow
import com.personalagent.shared.genui.Stat
import com.personalagent.shared.genui.SuggestionChip
import com.personalagent.shared.genui.SuggestionChips
import com.personalagent.shared.genui.ViewBlock

/**
 * Renders a [ComposedView] natively — a `when` over the sealed [ViewBlock], one
 * composable per primitive, inside the app's own trusted cards. The model never
 * ships markup: this is the ONLY thing that turns a validated view spec into
 * pixels, and it only knows the ~5 fixed primitives. Everything textual is already
 * sanitized/inert by the shared parser.
 *
 * The accent (from the user's theme choice) is used throughout, so re-theming the
 * app recolors these cards too.
 */
@Composable
fun ComposedViewCard(
    view: ComposedView,
    onPlanToggle: (PlanRow) -> Unit,
    onResourceOpen: (com.personalagent.shared.learning.LearningResource) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp)) {
            Eyebrow(view)
            view.title?.let { title ->
                Spacer(Modifier.height(6.dp))
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            view.blocks.forEach { block ->
                Spacer(Modifier.height(12.dp))
                when (block) {
                    is ViewBlock.ProseLine -> ProseLineView(block)
                    is ViewBlock.StatGrid -> StatGridView(block)
                    is ViewBlock.Sparkline -> SparklineView(block)
                    is ViewBlock.Plan -> PlanView(block, onPlanToggle)
                    is ViewBlock.ResourceRec -> ResourceRecView(block, onResourceOpen)
                }
            }
        }
    }
}

@Composable
private fun Eyebrow(view: ComposedView) {
    val provenance = if (view.provenance == ComposedView.PROVENANCE_LOCAL) "on your device" else "composed for you"
    Text(
        "· $provenance · ${view.view.replace('-', ' ')}".uppercase(),
        style = MaterialTheme.typography.labelSmall,
        letterSpacing = 1.5.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun ProseLineView(block: ViewBlock.ProseLine) {
    // Serif italic — the agent's voice. Emphasis substrings drawn in the accent.
    val accent = MaterialTheme.colorScheme.primary
    val base = MaterialTheme.colorScheme.onSurface
    val annotated = androidx.compose.ui.text.buildAnnotatedString {
        var text = block.text
        append(text)
        block.emphasis.forEach { phrase ->
            var idx = text.indexOf(phrase, ignoreCase = true)
            while (idx >= 0) {
                addStyle(androidx.compose.ui.text.SpanStyle(color = accent, fontWeight = FontWeight.Medium), idx, idx + phrase.length)
                idx = text.indexOf(phrase, idx + phrase.length, ignoreCase = true)
            }
        }
    }
    Text(
        annotated,
        style = MaterialTheme.typography.bodyLarge.copy(fontStyle = FontStyle.Italic),
        color = base,
    )
}

@Composable
private fun StatGridView(block: ViewBlock.StatGrid) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        block.stats.forEach { stat -> StatTile(stat, Modifier.weight(1f)) }
    }
}

@Composable
private fun StatTile(stat: Stat, modifier: Modifier) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(10.dp),
        modifier = modifier,
    ) {
        Column(Modifier.padding(vertical = 12.dp, horizontal = 8.dp)) {
            Text(
                stat.value,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                stat.label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SparklineView(block: ViewBlock.Sparkline) {
    val max = (block.points.maxOrNull() ?: 0.0).coerceAtLeast(1.0)
    val accent = MaterialTheme.colorScheme.primary
    val dim = MaterialTheme.colorScheme.surfaceVariant
    Column {
        Row(
            Modifier.fillMaxWidth().height(56.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            block.points.forEachIndexed { i, p ->
                val frac = (p / max).toFloat().coerceIn(0.06f, 1f)
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight(frac)
                        .clip(RoundedCornerShape(4.dp))
                        .background(if (i == block.highlightIndex) accent else dim),
                )
            }
        }
        block.caption?.let {
            Spacer(Modifier.height(6.dp))
            Text(it, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun PlanView(block: ViewBlock.Plan, onToggle: (PlanRow) -> Unit) {
    Column {
        Text(block.heading, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
        block.meta?.let {
            Text(it, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(8.dp))
        block.items.forEach { row -> PlanRowView(row, onToggle) }
    }
}

@Composable
private fun PlanRowView(row: PlanRow, onToggle: (PlanRow) -> Unit) {
    val tickable = row.actionable && (row.source == PlanRow.SOURCE_TASK ||
        row.source == PlanRow.SOURCE_PLAN || row.source == PlanRow.SOURCE_LEARNING)
    Row(
        Modifier
            .fillMaxWidth()
            .let { if (tickable) it.clickable { onToggle(row) } else it }
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = if (row.done) Icons.Filled.CheckCircle else Icons.Outlined.Circle,
            contentDescription = null,
            tint = if (row.done) MaterialTheme.colorScheme.primary
            else if (tickable) MaterialTheme.colorScheme.onSurfaceVariant
            else MaterialTheme.colorScheme.outline,
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                row.title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            row.note?.let {
                Text(it, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        row.time?.let {
            Text(it, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
private fun ResourceRecView(
    block: ViewBlock.ResourceRec,
    onOpen: (com.personalagent.shared.learning.LearningResource) -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(10.dp),
    ) {
        Column(Modifier.padding(12.dp)) {
            Text(
                block.goal + (block.level?.let { " · $it" } ?: ""),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                block.resource.title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.SemiBold,
            )
            if (block.resource.why.isNotBlank()) {
                Spacer(Modifier.height(2.dp))
                Text(block.resource.why, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(6.dp))
            TextButton(onClick = { onOpen(block.resource) }) {
                Icon(Icons.Filled.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Start reading")
            }
        }
    }
}

/** The transient "composing your view…" affordance while [GenerativeUiService] runs. */
@Composable
fun ComposingIndicator(modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        CircularProgressIndicator(
            modifier = Modifier.size(16.dp),
            strokeWidth = 2.dp,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            "composing your view…",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** The FIXED suggestion-chip row (shared curated copy). Taps compose a view. */
@Composable
fun SuggestionChipRow(onChip: (SuggestionChip) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        SuggestionChips.ALL.forEach { chip ->
            AssistChip(
                onClick = { onChip(chip) },
                label = { Text(chip.label) },
            )
        }
    }
}
