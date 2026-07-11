package com.personalagent.shared.genui

/**
 * The deterministic, offline fallback view — the direct analogue of the knowledge
 * graph's `keywordFallback`. When the model can't be reached or returns nothing
 * usable, we still compose an HONEST view straight from [Facts]: real counts, real
 * open items, no model in the loop. Returns null only when there's genuinely
 * nothing to show (caller then shows plain prose).
 */
object DefaultView {

    // Order we prefer to surface metrics in the fallback recap.
    private val STAT_PRIORITY = listOf(
        Facts.M_TASKS_OPEN,
        Facts.M_TASKS_DONE_TODAY,
        Facts.M_REMINDERS_UPCOMING,
        Facts.M_ACTIVE_GOALS,
        Facts.M_RESOURCES_STARTED,
        Facts.M_TASKS_DONE_WEEK,
        Facts.M_CHAT_MSGS_WEEK,
        Facts.M_CHAT_DAYS_WEEK,
        Facts.M_RESOURCES_FINISHED,
    )

    fun build(facts: Facts): ComposedView? {
        if (facts.isEmpty()) return null
        val blocks = mutableListOf<ViewBlock>()

        blocks += ViewBlock.ProseLine("Here's where things stand — pulled straight from your device.")

        // Stat grid: up to 4 metrics, real values, non-zero first, min 2.
        val nonZero = STAT_PRIORITY.filter { (facts.metrics[it] ?: 0) > 0 }
        val chosen = (nonZero + STAT_PRIORITY.filterNot { it in nonZero })
            .distinct()
            .take(maxOf(2, minOf(4, if (nonZero.isEmpty()) 0 else maxOf(2, nonZero.size))))
        if (chosen.size >= 2) {
            blocks += ViewBlock.StatGrid(
                chosen.take(4).map { key ->
                    Stat(value = (facts.metrics[key] ?: 0).toString(), label = Facts.defaultLabel(key))
                },
            )
        }

        // A short chat-activity sparkline if there's any signal.
        facts.spark[Facts.S_CHAT_7D]?.let { series ->
            if (series.any { it > 0 }) {
                blocks += ViewBlock.Sparkline(
                    points = series.map { it.toDouble() },
                    caption = facts.sparkCaptions[Facts.S_CHAT_7D],
                    highlightIndex = series.size - 1,
                )
            }
        }

        // A plan of real, still-open items (reminders + tasks + plan items).
        val openRows = facts.refs.values
            .filter { !it.done && it.source != PlanRow.SOURCE_LEARNING }
            .take(5)
            .map { ref ->
                PlanRow(
                    id = ref.id,
                    title = ref.title,
                    time = ref.time,
                    note = null,
                    source = ref.source,
                    sourceId = ref.id,
                    done = false,
                    actionable = true,
                )
            }
        if (openRows.isNotEmpty()) {
            blocks += ViewBlock.Plan(heading = "Still open", meta = null, items = openRows)
        }

        if (blocks.size == 1) {
            // Only the prose line survived — better than nothing, still honest.
            return ComposedView("day-recap", "Your day", blocks, ComposedView.PROVENANCE_LOCAL)
        }
        return ComposedView("day-recap", "Your day", blocks, ComposedView.PROVENANCE_LOCAL)
    }
}
