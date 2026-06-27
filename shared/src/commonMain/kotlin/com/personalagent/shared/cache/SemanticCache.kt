package com.personalagent.shared.cache

/**
 * One piece of cached **understanding** about a topic — the durable facts the
 * system has figured out about the user or a subject — together with how well it
 * matched the query that retrieved it.
 *
 * This is *understanding*, not a canned answer: [summary] holds what's been
 * learned (e.g. "User prefers terse replies; works in Kotlin/KMP"), never the
 * verbatim text of a previous reply. Retrieval surfaces it so a later turn can be
 * answered on-device instead of paying for the cloud again.
 *
 * @param topic short stable label the understanding is filed under.
 * @param summary the distilled, durable understanding (facts / what's been
 *   figured out), suitable for grounding a future on-device turn.
 * @param score similarity of this entry to the lookup query, in `[0f, 1f]`;
 *   `1f` for a direct [SemanticCache.store] round-trip where no scoring applies.
 */
data class CachedUnderstanding(
    val topic: String,
    val summary: String,
    val score: Float = 1f,
)

/**
 * A semantic cache of distilled **understanding**. Storing folds new understanding
 * about a [topic] in; looking up returns the most relevant prior understanding for
 * a free-text [query] so a turn can be served locally rather than escalated.
 *
 * 🤝 SHARED CONTRACT — Step 6. The cache *implementation* is owned by the sibling
 * agent (semantic embedding + nearest-neighbour scoring). This file is a **minimal
 * copy of the agreed interface** so that [UnderstandingDistiller] and its tests
 * compile standalone in this worktree.
 *
 * ⚠️ COORDINATOR: dedup this declaration against the sibling's canonical
 * `com.personalagent.shared.cache.SemanticCache` / `CachedUnderstanding` before
 * merge — keep exactly one. The signatures here match the contract verbatim:
 * `store(topic, summary)`, `lookup(query, topK=3, minScore=0.6f)`, `clear()`.
 */
interface SemanticCache {
    /** Fold new [summary] understanding about [topic] into the cache. */
    suspend fun store(topic: String, summary: String)

    /**
     * Return up to [topK] cached understandings most relevant to [query], each
     * with similarity ≥ [minScore], best-first. Empty when nothing clears the bar.
     */
    suspend fun lookup(
        query: String,
        topK: Int = 3,
        minScore: Float = 0.6f,
    ): List<CachedUnderstanding>

    /** Drop all cached understanding. */
    suspend fun clear()
}
