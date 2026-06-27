package com.personalagent.shared.cloud

/**
 * 🤝 SHARED CONTRACT — cloud-escalation payload preparation (Step 4).
 *
 * ⚠️ OWNERSHIP NOTE FOR THE COORDINATOR:
 * The sibling branch `feat/step4-shared` is the **owner** of this contract file
 * (it ships a PASSTHROUGH [PayloadPrep] for wiring). This copy exists ONLY so the
 * `feat/step4-anonymizer` branch compiles and tests in isolation. At merge time,
 * keep ONE definition of these types (the sibling's) and drop this duplicate —
 * the public API below is byte-for-byte the agreed shape, so reconciliation is
 * trivial. The REAL production implementation lives next to this file in
 * [DefaultPayloadPrep]; wire THAT as the production [PayloadPrep].
 *
 * ---
 *
 * Cloud escalation (Step 4) sends a question to a remote model only when the
 * on-device model can't answer it. Before anything leaves the device it passes
 * through a [PayloadPrep], whose job is to make the outbound text reveal as
 * little about the user as possible while still being answerable.
 *
 * Two defenses, in priority order:
 *   1. **MINIMIZE** (primary)  — send as little as possible: reduce the payload
 *      to the *shape of the question*, dropping detail the cloud doesn't need.
 *   2. **ANONYMIZE** (secondary) — replace identifying specifics that remain with
 *      stable placeholder tokens, recording the placeholder→real mapping in a
 *      [RehydrationMap] that NEVER leaves the device.
 *
 * The cloud answers in terms of the tokens; [rehydrate] restores the real values
 * locally so the user sees a normal answer.
 */
interface PayloadPrep {
    /**
     * Prepare [text] for cloud escalation.
     *
     * @param text the raw on-device text we would otherwise send verbatim.
     * @param contextHints optional, **on-device** known-sensitive literals (e.g.
     *   the user's name / city / employer pulled from local memory) that the
     *   caller wants guaranteed-tokenized even if the heuristics would miss them.
     *   Hints are authoritative: anything listed here is always scrubbed.
     * @return the minimized + anonymized text together with the on-device-only
     *   [RehydrationMap] needed to undo the anonymization in [rehydrate].
     */
    fun prepare(text: String, contextHints: List<String> = emptyList()): PreparedPayload

    /**
     * Restore the real values into a cloud answer, on-device, using [mapping].
     * The [mapping] must be the exact one returned alongside the payload that
     * produced [cloudAnswer]; it never travels with the request/response.
     */
    fun rehydrate(cloudAnswer: String, mapping: RehydrationMap): String
}

/**
 * The result of [PayloadPrep.prepare]: the text that is safe(r) to send, plus the
 * on-device-only [mapping] that can undo the anonymization once the cloud replies.
 *
 * Only [anonymizedText] is ever transmitted. [mapping] stays on the device.
 */
data class PreparedPayload(
    val anonymizedText: String,
    val mapping: RehydrationMap,
)

/**
 * Placeholder-token → real-value mapping. **ON-DEVICE ONLY.**
 *
 * 🔒 INVARIANT — this object must NEVER be serialized off-device. It is the
 * decryption key for the anonymization: if it ever travels alongside the
 * anonymized payload, the whole privacy guarantee is void.
 *
 * Enforcement (defense in depth — none is a substitute for code review):
 *  - It is deliberately **NOT** `@Serializable` (kotlinx.serialization will refuse
 *    to encode it; there is no generated serializer).
 *  - Its contents are `private`; the only accessors are `internal`, so off-device
 *    transport code in other modules cannot read the pairs out.
 *  - [toString] is redacted, so it cannot leak into a log line or crash report.
 *
 * These are guardrails, not proofs — a reviewer must still confirm no code path
 * places a [RehydrationMap] (or its contents) into any outbound request body.
 */
class RehydrationMap {
    // Insertion-ordered so rehydration is deterministic and debuggable on-device.
    private val tokenToReal = LinkedHashMap<String, String>()
    private val realToToken = HashMap<String, String>()

    internal fun record(token: String, real: String) {
        tokenToReal[token] = real
        realToToken[real] = token
    }

    /** Existing token for an already-seen real value, so the same entity reuses it. */
    internal fun tokenForReal(real: String): String? = realToToken[real]

    internal fun realForToken(token: String): String? = tokenToReal[token]

    /** Tokens longest-first, so `<PERSON_1>` never partially shadows `<PERSON_11>`. */
    internal fun tokensLongestFirst(): List<String> =
        tokenToReal.keys.sortedByDescending { it.length }

    /** Number of distinct entities recorded. Safe to expose; reveals no values. */
    val size: Int get() = tokenToReal.size

    /** Redacted on purpose — never print the real values. */
    override fun toString(): String =
        "RehydrationMap(size=$size, contents REDACTED — on-device only, never serialized)"
}
