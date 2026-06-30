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
    // Insertion-ordered so rehydration is deterministic and debuggable on-device.
    private val tokenToReal = LinkedHashMap<String, String>()
    // Reverse index so the anonymizer can reuse one token per real entity.
    private val realToToken = HashMap<String, String>()

    /** Record that [token] (which appears in the anonymized text) stands for [realValue]. */
    fun put(token: String, realValue: String): RehydrationMap {
        record(token, realValue)
        return this
    }

    /** The real value previously stored for [token], or null if none. */
    fun lookup(token: String): String? = tokenToReal[token]

    /** How many token→value pairs are held. (Count only — never the values.) */
    val size: Int get() = tokenToReal.size

    /** True when nothing was stripped (e.g. the passthrough prep). */
    fun isEmpty(): Boolean = tokenToReal.isEmpty()

    /**
     * Record a token→real-value pair (and its reverse). `internal` so only the
     * in-module anonymizer ([DefaultPayloadPrep]) writes entries; off-device
     * transport code in other modules cannot.
     */
    internal fun record(token: String, real: String) {
        tokenToReal[token] = real
        realToToken[real] = token
    }

    /** Existing token for an already-seen real value, so the same entity reuses it. */
    internal fun tokenForReal(real: String): String? = realToToken[real]

    /** The real value previously stored for [token], or null. (Module-internal.) */
    internal fun realForToken(token: String): String? = tokenToReal[token]

    /** Tokens longest-first, so `<PERSON_1>` never partially shadows `<PERSON_11>`. */
    internal fun tokensLongestFirst(): List<String> =
        tokenToReal.keys.sortedByDescending { it.length }

    /**
     * Rehydrate [text] by replacing every stripped token with its real value.
     * `internal` so rehydration stays on-device and inside this module — callers
     * (e.g. [PayloadPrep.rehydrate]) never read the plaintext values directly.
     * Longest-token-first so `<PERSON_1>` can't partially shadow `<PERSON_11>`.
     */
    internal fun rehydrate(text: String): String {
        var out = text
        for (token in tokensLongestFirst()) out = out.replace(token, tokenToReal.getValue(token))
        return out
    }

    /** Redacted — never leaks the private values it holds. */
    override fun toString(): String = "RehydrationMap(size=$size, contents=REDACTED)"
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
 * A whole anonymized conversation safe to send to a [CloudClient], paired with the
 * SINGLE on-device [mapping] shared across every turn — so the same real entity
 * maps to the same token everywhere (turn 1's `<PERSON_1>` is the same person in
 * turn 5). Only [messages] are transmitted; [mapping] stays on-device and
 * rehydrates the reply.
 */
data class PreparedConversation(
    val messages: List<com.personalagent.shared.conversation.ConversationTurn>,
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

    /**
     * Prepare a whole multi-turn conversation for the cloud. EVERY turn (user AND
     * assistant) is anonymized through ONE shared [RehydrationMap] so an entity is
     * tokenized consistently across turns. Returns the anonymized turns (to send as
     * a messages array) + the shared map (to rehydrate the reply locally).
     */
    fun prepareConversation(
        turns: List<com.personalagent.shared.conversation.ConversationTurn>,
        contextHints: List<String> = emptyList(),
    ): PreparedConversation

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

    override fun prepareConversation(
        turns: List<com.personalagent.shared.conversation.ConversationTurn>,
        contextHints: List<String>,
    ): PreparedConversation = PreparedConversation(messages = turns, mapping = RehydrationMap())

    override fun rehydrate(cloudAnswer: String, mapping: RehydrationMap): String =
        mapping.rehydrate(cloudAnswer)
}
