package com.personalagent.shared.provisioning

/**
 * The curated, trusted catalog of installable on-device models.
 *
 * 🔒 This is the trust root: every entry is hand-vetted, every [ModelOption.url]
 * is HTTPS on a [TrustedHosts] domain, and every [ModelOption.sha256] is a real
 * checksum pinned from the publisher's own Git-LFS pointer (`oid sha256:` + `size`
 * on the Hugging Face `…/raw/main/…` pointer — the authoritative hash of the exact
 * file the `…/resolve/main/…` URL serves). NO arbitrary user URLs are ever
 * accepted; the onboarding UI picks from this list only.
 *
 * ## Honesty: what "just works" vs. what is gated
 * The first four entries are **ungated** and permissively licensed (Apache-2.0 /
 * Apache-2.0-derived community quants). Their checksums are pinned, so the default
 * onboarding path — pick → download → verify → install — works end-to-end with no
 * account, token, or license click. [DEFAULT] points at the smallest of these.
 *
 * The last two are the popular **gated** models (Google Gemma, Meta Llama). Their
 * official repos return HTTP 401 without an accepted license + access token, so
 * `requiresProviderAuth = true` and their checksum is left [UNPINNED_SHA256]:
 * the maintainer cannot read the gated file to pin its hash, and the provisioner
 * **fails closed** on an unpinned checksum rather than installing something
 * unverified. The UI must route the user through the provider's consent + token
 * flow, after which the real checksum is pinned (see each entry's `note`).
 */
class DefaultModelCatalog : ModelCatalog {
    override fun options(): List<ModelOption> = CATALOG

