package com.personalagent.shared.knowledge

import kotlinx.serialization.Serializable

/**
 * A knowledge graph **derived from the user's own chat records** — the topics,
 * entities and concepts they've asked about and how those relate.
 *
 * IMPORTANT framing: this is honestly "derived from your conversations". It is
 * **not** Hermes' server-side memory of the user and must never be presented as
 * such — it's a local, best-effort model of what the user has explored in this
 * app, computed from the on-device [com.personalagent.shared.chat.ChatStore] and
 * cached in [KnowledgeGraphStore].
 */

/** One node — a topic/entity/concept the user has explored. [type] drives colour. */
@Serializable
data class KnowledgeNode(
    val id: String,
    val label: String,
    /** Free-form category (topic/entity/concept/person/place/activity/skill/…). */
    val type: String = "topic",
    /** Relative importance ≈ how often/how strongly it recurs (drives dot size). */
    val weight: Float = 1f,
    /** Related things the user actually asked, shown when the node is tapped. */
    val snippets: List<String> = emptyList(),
)

/** A relationship between two nodes. [relation] is a short human phrase. */
@Serializable
data class KnowledgeEdge(
    val from: String,
    val to: String,
    val relation: String = "related",
    val weight: Float = 1f,
)

/** How a graph was produced, so the UI can be transparent about it. */
enum class KnowledgeGraphSource { MODEL, KEYWORDS, EMPTY }

/**
 * The cached graph plus provenance. [sourceSignature] fingerprints the chat data
 * it was built from, so the service can tell when new chats have accumulated and a
 * rebuild is warranted (stale-while-revalidate, like the home cache).
 */
@Serializable
data class KnowledgeGraph(
    val nodes: List<KnowledgeNode> = emptyList(),
    val edges: List<KnowledgeEdge> = emptyList(),
    val builtAt: Long = 0L,
    val sourceConversationCount: Int = 0,
    val sourceSignature: String = "",
    val source: KnowledgeGraphSource = KnowledgeGraphSource.EMPTY,
) {
    val isEmpty: Boolean get() = nodes.isEmpty()
}
