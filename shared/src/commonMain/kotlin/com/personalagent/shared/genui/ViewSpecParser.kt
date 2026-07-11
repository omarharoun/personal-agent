package com.personalagent.shared.genui

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Lenient parse of the model's strict view-spec JSON into a validated
 * [ComposedView], enforcing the **honesty rule** as it goes. Mirrors
 * [com.personalagent.shared.knowledge.KnowledgeGraphExtractor.parse] and
 * [com.personalagent.shared.learning.LearningRecommendationParser]: extract the
 * outermost `{…}`, decode with `ignoreUnknownKeys`, then sanitize + drop.
 *
 * Reconciliation against [Facts] is where honesty is enforced (docs/GENERATIVE_UI.md):
 *  - a stat's number is NEVER taken from the model — it names a metric `key` and we
 *    write the REAL value; a stat with an unknown key is dropped;
 *  - a sparkline's points come from the real series for its `key`, else dropped;
 *  - a plan row survives only if its `id` resolves to a real record, and its tap
 *    target/done state mirror that record; an unresolved id is dropped, never faked;
 *  - a resource-rec renders only the user's real learning focus resource.
 *
 * Everything textual is sanitized (control chars stripped, whitespace collapsed,
 * length-capped) and treated as INERT display data — never markup, never executed.
 */
object ViewSpecParser {

    private const val MAX_BLOCKS = 8

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    @Serializable private data class SpecDto(
        val view: String = "",
        val title: String? = null,
        val blocks: List<BlockDto> = emptyList(),
    )

    @Serializable private data class BlockDto(
        val type: String = "",
        // prose-line
        val text: String? = null,
        val emphasis: List<String> = emptyList(),
        // stat-grid
        val stats: List<StatDto> = emptyList(),
        // sparkline
        val key: String? = null,
        val caption: String? = null,
        // plan
        val heading: String? = null,
        val meta: String? = null,
        val items: List<ItemDto> = emptyList(),
        // resource-rec
        val goal: String? = null,
        val level: String? = null,
        val resourceId: String? = null,
    )

    @Serializable private data class StatDto(val key: String = "", val label: String? = null)

    @Serializable private data class ItemDto(
        val id: String = "",
        val title: String? = null,
        val time: String? = null,
        val note: String? = null,
    )

    /**
     * Parse + reconcile [reply] against [facts]. Returns a [ComposedView] with at
     * least one surviving block, or null (→ caller falls back to
     * [defaultView]/prose). Never throws on bad model output.
     */
    fun parse(reply: String, facts: Facts): ComposedView? {
        val obj = extractJsonObject(reply) ?: return null
        val dto = runCatching { json.decodeFromString(SpecDto.serializer(), obj) }.getOrNull() ?: return null

        val blocks = dto.blocks
            .take(MAX_BLOCKS)
            .mapNotNull { reconcileBlock(it, facts) }
        if (blocks.isEmpty()) return null

        val view = dto.view.sanitize(40).ifBlank { "view" }
        val title = dto.title?.sanitize(60)?.ifBlank { null }
        return ComposedView(view = view, title = title, blocks = blocks, provenance = ComposedView.PROVENANCE_MODEL)
    }

    private fun reconcileBlock(b: BlockDto, facts: Facts): ViewBlock? = when (b.type.trim().lowercase()) {
        ViewBlock.ProseLine.KIND -> proseLine(b)
        ViewBlock.StatGrid.KIND -> statGrid(b, facts)
        ViewBlock.Sparkline.KIND -> sparkline(b, facts)
        ViewBlock.Plan.KIND -> plan(b, facts)
        ViewBlock.ResourceRec.KIND -> resourceRec(b, facts)
        else -> null // unknown primitive → dropped, never rendered
    }

    private fun proseLine(b: BlockDto): ViewBlock? {
        val text = (b.text ?: "").sanitize(200)
        if (text.isBlank()) return null
        val lc = text.lowercase()
        val emphasis = b.emphasis
            .map { it.sanitize(60) }
            .filter { it.isNotBlank() && lc.contains(it.lowercase()) }
            .distinct()
            .take(3)
        return ViewBlock.ProseLine(text, emphasis)
    }

    private fun statGrid(b: BlockDto, facts: Facts): ViewBlock? {
        val stats = b.stats
            .mapNotNull { s ->
                val key = s.key.trim()
                val real = facts.metrics[key] ?: return@mapNotNull null // unknown metric → drop
                val label = s.label?.sanitize(24)?.ifBlank { null } ?: Facts.defaultLabel(key)
                key to Stat(value = real.toString(), label = label)
            }
            .distinctBy { it.first }
            .map { it.second }
            .take(4)
        if (stats.size < 2) return null
        return ViewBlock.StatGrid(stats)
    }

    private fun sparkline(b: BlockDto, facts: Facts): ViewBlock? {
        val key = (b.key ?: "").trim()
        val series = facts.spark[key] ?: return null // unknown series → drop
        if (series.isEmpty()) return null
        val caption = b.caption?.sanitize(40)?.ifBlank { null } ?: facts.sparkCaptions[key]
        return ViewBlock.Sparkline(
            points = series.map { it.toDouble() },
            caption = caption,
            highlightIndex = series.size - 1, // accent "today" (newest bucket)
        )
    }

    private fun plan(b: BlockDto, facts: Facts): ViewBlock? {
        val rows = b.items.mapNotNull { it.toRow(facts) }.take(8)
        if (rows.isEmpty()) return null
        val heading = (b.heading ?: "").sanitize(40).ifBlank { "Your plan" }
        val meta = b.meta?.sanitize(32)?.ifBlank { null }
        return ViewBlock.Plan(heading, meta, rows)
    }

    private fun ItemDto.toRow(facts: Facts): PlanRow? {
        val ref = facts.refs[id.trim()] ?: return null // invented / unresolved id → drop
        val title = title?.sanitize(80)?.ifBlank { null } ?: ref.title.sanitize(80)
        if (title.isBlank()) return null
        return PlanRow(
            id = ref.id,
            title = title,
            time = time?.sanitize(24)?.ifBlank { null } ?: ref.time,
            note = note?.sanitize(60)?.ifBlank { null },
            source = ref.source,
            sourceId = ref.id,
            done = ref.done,
            actionable = true, // resolved to a real record
        )
    }

    private fun resourceRec(b: BlockDto, facts: Facts): ViewBlock? {
        // Only render the user's real, tracked learning focus — never a fabricated link.
        val focus = facts.focus ?: return null
        val wantId = b.resourceId?.trim()
        if (!wantId.isNullOrBlank() && wantId != focus.resource.id) return null
        val goal = b.goal?.sanitize(60)?.ifBlank { null } ?: focus.goalTopic
        val level = b.level?.sanitize(24)?.ifBlank { null } ?: focus.level
        return ViewBlock.ResourceRec(goal = goal, level = level, resource = focus.resource)
    }

    // --- helpers --------------------------------------------------------------

    /** Strip control chars, collapse whitespace, cap length (as the other parsers). */
    private fun String.sanitize(maxLen: Int): String =
        trim()
            .map { if (it.isISOControl()) ' ' else it }
            .joinToString("")
            .replace(Regex("\\s+"), " ")
            .trim()
            .take(maxLen)

    private fun extractJsonObject(raw: String): String? {
        val start = raw.indexOf('{')
        val end = raw.lastIndexOf('}')
        if (start < 0 || end <= start) return null
        return raw.substring(start, end + 1)
    }
}
