package com.personalagent.shared.conversation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class OutputSanitizerTest {

    @Test
    fun strips_the_exact_leaked_token_run_from_the_screenshot() {
        // Reproduces the reported bug: a long run of <|im_end|> then an
        // <|im_start|>assistant marker, then the real reply.
        val raw = "<|im_end|><|im_end|><|im_end|><|im_end|><|im_start|>assistant\n" +
            "Sure! Here are a few ideas for your weekend."
        val clean = OutputSanitizer.sanitize(raw)
        assertEquals("Sure! Here are a few ideas for your weekend.", clean)
        assertFalse(clean.contains("im_end"))
        assertFalse(clean.contains("im_start"))
        assertFalse(clean.contains("assistant"))
        assertFalse(clean.contains("<|"))
    }

    @Test
    fun strips_trailing_end_token_and_trims() {
        assertEquals("Hello there!", OutputSanitizer.sanitize("Hello there!<|im_end|>\n  "))
    }

    @Test
    fun strips_underscore_mangled_and_endoftext_variants() {
        // The renderer-mangled "<|imend|>" variant + <|endoftext|>.
        val raw = "<|imend|>Answer body<|endoftext|>"
        assertEquals("Answer body", OutputSanitizer.sanitize(raw))
    }

    @Test
    fun strips_leading_role_word_left_after_marker_removal() {
        assertEquals("Done.", OutputSanitizer.sanitize("<|im_start|>assistant Done."))
        assertEquals("Hi", OutputSanitizer.sanitize("system\nassistant\nHi"))
    }

    @Test
    fun leaves_ordinary_prose_untouched_including_markdown_pipes() {
        val prose = "Use the table:\n\n| a | b |\n| 1 | 2 |\n\nThat's it."
        assertEquals(prose, OutputSanitizer.sanitize(prose))
    }

    @Test
    fun does_not_eat_the_word_assistants_in_normal_text() {
        // LEADING_ROLE must respect word boundaries.
        val text = "Assistants can help with that."
        assertEquals(text, OutputSanitizer.sanitize(text))
    }

    @Test
    fun empty_and_token_only_inputs_become_empty() {
        assertEquals("", OutputSanitizer.sanitize(""))
        assertEquals("", OutputSanitizer.sanitize("<|im_end|><|im_end|>"))
        assertTrue(OutputSanitizer.sanitize("   <|im_start|>assistant\n   ").isEmpty())
    }
}
