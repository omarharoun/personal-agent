package com.personalagent.shared.provisioning

/**
 * Synchronous, Swift-facing seam for on-device model provisioning.
 *
 * WHY a separate (non-`suspend`, non-`Flow`) interface, instead of letting Swift
 * implement [ModelProvisioner] directly:
 *
 *   [ModelProvisioner.provision] returns a [Flow]. Having Swift *produce* a Kotlin
 *   [Flow] across the Kotlin/Native bridge is the most fragile corner of the
 *   interop (the supported, stable direction is Swift *calling* Kotlin
 *   suspend/Flow). So Swift implements this plain, blocking interface — the
 *   download/verify/install runs to completion and returns an [IosProvisionOutcome],
 *   pushing progress through Kotlin callbacks it just *invokes* — and
 *   [IosModelProvisioningAdapter] adapts it to the shared [ModelProvisioner]
 *   contract, moving the network/IO work off the caller's thread. This mirrors
 *   `IosNativeLlm`/`IosLlmAdapter` (Step 3) and `IosNativeEmbedder` (Step 2).
 *
 * Implemented in Swift by `IosModelProvisioner` (URLSession + CryptoKit). See
 * `iosApp/iosApp/IosModelProvisioner.swift`.
 */
interface IosNativeModelProvisioner {

    /** Whether [fileName]'s verified bundle already exists on this device. */
    fun isInstalled(fileName: String): Boolean

    /** Deletes [fileName]'s installed bundle. Returns true if a file was removed. */
    fun delete(fileName: String): Boolean

    /**
     * Blocking download → verify → install. Called by the adapter on
     * `Dispatchers.Default`, never the main thread, so blocking here is safe.
     *
     * Streams progress by invoking the Kotlin callbacks (the supported interop
     * direction — Swift just *calls* them):
     *  - [onProgress] `(done, total)` repeatedly during the download. [total] is 0
     *    when the source didn't report a content length (drives an indeterminate bar).
     *  - [onVerifying] once, after the bytes land and before the hash check.
     *  - [isCancelled] is polled during the download; when it returns true the
     *    Swift side aborts the transfer, deletes the partial file, and returns an
     *    outcome with neither path nor reason (a silent cancellation).
     *
     * Honors [wifiOnly]: if set and the device is not on un-metered Wi-Fi, the
     * Swift side returns a [IosProvisionOutcome] failure WITHOUT spending bytes.
     * [expectedSha256] (when non-empty) is verified before the file is promoted;
     * a mismatch fails and nothing is installed. [expectedSize] is a fallback
     * total when the server omits Content-Length.
     */
    fun provision(
        sourceUrl: String,
        fileName: String,
        expectedSha256: String,
        expectedSize: Long,
        wifiOnly: Boolean,
        onProgress: (Long, Long) -> Unit,
        onVerifying: () -> Unit,
        isCancelled: () -> Boolean,
    ): IosProvisionOutcome
}

/**
 * Terminal result of one native provisioning run.
 *
 * Exactly one of [installedPath] / [failureReason] is non-null on a completed
 * run; both null means the run was cancelled (no state to emit). Modeled as a
 * plain data class so it crosses the ObjC/Swift bridge cleanly.
 */
data class IosProvisionOutcome(
    /** Absolute path of the installed bundle on success; null otherwise. */
    val installedPath: String?,
    /** User-facing failure reason on failure; null on success/cancel. */
    val failureReason: String?,
) {
    companion object {
        fun installed(path: String) = IosProvisionOutcome(path, null)
        fun failed(reason: String) = IosProvisionOutcome(null, reason)
        fun cancelled() = IosProvisionOutcome(null, null)
    }
}
