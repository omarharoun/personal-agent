package com.personalagent.shared.cloud

/**
 * On-device mapping of **stripped token → real value** used to put identifying
 * detail back into a cloud answer after it returns.
 *
 * ⛔️ HARD RULE — ON-DEVICE ONLY. A [RehydrationMap] holds the *plaintext* private
 * values that were stripped out before anything left the device. It therefore
 * **must never be serialized, logged, sent to a [CloudClient], or persisted
 * off-device.** It exists only to rehydrate an answer locally and should be
 * discarded once the turn completes.
 *
 * Enforcement (defence in depth):
 *   - the backing map is **private**; no accessor exposes the values in bulk, so
 *     it cannot be trivially dumped or handed to a serializer;
 *   - the class is deliberately **not** `@Serializable` (kotlinx.serialization
 *     will refuse it) and exposes no DTO/`toMap()` form;
 *   - [toString] is **redacted** so it can never leak values into logs;
 *   - rehydration happens *inside* the class ([rehydrate], `internal`) so callers
 *     never need to read the values out.
 */
class RehydrationMap {
    // The only place plaintext private values live. Never widened to public.
    private val map = LinkedHashMap<String, String>()

    /** Record that [token] (which appears in the anonymized text) stands for [realValue]. */
    fun put(token: String, realValue: String): RehydrationMap {
        map[token] = realValue
        return this
    }

    /** The real value previously stored for [token], or null if none. */
    fun lookup(token: String): String? = map[token]

    /** How many token→value pairs are held. (Count only — never the values.) */
    val size: Int get() = map.size

    /** True when nothing was stripped (e.g. the passthrough prep). */
    fun isEmpty(): Boolean = map.isEmpty()

    /**
     * Rehydrate [text] by replacing every stripped token with its real value.
     * `internal` so rehydration stays on-device and inside this module — callers
     * (e.g. [PayloadPrep.rehydrate]) never read the plaintext values directly.
     */
    internal fun rehydrate(text: String): String {
        var out = text
        for ((token, real) in map) out = out.replace(token, real)
        return out
    }

    /** Redacted — never leaks the private values it holds. */
    override fun toString(): String = "RehydrationMap(size=${map.size}, contents=REDACTED)"
}

/**
 * The anonymized text that is safe to send to a [CloudClient], paired with the
 * on-device [mapping] needed to rehydrate the answer.
 *
 * Only [anonymizedText] is ever transmitted; [mapping] stays on-device.
 */
data class PreparedPayload(
    val anonymizedText: String,
    val mapping: RehydrationMap,
)

/**
 * Strips identifying detail out of text **before** it leaves the device and puts
 * it back into the cloud answer **after** it returns.
 *
 * 🤝 SHARED CONTRACT — the real implementation is provided by sibling
 * `feat/step4-anonymizer`. This slice ships only [PassthroughPayloadPrep] for
 * wiring/tests.
 */
interface PayloadPrep {
    /**
     * Produce a cloud-safe payload from [text]. [contextHints] (e.g. retrieved
     * memory snippets) may help the real anonymizer detect what to strip.
     */
    fun prepare(text: String, contextHints: List<String> = emptyList()): PreparedPayload

    /** Restore stripped detail in [cloudAnswer] using [mapping]. */
    fun rehydrate(cloudAnswer: String, mapping: RehydrationMap): String
}

/**
 * ⚠️ NOT the production anonymizer. A no-op [PayloadPrep] that returns text
 * **unchanged** with an **empty** mapping — for wiring and tests only. The real
 * anonymizer comes from sibling `feat/step4-anonymizer`; until it is wired in,
 * escalated text is **not** anonymized, so this must never be used on a path that
 * actually reaches a real [CloudClient] in production.
 *
 * [rehydrate] still applies whatever [mapping] it is given (identity for the
 * empty map), so it composes correctly once the real prep replaces it.
 */
class PassthroughPayloadPrep : PayloadPrep {
    override fun prepare(text: String, contextHints: List<String>): PreparedPayload =
        PreparedPayload(anonymizedText = text, mapping = RehydrationMap())

    override fun rehydrate(cloudAnswer: String, mapping: RehydrationMap): String =
        mapping.rehydrate(cloudAnswer)
}
