package com.personalagent.shared.provisioning

import kotlinx.coroutines.flow.Flow

/**
 * One downloadable on-device model the app is willing to install.
 *
 * 🤝 SHARED CONTRACT — model-provisioning step. THREE agents build to this EXACT
 * package (`com.personalagent.shared.provisioning`) and these EXACT signatures:
 *   - `:shared` (this slice) defines the contract, the curated [ModelCatalog],
 *     the security-hardened [KtorModelProvisioner], and the tests.
 *   - the Android sibling builds the onboarding UI against [ModelCatalog] /
 *     [ModelProvisioner] / [ProvisionState].
 *   - the iOS sibling builds the same onboarding UI against the same types.
 *
 * 🔒 SECURITY BOUNDARY: a [ModelOption] only ever originates from the curated
 * [ModelCatalog] — it is NEVER constructed from a user-entered URL. Every field
 * here is publisher metadata the app trusts; the [url] is fetched and the bytes
 * are accepted **only** if their SHA-256 equals [sha256]. See [KtorModelProvisioner].
 *
 * @param id stable machine identifier (used to derive the on-device model path).
 * @param displayName human label shown in onboarding.
 * @param sizeBytes expected download size in bytes (UI shows it before download;
 *   also a sanity bound — a body far larger than this is rejected).
 * @param url HTTPS download URL on a trusted host (see [TrustedHosts]); never a
 *   user-supplied URL.
 * @param sha256 lowercase hex SHA-256 of the exact file at [url]. The downloaded
 *   bytes must hash to this or they are discarded. An entry whose checksum has
 *   not yet been pinned uses [UNPINNED_SHA256] and fails closed (never installs).
 * @param quant quantization label (e.g. `Q4_K_M`) shown for transparency.
 * @param licenseName SPDX-ish license name (e.g. `Apache-2.0`, `Gemma`).
 * @param licenseUrl canonical license / model-card URL the user can read.
 * @param requiresProviderAuth true when the publisher gates the download behind an
 *   accepted license / access token (the UI must route the user through the
 *   provider's consent + token flow first). False = fetchable on the default path.
 * @param note short honest caveat shown in onboarding (gating, checksum status…).
 */
data class ModelOption(
    val id: String,
    val displayName: String,
    val sizeBytes: Long,
    val url: String,
    val sha256: String,
    val quant: String,
    val licenseName: String,
    val licenseUrl: String,
    val requiresProviderAuth: Boolean,
    val note: String,
)

/**
 * The CURATED set of trusted, installable models. Implementations return only
 * vetted entries from trusted sources — **never** anything derived from
 * user-entered URLs. This is the trust root of the whole feature.
 */
interface ModelCatalog {
    fun options(): List<ModelOption>
}

/**
 * The lifecycle of one provisioning attempt, surfaced to the onboarding UI.
 *
 * Happy path: [Downloading] (emitted repeatedly with growing progress) →
 * [Verifying] → [Installed]. Any guard failure, transport error, or checksum
 * mismatch ends in [Failed] with a short, non-sensitive [Failed.reason], and the
 * partially-downloaded bytes are discarded (an unverified blob is NEVER activated).
 */
sealed interface ProvisionState {
    /** Resting state: nothing in flight. */
    object Idle : ProvisionState

    /**
     * Streaming download in progress. [downloadedBytes] is cumulative and
     * monotonically non-decreasing; [totalBytes] is the expected size (the
     * server's Content-Length when present, otherwise [ModelOption.sizeBytes]).
     */
    data class Downloading(val downloadedBytes: Long, val totalBytes: Long) : ProvisionState

    /** Download finished; the SHA-256 of the bytes is being checked. */
    object Verifying : ProvisionState

    /** Bytes verified and atomically installed at the model path. AI features can light up. */
    object Installed : ProvisionState

    /** Provisioning aborted. [reason] is operator-facing and carries no secrets. */
    data class Failed(val reason: String) : ProvisionState
}

/**
 * Downloads, verifies, and installs a [ModelOption], and reports whether one is
 * already installed.
 *
 * 🤝 SHARED CONTRACT — EXACT signatures the platform UIs depend on.
 */
interface ModelProvisioner {
    /** True when [option] is already present and verified at the model path. */
    fun isInstalled(option: ModelOption): Boolean

    /**
     * Download → verify SHA-256 → install at the model path, as a cold [Flow] of
     * [ProvisionState]. Collecting starts the work; cancelling the collection
     * aborts the download and discards any partial file.
     *
     * @param wifiOnly a preference the UI honors before collecting (the provisioner
     *   cannot itself see the radio in common code); carried through for telemetry
     *   and so a future platform gate can consult it.
     */
    fun provision(option: ModelOption, wifiOnly: Boolean = true): Flow<ProvisionState>

    /** Remove an installed [option] from the model path. */
    fun delete(option: ModelOption)
}
