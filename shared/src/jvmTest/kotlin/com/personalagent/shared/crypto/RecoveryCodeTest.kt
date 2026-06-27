package com.personalagent.shared.crypto

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Recovery-code generation entropy/uniqueness and normalization. */
class RecoveryCodeTest {

    private val random = JvmSecureRandom()

    @Test
    fun generated_codes_are_unique_high_entropy() {
        val n = 5_000
        val codes = HashSet<String>()
        repeat(n) { codes.add(RecoveryCode.generate(random)) }
        // 130 bits of entropy → collisions in 5k draws are astronomically unlikely.
        assertEquals(n, codes.size, "generated recovery codes must be unique")
    }

    @Test
    fun code_uses_only_base32_alphabet_and_group_separators() {
        val code = RecoveryCode.generate(random)
        val allowed = ('A'..'Z').toSet() + ('2'..'7').toSet() + '-'
        assertTrue(code.all { it in allowed }, "unexpected character in code: $code")
        // 26 symbols in 4-char groups → 6 dashes.
        assertEquals(26, code.count { it != '-' })
        assertEquals(6, code.count { it == '-' })
    }

    @Test
    fun normalize_is_case_and_format_insensitive() {
        val code = RecoveryCode.generate(random)
        val canonical = RecoveryCode.normalize(code)
        assertEquals(canonical, RecoveryCode.normalize(code.lowercase()))
        assertEquals(canonical, RecoveryCode.normalize(code.replace("-", " ")))
        assertEquals(canonical, RecoveryCode.normalize("  $code  "))
        // Dashes/spaces removed; only alphabet symbols remain.
        assertEquals(26, canonical.length)
    }

    @Test
    fun distinct_seeds_yield_distinct_codes() {
        // Two independent draws from the CSPRNG differ.
        assertTrue(RecoveryCode.generate(random) != RecoveryCode.generate(random))
    }
}
