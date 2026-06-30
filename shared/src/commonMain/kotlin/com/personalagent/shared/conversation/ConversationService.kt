package com.personalagent.shared.conversation

import com.personalagent.shared.cache.CachedUnderstanding
import com.personalagent.shared.cache.CloudUsageRecorder
import com.personalagent.shared.cache.NoOpCloudUsageRecorder
import com.personalagent.shared.cache.NoOpSemanticCache
import com.personalagent.shared.cache.SemanticCache
import com.personalagent.shared.cloud.CloudClient
import com.personalagent.shared.cloud.CloudMessage
import com.personalagent.shared.cloud.EscalationPolicy
import com.personalagent.shared.cloud.LocalOnlyEscalationPolicy
import com.personalagent.shared.cloud.PassthroughPayloadPrep
import com.personalagent.shared.cloud.PayloadPrep
import com.personalagent.shared.cloud.UnavailableCloudClient
import com.personalagent.shared.memory.MemoryService
import com.personalagent.shared.model.MemoryEntry
import com.personalagent.shared.model.MemoryKind
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * Orchestrates one everyday conversation turn, fully on-device:
 *
 *  1. **retrieve** — pull relevant past context via [MemoryService.retrieveContext].
 *  2. **build** — assemble a grounded prompt (persona + retrieved context + turn)
 *     with [PromptBuilder].
 *  3. **generate** — call the local [OnDeviceLlm].
 *  4. **record** — write the turn back into memory so it informs future turns.
 *
 * Step 4 adds **cloud escalation**: the decision point ([shouldEscalate]) now
 * consults an injected [EscalationPolicy]. When it says LOCAL (the default), the
 * turn is answered on-device exactly as in Step 3. When it says ESCALATE, the turn
 * is anonymized ([PayloadPrep.prepare]), sent to a [CloudClient], and the answer
 * rehydrated locally ([PayloadPrep.rehydrate]) before being recorded + returned.
 *
 * Step 6 adds the **semantic cache of understanding**, consulted BEFORE the
 * escalation decision ([semanticCache]). If the cache has a strong hit for the
 * turn, that accumulated understanding GROUNDS a LOCAL answer and the turn is NOT
 * escalated — the cache short-circuits the cloud. Only on a cache MISS does the
 * [escalationPolicy] get to send the turn to the cloud. As understanding
 * accumulates, more turns are served locally, so cloud usage falls with use while
 * personalization deepens.
 *
 * Safe defaults keep this class local-only and network-free: a
 * [LocalOnlyEscalationPolicy] (never escalates), a [PassthroughPayloadPrep] (no-op
 * prep, for wiring only), an [UnavailableCloudClient] (throws if ever reached), and
 * a [NoOpSemanticCache] (always misses, so routing is byte-for-byte Step-4 behaviour
 * until a real cache is injected). So with defaults it stays fully unit-testable
 * with a [FakeOnDeviceLlm] and the real [MemoryService], with no network.
 *
 * 🤝 SHARED CONTRACT — constructor shape `(llm, memory)` is fixed. The remaining
 * parameters are appended with defaults so production can call
 * `ConversationService(llm, memory)` unchanged while tests tune them.
 *
 * @param llm the on-device model that produces the reply.
 * @param memory the Step-2 long-term memory engine (retrieval + recording).
 * @param promptBuilder assembles the grounded prompt (overridable for tests/persona).
 * @param contextTopK how many memories to retrieve and fold into the prompt.
 * @param options decoding options passed to the model (and cloud) on every turn.
 * @param escalationPolicy decides local-vs-cloud per turn (default: never escalate).
 * @param payloadPrep anonymizes before escalating + rehydrates the answer
 *   (default: passthrough — NOT the production anonymizer).
 * @param cloudClient remote model used on escalation (default: unavailable/throws).
 * @param semanticCache accumulated understanding consulted before escalating; a
 *   strong hit keeps the turn local (default: no-op cache — always misses).
 * @param cloudUsageRecorder telemetry hook called once per turn — `recordLocal()`
 *   for an on-device/cache-hit turn, `recordCloud()` for an escalated turn — so the
 *   Step-6 property (cloud usage falls as the cache learns) is measurable (default:
 *   no-op).
 */
