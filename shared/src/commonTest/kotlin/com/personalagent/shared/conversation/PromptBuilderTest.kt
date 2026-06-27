package com.personalagent.shared.conversation

import com.personalagent.shared.model.MemoryEntry
import com.personalagent.shared.model.MemoryKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PromptBuilderTest {

    private fun entry(content: String) = MemoryEntry(
        id = content.hashCode().toString(),
        content = content,
        kind = MemoryKind.EVENT,
        source = "interaction",
        createdAt = 1L,
    )

    @Test
    fun prompt_contains_persona_context_and_user_turn() {
        val prompt = PromptBuilder().build(
            userText = "remind me to call the dentist tomorrow",
            context = listOf(entry("user's dentist is Dr. Lee"), entry("user prefers morning appointments")),
        )

        // persona scope present
        assertTrue(prompt.contains("personal assistant"), "persona missing")
        assertTrue(prompt.contains("Stay in scope"), "scope guard missing")
        // both retrieved memories injected
        assertTrue(prompt.contains("user's dentist is Dr. Lee"))
        assertTrue(prompt.contains("user prefers morning appointments"))
        // the user turn injected
        assertTrue(prompt.contains("remind me to call the dentist tomorrow"))
        // sections present, in order
        val sys = prompt.indexOf(PromptBuilder.SECTION_SYSTEM)
        val ctx = prompt.indexOf(PromptBuilder.SECTION_CONTEXT)
        val usr = prompt.indexOf(PromptBuilder.SECTION_USER)
        val asst = prompt.indexOf(PromptBuilder.SECTION_ASSISTANT)
        assertTrue(sys in 0 until ctx && ctx < usr && usr < asst, "sections out of order")
    }

    @Test
    fun empty_context_uses_explicit_no_memory_marker() {
        val prompt = PromptBuilder().build("hello", context = emptyList())
        assertTrue(prompt.contains(PromptBuilder.NO_CONTEXT))
    }

    @Test
    fun is_deterministic() {
        val ctx = listOf(entry("a"), entry("b"))
        assertEquals(
            PromptBuilder().build("hi", ctx),
            PromptBuilder().build("hi", ctx),
        )
    }

    @Test
    fun custom_persona_is_used() {
        val prompt = PromptBuilder(persona = "CUSTOM-PERSONA-MARKER").build("hi", emptyList())
        assertTrue(prompt.contains("CUSTOM-PERSONA-MARKER"))
    }
}
