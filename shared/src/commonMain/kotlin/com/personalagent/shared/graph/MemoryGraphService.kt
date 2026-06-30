package com.personalagent.shared.graph

import com.personalagent.shared.memory.Embedder
import com.personalagent.shared.model.Ids
import com.personalagent.shared.util.Clock
import com.personalagent.shared.util.SystemClock
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json

/**
 * The on-device memory-graph engine: extracts items about the user after each
 * exchange, merges them (embedding-based dedup + salience bump), retrieves the
 * most relevant items to ground the LOCAL model, and offers the CRUD + export/
 * import the Memory screen drives. Every store access is serialized through a
 * coroutine [Mutex] (the store itself is not internally synchronized).
 *
 * 🔒 LOCAL-ONLY: nothing here is ever sent to the cloud. Grounding flows into the
 * on-device prompt only; the cloud path keeps just the short-term chat history.
 */
class MemoryGraphService(
    private val store: MemoryGraphStore,
    private val embedder: Embedder,
    private val extractor: MemoryExtractor,
    private val clock: Clock = SystemClock,
    private val json: Json = Json { ignoreUnknownKeys = true; encodeDefaults = true },
    /** Cosine ≥ this (same type) merges into an existing node instead of adding one. */
    private val dedupThreshold: Float = 0.86f,
) {
    private val mutex = Mutex()

    // --- Ingestion ----------------------------------------------------------
    /**
     * Extract memory from one exchange and merge it in. Best-effort and never
     * throws — callers run this AFTER the reply, off the main thread, so a failure
     * (no model, bad embed, junk output) can never break the conversation.
     */
    suspend fun ingest(userText: String, assistantText: String, chatId: String? = null) {
        runCatching {
            val extracted = extractor.extract(userText, assistantText)
            if (extracted.nodes.isEmpty() && extracted.edges.isEmpty()) return
            mutex.withLock { merge(extracted, chatId) }
        }
    }

    private suspend fun merge(extracted: ExtractedGraph, chatId: String?) {
        val now = clock.nowMillis()
        val labelToId = HashMap<String, String>()

        for (cand in extracted.nodes) {
            val id = upsertMerged(cand, chatId, now)
            labelToId[cand.label.normalized()] = id
        }
        // Ensure edge endpoints exist (e.g. the canonical "user" node, or a `to`
        // label the model named without a matching node entry).
        suspend fun resolve(label: String): String {
            val key = label.normalized()
            labelToId[key]?.let { return it }
            val type = if (key == USER_LABEL) MemoryNodeType.PERSON else MemoryNodeType.FACT
            val id = upsertMerged(ExtractedNode(type, label.trim()), chatId, now)
            labelToId[key] = id
            return id
        }

        for (e in extracted.edges) {
            val fromId = resolve(e.fromLabel)
            val toId = resolve(e.toLabel)
            if (fromId == toId) continue
            val exists = store.edges().any { it.fromId == fromId && it.toId == toId && it.relation == e.relation }
            if (!exists) {
                store.upsertEdge(MemoryEdge(Ids.next(now), fromId, toId, e.relation.normalizedRelation(), now))
            }
        }
    }

    /** Find a same-type node by exact label or embedding similarity; bump it, else add. */
    private suspend fun upsertMerged(cand: ExtractedNode, chatId: String?, now: Long): String {
        val emb = embedFor(cand.label, cand.attributes)
        val sameType = store.nodesByType(cand.type)
        val match = sameType.firstOrNull { it.label.normalized() == cand.label.normalized() }
            ?: sameType.filter { it.embedding.isNotEmpty() && emb.isNotEmpty() }
                .map { it to cosine(emb, it.embedding) }
                .filter { it.second >= dedupThreshold }
                .maxByOrNull { it.second }?.first

        return if (match != null) {
            store.upsertNode(
                match.copy(
                    salience = (match.salience + SALIENCE_BUMP),
                    updatedAt = now,
                    attributes = match.attributes + cand.attributes,
                    embedding = if (match.embedding.isEmpty()) emb else match.embedding,
                ),
            )
            match.id
        } else {
            val id = Ids.next(now)
            store.upsertNode(
                MemoryNode(
                    id = id,
                    type = cand.type,
                    label = cand.label.trim(),
                    attributes = cand.attributes,
                    salience = 1f,
                    createdAt = now,
                    updatedAt = now,
                    sourceChatId = chatId,
                    embedding = emb,
                ),
            )
            id
        }
    }

    private suspend fun embedFor(label: String, attributes: Map<String, String>): List<Float> {
        val text = (listOf(label) + attributes.values).joinToString(" ").trim()
        if (text.isEmpty()) return emptyList()
        return runCatching { embedder.embed(text).toList() }.getOrDefault(emptyList())
    }

    // --- Retrieval / grounding ---------------------------------------------
    /**
     * The most relevant memory items for [query]: top similarity hits plus their
     * 1-hop neighbors, rendered as compact human-readable fact lines for the
     * "What I know about you:" block injected into the LOCAL prompt. Best-effort:
     * returns empty if the embedder is unavailable.
     */
    suspend fun retrieveFacts(query: String, topK: Int = 5): List<String> =
        mutex.withLock {
            val qEmb = runCatching { embedder.embed(query).toList() }.getOrDefault(emptyList())
            if (qEmb.isEmpty()) return@withLock emptyList()
            val hits = store.similar(qEmb, topK = topK, minScore = MIN_RETRIEVAL_SCORE)
            if (hits.isEmpty()) return@withLock emptyList()

            val selected = LinkedHashMap<String, MemoryNode>()
            for (h in hits) {
                selected[h.node.id] = h.node
                store.neighbors(h.node.id).forEach { selected[it.id] = it } // 1-hop expansion
            }
            renderFacts(selected.values.toList())
        }

    private fun renderFacts(nodes: List<MemoryNode>): List<String> {
        val byId = nodes.associateBy { it.id }
        val lines = LinkedHashSet<String>()
        // Relationship facts from edges among the selected nodes.
        for (e in store.edges()) {
            val from = byId[e.fromId] ?: continue
            val to = byId[e.toId] ?: continue
            lines += "${human(from.label)} ${e.relation.replace('_', ' ')} ${human(to.label)}".trim()
        }
        // Standalone item facts (skip the bare self node — uninformative on its own).
        for (n in nodes.sortedByDescending { it.salience }) {
            if (n.label.normalized() == USER_LABEL) continue
            lines += human(n.label)
        }
        return lines.take(MAX_FACTS_HARD)
    }

    private fun human(label: String): String = if (label.normalized() == USER_LABEL) "You" else label

    // --- CRUD gateway (used by the Memory screen) ---------------------------
    suspend fun allNodes(): List<MemoryNode> = mutex.withLock { store.nodes() }
    suspend fun allEdges(): List<MemoryEdge> = mutex.withLock { store.edges() }
    suspend fun neighborsOf(id: String): List<MemoryNode> = mutex.withLock { store.neighbors(id) }

    suspend fun editNode(id: String, label: String, attributes: Map<String, String>) = mutex.withLock {
        val existing = store.node(id) ?: return@withLock
        val now = clock.nowMillis()
        store.upsertNode(
            existing.copy(
                label = label.trim(),
                attributes = attributes,
                updatedAt = now,
                embedding = embedFor(label, attributes),
            ),
        )
    }

    suspend fun deleteNode(id: String) = mutex.withLock { store.deleteNode(id) }
    suspend fun deleteEdge(id: String) = mutex.withLock { store.deleteEdge(id) }
    suspend fun clear() = mutex.withLock { store.clear() }

    // --- Export / import (portable, user-owned) -----------------------------
    /** The whole graph as pretty JSON, so the user can keep/move their memory. */
    suspend fun exportJson(): String = mutex.withLock {
        val snap = MemoryGraphSnapshot(nodes = store.nodes(), edges = store.edges())
        EXPORT_JSON.encodeToString(MemoryGraphSnapshot.serializer(), snap)
    }

    /** Replace the graph from previously-exported JSON. Returns node count, or -1 on bad input. */
    suspend fun importJson(text: String): Int = mutex.withLock {
        val snap = runCatching { json.decodeFromString(MemoryGraphSnapshot.serializer(), text) }.getOrNull()
            ?: return@withLock -1
        store.replaceAll(snap.nodes, snap.edges)
        snap.nodes.size
    }

    private companion object {
        const val SALIENCE_BUMP = 0.5f
        const val MIN_RETRIEVAL_SCORE = 0.25f
        const val MAX_FACTS_HARD = 10
        val EXPORT_JSON = Json { prettyPrint = true; encodeDefaults = true }
    }
}

private fun String.normalized(): String = trim().lowercase()
private fun String.normalizedRelation(): String =
    trim().lowercase().replace(Regex("\\s+"), "_")
