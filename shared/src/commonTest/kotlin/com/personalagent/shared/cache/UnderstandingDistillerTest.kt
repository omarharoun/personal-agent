package com.personalagent.shared.cache

import com.personalagent.shared.conversation.FakeOnDeviceLlm
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class UnderstandingDistillerTest {

    @Test
    fun distillsTopicAndSummaryAndStoresUnderstandingNotTheReply() = runTest {
        // The verbatim reply the assistant gave — chatty, with greetings/filler.
        val reply = "Sure thing! Your favorite coffee is an oat milk latte. Enjoy!"
        // A model that returns durable understanding, NOT the reply text.
        val llm = FakeOnDeviceLlm(
            respondWith = { _, _ ->
                "TOPIC: coffee preference\n" +
                    "SUMMARY: The user's go-to coffee order is an oat milk latte."
            },
        )
        val cache = FakeSemanticCache()

        val understanding = UnderstandingDistiller(llm)
            .distillInto(cache, "what's my favorite coffee again?", reply)

        assertNotNull(understanding)
        assertEquals("coffee preference", understanding.topic)

        // Exactly one understanding was stored.
        assertEquals(1, cache.stored.size)
        val stored = cache.stored.first()
        assertEquals("coffee preference", stored.topic)

        // It is UNDERSTANDING, not the canned reply.
        assertFalse(stored.summary == reply, "must not store the verbatim reply")
        assertFalse(stored.summary.contains("Sure thing"), "no greeting/filler from the reply")
        assertFalse(stored.summary.contains("Enjoy!"), "no sign-off from the reply")
        assertTrue(stored.summary.contains("oat milk latte"), "keeps the durable fact")

        // The distillation prompt fed the reply only as evidence and forbade copying it.
        val prompt = llm.lastPrompt
        assertNotNull(prompt)
        assertTrue(prompt.contains("do NOT copy"), "prompt instructs against copying the reply")
        assertTrue(prompt.contains(reply), "reply is supplied as evidence")
    }

    @Test
    fun fallsBackToFreeTextWhenModelIgnoresTheFormat() = runTest {
        // Model returns durable facts but ignores the TOPIC:/SUMMARY: markers.
        val llm = FakeOnDeviceLlm(
            response = "User prefers terse answers and works primarily in Kotlin/KMP.",
        )
        val cache = FakeSemanticCache()

        val understanding = UnderstandingDistiller(llm)
            .distillInto(cache, "tell me about my working preferences please", "ok")

        assertNotNull(understanding)
        assertEquals(1, cache.stored.size)
        assertTrue(understanding.summary.contains("Kotlin/KMP"))
        assertTrue(understanding.topic.isNotBlank())
    }

    @Test
    fun emptyAndShortTurnsAreSkippedGracefullyWithoutCallingTheModel() = runTest {
        val llm = FakeOnDeviceLlm(response = "TOPIC: x\nSUMMARY: y")
        val cache = FakeSemanticCache()
        val distiller = UnderstandingDistiller(llm)

        assertNull(distiller.distillInto(cache, "", "a reply"))
        assertNull(distiller.distillInto(cache, "   ", "a reply"))
        assertNull(distiller.distillInto(cache, "hi", "hello there")) // 1 word < min
        assertNull(distiller.distillInto(cache, "ok thanks", "you bet")) // 2 words < min

        assertEquals(0, cache.stored.size, "nothing trivial should be cached")
        assertEquals(0, llm.callCount, "short turns short-circuit before the model")
    }

    @Test
    fun returnsNullWhenNoModelIsAvailable() = runTest {
        val llm = FakeOnDeviceLlm(isAvailable = false)
        val cache = FakeSemanticCache()

        val result = UnderstandingDistiller(llm)
            .distillInto(cache, "a perfectly long enough turn to distill", "reply")

        assertNull(result)
        assertEquals(0, cache.stored.size)
        assertEquals(0, llm.callCount)
    }

    @Test
    fun returnsNullWhenModelProducesNothingUsable() = runTest {
        val llm = FakeOnDeviceLlm(response = "   ")
        val cache = FakeSemanticCache()

        val result = UnderstandingDistiller(llm)
            .distillInto(cache, "a perfectly long enough turn to distill", "reply")

        assertNull(result)
        assertEquals(0, cache.stored.size)
    }
}
