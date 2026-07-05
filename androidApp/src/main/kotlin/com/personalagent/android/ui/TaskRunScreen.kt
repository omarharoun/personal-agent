package com.personalagent.android.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Error
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.personalagent.android.ui.theme.HermesText
import com.personalagent.shared.hermes.ToolFinding
import com.personalagent.shared.hermes.WrittenDocument

/**
 * The tool-use preview surface. The user gives the agent a task; it runs via
 * `/v1/runs` and the app shows the work happening live — "🔍 browser_navigate →
 * python.org" while it runs, then "✓ 11.5s" — then the answer + what it found +
 * any written document, hydrated from the transcript. Real data end to end.
 */
@Composable
fun TaskRunScreen(vm: TaskRunViewModel) {
    val state by vm.state.collectAsStateWithLifecycle()
    var draft by remember { mutableStateOf("") }
    var openDoc by remember { mutableStateOf<WrittenDocument?>(null) }

    openDoc?.let { doc ->
        DocumentPreview(doc, onBack = { openDoc = null })
        return
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(top = 12.dp)) {
        Text(
            "Give the agent a task — search the web, look something up, draft a document. " +
                "You'll watch it work, then see what it found.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedTextField(
            value = draft,
            onValueChange = { draft = it },
            label = { Text("What should the agent do?") },
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
            minLines = 2,
            enabled = !state.running,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Default),
        )
        Button(
            onClick = { vm.run(draft); draft = "" },
            enabled = draft.isNotBlank() && !state.running,
            modifier = Modifier.padding(top = 8.dp),
        ) { Text(if (state.running) "Running…" else "Run task") }

        state.error?.let { ErrorText(it) }
        state.approvalNote?.let {
            Spacer(Modifier.height(10.dp))
            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.tertiary)
        }

        // Live activity card
        if (state.task.isNotBlank()) {
            Spacer(Modifier.height(16.dp))
            HermesCardBox {
                Column {
                    Text("ACTIVITY", style = HermesText.displayLabel, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(8.dp))
                    Text(state.task, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
                    Spacer(Modifier.height(10.dp))

                    if (state.activities.isEmpty() && state.running) {
                        ActivityRow(spinning = true, text = "thinking…")
                    }
                    state.activities.forEach { a ->
                        val label = "${toolEmoji(a.tool)} ${a.tool}" +
                            (if (a.preview.isNotBlank()) " — ${a.preview}" else "")
                        when {
                            !a.done -> ActivityRow(spinning = true, text = label)
                            a.error -> ActivityRow(icon = ActivityIcon.ERROR, text = "$label · failed")
                            else -> ActivityRow(icon = ActivityIcon.OK, text = "$label · ${"%.1f".format(a.durationSec ?: 0.0)}s")
                        }
                    }

                    state.reasoning?.let {
                        Spacer(Modifier.height(10.dp))
                        Reasoning(it)
                    }

                    if (state.answer.isNotBlank()) {
                        Spacer(Modifier.height(12.dp))
                        MarkdownText(text = state.answer, color = MaterialTheme.colorScheme.onSurface)
                    }
                    state.usage?.let { u ->
                        Spacer(Modifier.height(10.dp))
                        Text(
                            "${u.totalTokens} tokens (${u.inputTokens} in · ${u.outputTokens} out)",
                            style = HermesText.mono.copy(fontSize = 11.sp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        // Documents
        if (state.documents.isNotEmpty()) {
            Spacer(Modifier.height(16.dp))
            Text("DOCUMENTS", style = HermesText.displayLabel, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(8.dp))
            state.documents.forEach { doc ->
                Surface(
                    onClick = { openDoc = doc },
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    shape = MaterialTheme.shapes.large,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                ) {
                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Icon(Icons.Filled.Description, null, tint = MaterialTheme.colorScheme.primary)
                        Column(Modifier.weight(1f)) {
                            Text(doc.filename, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
                            Text("${doc.content.length} chars · tap to preview", style = HermesText.mono.copy(fontSize = 11.sp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }

        // What it found
        if (state.findings.isNotEmpty()) {
            Spacer(Modifier.height(16.dp))
            Text("WHAT IT FOUND", style = HermesText.displayLabel, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(8.dp))
            state.findings.forEach { f -> FindingCard(f) }
        }
        Spacer(Modifier.height(24.dp))
    }
}

// --- pieces -----------------------------------------------------------------

private enum class ActivityIcon { OK, ERROR }

@Composable
private fun ActivityRow(spinning: Boolean = false, icon: ActivityIcon? = null, text: String) {
    Row(Modifier.padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        when {
            spinning -> CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.primary)
            icon == ActivityIcon.OK -> Icon(Icons.Filled.CheckCircle, null, tint = MaterialTheme.colorScheme.tertiary, modifier = Modifier.size(16.dp))
            icon == ActivityIcon.ERROR -> Icon(Icons.Filled.Error, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp))
        }
        Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
private fun Reasoning(text: String) {
    var open by remember { mutableStateOf(false) }
    Column {
        TextButton(onClick = { open = !open }, contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)) {
            Text(if (open) "Hide reasoning" else "Show reasoning", style = MaterialTheme.typography.labelMedium)
        }
        if (open) {
            Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun FindingCard(f: ToolFinding) {
    var expanded by remember { mutableStateOf(false) }
    HermesCardBox {
        Column {
            Text("${toolEmoji(f.tool)} ${f.tool}", style = HermesText.mono.copy(fontSize = 11.sp), color = MaterialTheme.colorScheme.tertiary)
            Spacer(Modifier.height(6.dp))
            val shown = if (expanded) f.result else f.result.take(300)
            Text(shown, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface)
            if (f.result.length > 300) {
                TextButton(onClick = { expanded = !expanded }, contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)) {
                    Text(if (expanded) "Show less" else "Show more", style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
    Spacer(Modifier.height(8.dp))
}

@Composable
private fun DocumentPreview(doc: WrittenDocument, onBack: () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
            Text(doc.filename, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
        }
        Surface(
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            shape = MaterialTheme.shapes.large,
            modifier = Modifier.fillMaxSize(),
        ) {
            Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp)) {
                if (doc.filename.endsWith(".md", true) || doc.filename.endsWith(".markdown", true)) {
                    MarkdownText(text = doc.content, color = MaterialTheme.colorScheme.onSurface)
                } else {
                    Text(doc.content, fontFamily = FontFamily.Monospace, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface)
                }
            }
        }
    }
}

@Composable
private fun HermesCardBox(content: @Composable () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        shape = MaterialTheme.shapes.large,
        modifier = Modifier.fillMaxWidth(),
    ) { Column(Modifier.padding(14.dp)) { content() } }
}

@Composable
private fun ErrorText(msg: String) {
    Spacer(Modifier.height(10.dp))
    Text(msg, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
}

/** Our own tool→emoji mapping for the activity rows (clearly ours, not Hermes'). */
private fun toolEmoji(tool: String): String = when {
    tool.startsWith("browser") -> "🌐"
    tool.startsWith("web") -> "🔍"
    tool.contains("file") || tool.contains("write") -> "📄"
    tool.contains("terminal") || tool.contains("shell") || tool.contains("bash") -> "💻"
    tool.contains("image") -> "🎨"
    tool.contains("search") -> "🔎"
    tool.contains("memory") -> "💾"
    else -> "⚙️"
}
