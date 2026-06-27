package com.personalagent.shared.memory

import com.personalagent.shared.store.InMemoryKeyValueStorage
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class HashingEmbedderTest {

    @Test
    fun embedding_is_deterministic_and_fixed_dimension() = runTest {
        val embedder = HashingEmbedder(dimension = 64)
        val a = embedder.embed("buy oat milk on the way home")
        val b = embedder.embed("buy oat milk on the way home")
        assertEquals(64, a.size)
        assertTrue(a.contentEquals(b), "same text must embed to the same vector every time")
    }

    @Test
    fun shared_vocabulary_is_closer_than_unrelated_text() = runTest {
        // No model, yet related sentences land nearer under cosine — the property
        // the retrieval tests rely on. Compare via the index's cosine.
        val embedder = HashingEmbedder()
        val index = InMemoryVectorIndex(InMemoryKeyValueStorage())
        index.upsert("groceries", embedder.embed("remember to buy oat milk and bread"))
        index.upsert("dentist", embedder.embed("schedule a dentist appointment for next week"))

        val matches = index.query(embedder.embed("did I need to buy milk and bread"), topK = 2)
        assertEquals("groceries", matches.first().id)
        assertTrue(
            matches.first().score > matches.last().score,
            "the topically related note must score strictly higher",
        )
    }
}
