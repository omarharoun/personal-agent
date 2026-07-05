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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.personalagent.android.ui.theme.HermesText
import com.personalagent.shared.hermes.HermesSkill

/**
 * The skills gallery — browsable, searchable, grouped by category, from the real
 * `/v1/skills`. Category icons/labels are OUR own tasteful mapping (Hermes ships
 * no skill icons); we don't claim them as Hermes'.
 */
@Composable
fun SkillsScreen(vm: SkillsViewModel) {
    val state by vm.state.collectAsStateWithLifecycle()

    Column(Modifier.fillMaxSize().padding(top = 8.dp)) {
        OutlinedTextField(
            value = state.query,
            onValueChange = vm::onQueryChange,
            label = { Text("Search skills") },
            leadingIcon = { Icon(Icons.Filled.Search, null) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        if (state.all.isNotEmpty()) {
            Text(
                "${state.all.size} skills · ${state.grouped.size} categories",
                style = HermesText.mono.copy(fontSize = 11.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp, start = 2.dp),
            )
        }

        when {
            state.loading && state.all.isEmpty() ->
                Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
            state.error != null && state.all.isEmpty() ->
                Text(state.error!!, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 24.dp))
            state.all.isEmpty() ->
                Text("No skills installed on this Hermes.", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 24.dp))
            else -> LazyColumn(
                Modifier.fillMaxSize().padding(top = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                state.grouped.forEach { (category, skills) ->
                    item(key = "hdr_$category") { CategoryHeader(category, skills.size) }
                    items(skills, key = { it.name }) { s -> SkillCard(s, category) }
                }
                item { Spacer(Modifier.height(16.dp)) }
            }
        }
    }
}

@Composable
private fun CategoryHeader(category: String, count: Int) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 6.dp)) {
        Text(categoryEmoji(category), fontSize = 18.sp)
        Text(categoryLabel(category).uppercase(), style = HermesText.displayLabel, color = MaterialTheme.colorScheme.primary)
        Text("· $count", style = HermesText.mono.copy(fontSize = 11.sp), color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun SkillCard(skill: HermesSkill, category: String) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        shape = MaterialTheme.shapes.large,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(14.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(categoryEmoji(category), fontSize = 22.sp)
            Column(Modifier.weight(1f)) {
                Text(
                    skill.name.replace('-', ' ').replaceFirstChar { it.uppercase() },
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    skill.description.trim().replace('\n', ' '),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 3,
                )
            }
        }
    }
}

// --- our own category → icon/label mapping (clearly ours, not Hermes') -------

private fun categoryEmoji(category: String): String = when (category) {
    "autonomous-ai-agents" -> "🤖"
    "creative" -> "🎨"
    "data-science" -> "📊"
    "email" -> "✉️"
    "github" -> "🐙"
    "media" -> "🎬"
    "mlops" -> "⚙️"
    "note-taking" -> "📝"
    "productivity" -> "✅"
    "research" -> "🔬"
    "smart-home" -> "🏠"
    "social-media" -> "💬"
    "software-development" -> "💻"
    else -> "🧩"
}

private fun categoryLabel(category: String): String = when (category) {
    "other" -> "Other"
    else -> category.replace('-', ' ')
}
