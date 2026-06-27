package com.personalagent.shared.model

import kotlinx.serialization.Serializable

/**
 * A unit of the agent's long-term memory about the user.
 *
 * In Step 1 this is a plain typed record with no intelligence attached — we
 * define it now so the storage layer and tests are stable. Later steps wire
 * the real behaviour:
 *   - Step 2/3: the agent writes/reads these as durable memory.
 *   - tech-from-measurement: [embedding] is populated by an on-device
 *     embedding model and indexed in a vector store. That model + index choice
 *     is DEFERRED — not decided or built here. The field is nullable and
 *     unused for now so the schema doesn't churn when it lands.
 */
@Serializable
data class MemoryEntry(
    val id: String,
    val content: String,
    val kind: MemoryKind = MemoryKind.FACT,
    val source: String = "user",
    val createdAt: Long,
    // DEFERRED (later step): vector embedding for semantic recall. Null until
    // an on-device embedding model is chosen. Do not rely on this in Step 1.
    val embedding: List<Float>? = null,
) {
    companion object {
        fun create(
            content: String,
            nowMillis: Long,
            kind: MemoryKind = MemoryKind.FACT,
            source: String = "user",
        ): MemoryEntry = MemoryEntry(
            id = Ids.next(nowMillis),
            content = content.trim(),
            kind = kind,
            source = source,
            createdAt = nowMillis,
        )
    }
}

enum class MemoryKind {
    FACT,        // a stable fact about the user ("prefers metric units")
    PREFERENCE,  // an explicit preference the user stated
    EVENT,       // something that happened ("met with Sam on the 3rd")
}
