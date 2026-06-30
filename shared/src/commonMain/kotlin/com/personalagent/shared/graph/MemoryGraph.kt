package com.personalagent.shared.graph

import kotlinx.serialization.Serializable

/**
 * The kinds of things the on-device memory graph remembers ABOUT THE USER.
 * Deliberately small and human-meaningful so the Memory screen can group by type.
 */
enum class MemoryNodeType { PERSON, PREFERENCE, GOAL, FACT, EVENT, PLACE, TRAIT }

/**
 * A node in the on-device memory graph — one item known about the user.
 *
 * 🔒 This lives ONLY on the device (encrypted at rest) and is NEVER sent to the
 * cloud. It grounds the on-device model and is fully user-editable/exportable.
 *
 * @param attributes free-form extra detail (e.g. {"name":"Sarah","age":"30"}).
 * @param salience how strongly the user cares / how often it recurs; bumped on
 *   repeat mention so retrieval can prefer durable, frequently-referenced items.
 * @param embedding semantic vector of the node's text, used for dedup + retrieval.
 *   Empty when no embedder was available at write time (the graph still works).
 */
@Serializable
data class MemoryNode(
    val id: String,
    val type: MemoryNodeType,
    val label: String,
    val attributes: Map<String, String> = emptyMap(),
    val salience: Float = 1f,
    val createdAt: Long,
    val updatedAt: Long,
    val sourceChatId: String? = null,
    val embedding: List<Float> = emptyList(),
) {
    /** The text the embedder vectorizes — label plus any attribute values. */
    fun embeddingText(): String =
        (listOf(label) + attributes.entries.map { "${it.key} ${it.value}" }).joinToString(" ").trim()
}

/**
 * A directed relationship between two nodes (e.g. user —has_sister→ Sarah,
 * user —prefers→ mornings). [relation] is a short snake_case verb phrase.
 */
@Serializable
data class MemoryEdge(
    val id: String,
    val fromId: String,
    val toId: String,
    val relation: String,
    val createdAt: Long,
)

/** A node paired with its similarity score for a query (higher = more relevant). */
data class ScoredNode(val node: MemoryNode, val score: Float)

/** Serializable snapshot of the whole graph — the on-disk + export/import format. */
@Serializable
data class MemoryGraphSnapshot(
    val version: Int = 1,
    val nodes: List<MemoryNode> = emptyList(),
    val edges: List<MemoryEdge> = emptyList(),
)