    companion object {
        /**
         * The recommended default: smallest ungated model, so first-run onboarding
         * downloads the least and still lights up the on-device AI features.
         */
        val DEFAULT: ModelOption get() = CATALOG.first { it.id == "smollm2-360m-instruct-q4_k_m" }

        // Checksums + sizes below are the publisher's own Git-LFS `oid sha256:` /
        // `size`, pinned from each repo's `…/raw/main/…` pointer. The `url` is the
        // matching `…/resolve/main/…` download endpoint (302→ *.hf.co CDN, also
        // covered by the trusted-host allowlist).
        private val CATALOG: List<ModelOption> = listOf(
            // --- Ungated, permissively licensed: the default path works ---------
            ModelOption(
                id = "smollm2-360m-instruct-q4_k_m",
                displayName = "SmolLM2 360M Instruct (Q4_K_M)",
                sizeBytes = 270_590_880L,
                url = "https://huggingface.co/bartowski/SmolLM2-360M-Instruct-GGUF/resolve/main/SmolLM2-360M-Instruct-Q4_K_M.gguf",
                sha256 = "2fa3f013dcdd7b99f9b237717fa0b12d75bbb89984cc1274be1471a465bac9c2",
                quant = "Q4_K_M",
                licenseName = "Apache-2.0",
                licenseUrl = "https://huggingface.co/HuggingFaceTB/SmolLM2-360M-Instruct",
                requiresProviderAuth = false,
                note = "Smallest default — ~258 MB, no account or token needed. Great for low-end devices.",
            ),
            ModelOption(
                id = "qwen2.5-0.5b-instruct-q4_k_m",
                displayName = "Qwen2.5 0.5B Instruct (Q4_K_M)",
                sizeBytes = 491_400_032L,
                url = "https://huggingface.co/Qwen/Qwen2.5-0.5B-Instruct-GGUF/resolve/main/qwen2.5-0.5b-instruct-q4_k_m.gguf",
                sha256 = "74a4da8c9fdbcd15bd1f6d01d621410d31c6fc00986f5eb687824e7b93d7a9db",
                quant = "Q4_K_M",
                licenseName = "Apache-2.0",
                licenseUrl = "https://huggingface.co/Qwen/Qwen2.5-0.5B-Instruct-GGUF",
                requiresProviderAuth = false,
                note = "Official Qwen GGUF — no account needed. Stronger than SmolLM2 at a larger size.",
            ),
            ModelOption(
                id = "tinyllama-1.1b-chat-v1.0-q4_k_m",
                displayName = "TinyLlama 1.1B Chat v1.0 (Q4_K_M)",
                sizeBytes = 668_788_096L,
                url = "https://huggingface.co/TheBloke/TinyLlama-1.1B-Chat-v1.0-GGUF/resolve/main/tinyllama-1.1b-chat-v1.0.Q4_K_M.gguf",
                sha256 = "9fecc3b3cd76bba89d504f29b616eedf7da85b96540e490ca5824d3f7d2776a0",
                quant = "Q4_K_M",
                licenseName = "Apache-2.0",
                licenseUrl = "https://huggingface.co/TinyLlama/TinyLlama-1.1B-Chat-v1.0",
                requiresProviderAuth = false,
                note = "Apache-2.0 chat model, no account needed. Larger download for more capability.",
            ),
            // --- Ungated embeddings model (for on-device memory/RAG) ------------
            ModelOption(
                id = "nomic-embed-text-v1.5-q4_k_m",
                displayName = "Nomic Embed Text v1.5 (Q4_K_M, embeddings)",
                sizeBytes = 84_106_624L,
                url = "https://huggingface.co/nomic-ai/nomic-embed-text-v1.5-GGUF/resolve/main/nomic-embed-text-v1.5.Q4_K_M.gguf",
                sha256 = "d4e388894e09cf3816e8b0896d81d265b55e7a9fff9ab03fe8bf4ef5e11295ac",
                quant = "Q4_K_M",
                licenseName = "Apache-2.0",
                licenseUrl = "https://huggingface.co/nomic-ai/nomic-embed-text-v1.5-GGUF",
                requiresProviderAuth = false,
                note = "Embeddings model (not a chat model) — powers on-device memory/RAG. ~80 MB, ungated.",
            ),
            // --- Gated: provider auth required; checksum pinned only post-consent
            ModelOption(
                id = "gemma-2-2b-it-q4_k_m",
                displayName = "Gemma 2 2B Instruct (Q4_K_M)",
                sizeBytes = 1_708_582_752L, // size from the public community mirror; verify against the official file
                url = "https://huggingface.co/google/gemma-2-2b-it-GGUF/resolve/main/gemma-2-2b-it-Q4_K_M.gguf",
                sha256 = UNPINNED_SHA256,
                quant = "Q4_K_M",
                licenseName = "Gemma",
                licenseUrl = "https://ai.google.dev/gemma/terms",
                requiresProviderAuth = true,
                note = "GATED: the official Google repo returns 401 until you accept the Gemma license and " +
                    "supply a Hugging Face token. Checksum is unpinned and the installer fails closed until " +
                    "the real SHA-256 is pinned from the file you gain access to.",
            ),
            ModelOption(
                id = "llama-3.2-1b-instruct-q4_k_m",
                displayName = "Llama 3.2 1B Instruct (Q4_K_M)",
                sizeBytes = 807_694_464L, // size from the public community mirror; verify against the official file
                url = "https://huggingface.co/meta-llama/Llama-3.2-1B-Instruct-GGUF/resolve/main/Llama-3.2-1B-Instruct-Q4_K_M.gguf",
                sha256 = UNPINNED_SHA256,
                quant = "Q4_K_M",
                licenseName = "Llama-3.2-Community",
                licenseUrl = "https://huggingface.co/meta-llama/Llama-3.2-1B-Instruct/blob/main/LICENSE.txt",
                requiresProviderAuth = true,
                note = "GATED: the official Meta repo returns 401 until you accept the Llama 3.2 Community " +
                    "License and supply a Hugging Face token. Checksum is unpinned and the installer fails " +
                    "closed until the real SHA-256 is pinned from the file you gain access to.",
            ),
        )
    }
}
