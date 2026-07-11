package com.personalagent.shared.genui

import com.personalagent.shared.chat.StoredConversation
import com.personalagent.shared.chat.StoredMessage
import com.personalagent.shared.learning.LearningGoal
import com.personalagent.shared.learning.LearningResource
import com.personalagent.shared.learning.LearningState
import com.personalagent.shared.learning.LearningStatus
import com.personalagent.shared.model.PlanItem
import com.personalagent.shared.model.Reminder
import com.personalagent.shared.model.ReminderStatus
import com.personalagent.shared.tasks.Task
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Generative-UI pipeline tests — the parser's lenient parse + drop, and (most
 * importantly) the HONESTY reconciliation: numbers come from real facts, not the
 * model; invented ids don't tap through; fallbacks stay truthful.
 */
class GenUiTest {

    private val now = 1_000_000_000_000L
    private val hour = 3_600_000L
    private val day = 24 * hour

    private fun facts(
        tasks: List<Task> = emptyList(),
        reminders: List<Reminder> = emptyList(),
        planItems: List<PlanItem> = emptyList(),
        learning: LearningState = LearningState(),
        conversations: List<StoredConversation> = emptyList(),
    ): Facts = FactsCollector.build(now, tasks, reminders, planItems, learning, conversations)

    private fun task(id: String, done: Boolean, completedAgo: Long? = null, text: String = "Task $id") =
        Task(id = id, text = text, done = done, createdAt = now - 2 * day, completedAt = completedAgo?.let { now - it })

    private fun reminder(id: String, inFuture: Long, title: String = "Call $id") =
        Reminder(id = id, title = title, note = "", triggerAtMillis = now + inFuture, status = ReminderStatus.SCHEDULED, createdAt = now - day)

    // --- FactsCollector -------------------------------------------------------

    @Test
    fun facts_count_real_windows() {
        val f = facts(
            tasks = listOf(
                task("a", done = false),
                task("b", done = true, completedAgo = 2 * hour),   // today
                task("c", done = true, completedAgo = 3 * day),    // this week, not today
                task("d", done = true, completedAgo = 30 * day),   // outside window
            ),
            reminders = listOf(
                reminder("r1", inFuture = 5 * hour),
                reminder("r2", inFuture = -hour), // past → not upcoming
            ),
        )
        assertEquals(1, f.metrics[Facts.M_TASKS_OPEN])
        assertEquals(1, f.metrics[Facts.M_TASKS_DONE_TODAY])
        assertEquals(2, f.metrics[Facts.M_TASKS_DONE_WEEK])
        assertEquals(1, f.metrics[Facts.M_REMINDERS_UPCOMING])
        assertTrue(f.refs.containsKey("r1"))
    }

    @Test
    fun facts_empty_when_nothing() {
        assertTrue(facts().isEmpty())
    }

    @Test
    fun facts_sparkline_buckets_last_7_days() {
        val convo = StoredConversation(
            id = 1, title = "t", conversationId = "c", createdAt = now - 6 * day, updatedAt = now,
            messages = listOf(
                StoredMessage(1, "user", "hi", now),          // today
                StoredMessage(2, "user", "yo", now - day),    // yesterday
                StoredMessage(3, "assistant", "hello", now),  // not a user msg
            ),
        )
        val f = facts(conversations = listOf(convo))
        val series = f.spark[Facts.S_CHAT_7D]!!
        assertEquals(7, series.size)
        assertEquals(1, series[6]) // today = newest bucket
        assertEquals(1, series[5]) // yesterday
        assertEquals(2, f.metrics[Facts.M_CHAT_MSGS_WEEK])
        assertEquals(2, f.metrics[Facts.M_CHAT_DAYS_WEEK])
    }

    // --- Honesty: stat-grid numbers come from facts, not the model ------------

