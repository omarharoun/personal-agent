package com.personalagent.shared.cloud

import com.personalagent.shared.conversation.GenOptions
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.utils.io.ByteReadChannel
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.float
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * Hermetic tests for [HttpCloudClient] — NO real network. Ktor's [MockEngine]
 * stands in for the provider, so we can assert exactly what the client puts on
 * the wire and how it handles responses.
 */
class HttpCloudClientTest {

    private val cfg = CloudConfig(
        baseUrl = "https://api.example.test",
        model = "frontier-xl",
        apiKey = "test-key-123",
    )

    /** A MockEngine that records the single request and returns a canned completion. */
    private fun engineReturning(content: String): Pair<MockEngine, () -> io.ktor.client.request.HttpRequestData?> {
        var captured: io.ktor.client.request.HttpRequestData? = null
        val engine = MockEngine { request ->
            captured = request
            respond(
                content = ByteReadChannel(
                    """{"choices":[{"message":{"role":"assistant","content":${JsonPrimitive(content)}}}]}"""
                ),
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }
        return engine to { captured }
    }

    @Test
    fun sends_well_formed_https_request_and_parses_response() = runTest {
        val (engine, lastRequest) = engineReturning("hello from the ceiling")
        val client = HttpCloudClient(cfg, engine)

        val answer = client.complete(
            "What is 2+2?",
            GenOptions(maxTokens = 128, temperature = 0.2f, stop = listOf("\n\n", "END")),
        )

        // Response parsed from choices[0].message.content.
        assertEquals("hello from the ceiling", answer)

        val req = lastRequest() ?: fail("no request captured")

        // HTTPS only, correct method + endpoint.
        assertEquals("https", req.url.protocol.name)
        assertEquals("POST", req.method.value)
        assertEquals("/v1/chat/completions", req.url.encodedPath)

        // Minimum headers: bearer auth present, content-type JSON.
        assertEquals("Bearer test-key-123", req.headers[HttpHeaders.Authorization])

        // Body maps GenOptions correctly.
        val body = Json.parseToJsonElement(bodyText(req)).jsonObject
        assertEquals("frontier-xl", body["model"]!!.jsonPrimitive.content)
        assertEquals(128, body["max_tokens"]!!.jsonPrimitive.int)
        assertEquals(0.2f, body["temperature"]!!.jsonPrimitive.float)
        assertEquals(
            listOf("\n\n", "END"),
            body["stop"]!!.jsonArray.map { it.jsonPrimitive.content },
        )
        val messages = body["messages"]!!.jsonArray
        assertEquals(1, messages.size)
        assertEquals("user", messages[0].jsonObject["role"]!!.jsonPrimitive.content)
        assertEquals("What is 2+2?", messages[0].jsonObject["content"]!!.jsonPrimitive.content)
    }

    @Test
    fun omits_stop_when_empty() = runTest {
        val (engine, lastRequest) = engineReturning("ok")
        val client = HttpCloudClient(cfg, engine)

        client.complete("hi", GenOptions()) // default: empty stop list

        val body = Json.parseToJsonElement(bodyText(lastRequest()!!)).jsonObject
        assertNull(body["stop"], "empty stop list must be omitted from the request")
    }

    @Test
    fun rejects_non_https_base_url() {
        val ex = assertFailsWith<IllegalArgumentException> {
            HttpCloudClient(cfg.copy(baseUrl = "http://api.example.test"), MockEngine { respond("") })
        }
        assertContains(ex.message ?: "", "TLS-only")
    }

    @Test
    fun no_payload_logging_engine_sees_only_minimum_headers() = runTest {
        // Assert the client adds no surprise headers that could leak context, and
        // that prompt text appears only in the body we explicitly send (the engine
        // is the sole observer; there is no Logging plugin to capture payloads).
        val (engine, lastRequest) = engineReturning("ok")
        HttpCloudClient(cfg, engine).complete("secret-prompt-text", GenOptions())

        val req = lastRequest()!!
        // Only Authorization (+ Ktor's own Accept/Content-* negotiation) — no
        // custom telemetry/echo headers carrying the prompt.
        val custom = req.headers.names().filterNot {
            it in setOf(
                HttpHeaders.Authorization,
                HttpHeaders.ContentType,
                HttpHeaders.ContentLength,
                HttpHeaders.Accept,
            )
        }
        assertTrue(custom.isEmpty(), "unexpected headers added: $custom")
        // Prompt text lives only in the request body, never in a header.
        assertTrue(req.headers.entries().none { (_, v) -> v.any { it.contains("secret-prompt-text") } })
    }

    @Test
    fun maps_http_error_to_cloud_exception() = runTest {
        val engine = MockEngine { respond("nope", status = HttpStatusCode.InternalServerError) }
        val ex = assertFailsWith<CloudException> {
            HttpCloudClient(cfg, engine).complete("hi", GenOptions())
        }
        assertContains(ex.message ?: "", "500")
    }

    @Test
    fun guards_against_oversized_response() = runTest {
        val huge = "x".repeat(100)
        val engine = MockEngine {
            respond(
                content = """{"choices":[{"message":{"role":"assistant","content":"$huge"}}]}""",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }
        val ex = assertFailsWith<CloudException> {
            HttpCloudClient(cfg.copy(maxResponseChars = 50), engine).complete("hi", GenOptions())
        }
        assertContains(ex.message ?: "", "too large")
    }

    // --- Anthropic (Messages API) provider shape ----------------------------

    private val anthropicCfg = CloudConfig(
        baseUrl = "https://api.anthropic.test",
        model = "claude-3-5-sonnet-latest",
        apiKey = "anthropic-key-xyz",
    )

    @Test
    fun anthropic_sends_messages_request_and_parses_content_text() = runTest {
        var captured: io.ktor.client.request.HttpRequestData? = null
        val engine = MockEngine { request ->
            captured = request
            respond(
                content = ByteReadChannel(
                    """{"content":[{"type":"text","text":"hi from claude"}]}""",
                ),
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }
        val client = HttpCloudClient(anthropicCfg, engine, provider = CloudProvider.ANTHROPIC)

        val answer = client.complete(
            "What is 2+2?",
            GenOptions(maxTokens = 256, temperature = 0.3f),
        )

        // Parsed from content[0].text.
        assertEquals("hi from claude", answer)

        val req = captured ?: fail("no request captured")

        // HTTPS only, correct method + Anthropic endpoint (chatPath ignored).
        assertEquals("https", req.url.protocol.name)
        assertEquals("POST", req.method.value)
        assertEquals("/v1/messages", req.url.encodedPath)

        // Anthropic headers: x-api-key + anthropic-version (NOT bearer auth).
        assertEquals("anthropic-key-xyz", req.headers["x-api-key"])
        assertEquals("2023-06-01", req.headers["anthropic-version"])
        assertNull(req.headers[HttpHeaders.Authorization], "must not send a bearer Authorization header")

        // Body shape: {model, max_tokens (required), messages:[{role:user, content}]}.
        val body = Json.parseToJsonElement(bodyText(req)).jsonObject
        assertEquals("claude-3-5-sonnet-latest", body["model"]!!.jsonPrimitive.content)
        assertEquals(256, body["max_tokens"]!!.jsonPrimitive.int)
        val messages = body["messages"]!!.jsonArray
        assertEquals(1, messages.size)
        assertEquals("user", messages[0].jsonObject["role"]!!.jsonPrimitive.content)
        assertEquals("What is 2+2?", messages[0].jsonObject["content"]!!.jsonPrimitive.content)
    }

    /** Extract the serialized request body that ContentNegotiation produced. */
    private fun bodyText(req: io.ktor.client.request.HttpRequestData): String =
        (req.body as io.ktor.http.content.TextContent).text
}
