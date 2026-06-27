package com.personalagent.shared.memory

import com.personalagent.shared.model.Ids
import com.personalagent.shared.model.MemoryEntry
import com.personalagent.shared.model.MemoryKind
import com.personalagent.shared.store.LocalStore
import com.personalagent.shared.util.Clock
import com.personalagent.shared.util.SystemClock

/**
 * A recalled memory paired with its similarity [score] to the query
 * (cosine, higher = more relevant). Used where the caller wants to rank, filter,
 * or threshold context (e.g. only inject memories above some relevance).
 */
data class ScoredMemory(val entry: MemoryEntry, val score: Float)

/**
 * The on-device long-term memory engine: embed → index → persist on write, and
 * embed → search → resolve on read. Fully local, **no network**.
 *
 * Wiring:
 *   - [embedder] turns text into a vector (test [HashingEmbedder] now; real
 *     on-device model from a platform sibling later — same [Embedder] contract).
 *   - [index] does nearest-neighbour search ([InMemoryVectorIndex] reference).
 *   - [store] is the durable record of truth for [MemoryEntry]s (the existing
 *     [LocalStore], which already persists the reserved `embedding` field).
 *
 * Every write goes to BOTH the store (durable, full record incl. embedding) and
 * the index (searchable vector). Reads embed the query, ask the index for the
 * nearest ids, then resolve those ids back to full [MemoryEntry]s — so callers
 * get rich records, ranked by relevance.
 *
 * 🤝 SHARED CONTRACT — constructor shape `(embedder, index, store)` is fixed.
 * [clock] is appended with a default so production can call
 * `MemoryService(embedder, index, store)` unchanged while tests inject a fake.
 */
class MemoryService(
    private val embedder: Embedder,
    private val index: VectorIndex,
    private val store: LocalStore,
    private val clock: Clock = SystemClock,
) {

    /**
     * Persist a memory and make it recallable.
     *
     * Embeds [text], writes a full [MemoryEntry] (with the embedding) to the
     * durable store, and upserts the vector into the index under the same id.
     * Returns the stored entry. Blank text is rejected (returns null) — there is
     * nothing meaningful to embed or recall.
     *
     * [metadata] is attached to the index record (and augmented with kind /
     * source / timestamp) so future filtering can happen without a store lookup.
     */
    suspend fun remember(
        text: String,
        kind: MemoryKind = MemoryKind.FACT,
        source: String = "user",
        metadata: Map<String, String> = emptyMap(),
    ): MemoryEntry? {
        val content = text.trim()
        if (content.isEmpty()) return null

        val now = clock.nowMillis()
        val vector = embedder.embed(content)

        val entry = MemoryEntry(
            id = Ids.next(now),
            content = content,
            kind = kind,
            source = source,
            createdAt = now,
            embedding = vector.toList(), // reserved field, now populated
        )

        store.upsertMemoryEntry(entry)
        index.upsert(entry.id, vector, baseMetadata(entry) + metadata)
        return entry
    }

    /**
     * The [topK] stored memories most relevant to [query], best first.
     *
     * Embeds the query, asks the index for the nearest vectors, then resolves
     * those ids back to full [MemoryEntry]s — preserving the index's ranking and
     * silently dropping any id that no longer has a backing record (e.g. deleted
     * directly in the store).
     */
    suspend fun recall(query: String, topK: Int = DEFAULT_TOP_K): List<MemoryEntry> =
        recallScored(query, topK).map { it.entry }

    /** Like [recall] but keeps each memory's similarity score for ranking/thresholding. */
    suspend fun recallScored(query: String, topK: Int = DEFAULT_TOP_K): List<ScoredMemory> {
        val q = query.trim()
        if (q.isEmpty() || topK <= 0) return emptyList()

        val matches = index.query(embedder.embed(q), topK)
        if (matches.isEmpty()) return emptyList()

        val byId = store.allMemoryEntries().associateBy { it.id }
        return matches.mapNotNull { match ->
            byId[match.id]?.let { ScoredMemory(it, match.score) }
        }
    }

    /** Forget a single memory: drop it from both the store and the index. */
    suspend fun forget(id: String) {
        store.deleteMemoryEntry(id)
        index.delete(id)
    }

    /** Wipe all memories from both the store and the index. */
    suspend fun forgetAll() {
        for (entry in store.allMemoryEntries()) store.deleteMemoryEntry(entry.id)
        index.clear()
    }

    // --- Interaction path (the entry points the UI/agent layer calls) ---

    /**
     * Record one interaction turn as memory. This is the "on each interaction,
     * embed + store" hook: the UI/agent calls it with whatever the user said (or
     * a salient summary of the turn) and it becomes recallable context for later
     * requests. Recorded as a [MemoryKind.EVENT] by default.
     *
     * Returns the stored entry, or null if [text] was blank.
     */
    suspend fun recordInteraction(
        text: String,
        source: String = "interaction",
        kind: MemoryKind = MemoryKind.EVENT,
        metadata: Map<String, String> = emptyMap(),
    ): MemoryEntry? = remember(text, kind = kind, source = source, metadata = metadata)

    /**
     * Retrieve relevant past context for the current request. This is the
     * "retrieve relevant past context at request time" hook: the UI/agent calls
     * it with the incoming user request and gets back the most relevant prior
     * memories to fold into the prompt/response.
     *
     * Thin, intention-revealing alias over [recall] so the call site reads as
     * what it is.
     */
    suspend fun retrieveContext(request: String, topK: Int = DEFAULT_TOP_K): List<MemoryEntry> =
        recall(request, topK)

    private fun baseMetadata(entry: MemoryEntry): Map<String, String> = mapOf(
        "kind" to entry.kind.name,
        "source" to entry.source,
        "createdAt" to entry.createdAt.toString(),
    )

    companion object {
        const val DEFAULT_TOP_K = 5
    }
}
