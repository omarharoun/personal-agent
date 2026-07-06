package com.personalagent.shared.knowledge

import com.personalagent.shared.chat.StoredConversation
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Turns the user's saved chat records into a [KnowledgeGraph]. Pure, common, and
 * unit-tested so both the model path and the offline fallback are verifiable.
 *
 * Two ways to build the same shape:
 *  1. **Model** (preferred) — [buildExtractionPrompt] asks the user's Hermes for a
 *     strict `{nodes,edges}` JSON extraction; [parse] reads it back leniently.
 *  2. **Keywords** — [keywordFallback] derives nodes from term frequency and edges
 *     from co-occurrence within a conversation, so the map still populates with no
 *     model call (offline / not connected).
 *
 * Both go through [attachSnippets] so tapping a node shows the real questions the
 * user asked about it.
 */
object KnowledgeGraphExtractor {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    /** Only the user's own turns — the things *they* asked about / explored. */
    fun userTexts(conversations: List<StoredConversation>): List<String> =
        conversations.flatMap { c -> c.messages.filter { it.role == "user" && it.text.isNotBlank() }.map { it.text.trim() } }

    /**
     * A content fingerprint of the chat records, so the service knows when new
     * chats have accumulated (→ rebuild). Stable across launches for the same data.
     */
    fun signature(conversations: List<StoredConversation>): String {
        val basis = conversations
            .sortedBy { it.id }
            .joinToString("|") { "${it.conversationId}:${it.messages.size}:${it.updatedAt}" }
        var h = 1125899906842597L // FNV-ish rolling hash, platform-stable
        for (ch in basis) h = 31 * h + ch.code
        val userTurns = conversations.sumOf { c -> c.messages.count { it.role == "user" } }
        return "${conversations.size}c-${userTurns}u-${h.toULong().toString(16)}"
    }

    // --- Model path -----------------------------------------------------------

    /**
     * Build the strict extraction prompt from the user's questions. The most recent
     * [maxTurns] user turns are included (older ones drop off), each truncated, so a
     * long history still fits a single request.
     */
    fun buildExtractionPrompt(conversations: List<StoredConversation>, maxTurns: Int = 120): String {
        val turns = userTexts(conversations).takeLast(maxTurns).map { it.replace('\n', ' ').take(240) }
        val numbered = turns.mapIndexed { i, t -> "${i + 1}. $t" }.joinToString("\n")
        return buildString {
            append("You are building a KNOWLEDGE MAP of what a person has asked about and explored, ")
            append("based ONLY on the list of their own messages below. Identify the distinct topics, ")
            append("entities and concepts they engaged with, and how those relate.\n\n")
            append("Return ONLY a single minified JSON object, no prose, no code fence, in EXACTLY this shape:\n")
            append("{\"nodes\":[{\"id\":\"kebab-id\",\"label\":\"Short Label\",\"type\":\"topic|entity|concept|person|place|activity|skill\",\"weight\":1-10}],")
            append("\"edges\":[{\"from\":\"kebab-id\",\"to\":\"kebab-id\",\"relation\":\"short phrase\"}]}\n\n")
            append("Rules: 8-40 nodes; ids are lowercase kebab-case and unique; weight reflects how often/")
            append("how strongly the person returned to it; every edge's from/to MUST be an existing node id; ")
            append("prefer meaningful relations over a hub-and-spoke; do not invent topics not present below.\n\n")
            append("The person's messages:\n")
            append(numbered.ifBlank { "(none)" })
        }
    }

    @Serializable private data class Dto(val nodes: List<NodeDto> = emptyList(), val edges: List<EdgeDto> = emptyList())
    @Serializable private data class NodeDto(
        val id: String = "",
        val label: String = "",
        val type: String = "topic",
        val weight: Float = 1f,
    )
    @Serializable private data class EdgeDto(
        val from: String = "",
        val to: String = "",
        val relation: String = "related",
    )

    /**
     * Parse a model reply into a graph, tolerating code fences / surrounding prose.
     * Returns null if no usable JSON object with nodes is found. Edges whose
     * endpoints don't resolve to a node are dropped; weights are clamped.
     */
    fun parse(raw: String): KnowledgeGraph? {
        val obj = extractJsonObject(raw) ?: return null
        val dto = runCatching { json.decodeFromString(Dto.serializer(), obj) }.getOrNull() ?: return null
        val nodes = dto.nodes
            .filter { it.id.isNotBlank() && it.label.isNotBlank() }
            .distinctBy { it.id }
            .map {
                KnowledgeNode(
                    id = it.id.trim(),
                    label = it.label.trim(),
                    type = it.type.trim().ifBlank { "topic" }.lowercase(),
                    weight = it.weight.coerceIn(1f, 10f),
                )
            }
        if (nodes.isEmpty()) return null
        val ids = nodes.map { it.id }.toSet()
        val edges = dto.edges
            .filter { it.from in ids && it.to in ids && it.from != it.to }
            .distinctBy { setOf(it.from, it.to) }
            .map { KnowledgeEdge(it.from, it.to, it.relation.trim().ifBlank { "related" }) }
        return KnowledgeGraph(nodes = nodes, edges = edges, source = KnowledgeGraphSource.MODEL)
    }

