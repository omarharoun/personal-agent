package com.personalagent.shared.cloud

import com.personalagent.shared.conversation.ChatRole
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
 * @param socketTimeoutMs max gap between bytes once connected — catches a socket
 *   that stalls mid-response on a flaky mobile-data link (the whole-call budget
 *   alone is not always enough to break a half-open connection).
 * @param maxResponseChars guard against a runaway/oversized response body.
 */
data class CloudConfig(
    val baseUrl: String,
    val model: String,
    val apiKey: String,
    val chatPath: String = "/v1/chat/completions",
    // Budgets tuned for mobile data: a slow-but-alive link still completes, but
    // nothing can hang indefinitely — every expiry maps to a visible error.
    val connectTimeoutMs: Long = 15_000,
    val requestTimeoutMs: Long = 60_000,
    val socketTimeoutMs: Long = 30_000,
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
    /**
     * Which provider's wire protocol to speak. Defaults to [CloudProvider.OPENAI]
     * so existing OpenAI-shaped callers (and tests) are unchanged. Set
     * [CloudProvider.ANTHROPIC] to speak the Claude Messages API instead
     * (different endpoint, headers, request/response shape).
     */
    private val provider: CloudProvider = CloudProvider.OPENAI,
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
            socketTimeoutMillis = config.socketTimeoutMs
        }
        expectSuccess = false // we map status → CloudException ourselves
    }

    private val client: HttpClient =
        if (engine != null) HttpClient(engine, configure) else HttpClient(configure)

    /**
     * The POST target. OpenAI uses the configurable [CloudConfig.chatPath]
     * (default `/v1/chat/completions`); Anthropic's Messages API is always
     * `/v1/messages`, so the chatPath is ignored for that provider.
     */
    private val endpoint: String = run {
        val base = config.baseUrl.trimEnd('/')
        when (provider) {
            CloudProvider.OPENAI -> base + "/" + config.chatPath.trimStart('/')
            CloudProvider.ANTHROPIC -> "$base/v1/messages"
        }
    }

    // The single-prompt entry point is just a one-message conversation with no
    // system prompt, so all transport logic lives in completeConversation().
    override suspend fun complete(prompt: String, options: GenOptions): String =
        completeConversation(listOf(CloudMessage(ChatRole.USER, prompt)), system = null, options)

    override suspend fun completeConversation(
        messages: List<CloudMessage>,
        system: String?,
        options: GenOptions,
    ): String = when (provider) {
        CloudProvider.OPENAI -> completeOpenAi(messages, system, options)
        CloudProvider.ANTHROPIC -> completeAnthropic(messages, system, options)
    }

    private fun ChatRole.wire(): String = if (this == ChatRole.USER) "user" else "assistant"

    // --- OpenAI (chat-completions) ------------------------------------------
    // The system prompt is the first message (role:"system"); history + current
    // follow as alternating user/assistant messages.
    private suspend fun completeOpenAi(
        messages: List<CloudMessage>,
        system: String?,
        options: GenOptions,
    ): String {
        val wire = buildList {
            if (!system.isNullOrBlank()) add(ChatMessage(role = "system", content = system))
            messages.forEach { add(ChatMessage(role = it.role.wire(), content = it.content)) }
        }
        val request = ChatRequest(
            model = config.model,
            messages = wire,
            maxTokens = options.maxTokens,
            temperature = options.temperature,
            stop = options.stop.ifEmpty { null },
        )

        val response = postOrThrow {
            // Minimum headers only: bearer auth + JSON content type.
            headers { append(HttpHeaders.Authorization, "Bearer ${config.apiKey}") }
            contentType(ContentType.Application.Json)
            setBody(request)
        }

        val raw = readBodyOrThrow(response)
        val parsed = try {
            json.decodeFromString(ChatResponse.serializer(), raw)
        } catch (e: Throwable) {
            throw CloudException("cloud response was not valid JSON", e)
        }
        return parsed.choices.firstOrNull()?.message?.content
            ?: throw CloudException("cloud response contained no completion")
    }

    // --- Anthropic (Messages API) -------------------------------------------
    // POST {baseUrl}/v1/messages with x-api-key + anthropic-version headers; the
    // persona goes in the top-level `system` field, history + current in the
    // `messages` array (alternating user/assistant). Answer = content[0].text.
    private suspend fun completeAnthropic(
        messages: List<CloudMessage>,
        system: String?,
        options: GenOptions,
    ): String {
        val request = AnthropicRequest(
            model = config.model,
            // Anthropic REQUIRES max_tokens; reuse the GenOptions cap.
            maxTokens = options.maxTokens,
            system = system?.takeIf { it.isNotBlank() },
            messages = messages.map { AnthropicMessage(role = it.role.wire(), content = it.content) },
            temperature = options.temperature,
            stopSequences = options.stop.ifEmpty { null },
        )

        val response = postOrThrow {
            headers {
                append(ANTHROPIC_API_KEY_HEADER, config.apiKey)
                append(ANTHROPIC_VERSION_HEADER, ANTHROPIC_VERSION)
            }
            contentType(ContentType.Application.Json)
            setBody(request)
        }

        val raw = readBodyOrThrow(response)
        val parsed = try {
            json.decodeFromString(AnthropicResponse.serializer(), raw)
        } catch (e: Throwable) {
            throw CloudException("cloud response was not valid JSON", e)
        }
        return parsed.content.firstOrNull { it.text != null }?.text
            ?: throw CloudException("cloud response contained no completion")
    }

    /** POST [endpoint] with [block], mapping every failure to [CloudException]. */
    private suspend inline fun postOrThrow(
        crossinline block: io.ktor.client.request.HttpRequestBuilder.() -> Unit,
    ): HttpResponse = try {
        client.post(endpoint) { block() }
    } catch (e: HttpRequestTimeoutException) {
        throw CloudException("cloud call timed out after ${config.requestTimeoutMs}ms", e)
    } catch (e: CloudException) {
        throw e
    } catch (e: Throwable) {
        // Transport/TLS/DNS failure — note the failure, never the payload.
        throw CloudException("cloud transport failure: ${e::class.simpleName}", e)
    }

    /** Status-check + size-guard a response, returning its raw body text. */
    private suspend fun readBodyOrThrow(response: HttpResponse): String {
        if (!response.status.isSuccess()) {
            // Surface the status AND the provider's own error message. For the
            // failures users actually hit — bad/expired key, wrong model id,
            // rate limit, quota — Anthropic/OpenAI return a structured
            // {"error":{"type","message"}} whose message is provider-side text
            // ("invalid x-api-key", "model: … not found"), NOT an echo of the
            // prompt. Without it the user only ever sees a bare number and can't
            // tell a 401 (fix the key) from a 404 (fix the model). We parse only
            // those two fields; if parsing fails we fall back to status alone.
            val detail = runCatching {
                val errBody = response.bodyAsText().take(2_000)
                json.decodeFromString(ApiErrorEnvelope.serializer(), errBody).error
            }.getOrNull()
            val suffix = detail?.message?.takeIf { it.isNotBlank() }?.let { msg ->
                val type = detail.type?.takeIf { it.isNotBlank() }?.let { " ($it)" } ?: ""
                ": $msg$type"
            } ?: ""
            throw CloudException("API error ${response.status.value}$suffix")
        }
        val raw = response.bodyAsText()
        if (raw.length > config.maxResponseChars) {
            throw CloudException("cloud response too large (${raw.length} > ${config.maxResponseChars} chars)")
        }
        return raw
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

/**
 * Provider-aware construction helper: build the real cloud client for [provider]
 * for the current platform (Ktor selects the compiled-in engine). Tests pass a
 * `MockEngine` to the [HttpCloudClient] constructor directly.
 */
fun httpCloudClient(config: CloudConfig, provider: CloudProvider): CloudClient =
    HttpCloudClient(config, provider = provider)

// --- Anthropic Messages API headers ------------------------------------------
private const val ANTHROPIC_API_KEY_HEADER = "x-api-key"
private const val ANTHROPIC_VERSION_HEADER = "anthropic-version"
private const val ANTHROPIC_VERSION = "2023-06-01"

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

// --- Wire models (Anthropic Messages API shape) ------------------------------
// Request: {model, max_tokens (REQUIRED), messages:[{role,content}], ...};
// response: {content:[{type:"text", text:"..."}], ...}.

@Serializable
private data class AnthropicRequest(
    val model: String,
    @SerialName("max_tokens") val maxTokens: Int,
    val messages: List<AnthropicMessage>,
    val temperature: Float,
    // Anthropic's persona/system prompt is a top-level field (not a message).
    val system: String? = null,
    @SerialName("stop_sequences") val stopSequences: List<String>? = null,
)

@Serializable
private data class AnthropicMessage(
    val role: String,
    val content: String,
)

@Serializable
private data class AnthropicResponse(
    val content: List<AnthropicContentBlock> = emptyList(),
)

@Serializable
private data class AnthropicContentBlock(
    val type: String? = null,
    val text: String? = null,
)

// --- Shared error envelope (Anthropic & OpenAI both use {"error":{type,message}}) ---
@Serializable
private data class ApiErrorEnvelope(
    val error: ApiErrorDetail? = null,
)

@Serializable
private data class ApiErrorDetail(
    val type: String? = null,
    val message: String? = null,
)
