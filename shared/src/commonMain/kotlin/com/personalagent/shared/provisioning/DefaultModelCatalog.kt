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
 * ## FORMAT — MediaPipe LLM Inference `.task` bundles (not raw GGUF)
 * 🛠️ These are **`.task` LiteRT/MediaPipe bundles**, the format the Android
 * on-device runtime ([com.personalagent.android.llm.AndroidOnDeviceLlm] →
 * MediaPipe `LlmInference`) actually loads. An earlier catalog shipped raw GGUF
 * `Q4_K_M` files, which MediaPipe **cannot** load — so a "downloaded + verified"
 * model never generated. These entries point at Google's own **ungated**
 * `litert-community` `.task` conversions of the same small open models, so a
 * downloaded model truly runs on-device.
 *
 * (iOS uses a different runtime — MLX — which needs MLX-format weights, not these
 * `.task` bundles. The iOS model-provisioning path is a separate, Mac-verified
 * concern; see `iosApp` and the README. This catalog is correct for **Android**.)
 *
 * ## Honesty: every entry "just works" on Android
 * All three entries are **ungated** and permissively licensed (Apache-2.0). Their
 * checksums are pinned, so the default onboarding path — pick → download → verify
 * → install → load — works end-to-end with no account, token, or license click.
 * [DEFAULT] points at the smallest (SmolLM 135M).
 *
 * The provisioner still **fails closed** on any unpinned/mismatched checksum (the
 * [UNPINNED_SHA256] sentinel + the Sha256 verifier remain in the codebase); the
 * curated catalog simply offers only models the user can actually fetch + verify.
 */
class DefaultModelCatalog : ModelCatalog {
    override fun options(): List<ModelOption> = CATALOG

    companion object {
        /**
         * The recommended default: smallest ungated model, so first-run onboarding
         * downloads the least and still lights up the on-device AI features.
         */
        val DEFAULT: ModelOption get() = CATALOG.first { it.id == "smollm-135m-instruct-task-q8" }

        // Checksums + sizes below are the publisher's own Git-LFS `oid sha256:` /
        // `size`, pinned from each repo's `…/raw/main/…` pointer. The `url` is the
        // matching `…/resolve/main/…` download endpoint (302→ *.hf.co CDN, also
        // covered by the trusted-host allowlist). All are `.task` MediaPipe bundles.
        private val CATALOG: List<ModelOption> = listOf(
            ModelOption(
                id = "smollm-135m-instruct-task-q8",
                displayName = "SmolLM 135M Instruct (MediaPipe .task, q8)",
                sizeBytes = 166_754_726L,
                url = "https://huggingface.co/litert-community/SmolLM-135M-Instruct/resolve/main/SmolLM-135M-Instruct_multi-prefill-seq_q8_ekv1280.task",
                sha256 = "6987dce5ac4f71032b070cf13412a5de0e49c04d271a053fc7d9d59a0dc104e9",
                quant = "q8",
                licenseName = "Apache-2.0",
                licenseUrl = "https://huggingface.co/litert-community/SmolLM-135M-Instruct",
                requiresProviderAuth = false,
                note = "Smallest default — ~159 MB, no account or token needed. Loads in the on-device MediaPipe runtime. Great for low-end devices.",
            ),
            ModelOption(
                id = "qwen2.5-0.5b-instruct-task-q8",
                displayName = "Qwen2.5 0.5B Instruct (MediaPipe .task, q8)",
                sizeBytes = 546_660_344L,
                url = "https://huggingface.co/litert-community/Qwen2.5-0.5B-Instruct/resolve/main/Qwen2.5-0.5B-Instruct_multi-prefill-seq_q8_ekv1280.task",
                sha256 = "e608953f169aeb1bd7b9155fec2559825e08453fc209b84eda3a781ed0452fd2",
                quant = "q8",
                licenseName = "Apache-2.0",
                licenseUrl = "https://huggingface.co/litert-community/Qwen2.5-0.5B-Instruct",
                requiresProviderAuth = false,
                note = "~521 MB, ungated. Stronger than SmolLM at a larger size. Loads in the on-device MediaPipe runtime.",
            ),
            ModelOption(
                id = "tinyllama-1.1b-chat-task-q8",
                displayName = "TinyLlama 1.1B Chat v1.0 (MediaPipe .task, q8)",
                sizeBytes = 1_148_331_545L,
                url = "https://huggingface.co/litert-community/TinyLlama-1.1B-Chat-v1.0/resolve/main/TinyLlama-1.1B-Chat-v1.0_multi-prefill-seq_q8_ekv1280.task",
                sha256 = "0f09dc7f792bb8d49b6629effaee3ed1a99e4506b082cd353471bdf391dee053",
                quant = "q8",
                licenseName = "Apache-2.0",
                licenseUrl = "https://huggingface.co/litert-community/TinyLlama-1.1B-Chat-v1.0",
                requiresProviderAuth = false,
                note = "~1.07 GB, ungated. Most capable of the three; largest download. Loads in the on-device MediaPipe runtime.",
            ),
        )
    }
}
