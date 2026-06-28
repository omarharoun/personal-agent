package com.personalagent.shared.provisioning

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Guards the trust root: the curated catalog must be HTTPS-only, on trusted hosts,
 * honestly typed (pinned checksums for the ungated default path; unpinned + gated
 * for provider-gated models), and free of duplicate ids.
 */
class DefaultModelCatalogTest {

    private val catalog = DefaultModelCatalog().options()

    @Test
    fun is_non_empty_and_has_unique_ids() {
        assertTrue(catalog.isNotEmpty())
        val ids = catalog.map { it.id }
        assertEquals(ids.size, ids.toSet().size, "catalog ids must be unique")
    }

    @Test
    fun every_url_is_https_on_a_trusted_host() {
        for (option in catalog) {
            assertTrue(
                option.url.startsWith("https://"),
                "${option.id} url must be https: ${option.url}",
            )
            assertEquals(
                null,
                TrustedHosts.rejectionReason(option.url),
                "${option.id} url must pass the trusted-host guard: ${option.url}",
            )
        }
    }

    @Test
    fun has_a_real_fetchable_default_that_is_ungated_and_pinned() {
        // Honesty requirement: at least one option works on the default path —
        // ungated AND with a real pinned checksum the installer can verify.
        val fetchable = catalog.filter { !it.requiresProviderAuth && isChecksumPinned(it.sha256) }
        assertTrue(
            fetchable.isNotEmpty(),
            "at least one ungated, checksum-pinned model is required so the default path works",
        )
        // The advertised DEFAULT must be one of them.
        val default = DefaultModelCatalog.DEFAULT
        assertFalse(default.requiresProviderAuth, "DEFAULT must be ungated")
        assertTrue(isChecksumPinned(default.sha256), "DEFAULT must have a pinned checksum")
    }

    @Test
    fun pinned_checksums_are_64_char_lowercase_hex() {
        for (option in catalog.filter { isChecksumPinned(it.sha256) }) {
            assertEquals(64, option.sha256.length, "${option.id} checksum length")
            assertTrue(
                option.sha256.all { it in '0'..'9' || it in 'a'..'f' },
                "${option.id} checksum must be lowercase hex",
            )
        }
    }

    @Test
    fun gated_models_require_auth_and_carry_a_note() {
        val gated = catalog.filter { it.requiresProviderAuth }
        assertTrue(gated.isNotEmpty(), "expected at least one gated model to model the honest reality")
        for (option in gated) {
            // Gated entries are honestly left unpinned (maintainer can't read them to pin a hash).
            assertFalse(
                isChecksumPinned(option.sha256),
                "${option.id} is gated; its checksum should be the unpinned sentinel until access is granted",
            )
            assertTrue(option.note.isNotBlank(), "${option.id} must explain the gating to the user")
            assertTrue(option.licenseName.isNotBlank() && option.licenseUrl.startsWith("https://"))
        }
    }

    @Test
    fun includes_an_embeddings_option() {
        // The on-device memory/RAG path needs an embeddings model in the catalog.
        assertTrue(
            catalog.any { it.id.contains("embed") },
            "catalog should offer an embeddings model for on-device memory",
        )
    }
}