    @Test
    fun statgrid_overwrites_model_number_with_real_count() {
        val f = facts(tasks = listOf(task("a", false), task("b", false)))
        // Model LIES: claims 99 done today (real = 0) and 5 open (real = 2).
        val reply = """
            {"view":"day-recap","title":"Today","blocks":[
              {"type":"stat-grid","stats":[
                {"key":"tasks_done_today","label":"done"},
                {"key":"tasks_open","label":"to do"}
              ]}
            ]}
        """.trimIndent()
        val view = assertNotNull(ViewSpecParser.parse(reply, f))
        val grid = view.blocks.filterIsInstance<ViewBlock.StatGrid>().single()
        val byLabel = grid.stats.associate { it.label to it.value }
        assertEquals("0", byLabel["done"])  // real, not 99
        assertEquals("2", byLabel["to do"]) // real, not 5
    }

    @Test
    fun statgrid_drops_unknown_metric_keys_and_block_if_under_two() {
        val f = facts(tasks = listOf(task("a", false)))
        val reply = """{"view":"x","blocks":[
          {"type":"stat-grid","stats":[
            {"key":"tasks_open","label":"to do"},
            {"key":"made_up_metric","label":"fake"}
          ]}
        ]}"""
        // Only one valid stat survives → block dropped → whole view null.
        assertNull(ViewSpecParser.parse(reply, f))
    }

    // --- Honesty: sparkline points come from the real series ------------------

    @Test
    fun sparkline_uses_real_series_for_known_key_only() {
        val convo = StoredConversation(
            id = 1, title = "t", conversationId = "c", createdAt = now, updatedAt = now,
            messages = listOf(StoredMessage(1, "user", "hi", now)),
        )
        val f = facts(conversations = listOf(convo))
        val ok = """{"view":"x","blocks":[
          {"type":"prose-line","text":"A steady week."},
          {"type":"sparkline","key":"chat_7d","caption":"chats"}]}"""
        val view = assertNotNull(ViewSpecParser.parse(ok, f))
        val spark = view.blocks.filterIsInstance<ViewBlock.Sparkline>().single()
        assertEquals(7, spark.points.size)
        assertEquals(1.0, spark.points.last())

        val bad = """{"view":"x","blocks":[
          {"type":"prose-line","text":"hi"},
          {"type":"sparkline","key":"stock_prices"}]}"""
        // unknown series dropped; only prose survives
        val v2 = assertNotNull(ViewSpecParser.parse(bad, f))
        assertTrue(v2.blocks.none { it is ViewBlock.Sparkline })
    }

    // --- Honesty: plan rows only for real ids ---------------------------------

    @Test
    fun plan_keeps_real_ids_drops_invented_ones() {
        val f = facts(
            tasks = listOf(task("t1", false, text = "Buy milk")),
            reminders = listOf(reminder("r1", 3 * hour, title = "Call Sam")),
        )
        val reply = """{"view":"plan","blocks":[
          {"type":"plan","heading":"Evening","items":[
            {"id":"r1","title":"Call Sam back","note":"you meant to today"},
            {"id":"ghost_999","title":"Invented task"},
            {"id":"t1"}
          ]}
        ]}"""
        val view = assertNotNull(ViewSpecParser.parse(reply, f))
        val plan = view.blocks.filterIsInstance<ViewBlock.Plan>().single()
        assertEquals(2, plan.items.size) // ghost dropped
        val r1 = plan.items.first { it.id == "r1" }
        assertTrue(r1.actionable)
        assertEquals(PlanRow.SOURCE_REMINDER, r1.source)
        val t1 = plan.items.first { it.id == "t1" }
        assertEquals("Buy milk", t1.title) // fell back to the real record title
        assertEquals(PlanRow.SOURCE_TASK, t1.source)
    }

    // --- Parser leniency + safety ---------------------------------------------