class ConversationService(
    private val llm: OnDeviceLlm,
    private val memory: MemoryService,
    private val promptBuilder: PromptBuilder = PromptBuilder(),
    private val contextTopK: Int = MemoryService.DEFAULT_TOP_K,
    private val options: GenOptions = GenOptions(),
    private val escalationPolicy: EscalationPolicy = LocalOnlyEscalationPolicy,
    private val payloadPrep: PayloadPrep = PassthroughPayloadPrep(),
    private val cloudClient: CloudClient = UnavailableCloudClient,
    private val semanticCache: SemanticCache = NoOpSemanticCache,
    private val cloudUsageRecorder: CloudUsageRecorder = NoOpCloudUsageRecorder,
) {

    /**
     * Answer one user turn and return the complete reply.
     *
     * Retrieves context, builds the grounded prompt, generates locally, then
     * records both the user turn and the assistant reply into memory. Blank input
     * is short-circuited (returns empty, records nothing).
     */
    suspend fun respond(
        userText: String,
        history: List<ConversationTurn> = emptyList(),
    ): String {
        val turn = userText.trim()
        if (turn.isEmpty()) return ""

        val recent = history.takeLast(HISTORY_WINDOW)
        val context = retrieve(turn)

        // Step 6: cache-before-cloud. A strong cache hit grounds a LOCAL answer and
        // short-circuits escalation; only a cache MISS may go to the cloud.
        val cached = cacheLookup(turn)

        val reply = if (cached.isEmpty() && routeToCloud(turn, context)) {
            cloudUsageRecorder.recordCloud()
            escalate(turn, context, recent)
        } else {
            // Local turn (on-device generation, incl. a semantic-cache hit). The
            // prompt is multi-turn ChatML (persona + memory + recent history + turn)
            // so the on-device model has short-term conversation memory.
            cloudUsageRecorder.recordLocal()
            val prompt = promptBuilder.buildChatMl(turn, ground(context, cached), recent)
            llm.generate(prompt, options)
        }

        record(turn, reply)
        return reply
    }

    /**
     * Streaming variant of [respond]: emits the reply in incremental chunks, then
     * records the turn once the stream completes. Concatenating every emitted
     * chunk yields the same logical reply as [respond]. Blank input emits nothing.
     *
     * Recording happens *inside* the flow, after the upstream finishes, so the
     * interaction is persisted exactly when a consumer fully collects the reply.
     */
    fun respondStream(
        userText: String,
        history: List<ConversationTurn> = emptyList(),
    ): Flow<String> = flow {
        val turn = userText.trim()
        if (turn.isEmpty()) return@flow

        val recent = history.takeLast(HISTORY_WINDOW)
        val context = retrieve(turn)

        // Step 6: same cache-before-cloud routing as respond(). A strong cache hit
        // keeps the turn local (grounded), so only a cache miss can escalate.
        val cached = cacheLookup(turn)

        if (cached.isEmpty() && routeToCloud(turn, context)) {
            cloudUsageRecorder.recordCloud()
            // Cloud completion is non-streaming; emit the rehydrated answer as a
            // single chunk so the stream contract still holds (concatenation == reply).
            val answer = escalate(turn, context, recent)
            if (answer.isNotEmpty()) emit(answer)
            record(turn, answer)
            return@flow
        }

        // Local turn (on-device streaming, incl. a semantic-cache hit).
        cloudUsageRecorder.recordLocal()
        val prompt = promptBuilder.buildChatMl(turn, ground(context, cached), recent)

        val full = StringBuilder()
        llm.generateStream(prompt, options).collect { chunk ->
            full.append(chunk)
            emit(chunk)
        }

        record(turn, full.toString())
    }

    /**
     * Step 4 escalation decision point — now delegates to the injected
     * [escalationPolicy]. With the default [LocalOnlyEscalationPolicy] this is
     * always `false`, preserving Step 3's local-only behaviour. The policy sees the
     * turn plus the retrieved context as plain strings (it must not need anything
     * off-device to decide).
     */
    fun shouldEscalate(userText: String, context: List<MemoryEntry>): Boolean =
        escalationPolicy.shouldEscalate(userText, context.map { it.content })

    /**
     * The actual routing gate: send this turn to the cloud when EITHER the
     * escalation policy asks for it, OR there is **no usable on-device model**.
     *
     * The second clause is the fix for the device bug where the app never
     * answered: a user who set a cloud API key but installed no local `.task`
     * model would, for a normal short question, fail [shouldEscalate] (the
     * heuristic only fires on "think hard"/long-complex turns) and get routed to
     * `llm.generate` on an absent model — which throws/hangs. By routing to the
     * cloud whenever the local model is unavailable, a key-holder gets answered
     * for EVERY question. If the cloud is ALSO unconfigured, [escalate] →
     * [cloudClient.complete] throws a clear "no provider/key" error that the UI
     * renders, rather than a silent stall.
     */
    private fun routeToCloud(userText: String, context: List<MemoryEntry>): Boolean =
        shouldEscalate(userText, context) || !llm.isAvailable

    /**
     * Escalate one turn off-device: **anonymize → cloud → rehydrate**, strictly in
     * that order. The [CloudClient] only ever receives [PreparedPayload.anonymizedText]
     * — never raw user text and never the on-device [RehydrationMap].
     */
    private suspend fun escalate(
        turn: String,
        context: List<MemoryEntry>,
        history: List<ConversationTurn>,
    ): String {
        // Anonymize the WHOLE conversation (recent history + the current turn)
        // through ONE shared map, so an entity tokenizes consistently across turns.
        // The persona is sent as the cloud `system` prompt. On-device memory/notes
        // are NOT sent to the cloud — they are used only as anonymizer hints (the
        // existing privacy posture); they ground the LOCAL prompt only.
        val turns = history + ConversationTurn(ChatRole.USER, turn)
        val prepared = payloadPrep.prepareConversation(turns, context.map { it.content })
        val messages = prepared.messages.map { CloudMessage(it.role, it.text) }
        val cloudAnswer = cloudClient.completeConversation(
            messages = messages,
            system = promptBuilder.persona,
            options = options,
        )
        return payloadPrep.rehydrate(cloudAnswer, prepared.mapping)
    }

    /**
     * Retrieve grounding context — **best-effort**. Memory retrieval needs the
     * on-device embedding model (`all-MiniLM-L6-v2`); if that model is absent or
     * the embedder throws, we return NO context and continue. Embeddings are an
     * enhancement, never a prerequisite — they must never block a reply (the device
     * bug: a missing embedding model aborted the whole turn, including the cloud
     * path, for a user who only had an API key).
     */
    private suspend fun retrieve(turn: String): List<MemoryEntry> =
        try {
            memory.retrieveContext(turn, contextTopK)
        } catch (c: CancellationException) {
            throw c
        } catch (t: Throwable) {
            emptyList()
        }

    /** Semantic-cache lookup — **best-effort** for the same reason as [retrieve]
     *  (the real cache embeds the query). A miss/failure just means no cache hit. */
    private suspend fun cacheLookup(turn: String): List<CachedUnderstanding> =
        try {
            semanticCache.lookup(turn)
        } catch (c: CancellationException) {
            throw c
        } catch (t: Throwable) {
            emptyList()
        }

    /**
     * Fold cached [understandings] into the grounding context, best-first, ahead of
     * the retrieved [context]. Each understanding becomes a [MemoryKind.FACT] entry
     * so the prompt builder presents it as established knowledge the local model
     * should answer from — this is how a cache hit grounds the LOCAL reply instead
     * of going to the cloud. With an empty hit (the default no-op cache) this is a
     * no-op and the prompt is identical to Step 4.
     */
    private fun ground(
        context: List<MemoryEntry>,
        understandings: List<CachedUnderstanding>,
    ): List<MemoryEntry> {
        if (understandings.isEmpty()) return context
        val grounded = understandings.map { u ->
            MemoryEntry(
                id = u.id,
                content = u.summary,
                kind = MemoryKind.FACT,
                source = SOURCE_CACHE,
                createdAt = u.updatedAt,
                embedding = u.embedding.toList(),
            )
        }
        return grounded + context
    }

    /**
     * Persist the turn so it informs later retrievals — **best-effort**. Recording
     * embeds the text (same embedding model as [retrieve]); if that's unavailable we
     * skip persistence silently rather than fail a reply the user already received.
     */
    private suspend fun record(userText: String, reply: String) {
        recordSafely(userText, SOURCE_USER)
        if (reply.isNotBlank()) recordSafely(reply, SOURCE_ASSISTANT)
    }

    private suspend fun recordSafely(text: String, source: String) {
        try {
            memory.recordInteraction(text, source = source, kind = MemoryKind.EVENT)
        } catch (c: CancellationException) {
            throw c
        } catch (t: Throwable) {
            // Embedding model absent / embed failed — skip persistence, keep the reply.
        }
    }

    companion object {
        const val SOURCE_USER = "user"
        const val SOURCE_ASSISTANT = "assistant"
        const val SOURCE_CACHE = "cache"

        /**
         * How many recent prior turns of the active chat to include for short-term
         * conversation memory (≈5 exchanges). Bounded so the prompt fits small
         * on-device models' context; the caller may pass more — we keep the last N.
         */
        const val HISTORY_WINDOW = 10
    }
}
