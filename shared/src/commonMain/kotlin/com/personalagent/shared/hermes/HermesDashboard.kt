package com.personalagent.shared.hermes

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Read-model wire types for the dashboard, verified live against Hermes v0.18.0:
 *  - GET /api/sessions      → rich per-session cost/token/activity feed
 *  - GET /health/detailed   → system status
 *  - GET /v1/toolsets       → the agent's capabilities (emoji-labelled)
 *  - GET /v1/skills         → installed skills (name/description/category)
 * All fields observed in the live payloads; unknown keys are ignored by the client.
 */

// --- /api/sessions ----------------------------------------------------------

@Serializable
data class HermesSessionsPage(
    val data: List<HermesSessionCard> = emptyList(),
    @SerialName("has_more") val hasMore: Boolean = false,
)

@Serializable
data class HermesSessionCard(
    val id: String,
    val source: String? = null,
    val model: String? = null,
    val title: String? = null,
    val preview: String? = null,
    @SerialName("message_count") val messageCount: Int = 0,
    @SerialName("tool_call_count") val toolCallCount: Int = 0,
    @SerialName("input_tokens") val inputTokens: Long = 0,
    @SerialName("output_tokens") val outputTokens: Long = 0,
    @SerialName("cache_read_tokens") val cacheReadTokens: Long = 0,
    @SerialName("reasoning_tokens") val reasoningTokens: Long = 0,
    @SerialName("estimated_cost_usd") val estimatedCostUsd: Double? = null,
    @SerialName("actual_cost_usd") val actualCostUsd: Double? = null,
    @SerialName("api_call_count") val apiCallCount: Int = 0,
    @SerialName("parent_session_id") val parentSessionId: String? = null,
    @SerialName("started_at") val startedAt: Double? = null,
    @SerialName("last_active") val lastActive: Double? = null,
) {
    /** Best human label for the session card. */
    val displayTitle: String
        get() = title?.takeIf { it.isNotBlank() }
            ?: preview?.takeIf { it.isNotBlank() }?.take(60)
            ?: id
    val totalTokens: Long get() = inputTokens + outputTokens
    val costUsd: Double? get() = actualCostUsd ?: estimatedCostUsd
    val lastActiveMillis: Long? get() = lastActive?.let { (it * 1000).toLong() }
    val isFork: Boolean get() = !parentSessionId.isNullOrBlank()
}

/** Aggregate usage across the session feed — for the summary card. */
data class UsageSummary(
    val sessionCount: Int,
    val totalTokens: Long,
    val totalToolCalls: Int,
    val totalCostUsd: Double,
    val costIsEstimated: Boolean,
) {
    companion object {
        fun from(sessions: List<HermesSessionCard>): UsageSummary {
            var tokens = 0L; var tools = 0; var cost = 0.0; var anyEstimated = false
            for (s in sessions) {
                tokens += s.totalTokens
                tools += s.toolCallCount
                s.costUsd?.let { cost += it }
                if (s.actualCostUsd == null && s.estimatedCostUsd != null) anyEstimated = true
            }
            return UsageSummary(sessions.size, tokens, tools, cost, anyEstimated)
        }
    }
}

// --- /health/detailed -------------------------------------------------------

@Serializable
data class HermesHealthDetailed(
    val status: String? = null,
    val version: String? = null,
    @SerialName("gateway_state") val gatewayState: String? = null,
    @SerialName("active_agents") val activeAgents: Int = 0,
    @SerialName("gateway_busy") val gatewayBusy: Boolean = false,
    val platforms: Map<String, HermesPlatformState> = emptyMap(),
) {
    val isOk: Boolean get() = status.equals("ok", ignoreCase = true)
}

@Serializable
data class HermesPlatformState(
    val state: String? = null,
    @SerialName("error_message") val errorMessage: String? = null,
)

// --- /v1/toolsets -----------------------------------------------------------

@Serializable
data class HermesToolsetsResponse(val data: List<HermesToolset> = emptyList())

@Serializable
data class HermesToolset(
    val name: String,
    val label: String = "",
    val description: String = "",
    val enabled: Boolean = false,
    val configured: Boolean = false,
    val tools: List<String> = emptyList(),
)

// --- /v1/skills -------------------------------------------------------------

@Serializable
data class HermesSkillsResponse(val data: List<HermesSkill> = emptyList())

@Serializable
data class HermesSkill(
    val name: String,
    val description: String = "",
    val category: String? = null,
)
