package com.personalagent.shared.hermes

import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * Exercises [HermesClient] against Ktor's MockEngine — no real network, so this
 * runs in the JVM sandbox. The live-instance smoke test is documented separately
 * in docs/PHASE1.md; these lock the wire contract we verified in Phase 0.
 */
class HermesClientTest {

    private val cfg = HermesConfig(
        baseUrl = "http://127.0.0.1:8642",
        apiKey = "test-key",
        sessionKey = "lifeagent:user-abc",
    )

    private fun client(engine: MockEngine) = HermesClient(cfg, engine = engine)

    @Test
    fun health_parses_ok_body() = runTest {
        val engine = MockEngine {
            assertEquals("http://127.0.0.1:8642/health", it.url.toString())
            respond(
                content = """{"status":"ok","platform":"hermes-agent","version":"0.18.0"}""",
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }
        val h = client(engine).health()
        assertEquals("ok", h.status)
        assertEquals("0.18.0", h.version)
    }

    @Test
    fun health_sends_bearer_and_session_key() = runTest {
        val engine = MockEngine { req ->
            assertEquals("Bearer test-key", req.headers[HttpHeaders.Authorization])
            assertEquals("lifeagent:user-abc", req.headers[HermesClient.SESSION_KEY_HEADER])
            respond(
                content = """{"status":"ok"}""",
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }
        client(engine).health()
    }

    @Test
    fun models_lists_agent() = runTest {
        val engine = MockEngine {
            respond(
                content = """{"object":"list","data":[{"id":"hermes-agent","owned_by":"hermes"}]}""",
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }
        val models = client(engine).models()
        assertEquals(listOf("hermes-agent"), models.map { it.id })
    }

    @Test
    fun stream_emits_deltas_then_done() = runTest {
        val sse = buildString {
            append("data: {\"choices\":[{\"delta\":{\"role\":\"assistant\"}}]}\n\n")
            append("data: {\"choices\":[{\"delta\":{\"content\":\"Hello\"}}]}\n\n")
            append("data: {\"choices\":[{\"delta\":{\"content\":\" world\"}}]}\n\n")
            append("data: [DONE]\n\n")
        }
        val engine = MockEngine {
            assertEquals("http://127.0.0.1:8642/v1/chat/completions", it.url.toString())
            respond(content = sse, headers = headersOf(HttpHeaders.ContentType, "text/event-stream"))
        }
        val events = client(engine)
            .streamChat(listOf(HermesWireMessage("user", "hi")))
            .toList()
        val deltas = events.filterIsInstance<ChatStreamEvent.Delta>().joinToString("") { it.text }
        assertEquals("Hello world", deltas)
        assertTrue(events.last() is ChatStreamEvent.Done)
    }

    @Test
    fun stream_passes_session_id_header() = runTest {
        val engine = MockEngine { req ->
            assertEquals("conv-123", req.headers[HermesClient.SESSION_ID_HEADER])
            respond(
                content = "data: {\"choices\":[{\"delta\":{\"content\":\"ok\"}}]}\n\ndata: [DONE]\n\n",
                headers = headersOf(HttpHeaders.ContentType, "text/event-stream"),
            )
        }
        client(engine)
            .streamChat(listOf(HermesWireMessage("user", "hi")), sessionId = "conv-123")
            .toList()
    }

    @Test
    fun unauthorized_maps_to_friendly_message() = runTest {
        val engine = MockEngine {
            respond(
                content = """{"error":{"type":"auth_error","message":"invalid key"}}""",
                status = HttpStatusCode.Unauthorized,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }
        val ex = assertFailsWith<HermesException> { client(engine).health() }
        assertTrue(ex.message!!.contains("401"), "should name the status")
        assertTrue(ex.message!!.contains("API key"), "should point at the key")
    }

    @Test
    fun non_hermes_200_is_flagged() = runTest {
        val engine = MockEngine {
            respond(content = "<html>router login</html>", headers = headersOf(HttpHeaders.ContentType, "text/html"))
        }
        val ex = assertFailsWith<HermesException> { client(engine).health() }
        assertTrue(ex.message!!.contains("doesn't look like a Hermes"))
    }
}
