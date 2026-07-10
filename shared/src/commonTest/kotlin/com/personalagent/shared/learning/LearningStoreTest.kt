package com.personalagent.shared.learning

import com.personalagent.shared.hermes.HermesToolset
import com.personalagent.shared.hermes.LearningPrompts
import com.personalagent.shared.hermes.WebToolAvailability
import com.personalagent.shared.store.InMemoryKeyValueStorage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LearningStoreTest {

    private fun goal(id: String = "g1", topic: String = "Rust", active: Boolean = true) =
        LearningGoal(id = id, topic = topic, why = "build a CLI", level = "beginner", createdAt = 100L, active = active)

    private fun res(id: String, url: String, goalId: String = "g1", status: LearningStatus = LearningStatus.RECOMMENDED) =
        LearningResource(id = id, goalId = goalId, title = "T$id", url = url, recommendedAt = 100L, updatedAt = 100L, status = status)

    @Test
    fun goals_persist_and_active_first() {
        val storage = InMemoryKeyValueStorage()
        LearningStore(storage).apply {
            addGoal(goal("a", "Rust"))
            addGoal(goal("b", "Piano", active = false))
        }
        // survives "relaunch": a fresh store over the same storage sees them.
        val goals = LearningStore(storage).goals()
        assertEquals(2, goals.size)
        assertTrue(goals.first().active) // active goal sorts first
        assertEquals(1, LearningStore(storage).activeGoals().size)
    }

    @Test
    fun update_goal_records_level_and_style_once() {
        val storage = InMemoryKeyValueStorage()
        val store = LearningStore(storage).apply { addGoal(goal().copy(level = null, style = null)) }
        store.updateGoal("g1") { it.copy(level = "beginner", style = "prefers video") }
        val g = LearningStore(storage).goal("g1")!!
        assertEquals("beginner", g.level)
        assertEquals("prefers video", g.style)
    }

    @Test
    fun recommendations_dedup_by_url() {
        val storage = InMemoryKeyValueStorage()
        val store = LearningStore(storage).apply { addGoal(goal()) }
        val added1 = store.addRecommendations("g1", listOf(res("r1", "https://doc.rust-lang.org/book/")))
        assertEquals(1, added1.size)
        // Same URL (trailing slash / case variant) is not re-added.
        val added2 = store.addRecommendations("g1", listOf(res("r2", "https://doc.rust-lang.org/book")))
        assertTrue(added2.isEmpty())
        assertEquals(1, store.resources("g1").size)
    }

    @Test
    fun set_status_updates_resource() {
        val storage = InMemoryKeyValueStorage()
        val store = LearningStore(storage).apply {
            addGoal(goal())
            addRecommendations("g1", listOf(res("r1", "https://example.org/a")))
        }
        store.setStatus("r1", LearningStatus.FINISHED, nowMillis = 500L)
        val r = LearningStore(storage).resources("g1").first()
        assertEquals(LearningStatus.FINISHED, r.status)
        assertEquals(500L, r.updatedAt)
    }

    @Test
    fun removing_goal_removes_its_resources() {
        val storage = InMemoryKeyValueStorage()
        val store = LearningStore(storage).apply {
            addGoal(goal())
            addRecommendations("g1", listOf(res("r1", "https://example.org/a")))
        }
        store.removeGoal("g1")
        assertTrue(store.goals().isEmpty())
        assertTrue(store.resources("g1").isEmpty())
    }

    @Test
    fun save_learning_goal_prompt_is_compact_and_no_web() {
        val p = LearningPrompts.saveLearningGoal(goal())
        assertTrue(p.contains("Rust"))
        assertTrue(p.contains("beginner"))
        assertTrue(p.contains("do not search the web yet"))
    }

    @Test
    fun web_search_availability_reads_toolsets() {
        val on = listOf(HermesToolset(name = "web", enabled = true, configured = true, tools = listOf("web_search", "web_extract")))
        assertTrue(WebToolAvailability.isWebSearchAvailable(on))
        val off = listOf(HermesToolset(name = "web", enabled = false, configured = true, tools = listOf("web_search")))
        assertFalse(WebToolAvailability.isWebSearchAvailable(off))
        assertFalse(WebToolAvailability.isWebSearchAvailable(emptyList()))
    }
}
