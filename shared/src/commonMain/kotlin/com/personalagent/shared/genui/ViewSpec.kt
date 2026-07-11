package com.personalagent.shared.genui

import com.personalagent.shared.learning.LearningResource

/**
 * Generative UI — the composed **view spec** the client renders natively.
 *
 * The agent NEVER returns markup, HTML, SwiftUI or Compose. It returns a small,
 * strict JSON object naming an allowed primitive and its data (see
 * [ViewSpecPrompts]); [ViewSpecParser] validates it against this fixed schema and
 * builds the domain objects below. The client then renders each surviving block
 * with native components it already owns. This is the *exact* structured-
 * extraction → lenient-parse → native-render pattern the knowledge graph and
 * learning recommender already ship — applied to layout. Data can never become
 * code: an unknown/invalid block is dropped, never rendered as raw text, never
 * executed. See docs/GENERATIVE_UI.md.
 *
 * These are plain (non-`@Serializable`) domain types on purpose:
 *  - the wire DTOs live privately inside [ViewSpecParser];
 *  - the sealed [ViewBlock] hierarchy is exported to Swift by Kotlin/Native so the
 *    iOS renderer can `switch` over it — kept free of serialization annotations,
 *    with only Swift-friendly field types (String/Int/Bool/List and small value
 *    classes; `source` is a plain string, not an enum, to avoid enum bridging).
 */

/** One composed view: a semantic name + optional title + ordered, validated blocks. */
data class ComposedView(
    /** Semantic composition name — drives the *"composed for you · <view>"* eyebrow. */
    val view: String,
    /** Optional short heading (already sanitized), or null. */
    val title: String?,
    /** 1–8 primitives to stack, in order. Never empty for a real view. */
    val blocks: List<ViewBlock>,
    /** How this view was produced: [PROVENANCE_MODEL] or [PROVENANCE_LOCAL]. */
    val provenance: String,
) {
    companion object {
        /** The agent composed + we reconciled it against real facts. */
        const val PROVENANCE_MODEL = "model"
        /** Deterministic local fallback built straight from the user's real data. */
        const val PROVENANCE_LOCAL = "local-default"
    }
}

/**
 * The fixed primitive set. A sealed class so the Compose `when` and the SwiftUI
 * `switch` are (per platform) exhaustive over exactly these shapes. Each carries
 * a stable [kind] string discriminator so the renderers — and analytics/tests —
 * never depend on class identity across the bridge.
 */
sealed class ViewBlock {
    abstract val kind: String

    /** One warm agent sentence (serif italic). The connective tissue between cards. */
    data class ProseLine(
        val text: String,
        /** ≤3 substrings of [text] to render in the accent color. */
        val emphasis: List<String> = emptyList(),
    ) : ViewBlock() {
        override val kind: String get() = KIND
        companion object { const val KIND = "prose-line" }
    }

    /** 2–4 big-number / small-label tiles. Every [Stat.value] is a REAL count. */
    data class StatGrid(val stats: List<Stat>) : ViewBlock() {
        override val kind: String get() = KIND
        companion object { const val KIND = "stat-grid" }
    }

    /** A short single-metric trend; bars normalized client-side. */
    data class Sparkline(
        val points: List<Double>,
        val caption: String?,
        /** Which bar to accent (e.g. today), or -1 for none. */
        val highlightIndex: Int = -1,
    ) : ViewBlock() {
        override val kind: String get() = KIND
        companion object { const val KIND = "sparkline" }
    }

    /** An ordered, tickable list of real records (reminders / tasks / learning). */
    data class Plan(
        val heading: String,
        val meta: String?,
        val items: List<PlanRow>,
    ) : ViewBlock() {
        override val kind: String get() = KIND
        companion object { const val KIND = "plan" }
    }

    /** One next learning step — reuses the existing inert [LearningResource]. */
    data class ResourceRec(
        val goal: String,
        val level: String?,
        val resource: LearningResource,
    ) : ViewBlock() {
        override val kind: String get() = KIND
        companion object { const val KIND = "resource-rec" }
    }
}

/** A single headline stat tile. [value] is rendered verbatim — always a real count. */
data class Stat(val value: String, val label: String)

/**
 * One row in a [ViewBlock.Plan]. When [actionable] is true, [sourceId] resolved to
 * a real record in [source]'s store, so a tap can open/complete it; otherwise the
 * row is inert display only (a soft suggestion), never a dead tap-through.
 */
data class PlanRow(
    val id: String,
    val title: String,
    val time: String?,
    val note: String?,
    /** "reminder" | "task" | "plan" | "goal" | "learning" | "none". A plain string. */
    val source: String,
    val sourceId: String?,
    val done: Boolean,
    val actionable: Boolean,
) {
    companion object {
        const val SOURCE_REMINDER = "reminder"
        const val SOURCE_TASK = "task"
        const val SOURCE_PLAN = "plan"
        const val SOURCE_GOAL = "goal"
        const val SOURCE_LEARNING = "learning"
        const val SOURCE_NONE = "none"
    }
}

/** The result of a compose attempt handed to the UI. */
sealed class ComposeResult {
    /** A view is ready to render (from the model, or the honest local fallback). */
    data class Composed(val view: ComposedView) : ComposeResult()

    /** No card is possible/appropriate — show this as an ordinary agent prose line. */
    data class Prose(val text: String) : ComposeResult()
}
