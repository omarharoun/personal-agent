package com.personalagent.shared.genui

import com.personalagent.shared.chat.StoredConversation
import com.personalagent.shared.learning.LearningResource
import com.personalagent.shared.learning.LearningState
import com.personalagent.shared.learning.LearningStatus
import com.personalagent.shared.model.PlanItem
import com.personalagent.shared.model.Reminder
import com.personalagent.shared.model.ReminderStatus
import com.personalagent.shared.tasks.Task

/**
 * A compact, **honest** snapshot of the user's real local data — the ground truth
 * the generative-UI honesty rule pins every number to. Built purely from the
 * device's own stores by [FactsCollector.build]; nothing here is model-derived.
 *
 *  - [metrics] — the fixed vocabulary of counts the agent may reference *by key*.
 *    A stat/plan the model returns is reconciled against these before render, so a
 *    hallucinated number is overwritten with the truth (see [ViewSpecParser]).
 *  - [spark] — per-day series (last 7 rolling days, oldest→newest) for sparklines.
 *  - [refs] — real records the agent may lay out as plan rows, keyed by id, so a
 *    tap resolves to something that actually exists (unknown ids never tap through).
 */
data class Facts(
    val now: Long,
    val metrics: Map<String, Int>,
    val spark: Map<String, List<Int>>,
    val sparkCaptions: Map<String, String>,
    /** Real records by id (reminders, tasks, plan items, learning resources). */
    val refs: Map<String, FactRef>,
    /** The user's active learning focus, if anything is in progress. */
    val focus: FactFocus?,
) {
    /** True when there's genuinely nothing to show — drives the plain-prose fallback. */
    fun isEmpty(): Boolean =
        metrics.values.all { it == 0 } && refs.isEmpty() && focus == null

    companion object {
        // The honest metric vocabulary. The prompt lists these; the parser
        // overwrites any returned stat/sparkline whose key is one of these with
        // the real value here, and DROPS any it can't recompute.
        const val M_TASKS_DONE_TODAY = "tasks_done_today"
        const val M_TASKS_DONE_WEEK = "tasks_done_week"
        const val M_TASKS_OPEN = "tasks_open"
        const val M_REMINDERS_UPCOMING = "reminders_upcoming"
        const val M_ACTIVE_GOALS = "active_goals"
        const val M_RESOURCES_STARTED = "resources_started"
        const val M_RESOURCES_FINISHED = "resources_finished"
        const val M_CHAT_DAYS_WEEK = "chat_days_week"
        const val M_CHAT_MSGS_WEEK = "chat_msgs_week"

        const val S_CHAT_7D = "chat_7d"
        const val S_TASKS_7D = "tasks_7d"

        /** Default tile label per metric key (the model may override with its own). */
        fun defaultLabel(key: String): String = when (key) {
            M_TASKS_DONE_TODAY -> "done today"
            M_TASKS_DONE_WEEK -> "done this week"
            M_TASKS_OPEN -> "to do"
            M_REMINDERS_UPCOMING -> "reminders"
            M_ACTIVE_GOALS -> "goals"
            M_RESOURCES_STARTED -> "in progress"
            M_RESOURCES_FINISHED -> "finished"
            M_CHAT_DAYS_WEEK -> "active days"
            M_CHAT_MSGS_WEEK -> "chats"
            else -> key
        }
    }
}

/** A real record the agent may reference in a plan row (so a tap resolves). */
data class FactRef(
    val id: String,
    val title: String,
    val source: String,
    val time: String? = null,
    val done: Boolean = false,
)

/** The single "pick up where you left off" learning item, if any. */
data class FactFocus(
    val goalId: String,
    val goalTopic: String,
    val level: String?,
    val resource: LearningResource,
)

/**
 * Builds a [Facts] snapshot from already-fetched store data. Pure and fully unit-
 * tested; the platform/service layer does the (suspend) fetching and calls this.
 *
 * Windows are **rolling**: "today" = the last 24h, "this week" = the last 7 days,
 * and the sparkline buckets the last 7 rolling days oldest→newest. This keeps the
 * counting deterministic and timezone-free (epoch-millis only) — honest to the
 * "last 24 hours / last 7 days" wording used in the prompt.
 */
object FactsCollector {

    private const val DAY_MS = 24L * 60 * 60 * 1000
    private const val WEEK_MS = 7L * DAY_MS
    private const val SPARK_DAYS = 7

