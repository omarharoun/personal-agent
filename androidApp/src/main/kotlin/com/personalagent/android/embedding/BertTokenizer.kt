package com.personalagent.android.embedding

import java.io.BufferedReader
import java.io.InputStream
import java.text.Normalizer

/**
 * Minimal BERT WordPiece tokenizer for the `bert-base-uncased` vocabulary that
 * `all-MiniLM-L6-v2` was trained on. Pure Kotlin/JVM — no native deps — so it
 * runs identically on a device or in a JVM unit test.
 *
 * It reproduces the two stages HuggingFace's `BertTokenizer` uses with
 * `do_lower_case = true`:
 *   1. **Basic tokenization** — Unicode cleanup, lowercasing + accent stripping,
 *      whitespace splitting, and splitting punctuation into its own tokens.
 *   2. **WordPiece** — greedy longest-match-first subword splitting with the
 *      `##` continuation marker, falling back to `[UNK]` for unknown words.
 *
 * This is deliberately scoped to what a sentence-embedding model needs; it is
 * not a general-purpose, byte-identical port of every BERT edge case.
 */
internal class BertTokenizer private constructor(
    private val vocab: Map<String, Int>,
    private val maxSeqLen: Int,
) {
    private val unkId = vocab[UNK] ?: error("vocab missing $UNK")
    private val clsId = vocab[CLS] ?: error("vocab missing $CLS")
    private val sepId = vocab[SEP] ?: error("vocab missing $SEP")

    /** Token ids for [text], framed with [CLS]/[SEP] and truncated to [maxSeqLen]. */
    fun encode(text: String): LongArray {
        val pieces = ArrayList<Int>()
        pieces.add(clsId)
        // Reserve room for the trailing [SEP].
        val budget = maxSeqLen - 2
        outer@ for (token in basicTokenize(text)) {
            for (id in wordPiece(token)) {
                if (pieces.size - 1 >= budget) break@outer
                pieces.add(id)
            }
        }
        pieces.add(sepId)
        return LongArray(pieces.size) { pieces[it].toLong() }
    }

    // --- Stage 1: basic tokenizer -------------------------------------------

    private fun basicTokenize(text: String): List<String> {
        val cleaned = cleanText(text)
        val out = ArrayList<String>()
        for (whitespaceTok in cleaned.split(WHITESPACE).filter { it.isNotEmpty() }) {
            // Lowercase + strip accents (uncased model).
            val normalized = stripAccents(whitespaceTok.lowercase())
            splitOnPunctuation(normalized, out)
        }
        return out
    }

    /** Drop control chars and collapse all Unicode whitespace to a single space. */
    private fun cleanText(text: String): String {
        val sb = StringBuilder(text.length)
        for (ch in text) {
            val code = ch.code
            if (code == 0 || code == 0xFFFD || isControl(ch)) continue
            if (ch.isWhitespace()) sb.append(' ') else sb.append(ch)
        }
        return sb.toString()
    }

    private fun isControl(ch: Char): Boolean {
        if (ch == '\t' || ch == '\n' || ch == '\r') return false
        return Character.getType(ch).let {
            it == Character.CONTROL.toInt() || it == Character.FORMAT.toInt()
        }
    }

    private fun stripAccents(text: String): String {
        val nfd = Normalizer.normalize(text, Normalizer.Form.NFD)
        val sb = StringBuilder(nfd.length)
        for (ch in nfd) {
            if (Character.getType(ch) != Character.NON_SPACING_MARK.toInt()) sb.append(ch)
        }
        return sb.toString()
    }

    /** Each punctuation char becomes its own token (matches BERT). */
    private fun splitOnPunctuation(token: String, out: MutableList<String>) {
        val current = StringBuilder()
        for (ch in token) {
            if (isPunctuation(ch)) {
                if (current.isNotEmpty()) { out.add(current.toString()); current.clear() }
                out.add(ch.toString())
            } else {
                current.append(ch)
            }
        }
        if (current.isNotEmpty()) out.add(current.toString())
    }

    private fun isPunctuation(ch: Char): Boolean {
        val cp = ch.code
        // ASCII punctuation ranges treated as punctuation even when not "P*".
        if (cp in 33..47 || cp in 58..64 || cp in 91..96 || cp in 123..126) return true
        return when (Character.getType(ch).toByte()) {
            Character.CONNECTOR_PUNCTUATION, Character.DASH_PUNCTUATION,
            Character.START_PUNCTUATION, Character.END_PUNCTUATION,
            Character.INITIAL_QUOTE_PUNCTUATION, Character.FINAL_QUOTE_PUNCTUATION,
            Character.OTHER_PUNCTUATION -> true
            else -> false
        }
    }

    // --- Stage 2: WordPiece --------------------------------------------------

    private fun wordPiece(token: String): List<Int> {
        if (token.length > MAX_CHARS_PER_WORD) return listOf(unkId)
        val ids = ArrayList<Int>()
        var start = 0
        val chars = token
        while (start < chars.length) {
            var end = chars.length
            var curId: Int? = null
            while (start < end) {
                val sub = if (start == 0) chars.substring(start, end)
                          else "##" + chars.substring(start, end)
                val id = vocab[sub]
                if (id != null) { curId = id; break }
                end--
            }
            if (curId == null) return listOf(unkId) // any unmatchable piece → whole word UNK
            ids.add(curId)
            start = end
        }
        return ids
    }

    companion object {
        private const val UNK = "[UNK]"
        private const val CLS = "[CLS]"
        private const val SEP = "[SEP]"
        private const val MAX_CHARS_PER_WORD = 100
        private val WHITESPACE = Regex("\\s+")

        /** Builds a tokenizer from a `vocab.txt` stream (one token per line). */
        fun fromVocab(stream: InputStream, maxSeqLen: Int = 256): BertTokenizer {
            val vocab = HashMap<String, Int>(32_768)
            stream.bufferedReader().use { reader: BufferedReader ->
                var index = 0
                reader.forEachLine { line ->
                    // vocab.txt entries are not trimmed of internal chars; only the
                    // trailing newline matters, which forEachLine already removes.
                    vocab[line] = index
                    index++
                }
            }
            require(vocab.isNotEmpty()) { "empty vocab.txt" }
            return BertTokenizer(vocab, maxSeqLen)
        }
    }
}
