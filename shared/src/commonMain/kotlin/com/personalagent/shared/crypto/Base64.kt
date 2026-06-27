// 🔒 SECURITY-CRITICAL (Step 5) — pending human security review; do NOT ship to a
// real user until reviewed. Uses vetted standard crypto.
package com.personalagent.shared.crypto

/**
 * Minimal, self-contained **standard RFC 4648 Base64** (with padding) for encoding
 * binary crypto material (wrapped keys, salts, nonces) as strings in the
 * [com.personalagent.shared.store.KeyValueStorage] seam and the recovery blob.
 *
 * Implemented in commonMain (no `expect`/`actual`, no experimental stdlib opt-in) so
 * it builds identically on every target. This is an *encoding*, not a cipher — it
 * provides no confidentiality and is intentionally simple and auditable.
 */
internal object Base64 {
    private const val ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/"
    private val DECODE = IntArray(128) { -1 }.also { tbl ->
        for (i in ALPHABET.indices) tbl[ALPHABET[i].code] = i
    }

    fun encode(data: ByteArray): String {
        if (data.isEmpty()) return ""
        val sb = StringBuilder((data.size + 2) / 3 * 4)
        var i = 0
        while (i + 3 <= data.size) {
            val n = (data[i].toInt() and 0xFF shl 16) or
                (data[i + 1].toInt() and 0xFF shl 8) or
                (data[i + 2].toInt() and 0xFF)
            sb.append(ALPHABET[n ushr 18 and 0x3F])
            sb.append(ALPHABET[n ushr 12 and 0x3F])
            sb.append(ALPHABET[n ushr 6 and 0x3F])
            sb.append(ALPHABET[n and 0x3F])
            i += 3
        }
        when (data.size - i) {
            1 -> {
                val n = data[i].toInt() and 0xFF shl 16
                sb.append(ALPHABET[n ushr 18 and 0x3F])
                sb.append(ALPHABET[n ushr 12 and 0x3F])
                sb.append("==")
            }
            2 -> {
                val n = (data[i].toInt() and 0xFF shl 16) or (data[i + 1].toInt() and 0xFF shl 8)
                sb.append(ALPHABET[n ushr 18 and 0x3F])
                sb.append(ALPHABET[n ushr 12 and 0x3F])
                sb.append(ALPHABET[n ushr 6 and 0x3F])
                sb.append('=')
            }
        }
        return sb.toString()
    }

    fun decode(text: String): ByteArray {
        if (text.isEmpty()) return ByteArray(0)
        val clean = text.filter { it != '\n' && it != '\r' }
        require(clean.length % 4 == 0) { "Invalid Base64 length" }
        var padding = 0
        if (clean.endsWith("==")) padding = 2 else if (clean.endsWith("=")) padding = 1
        val outLen = clean.length / 4 * 3 - padding
        val out = ByteArray(outLen)
        var oi = 0
        var i = 0
        while (i < clean.length) {
            val c0 = sym(clean[i]); val c1 = sym(clean[i + 1])
            val c2 = if (clean[i + 2] == '=') 0 else sym(clean[i + 2])
            val c3 = if (clean[i + 3] == '=') 0 else sym(clean[i + 3])
            val n = (c0 shl 18) or (c1 shl 12) or (c2 shl 6) or c3
            if (oi < outLen) out[oi++] = (n ushr 16 and 0xFF).toByte()
            if (oi < outLen) out[oi++] = (n ushr 8 and 0xFF).toByte()
            if (oi < outLen) out[oi++] = (n and 0xFF).toByte()
            i += 4
        }
        return out
    }

    private fun sym(c: Char): Int {
        val v = if (c.code < 128) DECODE[c.code] else -1
        require(v >= 0) { "Invalid Base64 character: '$c'" }
        return v
    }
}
