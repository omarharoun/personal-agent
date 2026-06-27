package com.personalagent.shared.model

import kotlinx.serialization.Serializable

/**
 * A free-form note the user captures. The foundational unit the agent will
 * later reason over (Step 2+), but for Step 1 it is just user-owned text.
 *
 * Timestamps are epoch milliseconds (UTC). We deliberately avoid a date-time
 * dependency at this stage — a Long is unambiguous across all KMP targets.
 */
@Serializable
data class Note(
    val id: String,
    val title: String,
    val body: String,
    val createdAt: Long,
    val updatedAt: Long,
) {
    companion object {
        fun create(title: String, body: String, nowMillis: Long): Note = Note(
            id = Ids.next(nowMillis),
            title = title.trim(),
            body = body,
            createdAt = nowMillis,
            updatedAt = nowMillis,
        )
    }

    fun edited(title: String, body: String, nowMillis: Long): Note =
        copy(title = title.trim(), body = body, updatedAt = nowMillis)
}
