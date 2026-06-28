package com.personalagent.shared.provisioning

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.trySendBlocking
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive

/**
 * Bridges a Swift [IosNativeModelProvisioner] to the shared [ModelProvisioner].
 *
 * The native download/verify/install is synchronous and blocking: it runs on the
 * [channelFlow] producer coroutine (shifted to [Dispatchers.Default] via
 * [flowOn], so the network/IO never blocks the main/UI thread) and feeds
 * [ProvisionState] into the channel with [trySendBlocking], which applies natural
 * back-pressure if a slow collector falls behind. [buffer] decouples
 * producer/collector cadence so progress emission isn't gated by UI rendering.
 *
 * Cancelling the collector flips the producer's [isActive] to false; the native
 * side polls that via the `isCancelled` callback and aborts the transfer. Mirrors
 * `IosLlmAdapter.generateStream` (Step 3).
 */
class IosModelProvisioningAdapter(
    private val native: IosNativeModelProvisioner,
) : ModelProvisioner {

    override fun isInstalled(option: ModelOption): Boolean =
        native.isInstalled(option.fileName)

    override fun delete(option: ModelOption): Boolean =
        native.delete(option.fileName)

    override fun provision(option: ModelOption, wifiOnly: Boolean): Flow<ProvisionState> =
        channelFlow {
            val outcome = native.provision(
                sourceUrl = option.sourceUrl,
                fileName = option.fileName,
                expectedSha256 = option.sha256,
                expectedSize = option.sizeBytes,
                wifiOnly = wifiOnly,
                onProgress = { done, total -> trySendBlocking(ProvisionState.Downloading(done, total)) },
                onVerifying = { trySendBlocking(ProvisionState.Verifying) },
                isCancelled = { !isActive },
            )
            val path = outcome.installedPath
            val reason = outcome.failureReason
            when {
                path != null -> trySendBlocking(ProvisionState.Installed(path))
                reason != null -> trySendBlocking(ProvisionState.Failed(reason))
                // Both null → cancelled: emit nothing; the flow just completes.
            }
        }
            .buffer()
            .flowOn(Dispatchers.Default)
}
