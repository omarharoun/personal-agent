package com.personalagent.android.ui.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.personalagent.android.AppContainer
import com.personalagent.android.llm.ModelDownloadController
import com.personalagent.shared.provisioning.DefaultModelCatalog
import com.personalagent.shared.provisioning.ModelOption
import com.personalagent.shared.provisioning.ModelProvisioner
import com.personalagent.shared.provisioning.ProvisionState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ModelSetupUiState(
    val options: List<ModelOption> = DefaultModelCatalog().options(),
    val selected: ModelOption = DefaultModelCatalog.DEFAULT,
    /** Download-over-Wi-Fi only. Defaults ON, per the brief. */
    val wifiOnly: Boolean = true,
    val provisionState: ProvisionState = ProvisionState.Idle,
    /** Id of the model that is currently installed on-device, if any. */
    val installedOptionId: String? = null,
) {
    val isDownloading: Boolean get() = provisionState is ProvisionState.Downloading
    val isWorking: Boolean
        get() = provisionState is ProvisionState.Downloading || provisionState is ProvisionState.Verifying
    val isInstalled: Boolean get() = installedOptionId == selected.id
}

/**
 * Drives on-device model provisioning for the onboarding "Set up your AI" step and
 * the Settings entry.
 *
 * The download itself runs in a WorkManager **foreground service** (see
 * [ModelDownloadController]/[com.personalagent.android.llm.ModelDownloadWorker]),
 * so it survives the app backgrounding, the screen locking, and process death. This
 * ViewModel only *observes* that work — so closing/reopening the screen never kills
 * an in-flight download. Nothing downloads until [download] is called.
 */
class ModelSetupViewModel(
    private val provisioner: ModelProvisioner,
    private val downloads: ModelDownloadController,
) : ViewModel() {

    private val _state = MutableStateFlow(ModelSetupUiState())
    val state: StateFlow<ModelSetupUiState> = _state.asStateFlow()

    init {
        refreshInstalled()
        // Re-attach to any download already running (or just-finished) so reopening
        // the screen reflects the live foreground-service state.
        viewModelScope.launch {
            downloads.currentOptionId()?.let { id ->
                DefaultModelCatalog().options().firstOrNull { it.id == id }
                    ?.let { running -> _state.update { it.copy(selected = running) } }
            }
        }
        observeDownload()
    }

    private fun observeDownload() {
        viewModelScope.launch {
            downloads.stateFlow(fallbackTotal = _state.value.selected.sizeBytes).collect { ps ->
                _state.update { it.copy(provisionState = ps) }
                if (ps is ProvisionState.Installed) {
                    _state.update { it.copy(installedOptionId = it.selected.id) }
                    refreshInstalled()
                }
            }
        }
    }

    /** Re-checks which catalog option is installed on-device right now. */
    fun refreshInstalled() {
        val installed = DefaultModelCatalog().options().firstOrNull { provisioner.isInstalled(it) }
        _state.update {
            it.copy(
                installedOptionId = installed?.id,
                // Pre-select the installed model so Settings opens on it.
                selected = installed ?: it.selected,
            )
        }
    }

    fun selectOption(option: ModelOption) {
        if (_state.value.isWorking) return
        _state.update { it.copy(selected = option) }
    }

    fun setWifiOnly(enabled: Boolean) = _state.update { it.copy(wifiOnly = enabled) }

    /** Start (or resume) downloading the selected model in the foreground service. */
    fun download() {
        val s = _state.value
        if (s.isWorking) return
        downloads.enqueue(s.selected, s.wifiOnly)
    }

    /** Retry after a failure (re-enqueue; resumes from any partial file). */
    fun retry() = download()

    /** Cancel the in-flight download and reset to idle (discards the partial file). */
    fun cancel() {
        downloads.cancel()
        _state.update { it.copy(provisionState = ProvisionState.Idle) }
    }

    /** Delete the installed bundle for the selected model. */
    fun delete() {
        val s = _state.value
        if (s.isWorking) return
        provisioner.delete(s.selected)
        _state.update { it.copy(provisionState = ProvisionState.Idle) }
        refreshInstalled()
    }

    class Factory(private val container: AppContainer) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            ModelSetupViewModel(container.modelProvisioner, container.modelDownloadController) as T
    }
}
