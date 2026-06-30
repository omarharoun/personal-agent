package com.personalagent.shared.graph

import kotlin.math.sqrt

/**
 * CRUD + query surface for the on-device memory graph. Implementations persist
 * locally (encrypted) and never transmit anything off-device.
 */
interface MemoryGraphStore {
    fun nodes(): List<MemoryNode>
    fun edges(): List<MemoryEdge>
    fun node(id: String): MemoryNode?
    fun nodesByType(type: MemoryNodeType): List<MemoryNode>

    /** Insert or replace a node by id. */
    fun upsertNode(node: MemoryNode)

    /** Delete a node AND every edge incident to it. */
    fun deleteNode(id: String)

    /** Insert or replace an edge by id. */
    fun upsertEdge(edge: MemoryEdge)
    fun deleteEdge(id: String)

    /** Nodes one hop away from [nodeId] in either direction. */
    fun neighbors(nodeId: String): List<MemoryNode>

    /** Nodes ranked by cosine similarity of [embedding] to their stored embedding. */
    fun similar(embedding: List<Float>, topK: Int = 5, minScore: Float = 0.2f): List<ScoredNode>

    /** Replace the entire graph (used by import). */
    fun replaceAll(nodes: List<MemoryNode>, edges: List<MemoryEdge>)

    fun clear()
}

/** Cosine similarity of two equal-length vectors; 0 if either is empty/degenerate. */
internal fun cosine(a: List<Float>, b: List<Float>): Float {
    if (a.isEmpty() || b.isEmpty() || a.size != b.size) return 0f
    var dot = 0f
    var na = 0f
    var nb = 0f
    for (i in a.indices) {
        dot += a[i] * b[i]
        na += a[i] * a[i]
        nb += b[i] * b[i]
    }
    val denom = sqrt(na) * sqrt(nb)
    return if (denom > 1e-12f) dot / denom else 0f
}
