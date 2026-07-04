package com.personalagent.shared.hermes

import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class HermesJobsTest {

    private val cfg = HermesConfig("http://127.0.0.1:8642", "k", "lifeagent:user-x")

    @Test
    fun list_jobs_parses_shape() = runTest {
        val engine = MockEngine {
            assertEquals("http://127.0.0.1:8642/api/jobs", it.url.toString())
            respond(
                content = """{"jobs":[{"id":"abc","name":"Call sister","next_run_at":"2026-07-04T22:38:51+03:00","state":"scheduled","enabled":true}]}""",
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }
        val jobs = HermesClient(cfg, engine = engine).listJobs()
        assertEquals(1, jobs.size)
        assertEquals("Call sister", jobs[0].label)
        assertTrue(jobs[0].isActive)
        assertTrue(jobs[0].nextRunAtMillis != null)
    }

    @Test
    fun create_job_posts_and_returns_job() = runTest {
        val engine = MockEngine { req ->
            assertEquals(HttpMethod.Post, req.method)
            assertEquals("http://127.0.0.1:8642/api/jobs", req.url.toString())
            respond(
                content = """{"job":{"id":"newid","name":"Test","next_run_at":"2026-07-04T23:00:00+03:00","state":"scheduled"}}""",
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }
        val job = HermesClient(cfg, engine = engine).createJob("Test", "90m", "Remind me: test")
        assertEquals("newid", job.id)
    }

    @Test
    fun delete_job_hits_endpoint() = runTest {
        val engine = MockEngine { req ->
            assertEquals(HttpMethod.Delete, req.method)
            assertEquals("http://127.0.0.1:8642/api/jobs/abc", req.url.toString())
            respond(content = """{"ok":true}""", headers = headersOf(HttpHeaders.ContentType, "application/json"))
        }
        HermesClient(cfg, engine = engine).deleteJob("abc")
    }

    @Test
    fun create_job_error_is_surfaced() = runTest {
        val engine = MockEngine {
            respond(
                content = """{"error":"Schedule is required"}""",
                status = HttpStatusCode.BadRequest,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }
        assertFailsWith<HermesException> {
            HermesClient(cfg, engine = engine).createJob("x", "", "y")
        }
    }

    @Test
    fun one_shot_schedule_minutes_ceils_and_floors() {
        assertEquals("1m", oneShotScheduleMinutes(0, 0))          // past/zero → min 1
        assertEquals("1m", oneShotScheduleMinutes(0, 30_000))     // 30s → 1m
        assertEquals("2m", oneShotScheduleMinutes(0, 61_000))     // 61s → 2m (ceil)
        assertEquals("60m", oneShotScheduleMinutes(0, 3_600_000)) // 1h
    }

    @Test
    fun due_now_selects_only_active_recent_unnotified() {
        val now = 1_000_000_000_000L
        val minute = 60_000L
        val jobs = listOf(
            HermesJob(id = "due", name = "Due", nextRunAt = iso(now - minute), state = "scheduled"),
            HermesJob(id = "future", name = "Future", nextRunAt = iso(now + minute), state = "scheduled"),
            HermesJob(id = "paused", name = "Paused", nextRunAt = iso(now - minute), state = "paused", enabled = false),
            HermesJob(id = "stale", name = "Stale", nextRunAt = iso(now - 48L * 60 * minute), state = "scheduled"),
        )
        val due = ReminderPolling.dueNow(jobs, now, alreadyNotified = emptySet())
        assertEquals(listOf("due"), due.map { it.jobId })

        // Once notified, it's suppressed.
        val notified = setOf(due.first().fireKey)
        assertTrue(ReminderPolling.dueNow(jobs, now, notified).isEmpty())
    }

    // ISO helper mirroring the server's format (offset included).
    private fun iso(millis: Long): String =
        kotlinx.datetime.Instant.fromEpochMilliseconds(millis).toString()
}
