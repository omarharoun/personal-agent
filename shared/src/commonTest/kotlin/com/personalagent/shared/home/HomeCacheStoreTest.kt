package com.personalagent.shared.home

import com.personalagent.shared.store.InMemoryKeyValueStorage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class HomeCacheStoreTest {

    @Test
    fun empty_cache_is_stale_and_has_no_goals() {
        val store = HomeCacheStore(InMemoryKeyValueStorage())
        val cache = store.load()
        assertTrue(cache.goals.isEmpty())
        assertTrue(store.goalsAreStale(cache, nowMillis = 1_000L))
    }

    @Test
    fun put_then_load_roundtrips_and_is_fresh() {
        val storage = InMemoryKeyValueStorage()
        val store = HomeCacheStore(storage)
        store.putGoals(listOf("Run a 5K", "Read more"), nowMillis = 10_000L)

        // A brand-new store over the SAME storage sees the persisted goals
        // (survives process death / app relaunch).
        val reloaded = HomeCacheStore(storage).load()
        assertEquals(listOf("Run a 5K", "Read more"), reloaded.goals)
        assertFalse(store.goalsAreStale(reloaded, nowMillis = 10_000L + 1_000L))
    }

    @Test
    fun goals_go_stale_after_the_ttl() {
        val store = HomeCacheStore(InMemoryKeyValueStorage())
        val base = 1_000_000L // non-zero: 0L is the "never cached" sentinel
        store.putGoals(listOf("Goal"), nowMillis = base)
        val cache = store.load()
        assertFalse(store.goalsAreStale(cache, nowMillis = base + HomeCacheStore.STALE_AFTER_MS - 1))
        assertTrue(store.goalsAreStale(cache, nowMillis = base + HomeCacheStore.STALE_AFTER_MS))
    }
}
