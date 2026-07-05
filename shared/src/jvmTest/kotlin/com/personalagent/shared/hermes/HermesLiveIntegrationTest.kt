package com.personalagent.shared.hermes

import com.personalagent.shared.store.InMemoryKeyValueStorage
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
    fun agent_run_streams_tool_events_and_hydrates_live() {
        if (!enabled) { println("SKIP live test — set HERMES_BASE_URL + HERMES_API_KEY"); return }
        runBlocking {
            val c = client()
            try {
                val started = c.startRun("Search the web for the latest stable Python 3 version. Answer in one sentence.")
                println("run started: ${started.runId}")
                val events = c.runEvents(started.runId).toList()
                println("run events: ${events.map { it::class.simpleName }}")

                val completed = events.filterIsInstance<RunEvent.Completed>().firstOrNull()
                assertTrue(completed != null, "run.completed received")
                assertTrue(completed!!.output.isNotBlank(), "run produced an answer")
                println("run answer: ${completed.output.take(120)}")
                completed.usage?.let { println("run usage: ${it.totalTokens} tokens") }

                // Hydrate what it found from the transcript.
                val msgs = c.sessionMessages(started.runId)
                val findings = SessionHydration.findings(msgs)
                println("hydrated messages=${msgs.size}, findings=${findings.size}, firstTool=${findings.firstOrNull()?.tool}")
                assertTrue(msgs.isNotEmpty(), "transcript hydrated")
            } finally { c.close() }
        }
    }

    @Test
    fun dashboard_read_endpoints_return_real_data() {
        if (!enabled) { println("SKIP live test — set HERMES_BASE_URL + HERMES_API_KEY"); return }
        runBlocking {
            val c = client()
            try {
                val health = c.healthDetailed()
                assertTrue(health.isOk, "health/detailed ok")
                println("live health/detailed: v${health.version} gateway=${health.gatewayState}")

                val sessions = c.sessions()
                println("live sessions: ${sessions.size}; usage=${com.personalagent.shared.hermes.UsageSummary.from(sessions)}")
                assertTrue(sessions.isNotEmpty(), "has session activity")
                assertTrue(sessions.first().displayTitle.isNotBlank(), "session has a display title")

                val toolsets = c.toolsets()
                println("live toolsets: ${toolsets.size}, enabled=${toolsets.count { it.enabled }}")
                assertTrue(toolsets.size >= 20, "toolsets present")
                assertTrue(toolsets.any { it.label.isNotBlank() }, "toolsets have emoji labels")

                val skills = c.skills()
                println("live skills: ${skills.size}, categories=${skills.mapNotNull { it.category }.distinct().size}")
                assertTrue(skills.size >= 50, "skills present")
            } finally { c.close() }
        }
    }

    @Test
    fun complete_nonstreaming_returns_text() {
        // Validates the reflection/notes/goals one-shot path (non-streaming POST),
        // the fix for the "stuck on Reflecting…" hang.
        if (!enabled) { println("SKIP live test — set HERMES_BASE_URL + HERMES_API_KEY"); return }
        runBlocking {
            val c = client()
            try {
                val reply = c.complete(
                    listOf(HermesWireMessage("user", "Reply with exactly: complete ok")),
                    sessionId = "lifeagent-reflection",
                )
                println("live complete() reply: $reply")
                assertTrue(reply.isNotBlank(), "complete() returned text")
            } finally { c.close() }
        }
    }

    @Test
    fun jobs_create_list_delete_roundtrip() {
        if (!enabled) { println("SKIP live test — set HERMES_BASE_URL + HERMES_API_KEY"); return }
        runBlocking {
            val c = client()
            try {
                val name = "itest-reminder-${System.currentTimeMillis()}"
                val created = c.createJob(
                    name = name,
                    schedule = oneShotScheduleMinutes(0, 90 * 60_000L), // 90m from now
                    prompt = "Remind the user: integration test reminder",
                )
                println("live job created: id=${created.id} next=${created.nextRunAt} display=${created.scheduleDisplay}")
                assertTrue(created.id.isNotBlank())

                val listed = c.listJobs()
                assertTrue(listed.any { it.id == created.id }, "created job appears in list")
                assertTrue(listed.first { it.id == created.id }.nextRunAtMillis != null, "has parseable run time")

                c.deleteJob(created.id)
                assertTrue(c.listJobs().none { it.id == created.id }, "job removed after delete")
                println("live job deleted ok")
            } finally { c.close() }
        }
    }

    @Test
    fun poller_detects_due_job_and_notifies_once_live() {
        if (!enabled) { println("SKIP live test — set HERMES_BASE_URL + HERMES_API_KEY"); return }
        runBlocking {
            val c = client()
            try {
                val created = c.createJob(
                    name = "itest-poll-${System.currentTimeMillis()}",
                    schedule = oneShotScheduleMinutes(0, 5 * 60_000L),
                    prompt = "Remind the user: poll test",
                )
                val runAt = created.nextRunAtMillis ?: error("no run time")
                val store = NotifiedReminderStore(InMemoryKeyValueStorage())
                // Pretend "now" is just after the job's run time so it reads as due.
                val poller = HermesReminderPoller(c, store, now = { runAt + 60_000L })

                var notifiedBody: String? = null
                val due = poller.pollOnce { notifiedBody = it.body }
                println("live poller notified: $notifiedBody")
                assertTrue(due.any { it.jobId == created.id }, "poller surfaced the due job")

                // Second poll must NOT re-notify the same firing.
                val again = poller.pollOnce { error("should not re-notify") }
                assertTrue(again.none { it.jobId == created.id }, "notified-once dedup holds")

                c.deleteJob(created.id)
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
