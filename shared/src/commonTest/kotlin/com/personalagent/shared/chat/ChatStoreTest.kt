package com.personalagent.shared.chat

import com.personalagent.shared.store.InMemoryKeyValueStorage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ChatStoreTest {

    private fun convo(id: Long, updatedAt: Long, messages: List<StoredMessage>) =
        StoredConversation(
            id = id,
            title = "Chat $id",
            conversationId = "conv-$id",
            createdAt = 0L,
            updatedAt = updatedAt,
            messages = messages,
        )

    private fun msg(id: Long, text: String) = StoredMessage(id, "user", text, 100L)

    @Test
    fun persistsAndReloadsConversations() {
        val storage = InMemoryKeyValueStorage()
        ChatStore(storage).upsert(convo(1, 10L, listOf(msg(1, "hello"))))

        // A fresh store over the SAME storage sees the persisted data (survives "restart").
        val reloaded = ChatStore(storage).all()
        assertEquals(1, reloaded.size)
        assertEquals("hello", reloaded.first().messages.single().text)
        assertEquals("conv-1", reloaded.first().conversationId)
    }

    @Test
    fun ordersNewestUpdatedFirst() {
        val storage = InMemoryKeyValueStorage()
        val store = ChatStore(storage)
        store.upsert(convo(1, 10L, listOf(msg(1, "a"))))
        store.upsert(convo(2, 30L, listOf(msg(2, "b"))))
        store.upsert(convo(3, 20L, listOf(msg(3, "c"))))
        assertEquals(listOf(2L, 3L, 1L), store.all().map { it.id })
    }

    @Test
    fun upsertReplacesById() {
        val storage = InMemoryKeyValueStorage()
        val store = ChatStore(storage)
        store.upsert(convo(1, 10L, listOf(msg(1, "first"))))
        store.upsert(convo(1, 20L, listOf(msg(1, "first"), msg(2, "second"))))
        assertEquals(1, store.all().size)
        assertEquals(2, store.get(1)!!.messages.size)
    }

    @Test
    fun emptyThreadsAreNotPersisted() {
        val storage = InMemoryKeyValueStorage()
        val store = ChatStore(storage)
        store.upsert(convo(1, 10L, emptyList()))
        assertTrue(store.all().isEmpty())
    }

    @Test
    fun removeAndClearWork() {
        val storage = InMemoryKeyValueStorage()
        val store = ChatStore(storage)
        store.upsert(convo(1, 10L, listOf(msg(1, "a"))))
        store.upsert(convo(2, 20L, listOf(msg(2, "b"))))
        store.remove(1)
        assertNull(store.get(1))
        assertEquals(1, store.all().size)
        store.clear()
        assertTrue(store.all().isEmpty())
    }

    @Test
    fun capBoundsStoredConversations() {
        val storage = InMemoryKeyValueStorage()
        val store = ChatStore(storage, cap = 3)
        for (i in 1..10) store.upsert(convo(i.toLong(), i.toLong(), listOf(msg(i.toLong(), "m$i"))))
        val all = store.all()
        assertEquals(3, all.size)
        // Newest by updatedAt survive.
        assertEquals(listOf(10L, 9L, 8L), all.map { it.id })
    }
}
