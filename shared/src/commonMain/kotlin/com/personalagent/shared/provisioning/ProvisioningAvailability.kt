package com.personalagent.shared.provisioning

import com.personalagent.shared.conversation.GenOptions
import com.personalagent.shared.conversation.OnDeviceLlm
import kotlinx.coroutines.flow.Flow

/**
 * Ties model provisioning to the on-device LLM's availability so the AI features
 * "light up" exactly when — and only when — a verified model is installed.
 *
 * The shared [OnDeviceLlm] contract already exposes `isAvailable` ("a usable model
 * is loaded and ready right now"). A real platform engine reports false until its
 * weights are present on device. This decorator makes that dependency explicit and
 * testable in common code: it wraps a [delegate] LLM and reports `isAvailable`
 * only when BOTH
 *   - the chosen [activeOption] is installed & verified ([ModelProvisioner.isInstalled]), and
 *   - the underlying engine itself reports ready ([delegate].isAvailable).
 *
 * So the moment [KtorModelProvisioner.provision] reaches [ProvisionState.Installed]
 * for [activeOption], `isInstalled` flips true and — assuming the engine can load
 * those bytes — the assistant becomes available, with no other wiring. Generation
 * calls pass straight through to [delegate].
 *
 * Platform note: the Android/iOS LLM adapters point their engine at the same model
 * path [ModelFileStore] installs to, so "installed" and "loadable" refer to the
 * same file. This decorator is the portable seam that the onboarding flow and
 * `ConversationService` can both rely on.
 */
class ProvisioningBackedLlm(
    private val delegate: OnDeviceLlm,
    private val provisioner: ModelProvisioner,
    private val activeOption: ModelOption,
) : OnDeviceLlm {

    override val isAvailable: Boolean
        get() = provisioner.isInstalled(activeOption) && delegate.isAvailable

    override suspend fun generate(prompt: String, options: GenOptions): String =
        delegate.generate(prompt, options)

    override fun generateStream(prompt: String, options: GenOptions): Flow<String> =
        delegate.generateStream(prompt, options)
}

/**
 * Convenience read-model for onboarding UIs: "is the assistant ready, and if not,
 * what's the chosen model and is it installed yet?" Pure, synchronous, testable.
 */
class ModelReadiness(
    private val provisioner: ModelProvisioner,
    private val activeOption: ModelOption,
) {
    val option: ModelOption get() = activeOption

    /** True once [activeOption]'s verified bytes are installed at the model path. */
    val isModelInstalled: Boolean get() = provisioner.isInstalled(activeOption)
}
