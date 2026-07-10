package com.personalagent.shared.learning

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LearningRecommendationParserTest {

    @Test
    fun parses_strict_json_array() {
        val reply = """
            [
              {"title":"The Rust Book — Ch. 4 Ownership","url":"https://doc.rust-lang.org/book/ch04-00-understanding-ownership.html","source":"rust-lang.org","kind":"docs","why":"You're a beginner and ownership is the next concept to unlock.","concept":"ownership"},
              {"title":"Rust in 100 seconds","url":"https://youtube.com/watch?v=x","source":"YouTube","kind":"video","why":"A quick visual primer since you prefer video.","concept":"overview"}
            ]
        """.trimIndent()
        val out = LearningRecommendationParser.parse(reply, goalId = "g1", nowMillis = 100L)
        assertEquals(2, out.size)
        assertEquals(LearningKind.DOCS, out[0].kind)
        assertEquals(LearningStatus.RECOMMENDED, out[0].status)
        assertEquals("g1", out[0].goalId)
        assertEquals("ownership", out[0].concept)
    }

    @Test
    fun tolerates_code_fences_and_prose() {
        val reply = "Sure! Here you go:\n```json\n[{\"title\":\"T\",\"url\":\"https://a.org/x\",\"kind\":\"article\",\"why\":\"w\"}]\n```"
        val out = LearningRecommendationParser.parse(reply, "g1", 1L)
        assertEquals(1, out.size)
        assertEquals("https://a.org/x", out[0].url)
    }

    @Test
    fun drops_non_http_and_blank_entries() {
        val reply = """[
          {"title":"bad scheme","url":"javascript:alert(1)","kind":"other","why":"x"},
          {"title":"","url":"https://ok.org/a","kind":"other","why":"x"},
          {"title":"good","url":"https://ok.org/b","kind":"other","why":"x"}
        ]"""
        val out = LearningRecommendationParser.parse(reply, "g1", 1L)
        assertEquals(1, out.size)
        assertEquals("https://ok.org/b", out[0].url)
    }

    @Test
    fun sanitizes_control_chars_and_caps_count() {
        val reply = """[
          {"title":"a\nb\tc","url":"https://a.org/1","kind":"video","why":"line1\nline2"},
          {"title":"t2","url":"https://a.org/2","kind":"video","why":"w"},
          {"title":"t3","url":"https://a.org/3","kind":"video","why":"w"},
          {"title":"t4","url":"https://a.org/4","kind":"video","why":"w"}
        ]"""
        val out = LearningRecommendationParser.parse(reply, "g1", 1L, max = 3)
        assertEquals(3, out.size) // capped
        assertEquals("a b c", out[0].title) // control chars collapsed to spaces
        assertTrue(!out[0].why.contains("\n"))
    }

    @Test
    fun empty_array_and_garbage_yield_nothing() {
        assertTrue(LearningRecommendationParser.parse("[]", "g1", 1L).isEmpty())
        assertTrue(LearningRecommendationParser.parse("no json here", "g1", 1L).isEmpty())
    }

    @Test
    fun recommend_prompt_carries_avoid_and_rules() {
        val goal = LearningGoal(id = "g1", topic = "Rust", level = "beginner", createdAt = 1L)
        val avoid = listOf(
            LearningResource(id = "r1", goalId = "g1", title = "Old Book", url = "https://x.org/old", recommendedAt = 1L, updatedAt = 1L, status = LearningStatus.ABANDONED),
        )
        val p = com.personalagent.shared.hermes.LearningPrompts.recommendNext(goal, avoid, adaptationHint = "prefers video")
        assertTrue(p.contains("web_search"))
        assertTrue(p.contains("FREE and open web"))
        assertTrue(p.contains("Old Book"))
        assertTrue(p.contains("prefers video"))
        assertTrue(p.contains("untrusted"))
    }
}
