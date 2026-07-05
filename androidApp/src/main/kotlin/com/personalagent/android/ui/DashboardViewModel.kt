package com.personalagent.android.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.personalagent.android.AppContainer
import com.personalagent.shared.hermes.HermesClient
import com.personalagent.shared.hermes.HermesHealthDetailed
import com.personalagent.shared.hermes.HermesSessionCard
import com.personalagent.shared.hermes.HermesToolset
import com.personalagent.shared.hermes.UsageSummary
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * The dashboard home. Assembles a live view from real Hermes read endpoints:
 * `/health/detailed`, `/api/sessions`, `/v1/toolsets`. Each endpoint is fetched
 * independently so one failing doesn't blank the whole board — only if everything
 * fails do we show an error. No mocks: empty responses render honest empty states.
 */
class DashboardViewModel(
    private val hermes: HermesClient,
) : ViewModel() {

    data class State(
        val loading: Boolean = true,
        val error: String? = null,
        val health: HermesHealthDetailed? = null,
        val usage: UsageSummary? = null,
        val sessions: List<HermesSessionCard> = emptyList(),
        val toolsets: List<HermesToolset> = emptyList(),
    )

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    init { refresh() }

    fun refresh() {
        _state.update { it.copy(loading = true) }
        viewModelScope.launch {
            val health = runCatching { hermes.healthDetailed() }.getOrNull()
            val sessions = runCatching { hermes.sessions() }
                .getOrDefault(emptyList())
                .sortedByDescending { it.lastActiveMillis ?: 0 }
            val toolsets = runCatching { hermes.toolsets() }.getOrDefault(emptyList())

            val allFailed = health == null && sessions.isEmpty() && toolsets.isEmpty()
            _state.update {
                it.copy(
                    loading = false,
                    health = health,
                    sessions = sessions,
                    toolsets = toolsets,
                    usage = UsageSummary.from(sessions),
                    error = if (allFailed) "Couldn't reach your Hermes. Pull to refresh once it's running." else null,
                )
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        hermes.close()
    }

    class Factory(private val container: AppContainer) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            val client: HermesClient = container.hermesClientOrNull()
                ?: error("Hermes is not configured — Connect screen should gate this.")
            return DashboardViewModel(client) as T
        }
    }
}
