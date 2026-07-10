package com.personalagent.shared.learning

import kotlinx.serialization.Serializable

/**
 * Phase 6 — Learning Guide data models (shared, pure).
 *
 * Design (per Step 0 findings):
 *  - The **device-local [LearningStore]** is the AUTHORITATIVE record of what was
 *    recommended / started / finished / abandoned. Hermes memory (global +
 *    char-limited, NOT isolated per session-key) holds only the *current focus*.
 *    So we never overload Hermes memory with a growing resource history, and we
 *    never rely on per-user memory isolation.
 *
 * 🔒 REVIEW REQUIRED — untrusted web content. [LearningResource.title], [url],
 * [source] and [why] originate from web search/browse results the agent relayed.
 * They are **inert display data only**: never executed, never treated as
 * instructions, and [url] is opened in the system browser only (never an in-app
 * WebView of arbitrary HTML). Fetched article *bodies* are never stored — only
 * the user's own state plus the link/title/one-sentence rationale.
 */

/** Where the resource lives on the free, open web (for grouping + honesty). */
enum class LearningKind { VIDEO, ARTICLE, COURSE, DOCS, INTERACTIVE, OTHER }

/**
 * One-tap lifecycle + sentiment for a recommended resource. The taps the user
 * sees (started / finished / abandoned / loved / not-for-me) map 1:1 here. For
 * adaptation, LOVED counts as a strong positive finish and NOT_FOR_ME as a
 * strong negative abandon.
 */
enum class LearningStatus { RECOMMENDED, STARTED, FINISHED, ABANDONED, LOVED, NOT_FOR_ME }

/**
 * A single learning goal. The goal + why + level (asked once) + style (only if
 * volunteered) are the memory the recommendation loop filters against. Mirrored
 * to Hermes memory as the current focus, but kept here so the Learning view is
 * instant/offline and resources can attach to it.
 */
@Serializable
data class LearningGoal(
    val id: String,
    /** What the user wants to learn, in their words, e.g. "get good at Rust". */
    val topic: String,
    /** Their motivation, if given ("to build a CLI tool at work"). */
    val why: String? = null,
    /** Rough starting level — asked ONCE, then remembered: e.g. "beginner". */
    val level: String? = null,
    /** Preferred way to learn — recorded ONLY if the user volunteers it. */
    val style: String? = null,
    val createdAt: Long,
    val active: Boolean = true,
)

/**
 * A concrete free-open-web resource the agent recommended for a goal.
 *
 * 🔒 [title]/[url]/[source]/[why] are web-derived → inert display text (see file
 * header). [concept] lets adaptation notice "abandoned twice at the same concept".
 */
@Serializable
data class LearningResource(
    val id: String,
    val goalId: String,
    val title: String,
    val url: String,
    val source: String = "",
    val kind: LearningKind = LearningKind.OTHER,
    /** One honest sentence: why THIS, for the user, right now. Inert display text. */
    val why: String = "",
    /** Optional concept/topic tag the resource covers (for adaptation signals). */
    val concept: String? = null,
    val status: LearningStatus = LearningStatus.RECOMMENDED,
    val recommendedAt: Long,
    val updatedAt: Long,
)

/** The whole persisted learning state (one JSON blob, sealed at rest). */
@Serializable
data class LearningState(
    val goals: List<LearningGoal> = emptyList(),
    val resources: List<LearningResource> = emptyList(),
)
