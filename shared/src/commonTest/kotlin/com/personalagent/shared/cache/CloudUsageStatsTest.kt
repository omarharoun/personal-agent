package com.personalagent.shared.cache

import com.personalagent.shared.conversation.FakeOnDeviceLlm
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CloudUsageStatsTest {

    @Test
    fun countsRatiosSnapshotAndReset() {
        val stats = CloudUsageStats()

        // Empty: no division-by-zero, ratios are 0.
        assertEquals(0, stats.totalTurns)
        assertEquals(0.0, stats.cloudRatio.toDouble(), 1e-9)

        stats.recordCloud()
        stats.recordLocal()
        stats.recordLocal()

        assertEquals(1, stats.cloudTurns)
        assertEquals(2, stats.localTurns)
        assertEquals(3, stats.totalTurns)
        assertEquals(1.0 / 3.0, stats.cloudRatio.toDouble(), 1e-6)

        val snap = stats.snapshot()
        assertEquals(1, snap.cloudTurns)
        assertEquals(2, snap.localTurns)
        assertEquals(3, snap.totalTurns)
        assertEquals(2.0 / 3.0, snap.localRatio.toDouble(), 1e-6)

        stats.reset()
        assertEquals(0, stats.totalTurns)
        assertEquals(0.0, stats.cloudRatio.toDouble(), 1e-9)
    }

    @Test
    fun noOpRecorderIsInert() {
        // The default recorder must accept calls and keep no state.
        NoOpCloudUsageRecorder.recordCloud()
        NoOpCloudUsageRecorder.recordLocal()
        // Nothing to assert beyond "doesn't throw" — it intentionally holds no counts.
    }

    /**
     * The Step-6 property end-to-end: as the cache learns understanding, the same
     * questions get served locally and **cloud usage falls across the session**.
     *
     * A tiny router models the sibling's routing decision: lookup the cache first;
     * a hit is served locally ([CloudUsageStats.recordLocal]); a miss escalates to
     * the cloud ([CloudUsageStats.recordCloud]) and the interaction is distilled
     * into understanding for next time.
     */
    @Test
    fun cloudUsageFallsAsTheCacheLearns() = runTest {
        val stats = CloudUsageStats()
        val cache = FakeSemanticCache()
        // Distills each turn into understanding that contains the turn's own words,
        // so repeating the same question later scores a cache hit.
        val llm = FakeOnDeviceLlm(
            respondWith = { prompt, _ ->
                val turn = userTurnFrom(prompt)
                "TOPIC: $turn\nSUMMARY: Durable facts about: $turn"
            },
        )
        val distiller = UnderstandingDistiller(llm)

        // The router: returns true if the turn had to hit the cloud.
        suspend fun handle(query: String, reply: String): Boolean {
            val hits = cache.lookup(query, topK = 3, minScore = 0.6f)
            return if (hits.isNotEmpty()) {
                stats.recordLocal()
                false
            } else {
                stats.recordCloud()
                distiller.distillInto(cache, query, reply)
                true
            }
        }

        val questions = listOf(
            "what is my favorite coffee order",
            "which gym schedule do I keep",
            "what programming language do I use at work",
        )

        // --- Cold phase: every question is new → all escalate to the cloud. ---
        for (q in questions) handle(q, reply = "some chatty reply for $q")
        val afterCold = stats.snapshot()
        assertEquals(3, afterCold.cloudTurns)
        assertEquals(0, afterCold.localTurns)
        assertEquals(1.0, afterCold.cloudRatio.toDouble(), 1e-9) // 100% cloud while cold

        // Understanding was cached for each distinct topic.
        assertEquals(3, cache.stored.size)

        // --- Warm phase: ask the same questions repeatedly → cache hits, no cloud. ---
        repeat(3) {
            for (q in questions) handle(q, reply = "ignored — should be served locally")
        }
        val afterWarm = stats.snapshot()

        // No new cloud calls happened during the warm phase.
        assertEquals(afterCold.cloudTurns, afterWarm.cloudTurns, "warm turns never hit the cloud")
        assertEquals(9, afterWarm.localTurns - afterCold.localTurns)

        // The property: cumulative cloud ratio fell as the cache learned.
        assertTrue(
            afterWarm.cloudRatio < afterCold.cloudRatio,
            "cloud ratio must fall with use (cold=${afterCold.cloudRatio}, warm=${afterWarm.cloudRatio})",
        )
        // 3 cloud / 12 total = 0.25, and local now dominates.
        assertEquals(0.25, afterWarm.cloudRatio.toDouble(), 1e-6)
        assertTrue(afterWarm.localTurns > afterWarm.cloudTurns)
    }

    /** Pull the user turn back out of the distiller's prompt (test-only). */
    private fun userTurnFrom(prompt: String): String {
        val lines = prompt.lines()
        val idx = lines.indexOfFirst { it.trim() == "USER TURN:" }
        return if (idx >= 0 && idx + 1 < lines.size) lines[idx + 1].trim() else ""
    }
}