    @Test
    fun tolerates_code_fences_and_drops_unknown_blocks() {
        val f = facts(tasks = listOf(task("a", false), task("b", true, completedAgo = hour)))
        val reply = "Sure!\n```json\n{\"view\":\"day-recap\",\"blocks\":[" +
            "{\"type\":\"iframe\",\"src\":\"http://evil\"}," +
            "{\"type\":\"prose-line\",\"text\":\"Nice work today.\"}," +
            "{\"type\":\"stat-grid\",\"stats\":[{\"key\":\"tasks_open\"},{\"key\":\"tasks_done_today\"}]}" +
            "]}\n```"
        val view = assertNotNull(ViewSpecParser.parse(reply, f))
        assertTrue(view.blocks.none { it.kind == "iframe" })
        assertEquals(ViewBlock.ProseLine.KIND, view.blocks.first().kind)
        assertEquals(ComposedView.PROVENANCE_MODEL, view.provenance)
    }

    @Test
    fun garbage_returns_null() {
        assertNull(ViewSpecParser.parse("no json here", facts(tasks = listOf(task("a", false)))))
        assertNull(ViewSpecParser.parse("", facts()))
    }

    @Test
    fun prose_emphasis_must_be_substrings() {
        val f = facts(tasks = listOf(task("a", false)))
        val reply = """{"view":"x","blocks":[
          {"type":"prose-line","text":"A quietly productive day.","emphasis":["quietly productive","not-present"]}]}"""
        val view = assertNotNull(ViewSpecParser.parse(reply, f))
        val prose = view.blocks.filterIsInstance<ViewBlock.ProseLine>().single()
        assertEquals(listOf("quietly productive"), prose.emphasis)
    }

    // --- resource-rec only renders the real focus -----------------------------

    @Test
    fun resource_rec_requires_real_focus() {
        val goal = LearningGoal(id = "g1", topic = "Rust", level = "beginner", createdAt = now)
        val res = LearningResource(
            id = "res1", goalId = "g1", title = "Ownership chapter",
            url = "https://doc.rust-lang.org/book/ch04", why = "next up",
            status = LearningStatus.STARTED, recommendedAt = now - day, updatedAt = now,
        )
        val f = facts(learning = LearningState(goals = listOf(goal), resources = listOf(res)))
        val reply = """{"view":"resource-rec","blocks":[
          {"type":"resource-rec","goal":"Rust","level":"beginner","resourceId":"res1"}]}"""
        val view = assertNotNull(ViewSpecParser.parse(reply, f))
        val rec = view.blocks.filterIsInstance<ViewBlock.ResourceRec>().single()
        assertEquals("res1", rec.resource.id)

        // A resourceId that isn't the real focus is refused.
        val fake = """{"view":"x","blocks":[
          {"type":"resource-rec","goal":"Rust","resourceId":"nope"}]}"""
        assertNull(ViewSpecParser.parse(fake, f))
    }

    // --- Deterministic local fallback -----------------------------------------

    @Test
    fun default_view_is_truthful_and_nonempty() {
        val f = facts(
            tasks = listOf(task("a", false, text = "Ship build"), task("b", true, completedAgo = hour)),
            reminders = listOf(reminder("r1", 2 * hour, title = "Standup")),
        )
        val view = assertNotNull(DefaultView.build(f))
        assertEquals(ComposedView.PROVENANCE_LOCAL, view.provenance)
        assertTrue(view.blocks.any { it is ViewBlock.StatGrid })
        val plan = view.blocks.filterIsInstance<ViewBlock.Plan>().singleOrNull()
        assertNotNull(plan)
        assertTrue(plan.items.all { it.actionable })
    }

    @Test
    fun default_view_null_when_empty() {
        assertNull(DefaultView.build(facts()))
    }

    // --- Suggestion chips heuristic -------------------------------------------

    @Test
    fun chip_prompt_detection() {
        assertEquals(5, SuggestionChips.ALL.size)
        assertEquals("week-pulse", SuggestionChips.preferredViewFor("How's my week going?"))
        assertEquals("plan", SuggestionChips.preferredViewFor("can you plan my evening please"))
        assertEquals("resource-rec", SuggestionChips.preferredViewFor("what should I learn next"))
        assertNull(SuggestionChips.preferredViewFor("what's the capital of France"))
    }
}
