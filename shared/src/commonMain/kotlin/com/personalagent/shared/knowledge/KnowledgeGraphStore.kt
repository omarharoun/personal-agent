package com.personalagent.shared.knowledge

import com.personalagent.shared.store.KeyValueStorage
import kotlinx.serialization.json.Json

/**
 * Persistent, sealed-at-rest cache for the chat-derived [KnowledgeGraph].
 *
 * The graph is expensive to build (a model call, or a full scan of chat history),
 * so — like [com.personalagent.shared.home.HomeCacheStore] — it is cached and only
 * rebuilt on demand, when new chats have accumulated, or when it ages past
 * [STALE_AFTER_MS]. The Knowledge screen paints the cached graph instantly and can
 * revalidate in the background. Sealed at rest because node labels/snippets are
 * drawn from the user's conversations.
 */
class KnowledgeGraphStore(
    private val storage: KeyValueStorage,
) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    /** The cached graph, or null if none has been built yet. */
    fun load(): KnowledgeGraph? {
        val raw = storage.get(KEY) ?: return null
        return runCatching { json.decodeFromString(KnowledgeGraph.serializer(), raw) }.getOrNull()
    }

    fun save(graph: KnowledgeGraph) {
        storage.put(KEY, json.encodeToString(KnowledgeGraph.serializer(), graph))
    }

    fun clear() = storage.remove(KEY)

    /** Age-based staleness (content-based staleness is handled by the signature). */
    fun isOlderThan(graph: KnowledgeGraph, nowMillis: Long, maxAgeMs: Long = STALE_AFTER_MS): Boolean =
        graph.builtAt == 0L || nowMillis - graph.builtAt >= maxAgeMs

    companion object {
        private const val KEY = "knowledge_graph"

        /** Auto-refresh cadence when the screen is opened and the cache is old. */
        const val STALE_AFTER_MS: Long = 12 * 60 * 60 * 1000L // 12 hours
    }
}
