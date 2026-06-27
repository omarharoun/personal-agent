package com.personalagent.shared.conversation

import com.personalagent.shared.cache.EmbeddingSemanticCache
import com.personalagent.shared.cloud.EscalationPolicy
import com.personalagent.shared.cloud.FakeCloudClient
import com.personalagent.shared.memory.HashingEmbedder
import com.personalagent.shared.memory.InMemoryVectorIndex
import com.personalagent.shared.memory.MemoryService
import com.personalagent.shared.store.InMemoryKeyValueStorage
import com.personalagent.shared.store.PersistentLocalStore
import com.personalagent.shared.util.Clock
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private class CacheFakeClock(start: Long = 1_000L) : Clock {
    private var t = start
    override fun nowMillis(): Long = t++
}

/**
 * A policy that escalates whenever the turn contains [keyword] — a deterministic
 * stand-in for "this turn is hard enough to want the cloud". It ignores context,
 * so the ONLY thing that keeps such a turn local is a semantic-cache hit.
 */
private class KeywordEscalate(private val keyword: String) : EscalationPolicy {
    override fun shouldEscalate(userText: String, localContext: List<String>): Boolean =
        userText.contains(keyword, ignoreCase = true)
}

/**
 * Step 6 routing tests: the semantic cache is consulted BEFORE the escalation
 * decision, so accumulated understanding short-circuits the cloud. Fully offline —
 * the Step-2 [HashingEmbedder] + a counting [FakeCloudClient], no network/model.
 */
class ConversationServiceCacheTest {

    private fun memory(storage: InMemoryKeyValueStorage = InMemoryKeyValueStorage()): MemoryService =
        MemoryService(
            HashingEmbedder(),
            InMemoryVectorIndex(storage),
            PersistentLocalStore(storage),
            CacheFakeClock(),
        )

    private fun cache(storage: InMemoryKeyValueStorage): EmbeddingSemanticCache =
        EmbeddingSemanticCache(HashingEmbedder(), storage, CacheFakeClock())

    @Test
    fun a_cached_understanding_makes_a_related_hard_query_serve_locally() = runTest {
        val storage = InMemoryKeyValueStorage()
        val cache = cache(storage)
        val cloud = FakeCloudClient(response = "CLOUD")
        val local = FakeOnDeviceLlm(response = "LOCAL")
        val svc = ConversationService(
            llm = local,
            memory = memory(),
            escalationPolicy = KeywordEscalate("research"),
            cloudClient = cloud,
            semanticCache = cache,
        )

        // Pre-seed the accumulated understanding about the topic.
        cache.store(topic = "renewable energy storage", summary = "options for renewable energy storage")

        // A hard turn (would escalate) is served LOCALLY because the cache hits.
        val out = svc.respond("research renewable energy storage")

        assertEquals("LOCAL", out)
        assertEquals(0, cloud.callCount) // cache short-circuited the cloud
        assertEquals(1, local.callCount)
    }

    @Test
    fun grounded_local_prompt_contains_the_cached_understanding() = runTest {
        val storage = InMemoryKeyValueStorage()
        val cache = cache(storage)
        // Echo the prompt so we can assert the cached summary was injected.
        val local = FakeOnDeviceLlm.echo()
        val svc = ConversationService(
            llm = local,
            memory = memory(),
            escalationPolicy = KeywordEscalate("research"),
            cloudClient = FakeCloudClient(),
            semanticCache = cache,
        )

        cache.store(topic = "renewable energy storage", summary = "renewable energy storage favors batteries")

        val prompt = svc.respond("research renewable energy storage")

        assertTrue(
            prompt.contains("renewable energy storage favors batteries"),
            "cached understanding should ground the local prompt",
        )
    }

    @Test
    fun accumulated_cache_reduces_cloud_calls() = runTest {
        val storage = InMemoryKeyValueStorage()
        val cache = cache(storage)
        val cloud = FakeCloudClient(response = "CLOUD")
        val svc = ConversationService(
            llm = FakeOnDeviceLlm(response = "LOCAL"),
            memory = memory(),
            escalationPolicy = KeywordEscalate("research"),
            cloudClient = cloud,
            semanticCache = cache,
        )

        val hard = "research renewable energy storage"

        // 1) Cold cache: the hard turn MISSES and escalates → one cloud call.
        assertEquals("CLOUD", svc.respond(hard))
        assertEquals(1, cloud.callCount)

        // 2) Understanding accumulates (e.g. distilled from that cloud answer).
        cache.store(topic = "renewable energy storage", summary = "options for renewable energy storage")

        // 3) The same hard turn now HITS the cache → served locally, cloud FLAT.
        assertEquals("LOCAL", svc.respond(hard))
        assertEquals(1, cloud.callCount)

        // 4) A related hard turn is also served locally — cloud STILL flat.
        assertEquals("LOCAL", svc.respond("research renewable energy storage tradeoffs"))
        assertEquals(1, cloud.callCount)
    }

    @Test
    fun cache_miss_still_escalates() = runTest {
        val storage = InMemoryKeyValueStorage()
        val cache = cache(storage)
        val cloud = FakeCloudClient(response = "CLOUD")
        val svc = ConversationService(
            llm = FakeOnDeviceLlm(response = "LOCAL"),
            memory = memory(),
            escalationPolicy = KeywordEscalate("research"),
            cloudClient = cloud,
            semanticCache = cache,
        )

        // Cache holds understanding about a DIFFERENT topic — no hit for this turn.
        cache.store(topic = "sourdough baking", summary = "long cold ferment improves flavour")

        assertEquals("CLOUD", svc.respond("research renewable energy storage"))
        assertEquals(1, cloud.callCount)
    }

    @Test
    fun default_no_op_cache_keeps_prior_routing_behaviour() = runTest {
        // No semanticCache argument → NoOpSemanticCache: cache always misses, so a
        // hard turn still escalates exactly as in Step 4 (back-compat).
        val cloud = FakeCloudClient(response = "CLOUD")
        val svc = ConversationService(
            llm = FakeOnDeviceLlm(response = "LOCAL"),
            memory = memory(),
            escalationPolicy = KeywordEscalate("research"),
            cloudClient = cloud,
        )

        assertEquals("CLOUD", svc.respond("research renewable energy storage"))
        assertEquals(1, cloud.callCount)
    }
}
