package com.personalagent.shared.chat

import com.personalagent.shared.store.KeyValueStorage
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/** One persisted line in a conversation. [role] is "user" | "assistant" | "system". */
@Serializable
data class StoredMessage(
    val id: Long,
    val role: String,
    val text: String,
    val time: Long,
)

/**
 * One persisted conversation thread.
 *
 * [id] is the app-local, stable thread id used by the UI. [conversationId] is the
 * `X-Hermes-Session-Id` the server threads short-term context under — persisting
 * it is what keeps a reopened chat continuous (the app-wide `X-Hermes-Session-Key`
 * keeps long-term memory continuous across all threads). [hermesSessionId] marks a
 * thread that was hydrated from the server's `/api/sessions` list.
 */
@Serializable
data class StoredConversation(
    val id: Long,
    val title: String,
    val conversationId: String,
    val createdAt: Long,
    val updatedAt: Long,
    val messages: List<StoredMessage> = emptyList(),
    /** True when this thread was surfaced from Hermes `/api/sessions`, not typed here. */
    val fromHermes: Boolean = false,
) {
    /** A short preview line for the history list (last user/assistant text). */
    val preview: String
        get() = messages.lastOrNull { it.text.isNotBlank() }?.text?.take(80)?.trim().orEmpty()
}

/**
 * The **local, sealed-at-rest** record of the user's chat history.
 *
 * The app previously kept conversations only in memory, so they vanished on
 * restart. This store persists every thread (id, title, `X-Hermes-Session-Id`,
 * timestamps) and its messages (role, text, time) as encrypted JSON — the same
 * JSON-over-encrypted-`KeyValueStorage` shape as the app's other local stores
 * ([com.personalagent.shared.notes.MemoStore] etc.), so history survives relaunch
 * without pulling in a database dependency.
 *
 * Hermes still holds the authoritative server-side transcript (`/api/sessions`);
 * this is the device's own copy so the app can list + reopen chats instantly and
 * offline. Newest-updated first, capped. Clearing it never touches Hermes.
 */
class ChatStore(
    private val storage: KeyValueStorage,
    private val cap: Int = 200,
) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val serializer = ListSerializer(StoredConversation.serializer())

    /** All persisted conversations, newest-updated first. */
    fun all(): List<StoredConversation> {
        val raw = storage.get(KEY) ?: return emptyList()
        return runCatching { json.decodeFromString(serializer, raw) }
            .getOrDefault(emptyList())
            .sortedByDescending { it.updatedAt }
    }

    fun get(id: Long): StoredConversation? = all().firstOrNull { it.id == id }

    /**
     * Insert or replace [conversation] by [StoredConversation.id]. Empty threads
     * (no messages) are never persisted — a fresh "New chat" shouldn't clutter the
     * history until the user actually says something.
     */
    fun upsert(conversation: StoredConversation) {
        if (conversation.messages.isEmpty()) { remove(conversation.id); return }
        val others = all().filterNot { it.id == conversation.id }
        val merged = (listOf(conversation) + others)
            .sortedByDescending { it.updatedAt }
            .take(cap)
        storage.put(KEY, json.encodeToString(serializer, merged))
    }

    /** Persist a batch at once (used when saving the whole session list). */
    fun replaceAll(conversations: List<StoredConversation>) {
        val kept = conversations
            .filter { it.messages.isNotEmpty() }
            .sortedByDescending { it.updatedAt }
            .take(cap)
        storage.put(KEY, json.encodeToString(serializer, kept))
    }

    fun remove(id: Long) =
        storage.put(KEY, json.encodeToString(serializer, all().filterNot { it.id == id }))

    fun clear() = storage.remove(KEY)

    private companion object {
        const val KEY = "chat_history"
    }
}
