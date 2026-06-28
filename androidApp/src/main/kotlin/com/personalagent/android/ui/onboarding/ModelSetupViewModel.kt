package com.personalagent.android.ui.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.personalagent.android.AppContainer
import com.personalagent.shared.provisioning.ModelCatalog
import com.personalagent.shared.provisioning.ModelOption
import com.personalagent.shared.provisioning.ModelProvisioner
import com.personalagent.shared.provisioning.ProvisionState
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ModelSetupUiState(
    val options: List<ModelOption> = ModelCatalog.options,
    val selected: ModelOption = ModelCatalog.default,
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
 * Drives on-device model provisioning for both the onboarding "Set up your AI"
 * step and the Settings entry. All download/verify/install work goes through the
 * shared [ModelProvisioner] contract; the UI never touches the network directly.
 *
 * Nothing downloads until [download]/[retry] is called — there is no
 * auto-download.
 */
class ModelSetupViewModel(
    private val provisioner: ModelProvisioner,
) : ViewModel() {

    private val _state = MutableStateFlow(ModelSetupUiState())
    val state: StateFlow<ModelSetupUiState> = _state.asStateFlow()

    private var job: Job? = null

    init { refreshInstalled() }

    /** Re-checks which catalog option is installed on-device right now. */
    fun refreshInstalled() {
        val installed = ModelCatalog.options.firstOrNull { provisioner.isInstalled(it) }
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

    /** Start (or restart) provisioning the selected model. */
    fun download() {
        val s = _state.value
        if (s.isWorking) return
        job?.cancel()
        job = viewModelScope.launch {
            provisioner.provision(s.selected, s.wifiOnly).collect { ps ->
                _state.update { it.copy(provisionState = ps) }
                if (ps is ProvisionState.Installed) {
                    _state.update { it.copy(installedOptionId = it.selected.id) }
                }
            }
        }
    }

    /** Retry after a failure (same as starting again). */
    fun retry() {
        _state.update { it.copy(provisionState = ProvisionState.Idle) }
        download()
    }

    /** Cancel an in-flight download and reset to idle. */
    fun cancel() {
        job?.cancel()
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
            ModelSetupViewModel(container.modelProvisioner) as T
    }
}
