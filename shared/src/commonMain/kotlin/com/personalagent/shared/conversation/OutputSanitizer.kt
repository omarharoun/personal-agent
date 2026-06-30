package com.personalagent.shared.conversation

/**
 * Defensive cleanup of raw on-device model output before it is shown to the user.
 *
 * Small instruct models shipped as MediaPipe `.task` bundles (e.g. SmolLM-135M,
 * a ChatML model) can leak their chat-template control tokens into the visible
 * text when the prompt/template/stop-token wiring is imperfect — the reported bug
 * was a long run of `<|im_end|>` followed by `<|im_start|>assistant` before the
 * actual reply. The on-device LLM also registers these as stop sequences so
 * generation halts on them; this sanitizer is the belt-and-suspenders second line
 * that strips anything that still slips through, regardless of the `.task`
 * bundle's baked-in template.
 *
 * It is pure and platform-independent so it can be unit-tested with no model.
 * The patterns are deliberately narrow (they require the `<|…|>` / `<…>` token
 * delimiters) so ordinary prose — including markdown with stray `|` table pipes —
 * is never touched.
 */
object OutputSanitizer {

    // Generic ChatML/sentinel token: <| ... |>. Covers <|im_start|>, <|im_end|>,
    // <|endoftext|>, and the underscore-mangled "<|imend|>" variant. Requires the
    // <| … |> delimiters, so markdown table pipes ("a | b") are never matched.
    private val GENERIC_PIPE_TOKEN = Regex("<\\|[^<>]*?\\|>")

    // The same known markers when a pipe or the trailing '>' got dropped, e.g.
    // "<|im_start|>assistant", "<im_end>", "<|im_end". Anchored on a leading '<'.
    private val KNOWN_MARKER = Regex(
        "<\\|?\\s*(?:im_start|im_end|imstart|imend|endoftext|end_of_text)\\s*\\|?>?",
        RegexOption.IGNORE_CASE,
    )

    // Llama-style sentinels <s> </s> <bos> <eos> (harmless if the model never emits them).
    private val LLAMA_MARKER = Regex("</?(?:s|bos|eos)>", RegexOption.IGNORE_CASE)

    // A leading role word left once a start marker is stripped, e.g.
    // "<|im_start|>assistant\nHello" -> "assistant\nHello" -> "Hello".
    private val LEADING_ROLE = Regex("^\\s*(?:assistant|user|system)\\b\\s*", RegexOption.IGNORE_CASE)

    /**
     * Strip leaked special/control tokens and stray role markers, then trim.
     * Conservative on real prose: it only removes `<|…|>` / `<…>`-shaped tokens,
     * the named ChatML markers above, and leading role words.
     */
    fun sanitize(raw: String): String {
        var t = raw
        t = GENERIC_PIPE_TOKEN.replace(t, "")
        t = KNOWN_MARKER.replace(t, "")
        t = LLAMA_MARKER.replace(t, "")
        // Strip leading role words repeatedly (handles "system\nassistant\n…").
        var prev: String
        do {
            prev = t
            t = LEADING_ROLE.replace(t.trimStart(), "")
        } while (t != prev)
        return t.trim()
    }
}
