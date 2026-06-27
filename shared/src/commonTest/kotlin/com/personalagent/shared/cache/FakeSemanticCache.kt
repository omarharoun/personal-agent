package com.personalagent.shared.cache

/**
 * A deterministic in-memory stand-in for the sibling-owned [SemanticCache] — NO
 * embeddings, NO network. Lookup scores entries by word overlap between the query
 * and each entry's `topic + summary`, so tests can drive cache hits/misses
 * predictably and exercise [UnderstandingDistiller] + [CloudUsageStats] end-to-end.
 *
 * It is intentionally simple (not the production semantic ranker) and lives only
 * in test source.
 */
class FakeSemanticCache : SemanticCache {

    val stored: MutableList<CachedUnderstanding> = mutableListOf()

    override suspend fun store(topic: String, summary: String) {
        stored.add(CachedUnderstanding(topic, summary))
    }

    override suspend fun lookup(
        query: String,
        topK: Int,
        minScore: Float,
    ): List<CachedUnderstanding> {
        val qWords = tokenize(query)
        if (qWords.isEmpty()) return emptyList()
        return stored
            .map { it.copy(score = score(qWords, it)) }
            .filter { it.score >= minScore }
            .sortedByDescending { it.score }
            .take(topK)
    }

    override suspend fun clear() {
        stored.clear()
    }

    /** Fraction of query words that appear in the entry's topic+summary text. */
    private fun score(qWords: Set<String>, entry: CachedUnderstanding): Float {
        val eWords = tokenize("${entry.topic} ${entry.summary}")
        if (eWords.isEmpty()) return 0f
        val overlap = qWords.count { it in eWords }
        return overlap.toFloat() / qWords.size
    }

    private fun tokenize(text: String): Set<String> =
        text.lowercase()
            .split(Regex("[^a-z0-9]+"))
            .filter { it.isNotBlank() }
            .toSet()
}
