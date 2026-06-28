package com.personalagent.shared.provisioning

/**
 * A self-contained, incremental SHA-256 (FIPS 180-4).
 *
 * WHY pure Kotlin in commonMain (instead of an `expect`/`actual` over each
 * platform's digest API): checksum verification is the security heart of model
 * provisioning, so it must be (a) identical on every target — bit-for-bit, no
 * per-platform divergence to audit — and (b) fully exercisable in `:shared`'s
 * common tests with no device, no JVM crypto provider, and no real download.
 * SHA-256 is a fixed, well-specified function; a few dozen lines here buy a
 * single audited code path that every platform shares.
 *
 * Incremental by design: [update] is fed each network chunk as it streams in, so
 * the whole file never has to be buffered in memory to be hashed.
 *
 * Not thread-safe; one instance hashes one stream.
 */
internal class Sha256 {
    private val h = intArrayOf(
        0x6a09e667, -0x4498517b, 0x3c6ef372, -0x5ab00ac6,
        0x510e527f, -0x64fa9774, 0x1f83d9ab, 0x5be0cd19,
    )
    private val buffer = ByteArray(64)
    private var bufferLen = 0
    private var totalBytes = 0L
    private val w = IntArray(64)

    /** Feed [length] bytes from [data] (starting at index 0) into the digest. */
    fun update(data: ByteArray, length: Int) {
        var offset = 0
        totalBytes += length
        // Top up a partial block first.
        if (bufferLen > 0) {
            val need = 64 - bufferLen
            val take = if (length < need) length else need
            data.copyInto(buffer, bufferLen, 0, take)
            bufferLen += take
            offset += take
            if (bufferLen == 64) {
                processBlock(buffer, 0)
                bufferLen = 0
            }
        }
        // Process whole blocks straight out of the input.
        while (offset + 64 <= length) {
            processBlock(data, offset)
            offset += 64
        }
        // Stash the remainder.
        val remaining = length - offset
        if (remaining > 0) {
            data.copyInto(buffer, 0, offset, offset + remaining)
            bufferLen = remaining
        }
    }

    /** Finish and return the 32-byte digest. The instance must not be reused. */
    fun digest(): ByteArray {
        val bitLen = totalBytes * 8
        val padLen = if (bufferLen < 56) 56 - bufferLen else 120 - bufferLen
        val pad = ByteArray(padLen + 8)
        pad[0] = 0x80.toByte()
        for (i in 0 until 8) {
            pad[padLen + i] = (bitLen ushr (56 - 8 * i)).toByte()
        }
        update(pad, pad.size)
        val out = ByteArray(32)
        for (i in 0 until 8) {
            out[i * 4] = (h[i] ushr 24).toByte()
            out[i * 4 + 1] = (h[i] ushr 16).toByte()
            out[i * 4 + 2] = (h[i] ushr 8).toByte()
            out[i * 4 + 3] = h[i].toByte()
        }
        return out
    }

    /** Finish and return the digest as lowercase hex (matches `sha256sum`). */
    fun digestHex(): String = digest().toHexLower()

    private fun processBlock(block: ByteArray, start: Int) {
        for (i in 0 until 16) {
            val j = start + i * 4
            w[i] = ((block[j].toInt() and 0xff) shl 24) or
                ((block[j + 1].toInt() and 0xff) shl 16) or
                ((block[j + 2].toInt() and 0xff) shl 8) or
                (block[j + 3].toInt() and 0xff)
        }
        for (i in 16 until 64) {
            val s0 = (w[i - 15] rotr 7) xor (w[i - 15] rotr 18) xor (w[i - 15] ushr 3)
            val s1 = (w[i - 2] rotr 17) xor (w[i - 2] rotr 19) xor (w[i - 2] ushr 10)
            w[i] = w[i - 16] + s0 + w[i - 7] + s1
        }
        var a = h[0]; var b = h[1]; var c = h[2]; var d = h[3]
        var e = h[4]; var f = h[5]; var g = h[6]; var hh = h[7]
        for (i in 0 until 64) {
            val s1 = (e rotr 6) xor (e rotr 11) xor (e rotr 25)
            val ch = (e and f) xor (e.inv() and g)
            val t1 = hh + s1 + ch + K[i] + w[i]
            val s0 = (a rotr 2) xor (a rotr 13) xor (a rotr 22)
            val maj = (a and b) xor (a and c) xor (b and c)
            val t2 = s0 + maj
            hh = g; g = f; f = e; e = d + t1
            d = c; c = b; b = a; a = t1 + t2
        }
        h[0] += a; h[1] += b; h[2] += c; h[3] += d
        h[4] += e; h[5] += f; h[6] += g; h[7] += hh
    }

    private companion object {
        private val K = intArrayOf(
            0x428a2f98, 0x71374491, -0x4a3f0431, -0x164a245b,
            0x3956c25b, 0x59f111f1, -0x6dc07d5c, -0x54e3a12b,
            -0x27f85568, 0x12835b01, 0x243185be, 0x550c7dc3,
            0x72be5d74, -0x7f214e02, -0x6423f959, -0x3e640e8c,
            -0x1b64963f, -0x1041b87a, 0x0fc19dc6, 0x240ca1cc,
            0x2de92c6f, 0x4a7484aa, 0x5cb0a9dc, 0x76f988da,
            -0x67c1aeae, -0x57ce3993, -0x4ffcd838, -0x40a68039,
            -0x391ff40d, -0x2a586eb9, 0x06ca6351, 0x14292967,
            0x27b70a85, 0x2e1b2138, 0x4d2c6dfc, 0x53380d13,
            0x650a7354, 0x766a0abb, -0x7e3d36d2, -0x6d8dd37b,
            -0x5d40175f, -0x57e599b5, -0x3db47490, -0x3893ae5d,
            -0x2e6d17e7, -0x2966f9dc, -0xbf1ca7b, 0x106aa070,
            0x19a4c116, 0x1e376c08, 0x2748774c, 0x34b0bcb5,
            0x391c0cb3, 0x4ed8aa4a, 0x5b9cca4f, 0x682e6ff3,
            0x748f82ee, 0x78a5636f, -0x7b3787ec, -0x7338fdf8,
            -0x6f410006, -0x5baf9315, -0x41065c09, -0x398e870e,
        )

        private infix fun Int.rotr(bits: Int): Int = (this ushr bits) or (this shl (32 - bits))
    }
}

private const val HEX = "0123456789abcdef"

/** Lowercase hex, matching the output of `sha256sum` / a HF LFS `oid sha256:`. */
internal fun ByteArray.toHexLower(): String {
    val sb = StringBuilder(size * 2)
    for (byte in this) {
        val v = byte.toInt() and 0xff
        sb.append(HEX[v ushr 4]).append(HEX[v and 0x0f])
    }
    return sb.toString()
}
