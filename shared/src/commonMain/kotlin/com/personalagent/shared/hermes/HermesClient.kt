package com.personalagent.shared.hermes

import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.headers
import io.ktor.client.request.post
import io.ktor.client.request.prepareGet
import io.ktor.client.request.preparePost
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsChannel
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import io.ktor.utils.io.readUTF8Line
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.json.Json

/**
 * Raised when a Hermes call cannot complete. The [message] is a short,
 * user-actionable summary (bad key, unreachable host, wrong URL) and carries
 * **no request/response payload** — nothing sensitive ever reaches a log or a
 * crash report through this type.
 */
class HermesException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * One event in a streamed chat reply.
 *  - [Delta]: an incremental chunk of assistant text to append to the transcript.
 *  - [Done]: the stream finished cleanly (`data: [DONE]`).
 */
sealed interface ChatStreamEvent {
    data class Delta(val text: String) : ChatStreamEvent
    data object Done : ChatStreamEvent
}

/**
 * The thin HTTP bridge to a user-owned **Hermes Agent** (OpenAI-compatible API
 * server, v0.18.0). Hermes is the brain — memory, skills, scheduling, model
 * routing all live server-side; this client only orchestrates and presents.
 *
 * Design notes:
 *  - **No Logging plugin** is installed — request/response bodies (which contain
 *    the user's conversation) must never be logged.
 *  - Plain `http://` is allowed (unlike the retired cloud client): a
 *    bring-your-own-Hermes typically runs on localhost or a LAN/VPN the user
 *    controls. The Connect screen warns on a plaintext *remote* host.
 *  - The [engine] is injected so tests drive it with Ktor's `MockEngine`.
 *
 * @param config the user-configured target + credentials (see [HermesConfig]).
 * @param sessionId optional transcript id (`X-Hermes-Session-Id`); pass a stable
 *   id per open conversation so the server threads short-term context, and a new
 *   id to start a fresh thread. When null the server mints one per request.
 */