    fun build(
        now: Long,
        tasks: List<Task>,
        reminders: List<Reminder>,
        planItems: List<PlanItem>,
        learning: LearningState,
        conversations: List<StoredConversation>,
    ): Facts {
        val dayAgo = now - DAY_MS
        val weekAgo = now - WEEK_MS

        // --- Tasks -------------------------------------------------------------
        val tasksOpen = tasks.count { !it.done }
        val tasksDoneToday = tasks.count { it.done && (it.completedAt ?: 0L) in dayAgo..now }
        val tasksDoneWeek = tasks.count { it.done && (it.completedAt ?: 0L) in weekAgo..now }

        // --- Reminders (device-local, still-scheduled + in the future) ---------
        val upcoming = reminders
            .filter { it.status == ReminderStatus.SCHEDULED && it.triggerAtMillis > now }
            .sortedBy { it.triggerAtMillis }

        // --- Learning ----------------------------------------------------------
        val activeGoalIds = learning.goals.filter { it.active }.map { it.id }.toSet()
        val activeResources = learning.resources.filter { it.goalId in activeGoalIds }
        val started = activeResources.count { it.status == LearningStatus.STARTED }
        val finished = activeResources.count {
            it.status == LearningStatus.FINISHED || it.status == LearningStatus.LOVED
        }

        // --- Chat activity -----------------------------------------------------
        val userMsgTimes = conversations.flatMap { c ->
            c.messages.filter { it.role == "user" }.map { it.time }
        }
        val msgsWeek = userMsgTimes.count { it in weekAgo..now }
        val daysWeek = userMsgTimes
            .filter { it in weekAgo..now }
            .map { dayBucket(now, it) }
            .toSet()
            .size

        val metrics = mapOf(
            Facts.M_TASKS_DONE_TODAY to tasksDoneToday,
            Facts.M_TASKS_DONE_WEEK to tasksDoneWeek,
            Facts.M_TASKS_OPEN to tasksOpen,
            Facts.M_REMINDERS_UPCOMING to upcoming.size,
            Facts.M_ACTIVE_GOALS to activeGoalIds.size,
            Facts.M_RESOURCES_STARTED to started,
            Facts.M_RESOURCES_FINISHED to finished,
            Facts.M_CHAT_DAYS_WEEK to daysWeek,
            Facts.M_CHAT_MSGS_WEEK to msgsWeek,
        )

        // --- Sparklines (oldest → newest over the last 7 rolling days) ---------
        val chatSeries = perDay(now, userMsgTimes)
        val taskSeries = perDay(now, tasks.filter { it.done }.mapNotNull { it.completedAt })
        val spark = mapOf(
            Facts.S_CHAT_7D to chatSeries,
            Facts.S_TASKS_7D to taskSeries,
        )
        val sparkCaptions = mapOf(
            Facts.S_CHAT_7D to "conversations, last 7 days",
            Facts.S_TASKS_7D to "tasks done, last 7 days",
        )

        // --- Real records the agent may lay out as plan rows -------------------
        val refs = LinkedHashMap<String, FactRef>()
        for (r in upcoming.take(8)) {
            refs[r.id] = FactRef(r.id, r.title, PlanRow.SOURCE_REMINDER, time = clockLabel(r.triggerAtMillis, now), done = false)
        }
        for (t in tasks.take(12)) {
            refs[t.id] = FactRef(t.id, t.text, PlanRow.SOURCE_TASK, done = t.done)
        }
        for (p in planItems.take(12)) {
            refs[p.id] = FactRef(p.id, p.title, PlanRow.SOURCE_PLAN, done = p.done)
        }
        for (res in activeResources.take(8)) {
            refs[res.id] = FactRef(res.id, res.title, PlanRow.SOURCE_LEARNING, done = res.status == LearningStatus.FINISHED)
        }

        // --- Learning focus ----------------------------------------------------
        val focusResource = activeResources
            .filter { it.status == LearningStatus.STARTED }.maxByOrNull { it.updatedAt }
            ?: activeResources.filter { it.status == LearningStatus.RECOMMENDED }.maxByOrNull { it.recommendedAt }
        val focus = focusResource?.let { res ->
            val goal = learning.goals.firstOrNull { it.id == res.goalId }
            if (goal == null) null
            else FactFocus(goal.id, goal.topic, goal.level, res)
        }

        return Facts(
            now = now,
            metrics = metrics,
            spark = spark,
            sparkCaptions = sparkCaptions,
            refs = refs,
            focus = focus,
        )
    }

    /** Bucket 0 = today, 1 = yesterday, … (only used to count distinct active days). */
    private fun dayBucket(now: Long, t: Long): Int = ((now - t) / DAY_MS).toInt()

    /** A 7-length series, index 0 = 6 days ago … index 6 = today. */
    private fun perDay(now: Long, times: List<Long>): List<Int> {
        val buckets = IntArray(SPARK_DAYS)
        for (t in times) {
            val b = ((now - t) / DAY_MS).toInt()
            if (b in 0 until SPARK_DAYS) buckets[SPARK_DAYS - 1 - b] += 1
        }
        return buckets.toList()
    }

    /** A light relative time label ("in 2h", "in 3d") — display only, no timezone. */
    private fun clockLabel(triggerAt: Long, now: Long): String {
        val d = triggerAt - now
        if (d <= 0) return "now"
        val mins = d / 60000
        return when {
            mins < 60 -> "in ${mins}m"
            mins < 60 * 24 -> "in ${mins / 60}h"
            else -> "in ${mins / (60 * 24)}d"
        }
    }
}
