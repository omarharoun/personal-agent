package com.personalagent.shared.knowledge

import com.personalagent.shared.chat.StoredConversation
import com.personalagent.shared.chat.StoredMessage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class KnowledgeGraphExtractorTest {

    private var mid = 0L
    private fun user(text: String) = StoredMessage(mid++, "user", text, 0L)
    private fun asst(text: String) = StoredMessage(mid++, "assistant", text, 0L)
    private fun conv(id: Long, vararg msgs: StoredMessage) =
        StoredConversation(id, "c$id", "conv-$id", 0L, id, msgs.toList())

    @Test
    fun parseReadsModelJsonAndDropsDanglingEdges() {
        val raw = """
            Sure! Here is the graph:
            ```json
            {"nodes":[{"id":"running","label":"Running","type":"activity","weight":8},
                      {"id":"sleep","label":"Sleep","type":"topic","weight":5}],
             "edges":[{"from":"running","to":"sleep","relation":"affects"},
                      {"from":"running","to":"ghost","relation":"nope"}]}
            ```
        """.trimIndent()
        val g = KnowledgeGraphExtractor.parse(raw)!!
        assertEquals(2, g.nodes.size)
        assertEquals(KnowledgeGraphSource.MODEL, g.source)
        // The edge to the non-existent "ghost" node is dropped.
        assertEquals(1, g.edges.size)
        assertEquals("affects", g.edges.single().relation)
    }

    @Test
    fun parseReturnsNullOnGarbage() {
        assertNull(KnowledgeGraphExtractor.parse("no json here at all"))
        assertNull(KnowledgeGraphExtractor.parse("{\"nodes\":[]}"))
    }

    @Test
    fun keywordFallbackBuildsNodesAndCoOccurrenceEdges() {
        val convos = listOf(
            conv(1, user("I want to improve my running and marathon training"), asst("great")),
            conv(2, user("How does running affect my sleep quality?")),
            conv(3, user("Tips for better sleep and rest habits")),
        )
        val g = KnowledgeGraphExtractor.keywordFallback(convos)
        assertEquals(KnowledgeGraphSource.KEYWORDS, g.source)
        val labels = g.nodes.map { it.id }
        assertTrue("running" in labels, "expected 'running' node, got $labels")
        assertTrue("sleep" in labels, "expected 'sleep' node, got $labels")
        // running + sleep co-occur in conversation 2 → an edge between them.
        assertTrue(g.edges.any { setOf(it.from, it.to) == setOf("running", "sleep") })
    }

    @Test
    fun attachSnippetsSurfacesRealUserQuestions() {
        val convos = listOf(conv(1, user("How does running affect my sleep?")))
        val base = KnowledgeGraph(nodes = listOf(KnowledgeNode("running", "Running")))
        val withSnippets = KnowledgeGraphExtractor.attachSnippets(base, convos)
        assertTrue(withSnippets.nodes.single().snippets.any { it.contains("running", ignoreCase = true) })
    }

    @Test
    fun signatureChangesWhenChatsChange() {
        val a = listOf(conv(1, user("hello world topic")))
        val b = listOf(conv(1, user("hello world topic"), user("another one")))
        assertTrue(KnowledgeGraphExtractor.signature(a) != KnowledgeGraphExtractor.signature(b))
        // Stable for identical input.
        assertEquals(KnowledgeGraphExtractor.signature(a), KnowledgeGraphExtractor.signature(a))
    }

    @Test
    fun emptyHistoryYieldsEmptyGraph() {
        assertTrue(KnowledgeGraphExtractor.keywordFallback(emptyList()).isEmpty)
    }
}
