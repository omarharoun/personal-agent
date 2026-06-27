package com.personalagent.shared.cache

import com.personalagent.shared.memory.HashingEmbedder
import com.personalagent.shared.store.InMemoryKeyValueStorage
import com.personalagent.shared.util.Clock
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** A settable clock so [CachedUnderstanding.updatedAt] is deterministic under test. */
private class SettableClock(var t: Long = 1_000L) : Clock {
    override fun nowMillis(): Long = t
}

/**
 * Tests for the testable core ([EmbeddingSemanticCache]) — NO network, NO model:
 * the Step-2 [HashingEmbedder] makes cosine recall provable in CI. Covers
 * store→lookup, [SemanticCache.lookup] minScore + topK behaviour, freshest-first
 * tie-breaking, and the persistence round-trip through [InMemoryKeyValueStorage]
 * (the same seam that is the encrypted wallet in production).
 */
class EmbeddingSemanticCacheTest {

    private fun cache(
        storage: InMemoryKeyValueStorage = InMemoryKeyValueStorage(),
        clock: Clock = SettableClock(),
    ) = EmbeddingSemanticCache(HashingEmbedder(), storage, clock)

    @Test
    fun store_then_lookup_recalls_understanding_for_a_related_query() = runTest {
        val cache = cache()
        cache.store(topic = "renewable energy storage", summary = "options for renewable energy storage")

        // A query that shares vocabulary with the cached topic recalls it.
        val hits = cache.lookup("renewable energy storage tradeoffs")

        assertEquals(1, hits.size)
        assertEquals("renewable energy storage", hits.first().topic)
        assertEquals("options for renewable energy storage", hits.first().summary)
    }

    @Test
    fun lookup_misses_for_an_unrelated_query() = runTest {
        val cache = cache()
        cache.store(topic = "renewable energy storage", summary = "options for renewable energy storage")

        assertTrue(cache.lookup("banana bread recipe ideas").isEmpty())
    }

    @Test
    fun lookup_respects_minScore_threshold() = runTest {
        val cache = cache()
        cache.store(topic = "data science basics", summary = "")

        // A partial-overlap query clears the default 0.6 gate...
        assertTrue(cache.lookup("data science", minScore = 0.6f).isNotEmpty())
        // ...but an unreachably high threshold rejects the same hit.
        assertTrue(cache.lookup("data science", minScore = 0.999f).isEmpty())
    }

    @Test
    fun lookup_respects_topK() = runTest {
        val cache = cache()
        // Three distinct topics that all share "data science" with the query.
        cache.store("data science basics", "")
        cache.store("data science advanced", "")
        cache.store("data science applied", "")

        assertEquals(3, cache.size())
        assertEquals(2, cache.lookup("data science", topK = 2).size)
        assertEquals(0, cache.lookup("data science", topK = 0).size)
    }

    @Test
    fun lookup_breaks_score_ties_freshest_first() = runTest {
        val clock = SettableClock()
        val cache = cache(clock = clock)

        // Same token bag in two different topic orders ⇒ identical embeddings ⇒
        // identical cosine to any query: a genuine score tie. Different updatedAt.
        clock.t = 10
        cache.store(topic = "alpha beta", summary = "shared note")
        clock.t = 20
        cache.store(topic = "beta alpha", summary = "shared note")

        val hits = cache.lookup("alpha beta shared note", topK = 2)

        assertEquals(2, hits.size)
        // Freshest (updatedAt = 20) wins the tie.
        assertEquals(20L, hits[0].updatedAt)
        assertEquals(10L, hits[1].updatedAt)
    }

    @Test
    fun store_same_topic_upserts_rather_than_duplicating() = runTest {
        val clock = SettableClock()
        val cache = cache(clock = clock)

        clock.t = 1
        cache.store(topic = "weekly plan", summary = "gym on monday")
        clock.t = 2
        cache.store(topic = "weekly plan", summary = "gym on monday and wednesday")

        assertEquals(1, cache.size()) // one topic, not two
        val hits = cache.lookup("weekly plan gym")
        assertEquals(1, hits.size)
        assertEquals("gym on monday and wednesday", hits.first().summary) // freshest understanding
        assertEquals(2L, hits.first().updatedAt)
    }

    @Test
    fun understanding_survives_a_fresh_cache_over_the_same_storage() = runTest {
        val storage = InMemoryKeyValueStorage()

        cache(storage).store(topic = "trip to kyoto", summary = "cherry blossoms peak early april")

        // A brand-new cache instance over the SAME storage sees the prior write —
        // the persistence contract (and, in production, encrypted at rest).
        val reopened = cache(storage)
        val hits = reopened.lookup("trip to kyoto cherry blossoms")

        assertEquals(1, hits.size)
        assertEquals("trip to kyoto", hits.first().topic)
        assertEquals("cherry blossoms peak early april", hits.first().summary)
    }

    @Test
    fun clear_empties_the_cache() = runTest {
        val storage = InMemoryKeyValueStorage()
        val cache = cache(storage)
        cache.store(topic = "renewable energy storage", summary = "notes")
        assertEquals(1, cache.size())

        cache.clear()

        assertEquals(0, cache.size())
        assertTrue(cache.lookup("renewable energy storage").isEmpty())
        // And the clear is persisted: a fresh cache sees nothing.
        assertEquals(0, cache(storage).size())
    }
}
