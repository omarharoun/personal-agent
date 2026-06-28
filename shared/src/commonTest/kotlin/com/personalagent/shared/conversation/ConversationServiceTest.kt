package com.personalagent.shared.conversation

import com.personalagent.shared.cloud.CloudUnavailableException
import com.personalagent.shared.cloud.FakeCloudClient
import com.personalagent.shared.memory.HashingEmbedder
import com.personalagent.shared.memory.InMemoryVectorIndex
import com.personalagent.shared.memory.MemoryService
import com.personalagent.shared.store.InMemoryKeyValueStorage
import com.personalagent.shared.store.PersistentLocalStore
import com.personalagent.shared.util.Clock
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** Monotonic fake clock so generated ids/timestamps are unique and deterministic. */
private class FakeClock(start: Long = 1_000L) : Clock {
    private var t = start
    override fun nowMillis(): Long = t++
}

class ConversationServiceTest {

    private fun memory(): MemoryService {
        val storage = InMemoryKeyValueStorage()
        return MemoryService(
            HashingEmbedder(),
            InMemoryVectorIndex(storage),
            PersistentLocalStore(storage),
            FakeClock(),
        )
    }

    @Test
    fun respond_injects_retrieved_context_calls_model_and_returns_output() = runTest {
        // ACCEPTANCE: real MemoryService + a fake LLM, zero network.
        val mem = memory()
        // Seed prior context the turn should retrieve.
        mem.remember("the user's dentist is Dr. Lee")
        mem.remember("the user prefers morning appointments")
        mem.remember("the user's car is a blue hatchback") // unrelated; should rank lower

        val llm = FakeOnDeviceLlm(response = "Sure — I'll add a reminder.")
        val svc = ConversationService(llm, mem, contextTopK = 2)

        val out = svc.respond("remind me about my dentist appointment")

        // (3) returns the model output
        assertEquals("Sure — I'll add a reminder.", out)
        // model was actually called once
        assertEquals(1, llm.callCount)

        // (2) the prompt the model saw contained the retrieved context AND the turn
        val prompt = assertNotNull(llm.lastPrompt)
        assertTrue(prompt.contains("Dr. Lee"), "retrieved context not injected into prompt")
        assertTrue(prompt.contains("remind me about my dentist appointment"), "user turn not in prompt")
        assertTrue(prompt.contains("personal assistant"), "persona not in prompt")
    }

    @Test
    fun respond_records_the_interaction_back_into_memory() = runTest {
        val mem = memory()
        val llm = FakeOnDeviceLlm(response = "Noted: buy milk.")
        val svc = ConversationService(llm, mem)

        svc.respond("add a note to buy milk")

        // (4) the new interaction was recorded — recallable afterwards.
        val recalledUser = mem.recall("what did I ask to add a note about", topK = 5)
        assertTrue(recalledUser.any { it.content.contains("buy milk") }, "user turn not recorded")

        val recalledAssistant = mem.recall("Noted buy milk", topK = 5)
        assertTrue(
            recalledAssistant.any { it.content == "Noted: buy milk." },
            "assistant reply not recorded",
        )
    }

    @Test
    fun retrieved_context_actually_comes_from_prior_turns() = runTest {
        // End-to-end: a turn is recorded, then a later related turn retrieves it.
        val mem = memory()
        val llm = FakeOnDeviceLlm.echo() // echo so we can read the prompt as the output
        val svc = ConversationService(llm, mem)

        svc.respond("my project deadline is on Friday")
        val secondPrompt = svc.respond("when is my deadline again")

        assertTrue(
            secondPrompt.contains("deadline is on Friday"),
            "second turn did not retrieve context recorded by the first turn",
        )
    }

    @Test
    fun shouldEscalate_is_always_false_in_step3() = runTest {
        val mem = memory()
        val svc = ConversationService(FakeOnDeviceLlm(), mem)
        assertFalse(svc.shouldEscalate("anything at all", emptyList()))
        assertFalse(svc.shouldEscalate("please use the cloud", emptyList()))
    }

    @Test
    fun no_local_model_but_cloud_configured_routes_a_normal_question_to_cloud() = runTest {
        // THE DEVICE BUG: a user set an API key but installed no local model. A
        // plain question does NOT trip the escalation heuristic, so before the fix
        // it dead-ended at the absent local model (hang / silent fallback). Now an
        // unavailable local model alone must route the turn to the cloud.
        val mem = memory()
        val cloud = FakeCloudClient(response = "answer from the cloud")
        val svc = ConversationService(
            llm = FakeOnDeviceLlm(isAvailable = false),
            memory = mem,
            cloudClient = cloud,
        )

        val out = svc.respond("what's the capital of France?")

        assertEquals("answer from the cloud", out)
        assertEquals(1, cloud.callCount, "a no-local-model turn must hit the cloud")
    }

    @Test
    fun no_local_model_and_no_cloud_fails_loudly_not_silently() = runTest {
        // With neither a local model NOR a cloud provider, the turn must throw a
        // CLEAR error (which the UI renders as "no model + no key") — never a
        // silent stall. Default cloudClient is UnavailableCloudClient.
        val svc = ConversationService(
            llm = FakeOnDeviceLlm(isAvailable = false),
            memory = memory(),
        )

        assertFailsWith<CloudUnavailableException> {
            svc.respond("hello?")
        }
    }

    @Test
    fun local_model_available_keeps_a_normal_question_on_device() = runTest {
        // The inverse guard: when a local model IS present, a normal question
        // stays on-device and does NOT spend a cloud call.
        val mem = memory()
        val cloud = FakeCloudClient(response = "should not be used")
        val llm = FakeOnDeviceLlm(isAvailable = true, response = "local answer")
        val svc = ConversationService(llm = llm, memory = mem, cloudClient = cloud)

        val out = svc.respond("what's the capital of France?")

        assertEquals("local answer", out)
        assertEquals(0, cloud.callCount, "a normal turn with a local model must stay on-device")
        assertEquals(1, llm.callCount)
    }

    @Test
    fun blank_input_returns_empty_and_records_nothing() = runTest {
        val mem = memory()
        val llm = FakeOnDeviceLlm()
        val svc = ConversationService(llm, mem)

        assertEquals("", svc.respond("   "))
        assertEquals(0, llm.callCount)
        assertTrue(mem.recall("anything").isEmpty())
    }

    @Test
    fun decoding_options_are_passed_through_to_the_model() = runTest {
        val mem = memory()
        val llm = FakeOnDeviceLlm()
        val opts = GenOptions(maxTokens = 64, temperature = 0.1f, stop = listOf("\n\n"))
        val svc = ConversationService(llm, mem, options = opts)

        svc.respond("hello")
        assertEquals(opts, llm.lastOptions)
    }

    @Test
    fun respondStream_emits_chunks_then_records_the_turn() = runTest {
        val mem = memory()
        mem.remember("the user likes window seats")
        val llm = FakeOnDeviceLlm(response = "Booked your window seat now.")
        val svc = ConversationService(llm, mem)

        val chunks = svc.respondStream("book me a flight seat").toList()

        // streamed in pieces, and concatenation == the full reply
        assertTrue(chunks.size > 1, "expected multiple streamed chunks")
        assertEquals("Booked your window seat now.", chunks.joinToString(""))

        // recorded after the stream completed
        val recalled = mem.recall("Booked your window seat", topK = 5)
        assertTrue(recalled.any { it.content == "Booked your window seat now." })
    }
}
