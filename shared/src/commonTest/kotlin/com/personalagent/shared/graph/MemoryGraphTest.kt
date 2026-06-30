package com.personalagent.shared.graph

import com.personalagent.shared.conversation.FakeOnDeviceLlm
import com.personalagent.shared.memory.HashingEmbedder
import com.personalagent.shared.store.InMemoryKeyValueStorage
import com.personalagent.shared.util.Clock
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

private class GraphFakeClock(start: Long = 1_000L) : Clock {
    private var t = start
    override fun nowMillis(): Long = t++
}

/** Returns a fixed extraction, so ingestion/merge is deterministic with no model. */
private class FakeExtractor(private val result: ExtractedGraph) : MemoryExtractor {
    override suspend fun extract(userText: String, assistantText: String): ExtractedGraph = result
}

class MemoryGraphTest {

    private fun store() = PersistentMemoryGraphStore(InMemoryKeyValueStorage())

    private fun service(
        store: MemoryGraphStore = store(),
        extracted: ExtractedGraph = ExtractedGraph(),
    ) = MemoryGraphService(store, HashingEmbedder(), FakeExtractor(extracted), GraphFakeClock())

    // --- 1. store CRUD + persistence round-trip -----------------------------
    @Test
    fun store_crud_and_persistence_round_trip() {
        val backing = InMemoryKeyValueStorage()
        val s1 = PersistentMemoryGraphStore(backing)
        val a = MemoryNode("a", MemoryNodeType.PERSON, "Sarah", createdAt = 1, updatedAt = 1)
        val b = MemoryNode("b", MemoryNodeType.PREFERENCE, "mornings", createdAt = 1, updatedAt = 1)
        s1.upsertNode(a); s1.upsertNode(b)
        s1.upsertEdge(MemoryEdge("e1", "a", "b", "knows", 1))

        assertEquals(listOf("Sarah"), s1.nodesByType(MemoryNodeType.PERSON).map { it.label })
        assertEquals(listOf("mornings"), s1.neighbors("a").map { it.label })

        // A fresh store over the SAME backing must load the persisted graph (restart).
        val s2 = PersistentMemoryGraphStore(backing)
        assertEquals(2, s2.nodes().size)
        assertEquals(1, s2.edges().size)

        // Deleting a node cascades its edges.
        s2.deleteNode("a")
        assertEquals(1, s2.nodes().size)
        assertTrue(s2.edges().isEmpty(), "incident edge must be removed with the node")
    }

    // --- 2. extraction merge + dedup (same fact twice → one node, salience++) -
    @Test
    fun ingest_merges_duplicate_facts_and_bumps_salience() = runTest {
        val store = store()
        val extracted = ExtractedGraph(
            nodes = listOf(ExtractedNode(MemoryNodeType.PERSON, "Sarah")),
            edges = listOf(ExtractedEdge(USER_LABEL, "Sarah", "has_sister")),
        )
        val svc = service(store, extracted)

        svc.ingest("my sister is Sarah", "ok")
        svc.ingest("my sister Sarah called", "ok") // same fact again

        val sarahs = store.nodesByType(MemoryNodeType.PERSON).filter { it.label == "Sarah" }
        assertEquals(1, sarahs.size, "the same fact must not be duplicated")
        assertTrue(sarahs[0].salience > 1f, "a repeat mention must bump salience")
        // The user—has_sister→Sarah edge exists exactly once.
        assertEquals(1, store.edges().count { it.relation == "has_sister" })
    }

    // --- 3. malformed extraction is dropped safely --------------------------
    @Test
    fun llm_extraction_parses_defensively_and_drops_junk() {
        val ex = LlmMemoryExtractor(FakeOnDeviceLlm())
        // Pure prose → nothing.
        assertTrue(ex.parse("I have no idea what JSON is.").nodes.isEmpty())
        // Valid object wrapped in fences/prose → parsed.
        val ok = ex.parse("Sure!\n```json\n{\"nodes\":[{\"type\":\"PERSON\",\"label\":\"Sam\"}],\"edges\":[]}\n```")
        assertEquals(1, ok.nodes.size)
        assertEquals("Sam", ok.nodes[0].label)
        // Unterminated/broken JSON → empty, never throws.
        assertTrue(ex.parse("{ \"nodes\": [ broken ").nodes.isEmpty())
    }

    // --- 4. retrieval returns relevant nodes + neighbors --------------------
    @Test
    fun retrieve_facts_returns_hits_and_one_hop_neighbors() = runTest {
        val store = store()
        val svc = service(
            store,
            ExtractedGraph(
                nodes = listOf(ExtractedNode(MemoryNodeType.PREFERENCE, "mornings")),
                edges = listOf(ExtractedEdge(USER_LABEL, "mornings", "prefers")),
            ),
        )
        svc.ingest("I prefer mornings", "noted")

        val facts = svc.retrieveFacts("mornings")
        assertTrue(facts.any { it.contains("mornings") }, "the matching item must be retrieved")
        // 1-hop expansion surfaces the relationship back to the user node.
        assertTrue(facts.any { it.contains("prefers") }, "the relationship edge should be rendered")
    }

    // --- 5. export / import round-trips -------------------------------------
    @Test
    fun export_then_import_round_trips() = runTest {
        val store = store()
        val svc = service(
            store,
            ExtractedGraph(
                nodes = listOf(ExtractedNode(MemoryNodeType.GOAL, "learn guitar")),
                edges = listOf(ExtractedEdge(USER_LABEL, "learn guitar", "wants")),
            ),
        )
        svc.ingest("I want to learn guitar", "great")
        val before = svc.allNodes().map { it.label }.sorted()
        val json = svc.exportJson()
        assertTrue(json.contains("learn guitar"))

        // Import into a brand-new, empty service.
        val svc2 = service()
        val imported = svc2.importJson(json)
        assertEquals(before.size, imported)
        assertEquals(before, svc2.allNodes().map { it.label }.sorted())

        // Bad input fails closed (returns -1, leaves graph empty).
        val svc3 = service()
        assertEquals(-1, svc3.importJson("not json at all"))
        assertTrue(svc3.allNodes().isEmpty())
    }

    @Test
    fun extractFirstJsonObject_handles_nesting_and_strings() {
        assertEquals("""{"a":{"b":1}}""", extractFirstJsonObject("""noise {"a":{"b":1}} tail"""))
        assertEquals("""{"s":"}"}""", extractFirstJsonObject("""{"s":"}"}"""))
        assertNotNull(extractFirstJsonObject("{}"))
    }
}
