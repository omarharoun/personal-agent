package com.personalagent.shared.conversation

import com.personalagent.shared.memory.MemoryService
import com.personalagent.shared.model.MemoryEntry
import com.personalagent.shared.model.MemoryKind
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
 * Step 3 is **LOCAL-ONLY**: every turn is answered on-device. Cloud escalation is
 * Step 4 — the decision point exists here ([shouldEscalate]) but is a no-op stub
 * that always returns false, so this class needs no network and is fully unit-
 * testable with a [FakeOnDeviceLlm] and the real [MemoryService].
 *
 * 🤝 SHARED CONTRACT — constructor shape `(llm, memory)` is fixed. The remaining
 * parameters are appended with defaults so production can call
 * `ConversationService(llm, memory)` unchanged while tests tune them.
 *
 * @param llm the on-device model that produces the reply.
 * @param memory the Step-2 long-term memory engine (retrieval + recording).
 * @param promptBuilder assembles the grounded prompt (overridable for tests/persona).
 * @param contextTopK how many memories to retrieve and fold into the prompt.
 * @param options decoding options passed to the model on every turn.
 */
class ConversationService(
    private val llm: OnDeviceLlm,
    private val memory: MemoryService,
    private val promptBuilder: PromptBuilder = PromptBuilder(),
    private val contextTopK: Int = MemoryService.DEFAULT_TOP_K,
    private val options: GenOptions = GenOptions(),
) {

    /**
     * Answer one user turn and return the complete reply.
     *
     * Retrieves context, builds the grounded prompt, generates locally, then
     * records both the user turn and the assistant reply into memory. Blank input
     * is short-circuited (returns empty, records nothing).
     */
    suspend fun respond(userText: String): String {
        val turn = userText.trim()
        if (turn.isEmpty()) return ""

        val context = retrieve(turn)

        // Step 4 hook — local-only for now; see [shouldEscalate].
        check(!shouldEscalate(turn, context)) {
            "Cloud escalation is not implemented until Step 4"
        }

        val prompt = promptBuilder.build(turn, context)
        val reply = llm.generate(prompt, options)

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
    fun respondStream(userText: String): Flow<String> = flow {
        val turn = userText.trim()
        if (turn.isEmpty()) return@flow

        val context = retrieve(turn)
        check(!shouldEscalate(turn, context)) {
            "Cloud escalation is not implemented until Step 4"
        }

        val prompt = promptBuilder.build(turn, context)

        val full = StringBuilder()
        llm.generateStream(prompt, options).collect { chunk ->
            full.append(chunk)
            emit(chunk)
        }

        record(turn, full.toString())
    }

    /**
     * STUB — Step 4 escalation decision point. Always returns `false` in Step 3,
     * so every turn is answered locally.
     *
     * TODO(Step 4): replace this stub with the real local-vs-cloud routing —
     * e.g. escalate when the local model is unavailable ([OnDeviceLlm.isAvailable]
     * is false), when the request is out of the on-device model's depth, or on an
     * explicit user opt-in. Until then this is intentionally a no-op so Step 3
     * stays local-only and network-free.
     */
    @Suppress("UNUSED_PARAMETER")
    fun shouldEscalate(userText: String, context: List<MemoryEntry>): Boolean = false

    private suspend fun retrieve(turn: String): List<MemoryEntry> =
        memory.retrieveContext(turn, contextTopK)

    /** Persist the turn so it informs later retrievals. */
    private suspend fun record(userText: String, reply: String) {
        memory.recordInteraction(userText, source = SOURCE_USER, kind = MemoryKind.EVENT)
        if (reply.isNotBlank()) {
            memory.recordInteraction(reply, source = SOURCE_ASSISTANT, kind = MemoryKind.EVENT)
        }
    }

    companion object {
        const val SOURCE_USER = "user"
        const val SOURCE_ASSISTANT = "assistant"
    }
}
