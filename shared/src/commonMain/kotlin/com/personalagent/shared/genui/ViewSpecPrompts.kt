package com.personalagent.shared.genui

/**
 * Builds the strict view-spec prompt, mirroring
 * [com.personalagent.shared.knowledge.KnowledgeGraphExtractor.buildExtractionPrompt]:
 * a system-style instruction carrying the primitive catalog + the honesty rule,
 * then a **facts block** of the user's real counts/items, then the user's ask.
 *
 * The model's job is NARRATION + SELECTION only — pick a composition, choose a
 * layout of primitives, phrase the prose, and *reference metrics by key*. It must
 * NOT invent numbers: every stat/sparkline names a `key` from the facts block, and
 * the client overwrites the value from the real data ([ViewSpecParser]). Anything
 * it can't tie to a real key/id is dropped before render.
 */
object ViewSpecPrompts {

    /**
     * @param facts the honest snapshot (metrics + items + ids).
     * @param ask the user's request (chip canonical prompt or free text).
     * @param preferredView a soft hint (e.g. "week-pulse") — the agent still chooses.
     */
    fun build(facts: Facts, ask: String, preferredView: String? = null): String = buildString {
        append("You compose a small dashboard VIEW for a personal life-agent app by returning a ")
        append("single strict JSON object. You do NOT write HTML, markdown or code — only this JSON. ")
        append("The app renders it with its own native cards.\n\n")

        append("Return ONLY one minified JSON object, no prose, no code fence, EXACTLY this shape:\n")
        append("{\"view\":\"week-pulse|day-recap|plan|resource-rec|stat-grid\",")
        append("\"title\":\"short heading\",\"blocks\":[ ...1 to 6 blocks... ]}\n\n")

        append("Each block is ONE of these fixed primitives:\n")
        append("1. {\"type\":\"prose-line\",\"text\":\"one warm sentence\",\"emphasis\":[\"phrase in text\"]}\n")
        append("2. {\"type\":\"stat-grid\",\"stats\":[{\"key\":\"<metric key>\",\"label\":\"short label\"}, 2 to 4 of them]}\n")
        append("3. {\"type\":\"sparkline\",\"key\":\"<series key>\",\"caption\":\"short caption\"}\n")
        append("4. {\"type\":\"plan\",\"heading\":\"short\",\"meta\":\"optional subtitle\",")
        append("\"items\":[{\"id\":\"<real item id>\",\"title\":\"short\",\"time\":\"optional\",\"note\":\"one honest reason\"}]}\n")
        append("5. {\"type\":\"resource-rec\",\"goal\":\"the goal\",\"level\":\"optional\",")
        append("\"resourceId\":\"<real resource id, if listed below>\"}\n\n")

        append("HONESTY RULES (critical):\n")
        append("- For stat-grid, EVERY stat must name a `key` from METRICS below. Do NOT write the number; ")
        append("the app fills the real value. Pick 2-4 keys that best answer the ask.\n")
        append("- For sparkline, `key` must be one of the SERIES keys below.\n")
        append("- For plan, every item `id` MUST be one of the ITEM ids below — never invent an item. ")
        append("Only include items relevant to the ask.\n")
        append("- For resource-rec, use the FOCUS resourceId below if present.\n")
        append("- prose-line is your voice: encouraging and specific, but never assert a count the stats don't show.\n")
        append("- Omit any block you have no real data for. If there's almost nothing, return one honest prose-line.\n\n")

        if (preferredView != null) {
            append("The user's request suggests a \"$preferredView\" view, but choose whatever fits the data.\n\n")
        }

        append("=== THE USER'S REAL DATA (use ONLY this) ===\n")
        append("METRICS (key = value):\n")
        for ((k, v) in facts.metrics) append("  $k = $v\n")
        append("SERIES (key : 7 daily values, oldest to newest):\n")
        for ((k, series) in facts.spark) {
            val cap = facts.sparkCaptions[k] ?: k
            append("  $k : ${series.joinToString(",")}  ($cap)\n")
        }
        if (facts.refs.isNotEmpty()) {
            append("ITEMS (id | source | title):\n")
            for (ref in facts.refs.values) {
                val t = ref.title.replace('\n', ' ').take(80)
                append("  ${ref.id} | ${ref.source} | $t\n")
            }
        } else {
            append("ITEMS: (none)\n")
        }
        val focus = facts.focus
        if (focus != null) {
            append("FOCUS (learning): goal=\"${focus.goalTopic}\" level=\"${focus.level ?: "?"}\" ")
            append("resourceId=${focus.resource.id} title=\"${focus.resource.title.take(80)}\"\n")
        }
        append("\n=== THE USER ASKS ===\n")
        append(ask.replace('\n', ' ').take(300))
    }
}
