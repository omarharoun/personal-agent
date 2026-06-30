package com.personalagent.shared.graph

import com.personalagent.shared.conversation.GenOptions
import com.personalagent.shared.conversation.OnDeviceLlm
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** A candidate node the extractor proposes (label-based; ids are assigned on merge). */
data class ExtractedNode(
    val type: MemoryNodeType,
    val label: String,
    val attributes: Map<String, String> = emptyMap(),
)

/** A candidate relationship, by node LABEL (resolved to ids during merge). */
data class ExtractedEdge(val fromLabel: String, val toLabel: String, val relation: String)

/** The result of extracting memory items from one exchange. */
data class ExtractedGraph(
    val nodes: List<ExtractedNode> = emptyList(),
    val edges: List<ExtractedEdge> = emptyList(),
)

/** Pulls candidate memory items ABOUT THE USER from a user/assistant exchange. */
interface MemoryExtractor {
    suspend fun extract(userText: String, assistantText: String): ExtractedGraph
}

// --- Heuristic fallback ------------------------------------------------------
/**
 * Regex/keyword extractor used when no on-device model is available (or as a
 * backstop). Conservative: it only fires on clear first-person self-statements,
 * so it rarely invents facts. Everything links back to a canonical "user" node.
 */
class HeuristicMemoryExtractor : MemoryExtractor {
    override suspend fun extract(userText: String, assistantText: String): ExtractedGraph {
        val text = userText.trim()
        if (text.isEmpty()) return ExtractedGraph()
        val nodes = mutableListOf<ExtractedNode>()
        val edges = mutableListOf<ExtractedEdge>()

        fun add(type: MemoryNodeType, label: String, relation: String) {
            val clean = label.trim().trimEnd('.', '!', ',').trim()
            if (clean.isEmpty() || clean.length > 80) return
            nodes += ExtractedNode(type, clean)
            edges += ExtractedEdge(USER_LABEL, clean, relation)
        }

        NAME.find(text)?.groupValues?.get(1)?.let { add(MemoryNodeType.PERSON, it, "is_named") }
        PREFERS.findAll(text).forEach { add(MemoryNodeType.PREFERENCE, it.groupValues[2], "prefers") }
        DISLIKES.findAll(text).forEach { add(MemoryNodeType.PREFERENCE, it.groupValues[1], "dislikes") }
        GOAL.findAll(text).forEach { add(MemoryNodeType.GOAL, it.groupValues[2], "wants") }
        LIVES.find(text)?.groupValues?.get(1)?.let { add(MemoryNodeType.PLACE, it, "lives_in") }
        RELATIVE.findAll(text).forEach {
            add(MemoryNodeType.PERSON, it.groupValues[2], "has_${it.groupValues[1].lowercase()}")
        }

        return ExtractedGraph(nodes.distinctBy { it.type to it.label.lowercase() }, edges)
    }

    private companion object {
        val NAME = Regex("""\bmy name is\s+([A-Z][\w'-]+(?:\s+[A-Z][\w'-]+)?)""", RegexOption.IGNORE_CASE)
        val PREFERS = Regex("""\bi (prefer|like|love|enjoy)\s+([^.,!?;]{2,60})""", RegexOption.IGNORE_CASE)
        val DISLIKES = Regex("""\bi (?:dislike|hate|don't like|do not like)\s+([^.,!?;]{2,60})""", RegexOption.IGNORE_CASE)
        val GOAL = Regex("""\bi (want to|plan to|am trying to|hope to|aim to)\s+([^.,!?;]{2,80})""", RegexOption.IGNORE_CASE)
        val LIVES = Regex("""\bi live in\s+([A-Z][\w'-]+(?:\s+[A-Z][\w'-]+)?)""", RegexOption.IGNORE_CASE)
        val RELATIVE = Regex("""\bmy (sister|brother|mother|father|mom|dad|wife|husband|partner|son|daughter|friend|boss|manager)\s+(?:is\s+|named\s+|is named\s+)?([A-Z][\w'-]+)""", RegexOption.IGNORE_CASE)
    }
}

// --- LLM extractor -----------------------------------------------------------
/**
 * Asks the on-device LLM for a strict JSON object of memory items, then parses
 * DEFENSIVELY: it pulls the first {...} block out of whatever the model returns
 * (small models emit prose/fences around it), drops anything malformed, and never
 * throws. On empty/garbage output it returns an empty graph (the caller may then
 * fall back to the heuristic extractor).
 */
