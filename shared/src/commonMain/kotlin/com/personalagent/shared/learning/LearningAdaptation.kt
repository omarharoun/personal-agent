package com.personalagent.shared.learning

/**
 * Phase 6 Step 3 — turn the tracked history of a goal into a short, honest
 * adaptation hint that [com.personalagent.shared.hermes.LearningPrompts.recommendNext]
 * folds into the next request. Pure + unit-tested; no I/O.
 *
 * Signals (all derived only from the user's OWN tracked state):
 *  - abandoned the same concept twice → address it differently,
 *  - finishes resources quickly → step the difficulty up,
 *  - engages most with video → weight video.
 */
object LearningAdaptation {

    /** A finish that lands within this window of the recommendation counts as "fast". */
    const val FAST_FINISH_MS: Long = 2L * 24 * 60 * 60 * 1000 // 2 days

    /** Positive = the user got value from it; negative = they bounced off it. */
    private fun LearningStatus.isPositive() = this == LearningStatus.FINISHED || this == LearningStatus.LOVED
    private fun LearningStatus.isNegative() = this == LearningStatus.ABANDONED || this == LearningStatus.NOT_FOR_ME

    fun hint(resources: List<LearningResource>): String? {
        val hints = mutableListOf<String>()

        // 1. Abandoned the same concept ≥2 times → approach differently.
        val abandonedConcepts = resources
            .filter { it.status.isNegative() }
            .mapNotNull { it.concept?.trim()?.lowercase()?.ifBlank { null } }
            .groupingBy { it }.eachCount()
        abandonedConcepts.filter { it.value >= 2 }.keys.forEach { concept ->
            hints += "the learner has stepped away from \"$concept\" more than once — approach it from a " +
                "different angle or with a more foundational, differently-formatted resource"
        }

        val positive = resources.filter { it.status.isPositive() }

        // 2. Engages most with video → weight video.
        if (positive.size >= 2) {
            val videos = positive.count { it.kind == LearningKind.VIDEO }
            if (videos >= 2 && videos * 2 >= positive.size) {
                hints += "the learner engages most with video — favor good short video resources"
            }
        }

        // 3. Finishes quickly → step up the difficulty.
        val fastFinishes = positive.count { r -> (r.updatedAt - r.recommendedAt) in 1 until FAST_FINISH_MS }
        if (positive.size >= 2 && fastFinishes >= 2) {
            hints += "the learner tends to finish resources quickly — you can step up the depth/difficulty"
        }

        return hints.takeIf { it.isNotEmpty() }?.joinToString("; ")
    }
}