class HermesClient(
    private val config: HermesConfig,
    engine: HttpClientEngine? = null,
    private val connectTimeoutMs: Long = 15_000,
    private val requestTimeoutMs: Long = 120_000,
    private val socketTimeoutMs: Long = 60_000,
) {
    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        encodeDefaults = true
        // Hermes returns explicit `null` for absent list/scalar fields (e.g.
        // `tool_calls: null`); coerce those to the property default (emptyList).
        coerceInputValues = true
    }

    private val configure: HttpClientConfig<*>.() -> Unit = {
        install(ContentNegotiation) { json(json) }
        install(HttpTimeout) {
            connectTimeoutMillis = connectTimeoutMs
            requestTimeoutMillis = requestTimeoutMs
            socketTimeoutMillis = socketTimeoutMs
        }
        expectSuccess = false // we map status → HermesException ourselves
    }

    private val client: HttpClient =
        if (engine != null) HttpClient(engine, configure) else HttpClient(configure)

    // --- Discovery ------------------------------------------------------------

    /**
     * `GET /health`. Used by the Connect screen to test reachability + auth.
     * Returns the parsed health body on 200; throws [HermesException] with a
     * plain-language reason otherwise (so the UI can show the user how to fix it).
     */
    suspend fun health(): HermesHealth {
        val res = try {
            client.get(config.health) { authHeaders() }
        } catch (e: HttpRequestTimeoutException) {
            throw HermesException("Your Hermes didn't respond in time. Is it running and reachable?", e)
        } catch (e: Throwable) {
            throw HermesException(unreachable(), e)
        }
        if (!res.status.isSuccess()) throw statusException(res)
        return try {
            json.decodeFromString(HermesHealth.serializer(), res.bodyAsText())
        } catch (e: Throwable) {
            // A 200 that isn't Hermes JSON usually means the URL points at the
            // wrong service (a router page, a proxy, etc.).
            throw HermesException("That URL answered, but it doesn't look like a Hermes API server. Double-check the address and port.", e)
        }
    }

    /** `GET /v1/capabilities`. */
    suspend fun capabilities(): HermesCapabilities {
        val res = getAuthed(config.capabilities)
        return json.decodeFromString(HermesCapabilities.serializer(), res)
    }

    /** `GET /v1/models`. */
    suspend fun models(): List<HermesModel> {
        val res = getAuthed(config.models)
        return json.decodeFromString(HermesModelsList.serializer(), res).data
    }

    // --- Chat -----------------------------------------------------------------

    /**
     * Stream a chat reply from `POST /v1/chat/completions` (SSE). Emits
     * [ChatStreamEvent.Delta] for each text chunk and finally [ChatStreamEvent.Done].
     *
     * The whole conversation is sent each call (the endpoint is stateless per
     * request; continuity + long-term memory come from the session headers). The
     * caller supplies [messages] already in wire form (system/user/assistant).
     */
    fun streamChat(
        messages: List<HermesWireMessage>,
        sessionId: String? = null,
    ): Flow<ChatStreamEvent> = flow {
        val request = HermesChatRequest(
            model = HermesConfig.DEFAULT_MODEL_ID,
            messages = messages,
            stream = true,
        )
        val statement = try {
            client.preparePost(config.chatCompletions) {
                authHeaders()
                contentType(ContentType.Application.Json)
                headers {
                    append(HttpHeaders.Accept, "text/event-stream")
                    if (!sessionId.isNullOrBlank()) append(SESSION_ID_HEADER, sessionId)
                }
                setBody(json.encodeToString(HermesChatRequest.serializer(), request))
            }
        } catch (e: Throwable) {
            throw HermesException(unreachable(), e)
        }

        statement.execute { response ->
            if (!response.status.isSuccess()) throw statusException(response)
            val channel = response.bodyAsChannel()
            var sawAnyContent = false
            while (true) {
                val line = try {
                    channel.readUTF8Line()
                } catch (e: HttpRequestTimeoutException) {
                    throw HermesException("The reply stalled midway. Check your connection to Hermes.", e)
                } ?: break
                if (line.isEmpty()) continue
                if (!line.startsWith("data:")) continue
                val payload = line.substringAfter("data:").trim()
                if (payload == "[DONE]") {
                    emit(ChatStreamEvent.Done)
                    return@execute
                }
                val delta = runCatching {
                    json.decodeFromString(HermesChatChunk.serializer(), payload)
                        .choices.firstOrNull()?.delta?.content
                }.getOrNull()
                if (!delta.isNullOrEmpty()) {
                    sawAnyContent = true
                    emit(ChatStreamEvent.Delta(delta))
                }
            }
            // Stream ended without an explicit [DONE] — still a clean finish as
            // long as we received something; otherwise surface it.
            if (!sawAnyContent) {
                throw HermesException("Hermes returned an empty reply. Check that its model provider is configured.")
            }
            emit(ChatStreamEvent.Done)
        }
    }

    /**
     * One-shot ask: a plain NON-STREAMING `POST /v1/chat/completions` that returns
     * the whole reply as a string. Used by reflection / notes / goals — the SSE
     * path is only for the live chat transcript. Doing this as a normal request
     * (not by draining an SSE stream) makes one-shot calls simple and reliable:
     * it can't get stuck mid-stream, and any failure maps to a clear [HermesException].
     */
    suspend fun complete(messages: List<HermesWireMessage>, sessionId: String? = null): String {
        val request = HermesChatRequest(
            model = HermesConfig.DEFAULT_MODEL_ID,
            messages = messages,
            stream = false,
        )
        val res = try {
            client.post(config.chatCompletions) {
                authHeaders()
                contentType(ContentType.Application.Json)
                if (!sessionId.isNullOrBlank()) headers { append(SESSION_ID_HEADER, sessionId) }
                setBody(json.encodeToString(HermesChatRequest.serializer(), request))
            }
        } catch (e: HttpRequestTimeoutException) {
            throw HermesException("Hermes took too long to respond. Try again in a moment.", e)
        } catch (e: Throwable) {
            throw HermesException(unreachable(), e)
        }
        if (!res.status.isSuccess()) throw statusException(res)
        val parsed = try {
            json.decodeFromString(HermesChatResponse.serializer(), res.bodyAsText())
        } catch (e: Throwable) {
            throw HermesException("Hermes returned an unexpected response.", e)
        }
        return parsed.choices.firstOrNull()?.message?.content?.trim()
            ?: throw HermesException("Hermes returned an empty reply. Check that its model provider is configured.")
    }

    // --- Dashboard read models -----------------------------------------------

    /** `GET /api/sessions` — the rich per-session activity/cost feed. */
    suspend fun sessions(): List<HermesSessionCard> {
        val res = getAuthed("${config.baseUrl}/api/sessions")
        return json.decodeFromString(HermesSessionsPage.serializer(), res).data
    }

    /** `GET /health/detailed` — system status for the status card. */
    suspend fun healthDetailed(): HermesHealthDetailed {
        val res = getAuthed("${config.baseUrl}/health/detailed")
        return json.decodeFromString(HermesHealthDetailed.serializer(), res)
    }

    /** `GET /v1/toolsets` — the agent's capabilities (emoji-labelled). */
    suspend fun toolsets(): List<HermesToolset> {
        val res = getAuthed("${config.baseUrl}/v1/toolsets")
        return json.decodeFromString(HermesToolsetsResponse.serializer(), res).data
    }

    /** `GET /v1/skills` — installed skills (name/description/category). */
    suspend fun skills(): List<HermesSkill> {
        val res = getAuthed("${config.baseUrl}/v1/skills")
        return json.decodeFromString(HermesSkillsResponse.serializer(), res).data
    }

    // --- Agent runs (/v1/runs) + hydration -----------------------------------

    /** `POST /v1/runs` — start an agent task; returns the run id immediately. */
    suspend fun startRun(input: String): HermesRunStarted {
        val res = try {
            client.post("${config.baseUrl}/v1/runs") {
                authHeaders()
                contentType(ContentType.Application.Json)
                setBody(json.encodeToString(HermesRunRequest.serializer(), HermesRunRequest(input)))
            }
        } catch (e: HttpRequestTimeoutException) {
            throw HermesException("Hermes took too long to start the task.", e)
        } catch (e: Throwable) {
            throw HermesException(unreachable(), e)
        }
        if (!res.status.isSuccess()) throw statusException(res)
        return json.decodeFromString(HermesRunStarted.serializer(), res.bodyAsText())
    }

    /**
     * `GET /v1/runs/{id}/events` (SSE). Emits [RunEvent]s live — tool starts,
     * completions, reasoning, final answer + usage. The session id for hydration
     * equals the run id (verified live).
     */
    fun runEvents(runId: String): Flow<RunEvent> = flow {
        val statement = try {
            client.prepareGet("${config.baseUrl}/v1/runs/$runId/events") {
                authHeaders()
                headers { append(HttpHeaders.Accept, "text/event-stream") }
            }
        } catch (e: Throwable) {
            throw HermesException(unreachable(), e)
        }
        statement.execute { response ->
            if (!response.status.isSuccess()) throw statusException(response)
            val channel = response.bodyAsChannel()
            while (true) {
                val line = try {
                    channel.readUTF8Line()
                } catch (e: HttpRequestTimeoutException) {
                    throw HermesException("The task stream stalled. Check your connection to Hermes.", e)
                } ?: break
                if (line.isEmpty() || !line.startsWith("data:")) continue
                val payload = line.substringAfter("data:").trim()
                if (payload == "[DONE]") break
                parseRunEvent(payload, json)?.let { emit(it) }
            }
        }
    }

    /** `GET /api/sessions/{id}/messages` — full transcript incl. tool calls/results. */
    suspend fun sessionMessages(sessionId: String): List<HermesMessage> {
        val res = getAuthed("${config.baseUrl}/api/sessions/$sessionId/messages")
        return json.decodeFromString(HermesMessagesResponse.serializer(), res).data
    }

    // --- Reminders / jobs (/api/jobs) ----------------------------------------

    /** `GET /api/jobs` — the user's scheduled reminders (Hermes is source of truth). */
    suspend fun listJobs(): List<HermesJob> {
        val res = getAuthed(config.jobs)
        return json.decodeFromString(HermesJobsList.serializer(), res).jobs
    }

    /**
     * `POST /api/jobs` — create a one-shot reminder. [schedule] is a duration
     * like `"90m"` (see [oneShotScheduleMinutes]); [prompt] is what Hermes will
     * act on when it fires. Returns the created job (with its server id + run time).
     */
    suspend fun createJob(name: String, schedule: String, prompt: String): HermesJob {
        val body = HermesCreateJobRequest(name = name, schedule = schedule, prompt = prompt)
        val res = try {
            client.post(config.jobs) {
                authHeaders()
                contentType(ContentType.Application.Json)
                setBody(json.encodeToString(HermesCreateJobRequest.serializer(), body))
            }
        } catch (e: HttpRequestTimeoutException) {
            throw HermesException("Your Hermes didn't respond in time while saving the reminder.", e)
        } catch (e: Throwable) {
            throw HermesException(unreachable(), e)
        }
        if (!res.status.isSuccess()) throw statusException(res)
        return json.decodeFromString(HermesJobEnvelope.serializer(), res.bodyAsText()).job
            ?: throw HermesException("Hermes accepted the reminder but returned no job.")
    }

    /** `DELETE /api/jobs/{id}` — cancel a reminder. */
    suspend fun deleteJob(id: String) {
        val res = try {
            client.delete(config.job(id)) { authHeaders() }
        } catch (e: Throwable) {
            throw HermesException(unreachable(), e)
        }
        if (!res.status.isSuccess()) throw statusException(res)
    }

    // --- internals ------------------------------------------------------------

    private fun io.ktor.client.request.HttpRequestBuilder.authHeaders() {
        headers {
            if (config.apiKey.isNotBlank()) append(HttpHeaders.Authorization, "Bearer ${config.apiKey}")
            if (config.sessionKey.isNotBlank()) append(SESSION_KEY_HEADER, config.sessionKey)
        }
    }

    private suspend fun getAuthed(url: String): String {
        val res = try {
            client.get(url) { authHeaders() }
        } catch (e: HttpRequestTimeoutException) {
            throw HermesException("Your Hermes didn't respond in time.", e)
        } catch (e: Throwable) {
            throw HermesException(unreachable(), e)
        }
        if (!res.status.isSuccess()) throw statusException(res)
        return res.bodyAsText()
    }

    private fun unreachable(): String =
        "Couldn't reach your Hermes at ${config.baseUrl}. Make sure it's running (hermes gateway run) and the address is right."

    /** Map a non-2xx response to a user-actionable [HermesException]. */
    private suspend fun statusException(res: HttpResponse): HermesException {
        val code = res.status.value
        val providerMsg = runCatching {
            json.decodeFromString(HermesErrorEnvelope.serializer(), res.bodyAsText().take(2_000))
                .error?.message?.takeIf { it.isNotBlank() }
        }.getOrNull()
        val base = when (code) {
            401 -> "Authentication failed (401). Check the API key matches your Hermes API_SERVER_KEY."
            403 -> "Access was refused (403). Confirm the API key and that API_SERVER_KEY is set on Hermes."
            404 -> "Not found (404). Check the base URL — it should be your Hermes root, e.g. http://host:8642."
            in 500..599 -> "Your Hermes hit a server error ($code)."
            else -> "Hermes returned an error ($code)."
        }
        return HermesException(if (providerMsg != null) "$base $providerMsg" else base)
    }

    /** Release the underlying engine. */
    fun close() = client.close()

    companion object {
        const val SESSION_KEY_HEADER = "X-Hermes-Session-Key"
        const val SESSION_ID_HEADER = "X-Hermes-Session-Id"
    }
}
