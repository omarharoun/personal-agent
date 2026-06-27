package com.personalagent.shared.cloud

import com.personalagent.shared.conversation.GenOptions
import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.call.body
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.headers
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Raised when a cloud completion cannot be produced — a non-2xx status, a
 * timeout, a transport failure, or an oversized response. The escalation
 * orchestration catches this and **falls back to the local model**, so the
 * message is operator-facing, never the user's answer.
 *
 * It deliberately carries NO request/response payload — only status/shape — so
 * that nothing sensitive leaks into logs or crash reports.
 */
class CloudException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * Runtime configuration for [HttpCloudClient].
 *
 * 🔑 The [apiKey] is supplied **at runtime** (e.g. from secure storage / an env
 * value injected by the host app) and is NEVER hardcoded or committed. The
 * client only ever sends it as a bearer token over TLS.
 *
 * @param baseUrl provider API base; **must be `https://`** (TLS-only is enforced).
 * @param model the frontier model id to call (the "capability ceiling").
 * @param apiKey bearer credential, injected at runtime — do not log or persist.
 * @param chatPath endpoint path appended to [baseUrl]; defaults to the
 *   OpenAI-compatible chat-completions route most providers expose.
 * @param connectTimeoutMs TCP/TLS connect budget.
 * @param requestTimeoutMs whole-call budget; on expiry the call surfaces a clear error.
 * @param maxResponseChars guard against a runaway/oversized response body.
 */
data class CloudConfig(
    val baseUrl: String,
    val model: String,
    val apiKey: String,
    val chatPath: String = "/v1/chat/completions",
    val connectTimeoutMs: Long = 10_000,
    val requestTimeoutMs: Long = 30_000,
    val maxResponseChars: Int = 256_000,
)

/**
 * The real, network-backed [CloudClient]: a thin HTTPS bridge to a configurable
 * frontier model, built on the Ktor multiplatform client.
 *
 * **Zero-retention posture (see `docs/CLOUD.md`):** this client treats the cloud
 * as a *stateless calculator*. It keeps no server-side conversation state, sends
 * only the minimum headers, and **never logs request or response bodies**. It is
 * intended for use against a provider configured under a written zero-retention
 * agreement covering BOTH storage and training — a CONTRACTUAL prerequisite to
 * verify before any real user data flows through it.
 *
 * **Transport hardening:** TLS-only (a non-`https://` base URL is rejected at
 * construction), sensible connect/request timeouts, an oversized-response guard,
 * and uniform error mapping to [CloudException] so the caller can fall back to
 * the on-device model.
 *
 * The [engine] is injected: each platform wires its own ([httpCloudClient] picks
 * the platform default), and tests pass Ktor's `MockEngine` for hermetic runs.
 */
class HttpCloudClient(
    private val config: CloudConfig,
    engine: HttpClientEngine? = null,
) : CloudClient {

    init {
        // TLS-only: refuse plaintext (or any non-HTTPS) endpoints outright.
        require(config.baseUrl.trim().startsWith("https://", ignoreCase = true)) {
            "CloudConfig.baseUrl must be https:// (TLS-only); got: ${config.baseUrl}"
        }
        require(config.apiKey.isNotBlank()) { "CloudConfig.apiKey must be supplied at runtime" }
    }

    override val name: String = "cloud:${config.model}"

    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        encodeDefaults = false
    }

    private val configure: HttpClientConfig<*>.() -> Unit = {
        // No Logging plugin is installed — payloads must never be logged.
        install(ContentNegotiation) { json(json) }
        install(HttpTimeout) {
            connectTimeoutMillis = config.connectTimeoutMs
            requestTimeoutMillis = config.requestTimeoutMs
        }
        expectSuccess = false // we map status → CloudException ourselves
    }

    private val client: HttpClient =
        if (engine != null) HttpClient(engine, configure) else HttpClient(configure)

    private val endpoint: String = config.baseUrl.trimEnd('/') + "/" + config.chatPath.trimStart('/')

    override suspend fun complete(prompt: String, options: GenOptions): String {
        val request = ChatRequest(
            model = config.model,
            messages = listOf(ChatMessage(role = "user", content = prompt)),
            maxTokens = options.maxTokens,
            temperature = options.temperature,
            stop = options.stop.ifEmpty { null },
        )

        val response: HttpResponse = try {
            client.post(endpoint) {
                // Minimum headers only: bearer auth + JSON content type.
                headers { append(HttpHeaders.Authorization, "Bearer ${config.apiKey}") }
                contentType(ContentType.Application.Json)
                setBody(request)
            }
        } catch (e: HttpRequestTimeoutException) {
            throw CloudException("cloud call timed out after ${config.requestTimeoutMs}ms", e)
        } catch (e: CloudException) {
            throw e
        } catch (e: Throwable) {
            // Transport/TLS/DNS failure — note the failure, never the payload.
            throw CloudException("cloud transport failure: ${e::class.simpleName}", e)
        }

        if (!response.status.isSuccess()) {
            // Surface status only; the provider error body may echo the prompt.
            throw CloudException("cloud call failed with HTTP ${response.status.value}")
        }

        val raw = response.bodyAsText()
        if (raw.length > config.maxResponseChars) {
            throw CloudException("cloud response too large (${raw.length} > ${config.maxResponseChars} chars)")
        }

        val parsed = try {
            json.decodeFromString(ChatResponse.serializer(), raw)
        } catch (e: Throwable) {
            throw CloudException("cloud response was not valid JSON", e)
        }

        return parsed.choices.firstOrNull()?.message?.content
            ?: throw CloudException("cloud response contained no completion")
    }

    /** Release the underlying engine. Safe to call once the client is done. */
    fun close() = client.close()
}

/**
 * Tiny construction helper: build the real cloud client for the current platform.
 *
 * Passing `engine = null` lets Ktor select the engine compiled into the target
 * (OkHttp on Android, Darwin on iOS); tests call the [HttpCloudClient]
 * constructor directly with a `MockEngine`.
 */
fun httpCloudClient(config: CloudConfig): CloudClient = HttpCloudClient(config)

// --- Wire models (OpenAI-compatible chat-completions shape) -------------------
// Kept private to this file: the transport's request/response contract is an
// implementation detail behind [CloudClient].

@Serializable
private data class ChatRequest(
    val model: String,
    val messages: List<ChatMessage>,
    @SerialName("max_tokens") val maxTokens: Int,
    val temperature: Float,
    val stop: List<String>? = null,
)

@Serializable
private data class ChatMessage(
    val role: String,
    val content: String,
)

@Serializable
private data class ChatResponse(
    val choices: List<ChatChoice> = emptyList(),
)

@Serializable
private data class ChatChoice(
    val message: ChatMessage? = null,
)
