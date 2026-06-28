package com.personalagent.shared.provisioning

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Anchors the in-house [Sha256] against published known-answer vectors so the
 * rest of the provisioning suite can trust it as the verification primitive. If
 * this drifts, every checksum guarantee built on it is suspect.
 */
class Sha256Test {

    private fun hashOf(s: String): String {
        val bytes = s.encodeToByteArray()
        val sha = Sha256()
        sha.update(bytes, bytes.size)
        return sha.digestHex()
    }

    @Test
    fun matches_nist_vectors() {
        // Empty input.
        assertEquals(
            "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
            hashOf(""),
        )
        // "abc"
        assertEquals(
            "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
            hashOf("abc"),
        )
        // 448-bit two-block message.
        assertEquals(
            "248d6a61d20638b8e5c026930c3e6039a33ce45964ff2167f6ecedd419db06c1",
            hashOf("abcdbcdecdefdefgefghfghighijhijkijkljklmklmnlmnomnopnopq"),
        )
    }

    @Test
    fun chunked_updates_match_single_update() {
        // 1000 'a's — fed all at once vs. in awkward chunk sizes spanning block boundaries.
        val data = ByteArray(1000) { 'a'.code.toByte() }

        val whole = Sha256().apply { update(data, data.size) }.digestHex()

        val chunked = Sha256()
        var i = 0
        val sizes = intArrayOf(1, 63, 64, 65, 127, 200, 480) // sum < 1000; remainder in one go
        for (s in sizes) {
            val part = data.copyOfRange(i, i + s)
            chunked.update(part, part.size)
            i += s
        }
        val rest = data.copyOfRange(i, data.size)
        chunked.update(rest, rest.size)

        assertEquals(whole, chunked.digestHex())
    }
}
