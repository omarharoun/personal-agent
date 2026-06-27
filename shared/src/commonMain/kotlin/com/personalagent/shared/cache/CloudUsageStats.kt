package com.personalagent.shared.cache

/**
 * The hook the routing decision point calls to report how a turn was served.
 *
 * 🤝 SHARED CONTRACT — Step 6. The sibling's `ConversationService` routing (cache
 * short-circuit / local vs cloud) is wired to call exactly one of these per turn;
 * the coordinator connects a concrete [CloudUsageStats] in production and the
 * default [NoOpCloudUsageRecorder] keeps the service network-free and zero-cost
 * when telemetry isn't wanted.
 */
interface CloudUsageRecorder {
    /** A turn that was answered on-device (including a semantic-cache hit). */
    fun recordLocal()

    /** A turn that was escalated to the cloud. */
    fun recordCloud()
}

/** No-op recorder: the safe default so telemetry is strictly opt-in. */
object NoOpCloudUsageRecorder : CloudUsageRecorder {
    override fun recordLocal() {}
    override fun recordCloud() {}
}

/**
 * An immutable point-in-time readout of [CloudUsageStats].
 *
 * @param localTurns turns served on-device so far.
 * @param cloudTurns turns escalated to the cloud so far.
 */
data class CloudUsageSnapshot(
    val localTurns: Int,
    val cloudTurns: Int,
) {
    /** Total turns observed. */
    val totalTurns: Int get() = localTurns + cloudTurns

    /** Fraction of turns that hit the cloud, in `[0f, 1f]`; `0f` before any turn. */
    val cloudRatio: Float
        get() = if (totalTurns == 0) 0f else cloudTurns.toFloat() / totalTurns

    /** Fraction of turns served locally, in `[0f, 1f]`; `0f` before any turn. */
    val localRatio: Float
        get() = if (totalTurns == 0) 0f else localTurns.toFloat() / totalTurns
}

/**
 * Tracks local-vs-cloud turns over a session so the Step-6 property — *cloud usage
 * falls as the cache learns* — is measurable and observable.
 *
 * Counts are cumulative; [snapshot] gives an immutable readout and the convenience
 * accessors expose the running totals + ratio. For windowed comparisons (e.g.
 * "cloud ratio in the first N turns vs the last N") read [cloudRatio] /
 * [snapshot] at the two points and compare.
 *
 * Intended to be called from the single routing path per turn; it keeps plain
 * counters rather than pulling in atomics, so a caller that fans turns across
 * threads should serialize the `record*` calls.
 */
class CloudUsageStats : CloudUsageRecorder {

    private var local = 0
    private var cloud = 0

    override fun recordLocal() {
        local++
    }

    override fun recordCloud() {
        cloud++
    }

    /** Turns served on-device so far. */
    val localTurns: Int get() = local

    /** Turns escalated to the cloud so far. */
    val cloudTurns: Int get() = cloud

    /** Total turns observed so far. */
    val totalTurns: Int get() = local + cloud

    /** Fraction of turns that hit the cloud, in `[0f, 1f]`; `0f` before any turn. */
    val cloudRatio: Float get() = snapshot().cloudRatio

    /** Immutable readout of the current counts. */
    fun snapshot(): CloudUsageSnapshot = CloudUsageSnapshot(local, cloud)

    /** Reset all counters to zero (e.g. to start a fresh measurement window). */
    fun reset() {
        local = 0
        cloud = 0
    }
}
