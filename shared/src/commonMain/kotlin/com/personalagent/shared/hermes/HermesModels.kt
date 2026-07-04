package com.personalagent.shared.hermes

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Wire models for the Hermes Agent OpenAI-compatible API server (v0.18.0).
 *
 * Verified live in Phase 0 (see `docs/PHASE0.md`): the app talks to
 * `POST /v1/chat/completions` (OpenAI Chat Completions shape, SSE streaming via
 * `chat.completion.chunk` deltas terminated by `data: [DONE]`), plus the
 * discovery endpoints `GET /health`, `/v1/capabilities`, `/v1/models`.
 *
 * The `model` field is **cosmetic** — the real model is set server-side in the
 * user's Hermes `config.yaml`. We send the id the server advertises (`hermes-agent`)
 * and never build a model picker (Phase 0 finding).
 */

// --- Chat completions request ------------------------------------------------

@Serializable
data class HermesChatRequest(
    val model: String,
    val messages: List<HermesWireMessage>,
    val stream: Boolean = false,
)

@Serializable
data class HermesWireMessage(
    val role: String,
    val content: String,
)

// --- Non-streaming response --------------------------------------------------

@Serializable
data class HermesChatResponse(
    val choices: List<HermesChoice> = emptyList(),
)

@Serializable
data class HermesChoice(
    val message: HermesWireMessage? = null,
    @SerialName("finish_reason") val finishReason: String? = null,
)

// --- Streaming chunk ---------------------------------------------------------

@Serializable
data class HermesChatChunk(
    val choices: List<HermesChunkChoice> = emptyList(),
)

@Serializable
data class HermesChunkChoice(
    val delta: HermesDelta? = null,
    @SerialName("finish_reason") val finishReason: String? = null,
)

@Serializable
data class HermesDelta(
    val role: String? = null,
    val content: String? = null,
)

// --- Discovery ---------------------------------------------------------------

@Serializable
data class HermesHealth(
    val status: String? = null,
    val platform: String? = null,
    val version: String? = null,
)

@Serializable
data class HermesModelsList(
    val data: List<HermesModel> = emptyList(),
)

@Serializable
data class HermesModel(
    val id: String,
    @SerialName("owned_by") val ownedBy: String? = null,
)

/**
 * A trimmed view of `GET /v1/capabilities`. We only read the few fields the app
 * actually acts on; `ignoreUnknownKeys` drops the rest so a newer Hermes adding
 * fields never breaks the client.
 */
@Serializable
data class HermesCapabilities(
    val platform: String? = null,
    val model: String? = null,
    val features: HermesFeatures = HermesFeatures(),
)

@Serializable
data class HermesFeatures(
    @SerialName("chat_completions") val chatCompletions: Boolean = false,
    @SerialName("chat_completions_streaming") val chatCompletionsStreaming: Boolean = false,
    @SerialName("session_key_header") val sessionKeyHeader: String? = null,
    @SerialName("session_continuity_header") val sessionContinuityHeader: String? = null,
)

// --- Shared error envelope (OpenAI-shaped: {"error":{type,message}}) ----------

@Serializable
data class HermesErrorEnvelope(
    val error: HermesErrorDetail? = null,
)

@Serializable
data class HermesErrorDetail(
    val type: String? = null,
    val message: String? = null,
)
