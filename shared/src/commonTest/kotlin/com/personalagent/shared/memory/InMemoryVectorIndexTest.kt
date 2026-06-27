package com.personalagent.shared.memory

import com.personalagent.shared.store.InMemoryKeyValueStorage
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class InMemoryVectorIndexTest {

    @Test
    fun cosine_is_correct_for_known_vectors() = runTest {
        val index = InMemoryVectorIndex(InMemoryKeyValueStorage())
        index.upsert("same", floatArrayOf(1f, 0f))
        index.upsert("orthogonal", floatArrayOf(0f, 1f))
        index.upsert("opposite", floatArrayOf(-1f, 0f))
        index.upsert("scaled", floatArrayOf(5f, 0f)) // same direction, different magnitude

        val byId = index.query(floatArrayOf(1f, 0f), topK = 4).associateBy { it.id }

        // cosine ignores magnitude: identical and scaled both score 1.0
        assertEquals(1f, byId.getValue("same").score, 1e-6f)
        assertEquals(1f, byId.getValue("scaled").score, 1e-6f)
        assertEquals(0f, byId.getValue("orthogonal").score, 1e-6f)
        assertEquals(-1f, byId.getValue("opposite").score, 1e-6f)
    }

    @Test
    fun query_returns_topK_ranked_best_first() = runTest {
        val index = InMemoryVectorIndex(InMemoryKeyValueStorage())
        index.upsert("a", floatArrayOf(1f, 0f))     // cos = 1.0 vs query
        index.upsert("b", floatArrayOf(1f, 1f))     // cos ≈ 0.707
        index.upsert("c", floatArrayOf(0f, 1f))     // cos = 0.0
        index.upsert("d", floatArrayOf(-1f, 0f))    // cos = -1.0

        val top2 = index.query(floatArrayOf(1f, 0f), topK = 2)
        assertEquals(listOf("a", "b"), top2.map { it.id })
        // ranking is monotonically non-increasing
        assertTrue(top2[0].score >= top2[1].score)
    }

    @Test
    fun upsert_replaces_vector_and_metadata_in_place() = runTest {
        val index = InMemoryVectorIndex(InMemoryKeyValueStorage())
        index.upsert("x", floatArrayOf(1f, 0f), mapOf("v" to "1"))
        index.upsert("x", floatArrayOf(0f, 1f), mapOf("v" to "2"))

        assertEquals(1, index.size())
        val hit = index.query(floatArrayOf(0f, 1f), topK = 1).single()
        assertEquals("x", hit.id)
        assertEquals(1f, hit.score, 1e-6f)        // now points the new direction
        assertEquals("2", hit.metadata["v"])      // metadata updated too
    }

    @Test
    fun delete_removes_only_target() = runTest {
        val index = InMemoryVectorIndex(InMemoryKeyValueStorage())
        index.upsert("a", floatArrayOf(1f, 0f))
        index.upsert("b", floatArrayOf(0f, 1f))
        index.delete("a")
        assertEquals(1, index.size())
        assertEquals(listOf("b"), index.query(floatArrayOf(0f, 1f), topK = 5).map { it.id })
    }

    @Test
    fun clear_drops_everything() = runTest {
        val index = InMemoryVectorIndex(InMemoryKeyValueStorage())
        index.upsert("a", floatArrayOf(1f, 0f))
        index.upsert("b", floatArrayOf(0f, 1f))
        index.clear()
        assertEquals(0, index.size())
        assertTrue(index.query(floatArrayOf(1f, 0f), topK = 5).isEmpty())
    }

    @Test
    fun state_persists_across_index_instances_sharing_storage() = runTest {
        // The persistence-round-trip acceptance: a brand-new index over the same
        // KeyValueStorage sees vectors written by the first — survives "restart".
        val storage = InMemoryKeyValueStorage()
        InMemoryVectorIndex(storage).apply {
            upsert("a", floatArrayOf(1f, 0f), mapOf("tag" to "alpha"))
            upsert("b", floatArrayOf(0f, 1f))
        }

        val reopened = InMemoryVectorIndex(storage)
        assertEquals(2, reopened.size())
        val hit = reopened.query(floatArrayOf(1f, 0f), topK = 1).single()
        assertEquals("a", hit.id)
        assertEquals("alpha", hit.metadata["tag"]) // metadata round-trips too
    }

    @Test
    fun query_guards_empty_index_and_nonpositive_topK() = runTest {
        val index = InMemoryVectorIndex(InMemoryKeyValueStorage())
        assertTrue(index.query(floatArrayOf(1f, 0f), topK = 5).isEmpty())
        index.upsert("a", floatArrayOf(1f, 0f))
        assertTrue(index.query(floatArrayOf(1f, 0f), topK = 0).isEmpty())
    }
}
