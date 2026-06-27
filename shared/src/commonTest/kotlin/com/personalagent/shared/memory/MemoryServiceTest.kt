package com.personalagent.shared.memory

import com.personalagent.shared.model.MemoryKind
import com.personalagent.shared.store.InMemoryKeyValueStorage
import com.personalagent.shared.store.KeyValueStorage
import com.personalagent.shared.store.PersistentLocalStore
import com.personalagent.shared.util.Clock
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Monotonic fake clock so generated ids/timestamps are unique and deterministic. */
private class FakeClock(start: Long = 1_000L) : Clock {
    private var t = start
    override fun nowMillis(): Long = t++
}

class MemoryServiceTest {

    private fun service(storage: KeyValueStorage = InMemoryKeyValueStorage()): MemoryService {
        val store = PersistentLocalStore(storage)
        val index = InMemoryVectorIndex(storage)
        return MemoryService(HashingEmbedder(), index, store, FakeClock())
    }

    @Test
    fun recall_surfaces_the_relevant_past_memory() = runTest {
        // ACCEPTANCE: store a few notes; a related query surfaces the right one,
        // deterministically, with zero network.
        val svc = service()
        svc.remember("I'm allergic to peanuts", kind = MemoryKind.FACT)
        svc.remember("My favourite programming language is Kotlin", kind = MemoryKind.PREFERENCE)
        svc.remember("Met Sam for coffee downtown on Tuesday", kind = MemoryKind.EVENT)

        val hits = svc.recall("what foods am I allergic to", topK = 1)
        assertEquals(1, hits.size)
        assertEquals("I'm allergic to peanuts", hits.first().content)
    }

    @Test
    fun recall_ranks_by_relevance() = runTest {
        val svc = service()
        svc.remember("the project deadline is on Friday")
        svc.remember("remember to water the office plants")
        svc.remember("Friday is the deadline for the quarterly report")

        val scored = svc.recallScored("when is the deadline", topK = 3)
        // both deadline notes outrank the plants note
        assertTrue(scored.first().entry.content.contains("deadline"))
        assertTrue(scored.last().entry.content.contains("plants"))
        assertTrue(scored.first().score >= scored[1].score)
    }

    @Test
    fun remember_populates_the_embedding_field_and_persists() = runTest {
        val svc = service()
        val entry = svc.remember("vector recall test")
        assertNotNull(entry)
        val embedding = assertNotNull(entry.embedding, "remember must populate the reserved embedding field")
        assertEquals(HashingEmbedder.DEFAULT_DIMENSION, embedding.size)
    }

    @Test
    fun remember_rejects_blank_text() = runTest {
        val svc = service()
        assertNull(svc.remember("   "))
        assertTrue(svc.recall("anything").isEmpty())
    }

    @Test
    fun interaction_path_records_and_retrieves_context() = runTest {
        // The shared "embed+store on each interaction" + "retrieve relevant past
        // context at request time" hooks the UI layer calls.
        val svc = service()
        svc.recordInteraction("user asked to book a flight to Berlin in July")
        svc.recordInteraction("user prefers window seats")
        svc.recordInteraction("user wants a vegetarian meal on flights")

        val context = svc.retrieveContext("which seat does the user like", topK = 1)
        assertEquals(1, context.size)
        assertTrue(context.first().content.contains("window"))
        assertEquals(MemoryKind.EVENT, context.first().kind)
        assertEquals("interaction", context.first().source)
    }

    @Test
    fun memory_survives_restart_through_keyvaluestorage() = runTest {
        // Same KeyValueStorage, brand-new service objects: a recall after
        // "restart" still finds what an earlier session remembered.
        val storage = InMemoryKeyValueStorage()
        service(storage).remember("the wifi password is hunter2", kind = MemoryKind.FACT)

        val reopened = service(storage)
        val hits = reopened.recall("what is the wifi password", topK = 1)
        assertEquals(1, hits.size)
        assertTrue(hits.first().content.contains("hunter2"))
    }

    @Test
    fun forget_removes_from_both_store_and_index() = runTest {
        val storage = InMemoryKeyValueStorage()
        val store = PersistentLocalStore(storage)
        val index = InMemoryVectorIndex(storage)
        val svc = MemoryService(HashingEmbedder(), index, store, FakeClock())

        val keep = svc.remember("keep this memory")!!
        val drop = svc.remember("drop this memory")!!
        svc.forget(drop.id)

        assertEquals(1, store.allMemoryEntries().size)
        assertEquals(1, index.size())
        assertEquals(keep.id, store.allMemoryEntries().single().id)
    }

    @Test
    fun forgetAll_clears_store_and_index() = runTest {
        val storage = InMemoryKeyValueStorage()
        val store = PersistentLocalStore(storage)
        val index = InMemoryVectorIndex(storage)
        val svc = MemoryService(HashingEmbedder(), index, store, FakeClock())

        svc.remember("one"); svc.remember("two"); svc.remember("three")
        svc.forgetAll()

        assertTrue(store.allMemoryEntries().isEmpty())
        assertEquals(0, index.size())
        assertTrue(svc.recall("one").isEmpty())
    }
}
