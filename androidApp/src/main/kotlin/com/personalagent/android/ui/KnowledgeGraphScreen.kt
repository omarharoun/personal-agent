package com.personalagent.android.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountTree
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.personalagent.android.ui.theme.HermesText
import com.personalagent.shared.knowledge.KnowledgeGraph
import com.personalagent.shared.knowledge.KnowledgeGraphSource
import com.personalagent.shared.knowledge.KnowledgeNode
import kotlin.math.min
import kotlin.math.sqrt

/**
 * The Knowledge screen — an interactive node-link map of the topics/entities/
 * concepts the user has explored, **derived from their saved chat records** (not
 * Hermes memory; the header says so). Dots are sized by weight and coloured by
 * type; edges connect related topics. Pan + pinch-zoom to explore; tap a node to
 * see the real questions the user asked about it. Rebuild re-extracts from the
 * latest chat history.
 */
@Composable
fun KnowledgeGraphScreen(vm: KnowledgeGraphViewModel) {
    val state by vm.state.collectAsStateWithLifecycle()
    val graph = state.graph

    Column(Modifier.fillMaxSize()) {
        Header(
            graph = graph,
            building = state.building,
            onRebuild = { vm.rebuild() },
        )

        if (graph == null || graph.isEmpty) {
            EmptyState(building = state.building, onRebuild = { vm.rebuild() })
        } else {
            Box(Modifier.fillMaxSize()) {
                GraphCanvas(
                    graph = graph,
                    onNodeTap = { vm.selectNode(it) },
                    modifier = Modifier.fillMaxSize(),
                )
                Legend(
                    graph = graph,
                    modifier = Modifier.align(Alignment.TopEnd).padding(8.dp),
                )
                state.selected?.let { node ->
                    NodeDetailSheet(
                        node = node,
                        onDismiss = { vm.selectNode(null) },
                        modifier = Modifier.align(Alignment.BottomCenter),
                    )
                }
            }
        }
    }
}

