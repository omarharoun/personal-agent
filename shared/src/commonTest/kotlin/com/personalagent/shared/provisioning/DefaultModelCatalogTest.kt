package com.personalagent.shared.provisioning

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Guards the trust root: the curated catalog must be HTTPS-only, on trusted hosts,
 * honestly typed (every entry is ungated with a real pinned checksum), and free of
 * duplicate ids. No provider-gated entries are shipped, yet the fail-closed
 * mechanism for unpinned checksums still holds (see [unpinned_checksum_fails_closed]).
 */
class DefaultModelCatalogTest {

    private val catalog = DefaultModelCatalog().options()

    @Test
    fun has_three_ungated_task_entries_with_qwen_05b_default() {
        assertEquals(3, catalog.size, "catalog should have exactly three curated entries")
        assertTrue(
            catalog.none { it.requiresProviderAuth },
            "no entry may require provider auth — every model must be fetchable",
        )
        assertEquals(
            "qwen2.5-0.5b-instruct-task-q8",
            DefaultModelCatalog.DEFAULT.id,
            "DEFAULT must be the recommended ungated balance (Qwen2.5-0.5B)",
        )
    }

    @Test
    fun every_model_is_a_mediapipe_task_bundle() {
        // 🛠️ The Android on-device runtime is MediaPipe LLM Inference, which loads
        // a `.task` bundle — NOT raw GGUF. Every catalog URL must point at a `.task`
        // file so a downloaded model can actually load and generate. (This is the
        // exact bug that made "installed but not working": GGUF can't load.)
        for (option in catalog) {
            assertTrue(
                option.url.endsWith(".task"),
                "${option.id} must be a MediaPipe .task bundle, not ${option.url.substringAfterLast('.')}",
            )
        }
    }

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
    fun every_entry_is_ungated_and_pinned() {
        // The curated catalog no longer ships gated entries: every model must be
        // fetchable without a provider account AND carry a real pinned checksum.
        for (option in catalog) {
            assertFalse(
                option.requiresProviderAuth,
                "${option.id} must not require provider auth",
            )
            assertTrue(
                isChecksumPinned(option.sha256),
                "${option.id} must have a real pinned checksum the installer can verify",
            )
        }
    }

    @Test
    fun unpinned_checksum_fails_closed() {
        // The fail-closed mechanism is independent of the catalog: an UNPINNED_SHA256
        // (or otherwise non-hex) checksum is never treated as pinned, so the
        // provisioner refuses to install it. We assert this directly on an inline
        // ModelOption rather than relying on a catalog entry to carry the sentinel.
        val unverified = ModelOption(
            id = "inline-unpinned",
            displayName = "Inline Unpinned (test)",
            sizeBytes = 1L,
            url = "https://huggingface.co/example/repo/resolve/main/model.gguf",
            sha256 = UNPINNED_SHA256,
            quant = "Q4_K_M",
            licenseName = "Test",
            licenseUrl = "https://huggingface.co/example/repo",
            requiresProviderAuth = true,
            note = "Inline fixture: an unpinned checksum must fail closed.",
        )
        assertFalse(
            isChecksumPinned(unverified.sha256),
            "the UNPINNED_SHA256 sentinel must never count as a pinned checksum",
        )
    }

    // NOTE: embeddings are NOT in this catalog. On-device memory/RAG uses the
    // ONNX embedder (AndroidEmbedder + its bundled all-MiniLM asset), not a
    // catalog download — so the LLM catalog stays `.task`-only and coherent with
    // the MediaPipe runtime.
}