class LlmMemoryExtractor(
    private val llm: OnDeviceLlm,
    private val json: Json = LENIENT,
) : MemoryExtractor {

    override suspend fun extract(userText: String, assistantText: String): ExtractedGraph {
        val prompt = buildPrompt(userText, assistantText)
        val raw = runCatching { llm.generate(prompt, OPTIONS) }.getOrNull() ?: return ExtractedGraph()
        return parse(raw)
    }

    /** Public so tests can exercise defensive parsing without a model. */
    fun parse(raw: String): ExtractedGraph {
        val obj = extractFirstJsonObject(raw) ?: return ExtractedGraph()
        val dto = runCatching { json.decodeFromString(ExtractionDto.serializer(), obj) }.getOrNull()
            ?: return ExtractedGraph()
        val nodes = dto.nodes.orEmpty().mapNotNull { it.toNodeOrNull() }
        val edges = dto.edges.orEmpty().mapNotNull { it.toEdgeOrNull() }
        return ExtractedGraph(nodes, edges)
    }

    private fun buildPrompt(userText: String, assistantText: String): String =
        """
        Extract durable facts ABOUT THE USER from this exchange. Output ONLY a JSON
        object, no prose. Schema:
        {"nodes":[{"type":"PERSON|PREFERENCE|GOAL|FACT|EVENT|PLACE|TRAIT","label":"...","attributes":{}}],
         "edges":[{"from":"user","to":"<label>","relation":"snake_case_verb"}]}
        Only include things clearly about the user. If nothing, return {"nodes":[],"edges":[]}.

        User: ${userText.trim()}
        Assistant: ${assistantText.trim()}
        """.trimIndent()

    @Serializable private data class ExtractionDto(val nodes: List<NodeDto>? = null, val edges: List<EdgeDto>? = null)
    @Serializable private data class NodeDto(
        val type: String? = null,
        val label: String? = null,
        val attributes: Map<String, String>? = null,
    ) {
        fun toNodeOrNull(): ExtractedNode? {
            val l = label?.trim()?.takeIf { it.isNotEmpty() && it.length <= 120 } ?: return null
            val t = type?.trim()?.uppercase()?.let { runCatching { MemoryNodeType.valueOf(it) }.getOrNull() }
                ?: MemoryNodeType.FACT
            return ExtractedNode(t, l, attributes.orEmpty())
        }
    }

    @Serializable private data class EdgeDto(
        val from: String? = null,
        val to: String? = null,
        val relation: String? = null,
    ) {
        fun toEdgeOrNull(): ExtractedEdge? {
            val f = from?.trim()?.ifEmpty { null } ?: USER_LABEL
            val t = to?.trim()?.takeIf { it.isNotEmpty() } ?: return null
            val r = relation?.trim()?.takeIf { it.isNotEmpty() } ?: "related_to"
            return ExtractedEdge(f, t, r)
        }
    }

    private companion object {
        val OPTIONS = GenOptions(maxTokens = 256, temperature = 0.0f)
        val LENIENT = Json { ignoreUnknownKeys = true; isLenient = true; coerceInputValues = true }
    }
}

/** Extract the first balanced {...} JSON object substring, or null. */
internal fun extractFirstJsonObject(text: String): String? {
    val start = text.indexOf('{')
    if (start < 0) return null
    var depth = 0
    var inString = false
    var escaped = false
    for (i in start until text.length) {
        val c = text[i]
        when {
            escaped -> escaped = false
            c == '\\' && inString -> escaped = true
            c == '"' -> inString = !inString
            !inString && c == '{' -> depth++
            !inString && c == '}' -> {
                depth--
                if (depth == 0) return text.substring(start, i + 1)
            }
        }
    }
    return null
}

/**
 * Uses [LlmMemoryExtractor] when the model is available and it returns something,
 * otherwise falls back to [HeuristicMemoryExtractor]. Never throws.
 */
class CompositeMemoryExtractor(
    private val llm: OnDeviceLlm,
    json: Json = Json { ignoreUnknownKeys = true; isLenient = true; coerceInputValues = true },
) : MemoryExtractor {
    private val llmExtractor = LlmMemoryExtractor(llm, json)
    private val heuristic = HeuristicMemoryExtractor()

    override suspend fun extract(userText: String, assistantText: String): ExtractedGraph {
        if (llm.isAvailable) {
            val viaLlm = runCatching { llmExtractor.extract(userText, assistantText) }.getOrNull()
            if (viaLlm != null && (viaLlm.nodes.isNotEmpty() || viaLlm.edges.isNotEmpty())) return viaLlm
        }
        return runCatching { heuristic.extract(userText, assistantText) }.getOrDefault(ExtractedGraph())
    }
}

/** The canonical self-node label everything about the user links back to. */
const val USER_LABEL = "user"
