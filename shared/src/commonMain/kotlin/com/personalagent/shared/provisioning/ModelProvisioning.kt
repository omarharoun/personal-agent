package com.personalagent.shared.provisioning

import kotlinx.coroutines.flow.Flow

/**
 * 🤝 SHARED CONTRACT — on-device model provisioning.
 *
 * ⚠️ COORDINATOR / DEDUP NOTE ⚠️
 * The provisioning contract (`com.personalagent.shared.provisioning`:
 * [ModelOption] / [ModelCatalog] / [ModelProvisioner] / [ProvisionState]) is
 * OWNED BY THE SIBLING. This file is a **standalone copy** added on the
 * `feat/model-provisioning-android` branch ONLY so the Android onboarding /
 * Settings UI compiles in isolation. When the sibling's canonical version
 * lands, **delete this file** and keep theirs — the UI is written to these exact
 * signatures, so a matching contract is a drop-in. If shapes diverge, the
 * sibling's wins; reconcile the UI to it (see the notes on each type below).
 *
 * Intent (the brief): the app installs small, then the user installs the model
 * AFTERWARD, FROM A TRUSTED SOURCE, presented during onboarding. The model is
 * verified before use. No auto-download — the user always starts it.
 */

/**
 * One curated, trusted on-device model the user may choose to install.
 *
 * Everything the setup UI needs to be honest is here: human-readable name, the
 * on-disk [sizeBytes] (so we can state the size up front), the [quantization],
 * the [licenseName] + openable [licenseUrl], and whether the model is gated
 * ([requiresProviderAuth]) so the user must accept the provider's license before
 * the file can be fetched. [sourceUrl] is the trusted origin; [sha256] is the
 * expected hash the provisioner checks during the Verifying phase before the
 * model is ever loaded.
 */
data class ModelOption(
    /** Stable id (used to persist the chosen model + resolve files). */
    val id: String,
    /** Human-readable name, e.g. "Gemma 3 1B (instruct)". */
    val displayName: String,
    /** Approximate installed size in bytes (stated to the user before download). */
    val sizeBytes: Long,
    /** Quantization label, e.g. "int4". */
    val quantization: String,
    /** License display name, e.g. "Gemma Terms of Use". */
    val licenseName: String,
    /** Openable link to the full license / model card. */
    val licenseUrl: String,
    /**
     * True for gated models: the user must accept the provider's license (and
     * may need provider auth) before the file can be downloaded.
     */
    val requiresProviderAuth: Boolean,
    /** Trusted source the bundle is fetched from. */
    val sourceUrl: String,
    /** Installed filename (matches the LLM runtime's expected bundle name). */
    val fileName: String,
    /** Expected SHA-256 (hex) used to verify the download before first use. */
    val sha256: String,
) {
    /** Size rendered as a short human string, e.g. "0.5 GB" / "554 MB". */
    val humanSize: String get() = humanBytes(sizeBytes)
}

/** Curated, trusted model options the setup UI offers. */
object ModelCatalog {

    /**
     * The footprint pick first. These are TRUSTED-SOURCE entries the app curates;
     * the sibling's canonical catalog may carry exact sizes/hashes filled in from
     * the published bundles. The UI reads only the fields above, so additions
     * here are safe.
     */
    val options: List<ModelOption> = listOf(
        ModelOption(
            id = "gemma3-1b-it-int4",
            displayName = "Gemma 3 1B (instruct)",
            sizeBytes = 555_000_000L,
            quantization = "int4",
            licenseName = "Gemma Terms of Use",
            licenseUrl = "https://ai.google.dev/gemma/terms",
            requiresProviderAuth = true,
            sourceUrl = "https://huggingface.co/litert-community/Gemma3-1B-IT",
            fileName = "gemma3-1b-it-int4.task",
            sha256 = "",
        ),
        ModelOption(
            id = "llama3.2-3b-it-int4",
            displayName = "Llama 3.2 3B (instruct)",
            sizeBytes = 1_900_000_000L,
            quantization = "int4",
            licenseName = "Llama 3.2 Community License",
            licenseUrl = "https://www.llama.com/llama3_2/license/",
            requiresProviderAuth = true,
            sourceUrl = "https://huggingface.co/litert-community/Llama-3.2-3B-Instruct",
            fileName = "llama-3.2-3b-it-int4.task",
            sha256 = "",
        ),
    )

    /** The recommended default (smallest credible instruct model). */
    val default: ModelOption get() = options.first()

    fun byId(id: String): ModelOption? = options.firstOrNull { it.id == id }
}

/**
 * Progress of a single provisioning run. The setup UI renders a real progress
 * bar from [Downloading], then shows Verifying → Installed, and a clear retry on
 * [Failed].
 */
sealed interface ProvisionState {
    /** Nothing started yet. */
    data object Idle : ProvisionState

    /** Bytes fetched so far. [total] may be 0 if the source didn't report length. */
    data class Downloading(val done: Long, val total: Long) : ProvisionState {
        /** Fraction in 0f..1f, or null when [total] is unknown (indeterminate bar). */
        val fraction: Float? get() = if (total > 0L) (done.toFloat() / total).coerceIn(0f, 1f) else null
    }

    /** Download finished; verifying integrity (hash) before the model is used. */
    data object Verifying : ProvisionState

    /** Model is verified and installed at [path]; on-device AI can light up. */
    data class Installed(val path: String) : ProvisionState

    /** Provisioning failed; [reason] is user-facing. The UI offers retry. */
    data class Failed(val reason: String) : ProvisionState
}

/**
 * Downloads, verifies, and installs a [ModelOption] on-device. The fetch happens
 * at runtime and needs a device + network — there is no auto-download; the caller
 * always initiates [provision].
 */
interface ModelProvisioner {
    /**
     * Provision [option], emitting [ProvisionState] from Downloading → Verifying →
     * Installed (or [ProvisionState.Failed]). Honors [wifiOnly]: if set and the
     * device is not on un-metered Wi-Fi, it fails fast rather than spending mobile
     * data. Collect on a background dispatcher; cancelling the collector aborts.
     */
    fun provision(option: ModelOption, wifiOnly: Boolean = true): Flow<ProvisionState>

    /** Whether [option]'s verified bundle is already installed on this device. */
    fun isInstalled(option: ModelOption): Boolean

    /** Deletes [option]'s installed bundle. Returns true if a file was removed. */
    fun delete(option: ModelOption): Boolean
}

/** Format bytes as a short human string (GB above ~1 GB, else MB). */
fun humanBytes(bytes: Long): String {
    val mb = bytes.toDouble() / 1_000_000.0
    return if (mb >= 1000.0) {
        val gb = mb / 1000.0
        val rounded = (gb * 10).toLong() / 10.0
        "$rounded GB"
    } else {
        "${mb.toLong()} MB"
    }
}