    /** Grab the outermost `{...}` object from a possibly-noisy string. */
    private fun extractJsonObject(raw: String): String? {
        val start = raw.indexOf('{')
        val end = raw.lastIndexOf('}')
        if (start < 0 || end <= start) return null
        return raw.substring(start, end + 1)
    }

    // --- Keyword / co-occurrence fallback ------------------------------------

    /**
     * Offline extraction: nodes = most-frequent meaningful terms across the user's
     * messages (weight ≈ frequency), edges = terms that co-occur within the same
     * conversation. Deterministic; works with no model / no connection.
     */
    fun keywordFallback(
        conversations: List<StoredConversation>,
        maxNodes: Int = 28,
        maxEdges: Int = 40,
    ): KnowledgeGraph {
        // Tokenize each conversation into its set of meaningful terms.
        val perConvTerms: List<Set<String>> = conversations.map { c ->
            val terms = LinkedHashSet<String>()
            for (m in c.messages) if (m.role == "user") terms += tokenize(m.text)
            terms
        }
        // Global frequency = how many conversations a term appears in + raw count.
        val docFreq = HashMap<String, Int>()
        val rawFreq = HashMap<String, Int>()
        for (c in conversations) for (m in c.messages) if (m.role == "user") {
            val seen = HashSet<String>()
            for (t in tokenize(m.text)) {
                rawFreq[t] = (rawFreq[t] ?: 0) + 1
                if (seen.add(t)) docFreq[t] = (docFreq[t] ?: 0) + 1
            }
        }
        if (rawFreq.isEmpty()) return KnowledgeGraph(source = KnowledgeGraphSource.EMPTY)

        val top = rawFreq.entries
            .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
            .take(maxNodes)
            .map { it.key }
        val topSet = top.toSet()
        val maxCount = top.maxOf { rawFreq[it] ?: 1 }

        val nodes = top.map { term ->
            KnowledgeNode(
                id = term,
                label = displayLabel(term),
                type = "topic",
                weight = 1f + 9f * ((rawFreq[term] ?: 1).toFloat() / maxCount),
            )
        }

        // Co-occurrence within a conversation → undirected edges.
        val pairCount = HashMap<Pair<String, String>, Int>()
        for (termsInConv in perConvTerms) {
            val present = termsInConv.filter { it in topSet }.sorted()
            for (i in present.indices) for (j in i + 1 until present.size) {
                val key = present[i] to present[j]
                pairCount[key] = (pairCount[key] ?: 0) + 1
            }
        }
        val edges = pairCount.entries
            .sortedByDescending { it.value }
            .take(maxEdges)
            .map { (pair, count) -> KnowledgeEdge(pair.first, pair.second, "mentioned together", count.toFloat()) }

        return KnowledgeGraph(nodes = nodes, edges = edges, source = KnowledgeGraphSource.KEYWORDS)
    }

    /**
     * Attach up to [perNode] real user questions that mention each node's label, so
     * a tap reveals what the user actually asked. Works for both build paths.
     */
    fun attachSnippets(graph: KnowledgeGraph, conversations: List<StoredConversation>, perNode: Int = 4): KnowledgeGraph {
        val texts = userTexts(conversations)
        val enriched = graph.nodes.map { node ->
            val needles = (listOf(node.label) + node.label.split(' ', '-'))
                .map { it.trim().lowercase() }
                .filter { it.length >= 3 }
                .distinct()
            val hits = texts.filter { t ->
                val lc = t.lowercase()
                needles.any { lc.contains(it) }
            }.distinct().take(perNode).map { it.replace('\n', ' ').take(160) }
            node.copy(snippets = hits)
        }
        return graph.copy(nodes = enriched)
    }

    // --- tokenization ---------------------------------------------------------

    private fun tokenize(text: String): List<String> =
        text.lowercase()
            .map { if (it.isLetterOrDigit()) it else ' ' }
            .joinToString("")
            .split(' ')
            .map { it.trim() }
            .filter { it.length >= 4 && it.length <= 20 && it !in STOPWORDS && !it.all { c -> c.isDigit() } }

    private fun displayLabel(term: String): String =
        term.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }

    private val STOPWORDS: Set<String> = setOf(
        "the", "and", "for", "are", "but", "not", "you", "your", "with", "this", "that", "have",
        "from", "they", "will", "would", "there", "their", "what", "about", "which", "when", "make",
        "like", "time", "just", "know", "take", "into", "than", "then", "them", "these", "some",
        "could", "should", "because", "been", "being", "were", "does", "doing", "done", "want",
        "need", "help", "please", "tell", "give", "much", "many", "more", "most", "other", "also",
        "very", "really", "thing", "things", "something", "anything", "everything", "someone",
        "cant", "dont", "wont", "isnt", "im", "ive", "id", "ill", "youre", "were", "lets",
        "how", "why", "who", "where", "can", "get", "got", "one", "two", "out", "off", "our",
        "should", "here", "over", "such", "only", "even", "still", "back", "good", "well", "going",
        "think", "maybe", "sure", "yeah", "okay", "hello", "thanks", "thank", "remember", "reminder",
        "remind", "note", "notes", "today", "tomorrow", "yesterday", "week", "month", "year",
        "hermes", "agent", "chat", "message", "reply", "answer", "question",
    )
}
