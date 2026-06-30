package com.personalagent.shared.conversation

/** Author of a conversation turn. Only the two model-relevant roles (system is
 *  injected separately as the persona). */
enum class ChatRole { USER, ASSISTANT }

/**
 * One prior message in the active chat, used to give the model short-term
 * conversation memory. The caller (UI) supplies recent turns in order, oldest
 * first; [ConversationService] bounds them to a window before prompting.
 */
data class ConversationTurn(val role: ChatRole, val text: String)
