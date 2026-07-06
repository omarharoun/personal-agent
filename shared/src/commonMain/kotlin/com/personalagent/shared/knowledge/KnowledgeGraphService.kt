package com.personalagent.shared.knowledge

import com.personalagent.shared.chat.ChatStore
import com.personalagent.shared.chat.StoredConversation
import com.personalagent.shared.hermes.HermesClient
import com.personalagent.shared.hermes.HermesWireMessage

/**
 * Builds and caches the chat-derived [KnowledgeGraph]. The graph is sourced from
 * the on-device [ChatStore] — the user's own conversations — NOT from Hermes'
 * server-side memory; the UI is labelled accordingly.
 *
 * Policy mirrors the home dashboard: paint the cached graph instantly, rebuild only
 * on demand, when new chats have accumulated (signature change), or when the cache
 * ages out. Rebuild prefers a Hermes structured-JSON extraction and falls back to a
 * fully-offline keyword/co-occurrence pass so the map still populates unconnected.
 */
class KnowledgeGraphService(
    private val chatStore: ChatStore,
    private val store: KnowledgeGraphStore,
) {
    /** The last cached graph, or null if none built yet. */
    fun cached(): KnowledgeGraph? = store.load()

    /** True when the chat records have changed since the cached graph was built. */
    fun contentChanged(cache: KnowledgeGraph?): Boolean {
        val c = cache ?: return true
        return c.sourceSignature != KnowledgeGraphExtractor.signature(chatStore.all())
    }

    /** True when a rebuild is warranted (missing, content changed, or aged out). */
    fun shouldRebuild(nowMillis: Long): Boolean {
        val c = store.load() ?: return true
        return contentChanged(c) || store.isOlderThan(c, nowMillis)
    }

    /**
     * Rebuild from the current chat history and cache the result. [hermes] may be
     * null (or unreachable) — then the offline keyword fallback is used. The
     * extraction runs under its own session id so it doesn't disturb the live chat
     * thread's short-term context.
     */
    suspend fun rebuild(hermes: HermesClient?, nowMillis: Long): KnowledgeGraph {
        val conversations = chatStore.all()
        if (conversations.isEmpty() || KnowledgeGraphExtractor.userTexts(conversations).isEmpty()) {
            val empty = KnowledgeGraph(
                builtAt = nowMillis,
                sourceSignature = KnowledgeGraphExtractor.signature(conversations),
                sourceConversationCount = conversations.size,
                source = KnowledgeGraphSource.EMPTY,
            )
            store.save(empty)
            return empty
        }

        val built = buildViaModel(hermes, conversations)
            ?: KnowledgeGraphExtractor.attachSnippets(
                KnowledgeGraphExtractor.keywordFallback(conversations),
                conversations,
            )

        val stamped = built.copy(
            builtAt = nowMillis,
            sourceSignature = KnowledgeGraphExtractor.signature(conversations),
            sourceConversationCount = conversations.size,
        )
        store.save(stamped)
        return stamped
    }

    private suspend fun buildViaModel(
        hermes: HermesClient?,
        conversations: List<StoredConversation>,
    ): KnowledgeGraph? {
        if (hermes == null) return null
        val prompt = KnowledgeGraphExtractor.buildExtractionPrompt(conversations)
        val reply = runCatching {
            hermes.complete(
                messages = listOf(HermesWireMessage(role = "user", content = prompt)),
                sessionId = EXTRACTION_SESSION_ID,
            )
        }.getOrNull() ?: return null
        val parsed = KnowledgeGraphExtractor.parse(reply) ?: return null
        return KnowledgeGraphExtractor.attachSnippets(parsed, conversations)
    }

    private companion object {
        /** Isolated thread so extraction doesn't pollute the live chat's context. */
        const val EXTRACTION_SESSION_ID = "lifeagent-knowledge-extract"
    }
}
