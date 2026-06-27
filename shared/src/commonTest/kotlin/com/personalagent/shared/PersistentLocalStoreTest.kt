package com.personalagent.shared

import com.personalagent.shared.model.Note
import com.personalagent.shared.model.PlanItem
import com.personalagent.shared.store.InMemoryKeyValueStorage
import com.personalagent.shared.store.PersistentLocalStore
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PersistentLocalStoreTest {

    @Test
    fun upsert_then_read_roundtrips() = runTest {
        val store = PersistentLocalStore(InMemoryKeyValueStorage())
        val note = Note.create("title", "body", nowMillis = 1L)
        store.upsertNote(note)
        assertEquals(listOf(note), store.allNotes())
        assertEquals(note, store.getNote(note.id))
    }

    @Test
    fun upsert_existing_id_updates_in_place() = runTest {
        val store = PersistentLocalStore(InMemoryKeyValueStorage())
        val note = Note.create("title", "body", nowMillis = 1L)
        store.upsertNote(note)
        store.upsertNote(note.edited("title2", "body2", nowMillis = 2L))
        val all = store.allNotes()
        assertEquals(1, all.size)
        assertEquals("title2", all.first().title)
    }

    @Test
    fun delete_removes_only_target() = runTest {
        val store = PersistentLocalStore(InMemoryKeyValueStorage())
        val a = Note.create("a", "", 1L)
        val b = Note.create("b", "", 1L)
        store.upsertNote(a); store.upsertNote(b)
        store.deleteNote(a.id)
        assertEquals(listOf(b), store.allNotes())
        assertNull(store.getNote(a.id))
    }

    @Test
    fun data_persists_across_store_instances_sharing_storage() = runTest {
        // Proves persistence is in the storage layer, not the store object —
        // a fresh PersistentLocalStore over the same KeyValueStorage sees data.
        val storage = InMemoryKeyValueStorage()
        PersistentLocalStore(storage).upsertNote(Note.create("kept", "x", 1L))
        val reopened = PersistentLocalStore(storage)
        assertEquals(1, reopened.allNotes().size)
        assertEquals("kept", reopened.allNotes().first().title)
    }

    @Test
    fun plan_items_returned_sorted_by_order() = runTest {
        val store = PersistentLocalStore(InMemoryKeyValueStorage())
        store.upsertPlanItem(PlanItem.create("third", 1L, order = 3))
        store.upsertPlanItem(PlanItem.create("first", 1L, order = 1))
        store.upsertPlanItem(PlanItem.create("second", 1L, order = 2))
        assertEquals(listOf("first", "second", "third"), store.allPlanItems().map { it.title })
    }

    @Test
    fun empty_store_returns_empty_lists() = runTest {
        val store = PersistentLocalStore(InMemoryKeyValueStorage())
        assertTrue(store.allNotes().isEmpty())
        assertTrue(store.allReminders().isEmpty())
        assertTrue(store.allPlanItems().isEmpty())
        assertTrue(store.allMemoryEntries().isEmpty())
    }
}
