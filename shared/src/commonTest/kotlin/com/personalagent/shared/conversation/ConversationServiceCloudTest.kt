package com.personalagent.shared.conversation

import com.personalagent.shared.cloud.CloudClient
import com.personalagent.shared.cloud.CloudUnavailableException
import com.personalagent.shared.cloud.EscalationPolicy
import com.personalagent.shared.cloud.FakeCloudClient
import com.personalagent.shared.cloud.HeuristicEscalationPolicy
import com.personalagent.shared.cloud.PayloadPrep
import com.personalagent.shared.cloud.PreparedPayload
import com.personalagent.shared.cloud.RehydrationMap
import com.personalagent.shared.memory.HashingEmbedder
import com.personalagent.shared.memory.InMemoryVectorIndex
import com.personalagent.shared.memory.MemoryService
import com.personalagent.shared.store.InMemoryKeyValueStorage
import com.personalagent.shared.store.PersistentLocalStore
import com.personalagent.shared.util.Clock
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

private class CloudFakeClock(start: Long = 1_000L) : Clock {
    private var t = start
    override fun nowMillis(): Long = t++
}

/** Always-escalate policy, so the cloud path is exercised deterministically. */
private object AlwaysEscalate : EscalationPolicy {
    override fun shouldEscalate(userText: String, localContext: List<String>): Boolean = true
}

/** Records call order across prep + cloud so a test can assert prep→cloud→rehydrate. */
private class RecordingPrep(val events: MutableList<String>) : PayloadPrep {
    override fun prepare(text: String, contextHints: List<String>): PreparedPayload {
        events += "prepare"
        return PreparedPayload(text, RehydrationMap())
    }

    override fun rehydrate(cloudAnswer: String, mapping: RehydrationMap): String {
        events += "rehydrate"
        return cloudAnswer
    }
}

private class RecordingCloud(val events: MutableList<String>) : CloudClient {
    override val name: String = "recording"
    override suspend fun complete(prompt: String, options: GenOptions): String {
        events += "complete"
        return "cloud says hi"
    }
}

/** Tokenizes a known secret so we can prove the cloud never saw it. */
private class TokenizingPrep(private val secret: String, private val token: String) : PayloadPrep {
    override fun prepare(text: String, contextHints: List<String>): PreparedPayload {
        val map = RehydrationMap().put(token, secret)
        return PreparedPayload(text.replace(secret, token), map)
    }

    override fun rehydrate(cloudAnswer: String, mapping: RehydrationMap): String =
        mapping.rehydrate(cloudAnswer)
}

class ConversationServiceCloudTest {

    private fun memory(): MemoryService {
        val storage = InMemoryKeyValueStorage()
        return MemoryService(
            HashingEmbedder(),
            InMemoryVectorIndex(storage),
            PersistentLocalStore(storage),
            CloudFakeClock(),
        )
    }

    @Test
    fun escalate_path_calls_prepare_then_cloud_then_rehydrate_in_order() = runTest {
        val events = mutableListOf<String>()
        val local = FakeOnDeviceLlm(response = "LOCAL")
        val svc = ConversationService(
            llm = local,
            memory = memory(),
            escalationPolicy = AlwaysEscalate,
            payloadPrep = RecordingPrep(events),
            cloudClient = RecordingCloud(events),
        )

        val out = svc.respond("anything")

        assertEquals(listOf("prepare", "complete", "rehydrate"), events)
        assertEquals("cloud says hi", out)
        // The local model is NOT used on the escalated path.
        assertEquals(0, local.callCount)
    }

    @Test
    fun escalated_answer_is_the_rehydrated_cloud_output() = runTest {
        // The cloud echoes the (anonymized) prompt it received; rehydrate restores it.
        val cloud = FakeCloudClient(respondWith = { prompt, _ -> "Done: $prompt" })
        val svc = ConversationService(
            llm = FakeOnDeviceLlm(response = "LOCAL"),
            memory = memory(),
            escalationPolicy = AlwaysEscalate,
            payloadPrep = TokenizingPrep(secret = "Dr. Lee", token = "<NAME_1>"),
            cloudClient = cloud,
        )

        val out = svc.respond("book an appointment with Dr. Lee")

        // The cloud only ever saw the anonymized text — never the real name.
        assertTrue(cloud.lastPrompt!!.contains("<NAME_1>"))
        assertFalse(cloud.lastPrompt!!.contains("Dr. Lee"), "cloud must not see identifying detail")
        // The returned answer is rehydrated back to the real value.
        assertEquals("Done: book an appointment with Dr. Lee", out)
        assertFalse(out.contains("<NAME_1>"))
    }

    @Test
    fun escalated_turn_is_recorded_into_memory() = runTest {
        val mem = memory()
        val svc = ConversationService(
            llm = FakeOnDeviceLlm(),
            memory = mem,
            escalationPolicy = AlwaysEscalate,
            cloudClient = FakeCloudClient(response = "Cloud handled your taxes."),
        )

        svc.respond("help me with my taxes")

        val recalled = mem.recall("Cloud handled your taxes", topK = 5)
        assertTrue(recalled.any { it.content == "Cloud handled your taxes." }, "escalated reply not recorded")
    }

    @Test
    fun default_config_stays_local_even_for_hard_turns() = runTest {
        val mem = memory()
        val local = FakeOnDeviceLlm(response = "answered locally")
        // Defaults: LocalOnly policy + Unavailable cloud. A hard turn must NOT
        // touch the (throwing) cloud and must be answered locally.
        val svc = ConversationService(local, mem)

        val out = svc.respond("think hard and do deep research on my whole financial plan")

        assertEquals("answered locally", out)
        assertEquals(1, local.callCount)
    }

    @Test
    fun escalating_without_a_real_cloud_fails_loudly() = runTest {
        // Policy escalates but no transport is wired in → clear error, no silent leak.
        val svc = ConversationService(
            llm = FakeOnDeviceLlm(),
            memory = memory(),
            escalationPolicy = AlwaysEscalate, // UnavailableCloudClient is the default
        )
        assertFailsWith<CloudUnavailableException> { svc.respond("anything") }
    }

    @Test
    fun heuristic_policy_wired_end_to_end_routes_hard_turn_to_cloud() = runTest {
        val cloud = FakeCloudClient(response = "cloud plan")
        val svc = ConversationService(
            llm = FakeOnDeviceLlm(response = "local"),
            memory = memory(),
            escalationPolicy = HeuristicEscalationPolicy(),
            cloudClient = cloud,
        )

        // Explicit "think hard" ask → escalates through the real heuristic.
        assertEquals("cloud plan", svc.respond("think hard about this"))
        assertEquals(1, cloud.callCount)

        // Ordinary turn → stays local, cloud untouched.
        assertEquals("local", svc.respond("what's the weather like"))
        assertEquals(1, cloud.callCount)
    }
}
