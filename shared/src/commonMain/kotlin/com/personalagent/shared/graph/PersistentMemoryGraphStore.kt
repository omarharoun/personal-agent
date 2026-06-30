package com.personalagent.shared.graph

import com.personalagent.shared.store.KeyValueStorage
import kotlinx.serialization.json.Json

/**
 * A [MemoryGraphStore] persisted as a single JSON snapshot in a [KeyValueStorage].
 *
 * 🔒 In production the backing store is an `EncryptedKeyValueStorage`, so the whole
 * graph is sealed at rest by the device's hardware-backed key — it never leaves the
 * device and is unreadable without it. The graph is loaded into memory on first use
 * and every mutation writes the full snapshot back (graphs are small in v1).
 *
 * Not internally synchronized (KMP commonMain has no `synchronized`); concurrency is
 * the caller's responsibility — [MemoryGraphService] funnels all access through a
 * coroutine [kotlinx.coroutines.sync.Mutex].
 */
class PersistentMemoryGraphStore(
    private val storage: KeyValueStorage,
    private val json: Json = DEFAULT_JSON,
) : MemoryGraphStore {

    private val nodesById = LinkedHashMap<String, MemoryNode>()
    private val edgesById = LinkedHashMap<String, MemoryEdge>()
    private var loaded = false

    private fun ensureLoaded() {
        if (loaded) return
        val raw = storage.get(KEY)
        if (!raw.isNullOrBlank()) {
            runCatching { json.decodeFromString(MemoryGraphSnapshot.serializer(), raw) }
                .getOrNull()
                ?.let { snap ->
                    snap.nodes.forEach { nodesById[it.id] = it }
                    snap.edges.forEach { edgesById[it.id] = it }
                }
        }
        loaded = true
    }

    private fun persist() {
        val snap = MemoryGraphSnapshot(nodes = nodesById.values.toList(), edges = edgesById.values.toList())
        storage.put(KEY, json.encodeToString(MemoryGraphSnapshot.serializer(), snap))
    }

    override fun nodes(): List<MemoryNode> { ensureLoaded(); return nodesById.values.toList() }
    override fun edges(): List<MemoryEdge> { ensureLoaded(); return edgesById.values.toList() }
    override fun node(id: String): MemoryNode? { ensureLoaded(); return nodesById[id] }

    override fun nodesByType(type: MemoryNodeType): List<MemoryNode> {
        ensureLoaded(); return nodesById.values.filter { it.type == type }
    }

    override fun upsertNode(node: MemoryNode) { ensureLoaded(); nodesById[node.id] = node; persist() }

    override fun deleteNode(id: String) {
        ensureLoaded()
        nodesById.remove(id)
        // Drop incident edges so the graph stays consistent.
        edgesById.values.filter { it.fromId == id || it.toId == id }.map { it.id }
            .forEach { edgesById.remove(it) }
        persist()
    }

    override fun upsertEdge(edge: MemoryEdge) { ensureLoaded(); edgesById[edge.id] = edge; persist() }
    override fun deleteEdge(id: String) { ensureLoaded(); edgesById.remove(id); persist() }

    override fun neighbors(nodeId: String): List<MemoryNode> {
        ensureLoaded()
        val ids = edgesById.values.mapNotNull {
            when (nodeId) {
                it.fromId -> it.toId
                it.toId -> it.fromId
                else -> null
            }
        }.toSet()
        return ids.mapNotNull { nodesById[it] }
    }

    override fun similar(embedding: List<Float>, topK: Int, minScore: Float): List<ScoredNode> {
        ensureLoaded()
        if (embedding.isEmpty()) return emptyList()
        return nodesById.values
            .mapNotNull { n -> if (n.embedding.isEmpty()) null else ScoredNode(n, cosine(embedding, n.embedding)) }
            .filter { it.score >= minScore }
            .sortedByDescending { it.score }
            .take(topK)
    }

    override fun replaceAll(nodes: List<MemoryNode>, edges: List<MemoryEdge>) {
        ensureLoaded()
        nodesById.clear(); edgesById.clear()
        nodes.forEach { nodesById[it.id] = it }
        edges.forEach { edgesById[it.id] = it }
        persist()
    }

    override fun clear() { ensureLoaded(); nodesById.clear(); edgesById.clear(); persist() }

    companion object {
        const val KEY = "memory.graph.v1"
        val DEFAULT_JSON = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    }
}
