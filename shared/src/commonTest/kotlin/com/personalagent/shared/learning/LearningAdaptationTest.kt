package com.personalagent.shared.learning

import kotlin.test.Test
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LearningAdaptationTest {

    private fun res(
        id: String,
        status: LearningStatus,
        kind: LearningKind = LearningKind.OTHER,
        concept: String? = null,
        recommendedAt: Long = 0L,
        updatedAt: Long = 0L,
    ) = LearningResource(
        id = id, goalId = "g1", title = "T$id", url = "https://x.org/$id",
        kind = kind, concept = concept, status = status,
        recommendedAt = recommendedAt, updatedAt = updatedAt,
    )

    @Test
    fun no_signal_returns_null() {
        assertNull(LearningAdaptation.hint(emptyList()))
        assertNull(LearningAdaptation.hint(listOf(res("a", LearningStatus.RECOMMENDED))))
    }

    @Test
    fun abandoning_a_concept_twice_flags_it() {
        val hint = LearningAdaptation.hint(listOf(
            res("a", LearningStatus.ABANDONED, concept = "Ownership"),
            res("b", LearningStatus.NOT_FOR_ME, concept = "ownership"),
        ))!!
        assertTrue(hint.contains("ownership"))
        assertTrue(hint.contains("different"))
    }

    @Test
    fun video_preference_is_detected() {
        val hint = LearningAdaptation.hint(listOf(
            res("a", LearningStatus.FINISHED, kind = LearningKind.VIDEO),
            res("b", LearningStatus.LOVED, kind = LearningKind.VIDEO),
            res("c", LearningStatus.FINISHED, kind = LearningKind.ARTICLE),
        ))!!
        assertTrue(hint.contains("video"))
    }

    @Test
    fun fast_finishing_suggests_stepping_up() {
        val gap = 60L * 60 * 1000 // 1 hour < FAST_FINISH_MS
        val hint = LearningAdaptation.hint(listOf(
            res("a", LearningStatus.FINISHED, recommendedAt = 0L, updatedAt = gap),
            res("b", LearningStatus.FINISHED, recommendedAt = 0L, updatedAt = gap),
        ))!!
        assertTrue(hint.contains("step up") || hint.contains("difficulty") || hint.contains("depth"))
    }

    @Test
    fun status_text_covers_all_taps() {
        LearningStatusText.TAP_OPTIONS.forEach {
            assertTrue(LearningStatusText.label(it).isNotBlank())
            assertTrue(LearningStatusText.memoryPhrase(it).isNotBlank())
        }
    }
}