// --- Header ------------------------------------------------------------------
@Composable
private fun Header(graph: KnowledgeGraph?, building: Boolean, onRebuild: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    "KNOWLEDGE MAP",
                    style = HermesText.displayLabel,
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    "Derived from your conversations · not Hermes memory",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (building) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.size(8.dp))
            }
            IconButton(onClick = onRebuild, enabled = !building) {
                Icon(Icons.Filled.Refresh, contentDescription = "Rebuild map",
                    tint = MaterialTheme.colorScheme.primary)
            }
        }
        if (graph != null && !graph.isEmpty) {
            val src = when (graph.source) {
                KnowledgeGraphSource.MODEL -> "extracted by your agent"
                KnowledgeGraphSource.KEYWORDS -> "from keywords (offline)"
                KnowledgeGraphSource.EMPTY -> ""
            }
            Text(
                "${graph.nodes.size} topics · ${graph.edges.size} links · ${graph.sourceConversationCount} chats · $src",
                style = HermesText.mono,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

// --- Interactive canvas ------------------------------------------------------
@Composable
private fun GraphCanvas(
    graph: KnowledgeGraph,
    onNodeTap: (KnowledgeNode) -> Unit,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    // Normalized [0,1] layout, recomputed only when the graph itself changes.
    val layout = remember(graph.sourceSignature, graph.nodes.size) { forceLayout(graph) }

    var scale by remember { mutableStateOf(1f) }
    var pan by remember { mutableStateOf(Offset.Zero) }
    var canvasSize by remember { mutableStateOf(Offset.Zero) } // (w,h) in px

    val padPx = with(density) { 40.dp.toPx() }
    val labelPx = with(density) { 12.sp.toPx() }

    // Map a node's normalized position to on-screen pixels (with pan/zoom baked in).
    fun pixelOf(id: String): Offset {
        val w = canvasSize.x; val h = canvasSize.y
        val norm = layout[id] ?: Offset(0.5f, 0.5f)
        val x = padPx + norm.x * (w - 2 * padPx)
        val y = padPx + norm.y * (h - 2 * padPx)
        val cx = w / 2f; val cy = h / 2f
        return Offset(cx + (x - cx) * scale + pan.x, cy + (y - cy) * scale + pan.y)
    }

    val scheme = MaterialTheme.colorScheme
    val outline = scheme.outline
    val edgeColor = scheme.onSurfaceVariant.copy(alpha = 0.35f)
    val labelColor = scheme.onBackground

    Canvas(
        modifier = modifier
            .onSizeChanged { canvasSize = Offset(it.width.toFloat(), it.height.toFloat()) }
            .pointerInput(graph.sourceSignature) {
                detectTransformGestures { _, gPan, gZoom, _ ->
                    scale = (scale * gZoom).coerceIn(0.5f, 3.5f)
                    pan += gPan
                }
            }
            .pointerInput(graph.sourceSignature) {
                detectTapGestures { tap ->
                    // Nearest node within a touch radius (compare in screen space).
                    var best: KnowledgeNode? = null
                    var bestD = Float.MAX_VALUE
                    for (n in graph.nodes) {
                        val p = pixelOf(n.id)
                        val d = (p - tap).getDistance()
                        if (d < bestD) { bestD = d; best = n }
                    }
                    val hitR = with(density) { 34.dp.toPx() } * scale
                    if (best != null && bestD <= hitR) onNodeTap(best)
                }
            },
    ) {
        val maxW = graph.nodes.maxOfOrNull { it.weight } ?: 1f

        // Edges first, so nodes sit on top.
        for (e in graph.edges) {
            val a = pixelOf(e.from); val b = pixelOf(e.to)
            drawLine(color = edgeColor, start = a, end = b, strokeWidth = 1.5f * scale)
        }
        // Nodes + labels.
        for (n in graph.nodes) {
            val p = pixelOf(n.id)
            val r = nodeRadiusPx(n.weight, maxW, density) * scale
            val fill = colorForType(n.type, scheme)
            drawCircle(color = fill.copy(alpha = 0.9f), radius = r, center = p)
            drawCircle(color = outline, radius = r, center = p, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.2f * scale))
            drawNodeLabel(n.label, p, r, labelPx * min(scale, 1.6f), labelColor.toArgb())
        }
    }
}

private fun DrawScope.drawNodeLabel(text: String, center: Offset, radius: Float, textSizePx: Float, color: Int) {
    val paint = android.graphics.Paint().apply {
        this.color = color
        this.textSize = textSizePx
        this.isAntiAlias = true
        this.textAlign = android.graphics.Paint.Align.CENTER
        this.isFakeBoldText = true
    }
    val label = if (text.length > 18) text.take(17) + "…" else text
    drawContext.canvas.nativeCanvas.drawText(label, center.x, center.y + radius + textSizePx + 2f, paint)
}

private fun nodeRadiusPx(weight: Float, maxWeight: Float, density: androidx.compose.ui.unit.Density): Float {
    val t = if (maxWeight <= 1f) 0.5f else (weight - 1f) / (maxWeight - 1f)
    val dp = 7f + 15f * t.coerceIn(0f, 1f) // 7dp..22dp
    return with(density) { dp.dp.toPx() }
}

// --- Legend ------------------------------------------------------------------
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Legend(graph: KnowledgeGraph, modifier: Modifier = Modifier) {
    val types = graph.nodes.map { it.type }.distinct().take(6)
    if (types.size <= 1) return
    Surface(
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = modifier,
    ) {
        FlowRow(
            Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            val scheme = MaterialTheme.colorScheme
            types.forEach { t ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(9.dp).background(colorForType(t, scheme), RoundedCornerShape(50)))
                    Spacer(Modifier.size(5.dp))
                    Text(t.replaceFirstChar { it.uppercase() },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface)
                }
            }
        }
    }
}

