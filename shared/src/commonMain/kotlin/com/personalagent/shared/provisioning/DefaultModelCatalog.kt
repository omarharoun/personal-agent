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
 * The chat model is **download-on-demand** (no longer bundled in the APK); only the
 * small ONNX embedder is bundled. [DEFAULT] suggests Qwen2.5-0.5B — a genuinely
 * usable assistant at a modest (~0.5 GB) download — with a tiny SmolLM-135M for
 * low-end devices and a stronger Qwen2.5-1.5B for those who want more.
 *
 * The provisioner still **fails closed** on any unpinned/mismatched checksum (the
 * [UNPINNED_SHA256] sentinel + the Sha256 verifier remain in the codebase); the
 * curated catalog simply offers only models the user can actually fetch + verify.
 */
class DefaultModelCatalog : ModelCatalog {
    override fun options(): List<ModelOption> = CATALOG

    companion object {
        /**
         * The recommended default: a genuinely usable small assistant at a modest
         * download (~0.5 GB), ungated + checksum-pinned. Users can pick the tiny
         * SmolLM-135M for low-end devices or the stronger Qwen2.5-1.5B instead.
         */
        val DEFAULT: ModelOption get() = CATALOG.first { it.id == "qwen2.5-0.5b-instruct-task-q8" }

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
                note = "Smallest — ~159 MB, no account or token needed. Best for low-end devices; replies are basic. Loads in the on-device MediaPipe runtime.",
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
                note = "Recommended default — ~521 MB, ungated. A genuinely usable little assistant; a good balance of quality and size. Loads in the on-device MediaPipe runtime.",
            ),
            ModelOption(
                id = "qwen2.5-1.5b-instruct-task-q8",
                displayName = "Qwen2.5 1.5B Instruct (MediaPipe .task, q8)",
                sizeBytes = 1_597_913_616L,
                url = "https://huggingface.co/litert-community/Qwen2.5-1.5B-Instruct/resolve/main/Qwen2.5-1.5B-Instruct_multi-prefill-seq_q8_ekv1280.task",
                sha256 = "8d867a7c93a6acf2892f08e0174e2f6f351ad256b7e3cfb6d6cd9c89794b42e0",
                quant = "q8",
                licenseName = "Apache-2.0",
                licenseUrl = "https://huggingface.co/litert-community/Qwen2.5-1.5B-Instruct",
                requiresProviderAuth = false,
                note = "Most capable — ~1.49 GB, ungated. Best replies of the three; largest download, needs a newer phone with enough RAM. Loads in the on-device MediaPipe runtime.",
            ),
        )
    }
}
