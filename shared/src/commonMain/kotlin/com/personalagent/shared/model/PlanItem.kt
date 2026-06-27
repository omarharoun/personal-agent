package com.personalagent.shared.model

import kotlinx.serialization.Serializable

/**
 * A single item in the user's plan (a lightweight task/agenda entry).
 * The simple planning view in Step 1 lists these; later steps let the agent
 * propose and re-order them. [order] gives a stable manual sort.
 */
@Serializable
data class PlanItem(
    val id: String,
    val title: String,
    val done: Boolean = false,
    val dueAtMillis: Long? = null,
    val order: Int = 0,
    val createdAt: Long,
) {
    companion object {
        fun create(title: String, nowMillis: Long, dueAtMillis: Long? = null, order: Int = 0): PlanItem =
            PlanItem(
                id = Ids.next(nowMillis),
                title = title.trim(),
                done = false,
                dueAtMillis = dueAtMillis,
                order = order,
                createdAt = nowMillis,
            )
    }

    fun toggled(): PlanItem = copy(done = !done)
}