// --- Node detail sheet -------------------------------------------------------
@Composable
private fun NodeDetailSheet(node: KnowledgeNode, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        shadowElevation = 12.dp,
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp).verticalScroll(rememberScrollState())) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(12.dp).background(colorForType(node.type, MaterialTheme.colorScheme), RoundedCornerShape(50)))
                Spacer(Modifier.size(8.dp))
                Column(Modifier.weight(1f)) {
                    Text(node.label, style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                    Text(node.type.replaceFirstChar { it.uppercase() },
                        style = HermesText.mono, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Filled.Close, contentDescription = "Close",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Spacer(Modifier.height(8.dp))
            if (node.snippets.isEmpty()) {
                Text(
                    "No specific questions found for this topic in your chats.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Text("YOU ASKED ABOUT THIS", style = HermesText.displayLabel,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(6.dp))
                node.snippets.forEach { s ->
                    Row(Modifier.padding(vertical = 4.dp)) {
                        Text("“", color = MaterialTheme.colorScheme.primary,
                            style = MaterialTheme.typography.titleMedium)
                        Text(s, style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(start = 4.dp))
                    }
                }
            }
        }
    }
}

// --- Empty state -------------------------------------------------------------
@Composable
private fun EmptyState(building: Boolean, onRebuild: () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(32.dp),
        ) {
            Icon(Icons.Filled.AccountTree, contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(44.dp))
            Spacer(Modifier.height(14.dp))
            Text("Your knowledge map is empty", style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground)
            Spacer(Modifier.height(6.dp))
            Text(
                "Your knowledge map grows as you chat — ask a few things, then rebuild to see " +
                    "the topics you've explored connect up.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(18.dp))
            Surface(
                onClick = onRebuild,
                enabled = !building,
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.primary,
            ) {
                Row(
                    Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (building) {
                        CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary)
                    } else {
                        Icon(Icons.Filled.Refresh, contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(18.dp))
                    }
                    Spacer(Modifier.size(8.dp))
                    Text(if (building) "Building…" else "Build map",
                        color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

// --- Colours by type ---------------------------------------------------------
private fun colorForType(type: String, scheme: androidx.compose.material3.ColorScheme): Color =
    when (type.lowercase()) {
        "person", "people" -> scheme.tertiary
        "place", "location" -> scheme.secondary
        "activity", "habit", "skill" -> scheme.primary
        "concept", "idea" -> scheme.onSurfaceVariant
        "entity", "thing", "product", "tool" -> scheme.error
        else -> scheme.primary // topic + unknown
    }

// --- Force-directed layout (pure) -------------------------------------------
/**
 * A small Fruchterman–Reingold layout producing normalized [0,1] positions.
 * Deterministic (seeded by node id) so the same graph always lays out the same
 * way. n is capped upstream (≤ ~40), so O(n²·iters) is cheap.
 */
private fun forceLayout(graph: KnowledgeGraph, iterations: Int = 320): Map<String, Offset> {
    val ids = graph.nodes.map { it.id }
    val n = ids.size
    if (n == 0) return emptyMap()
    if (n == 1) return mapOf(ids[0] to Offset(0.5f, 0.5f))

    val idx = ids.withIndex().associate { (i, id) -> id to i }
    val px = FloatArray(n); val py = FloatArray(n)
    // Deterministic seed positions on a circle + id-hash jitter.
    for (i in 0 until n) {
        val ang = (2.0 * kotlin.math.PI * i / n).toFloat()
        val jitter = ((ids[i].hashCode() and 0xFF) / 255f - 0.5f) * 0.15f
        px[i] = 0.5f + (0.35f + jitter) * kotlin.math.cos(ang)
        py[i] = 0.5f + (0.35f + jitter) * kotlin.math.sin(ang)
    }
    val edges = graph.edges.mapNotNull { e ->
        val a = idx[e.from]; val b = idx[e.to]
        if (a != null && b != null) a to b else null
    }

    val k = (0.9f * sqrt(1f / n)).coerceAtLeast(0.05f) // ideal edge length
    var temp = 0.12f
    val dispX = FloatArray(n); val dispY = FloatArray(n)

    repeat(iterations) {
        for (i in 0 until n) { dispX[i] = 0f; dispY[i] = 0f }
        // Repulsion between every pair.
        for (i in 0 until n) for (j in i + 1 until n) {
            var dx = px[i] - px[j]; var dy = py[i] - py[j]
            var dist = sqrt(dx * dx + dy * dy)
            if (dist < 1e-4f) { dx = ((i + 1) * 0.001f); dy = ((j + 1) * 0.001f); dist = 0.01f }
            val rep = (k * k) / dist
            val ux = dx / dist; val uy = dy / dist
            dispX[i] += ux * rep; dispY[i] += uy * rep
            dispX[j] -= ux * rep; dispY[j] -= uy * rep
        }
        // Attraction along edges.
        for ((a, b) in edges) {
            var dx = px[a] - px[b]; var dy = py[a] - py[b]
            val dist = sqrt(dx * dx + dy * dy).coerceAtLeast(1e-4f)
            val att = (dist * dist) / k
            val ux = dx / dist; val uy = dy / dist
            dispX[a] -= ux * att; dispY[a] -= uy * att
            dispX[b] += ux * att; dispY[b] += uy * att
        }
        // Apply, capped by temperature; keep pulled gently toward center.
        for (i in 0 until n) {
            val d = sqrt(dispX[i] * dispX[i] + dispY[i] * dispY[i]).coerceAtLeast(1e-4f)
            px[i] += (dispX[i] / d) * min(d, temp)
            py[i] += (dispY[i] / d) * min(d, temp)
            px[i] += (0.5f - px[i]) * 0.01f
            py[i] += (0.5f - py[i]) * 0.01f
        }
        temp = (temp * 0.985f).coerceAtLeast(0.005f)
    }

    // Normalize to [0.06, 0.94] so nodes don't hug the very edge.
    var minX = px.min(); var maxX = px.max(); var minY = py.min(); var maxY = py.max()
    val spanX = (maxX - minX).coerceAtLeast(1e-3f); val spanY = (maxY - minY).coerceAtLeast(1e-3f)
    return ids.withIndex().associate { (i, id) ->
        id to Offset(
            0.06f + 0.88f * ((px[i] - minX) / spanX),
            0.06f + 0.88f * ((py[i] - minY) / spanY),
        )
    }
}
