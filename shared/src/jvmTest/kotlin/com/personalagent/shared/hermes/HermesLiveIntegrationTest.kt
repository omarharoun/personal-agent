package com.personalagent.shared.hermes

import io.ktor.client.engine.cio.CIO
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * End-to-end test of the REAL [HermesClient] code against a RUNNING Hermes.
 *
 * This is how we honor "test anything touching Hermes against the real instance"
 * on a host that can't boot the Android emulator: the same shared client the app
 * ships is driven here over a real Ktor engine (CIO) at the live server.
 *
 * Opt-in — provide the connection via env and the test runs; otherwise it SKIPS
 * (returns green) so normal/CI runs stay hermetic:
 *
 *   HERMES_BASE_URL=http://127.0.0.1:8642 \
 *   HERMES_API_KEY=<key from ~/.hermes/.env> \
 *   ./gradlew :shared:jvmTest --tests '*HermesLiveIntegrationTest*'
 */
class HermesLiveIntegrationTest {

    private val baseUrl = System.getenv("HERMES_BASE_URL")
    private val apiKey = System.getenv("HERMES_API_KEY")
    private val enabled = !baseUrl.isNullOrBlank() && !apiKey.isNullOrBlank()

    private fun client(sessionKey: String = "lifeagent:user-jvm-itest") = HermesClient(
        HermesConfig(
            baseUrl = HermesConfig.normalizeBaseUrl(baseUrl!!)!!,
            apiKey = apiKey!!,
            sessionKey = sessionKey,
        ),
        engine = CIO.create(),
    )

    @Test
    fun health_and_models_and_capabilities() {
        if (!enabled) { println("SKIP live test — set HERMES_BASE_URL + HERMES_API_KEY"); return }
        runBlocking {
            val c = client()
            try {
                val h = c.health()
                assertTrue(h.status.equals("ok", ignoreCase = true), "health status ok")
                println("live health: status=${h.status} version=${h.version}")

                val caps = c.capabilities()
                assertTrue(caps.features.chatCompletions, "chat_completions advertised")
                assertTrue(caps.features.chatCompletionsStreaming, "streaming advertised")
                assertTrue(
                    caps.features.sessionKeyHeader == HermesClient.SESSION_KEY_HEADER,
                    "session_key_header == ${HermesClient.SESSION_KEY_HEADER}",
                )

                val models = c.models()
                assertTrue(models.any { it.id == HermesConfig.DEFAULT_MODEL_ID }, "agent model listed")
                println("live models: ${models.map { it.id }}")
            } finally { c.close() }
        }
    }

    @Test
    fun streaming_chat_returns_text() {
        if (!enabled) { println("SKIP live test — set HERMES_BASE_URL + HERMES_API_KEY"); return }
        runBlocking {
            val c = client()
            try {
                val events = c.streamChat(
                    listOf(HermesWireMessage("user", "Reply with exactly: streaming ok")),
                    sessionId = "lifeagent-conv-itest",
                ).toList()
                val text = events.filterIsInstance<ChatStreamEvent.Delta>().joinToString("") { it.text }
                println("live stream reply: $text")
                assertTrue(text.isNotBlank(), "got streamed text")
                assertTrue(events.last() is ChatStreamEvent.Done, "stream terminated with Done")
            } finally { c.close() }
        }
    }

    @Test
    fun memory_persists_across_conversations_for_one_session_key() {
        if (!enabled) { println("SKIP live test — set HERMES_BASE_URL + HERMES_API_KEY"); return }
        runBlocking {
            // Unique scope per run so we don't collide with real memory.
            val scope = "lifeagent:user-itest-${System.currentTimeMillis()}"
            val fact = "my lucky number is 4273"
            val store = client(scope)
            try {
                store.streamChat(
                    listOf(HermesWireMessage("user", "Please remember this: $fact. Just say ok.")),
                    sessionId = "conv-a",
                ).toList()
            } finally { store.close() }

            // Fresh conversation (new session-id), SAME session-key → should recall.
            val recall = client(scope)
            try {
                val events = recall.streamChat(
                    listOf(HermesWireMessage("user", "What is my lucky number? Reply with just the number.")),
                    sessionId = "conv-b",
                ).toList()
                val text = events.filterIsInstance<ChatStreamEvent.Delta>().joinToString("") { it.text }
                println("live memory recall: $text")
                assertTrue(text.contains("4273"), "recalled the fact across conversations")
            } finally { recall.close() }
        }
    }
}
