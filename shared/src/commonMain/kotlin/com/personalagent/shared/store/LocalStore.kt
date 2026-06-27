package com.personalagent.shared.store

import com.personalagent.shared.model.MemoryEntry
import com.personalagent.shared.model.Note
import com.personalagent.shared.model.PlanItem
import com.personalagent.shared.model.Reminder

/**
 * On-device persistence contract for all Step-1 entities.
 *
 * Callers (UI, view models, the future agent) depend ONLY on this interface,
 * never on a concrete store. That keeps two later steps clean:
 *   - Step 5 (encryption): swap the [KeyValueStorage] under the implementation;
 *     this interface and its callers do not change.
 *   - Cloud sync (later, gated): an alternate implementation can mirror writes;
 *     callers do not change.
 *
 * All operations are `suspend` so the encrypted/disk-backed implementations can
 * do real I/O off the main thread without changing the contract.
 */
interface LocalStore {
    // --- Notes ---
    suspend fun upsertNote(note: Note)
    suspend fun deleteNote(id: String)
    suspend fun getNote(id: String): Note?
    suspend fun allNotes(): List<Note>

    // --- Reminders ---
    suspend fun upsertReminder(reminder: Reminder)
    suspend fun deleteReminder(id: String)
    suspend fun getReminder(id: String): Reminder?
    suspend fun allReminders(): List<Reminder>

    // --- Plan items ---
    suspend fun upsertPlanItem(item: PlanItem)
    suspend fun deletePlanItem(id: String)
    suspend fun allPlanItems(): List<PlanItem>

    // --- Memory entries (typed now, agent-driven later) ---
    suspend fun upsertMemoryEntry(entry: MemoryEntry)
    suspend fun deleteMemoryEntry(id: String)
    suspend fun allMemoryEntries(): List<MemoryEntry>
}
