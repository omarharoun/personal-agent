package com.personalagent.android.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.personalagent.android.AppContainer
import com.personalagent.shared.hermes.HermesClient
import com.personalagent.shared.knowledge.KnowledgeGraph
import com.personalagent.shared.knowledge.KnowledgeGraphService
import com.personalagent.shared.knowledge.KnowledgeNode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Drives the Knowledge screen: paints the cached chat-derived graph instantly, then
 * revalidates in the background (stale-while-revalidate). Rebuilds prefer a Hermes
 * structured-JSON extraction and fall back to the offline keyword pass — see
 * [KnowledgeGraphService]. The graph is honestly "derived from your conversations",
 * never presented as Hermes memory.
 */
class KnowledgeGraphViewModel(
    private val service: KnowledgeGraphService,
    /** May be null if not connected — then rebuild uses the offline fallback. */
    private val hermes: HermesClient?,
) : ViewModel() {

    data class State(
        val graph: KnowledgeGraph? = null,
        val building: Boolean = false,
        val selected: KnowledgeNode? = null,
        val error: String? = null,
    )

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    init {
        val cached = service.cached()
        _state.update { it.copy(graph = cached) }
        // Revalidate quietly if the chat records have changed or the cache aged out.
        if (service.shouldRebuild(now())) rebuild()
    }

    /** Force a rebuild (the screen's "Rebuild" action). */
    fun rebuild() {
        if (_state.value.building) return
        viewModelScope.launch {
            _state.update { it.copy(building = true, error = null) }
            val graph = runCatching { service.rebuild(hermes, now()) }.getOrNull()
            _state.update { st ->
                st.copy(
                    building = false,
                    graph = graph ?: st.graph,
                    error = if (graph == null) "Couldn't rebuild the map right now." else null,
                )
            }
        }
    }

    fun selectNode(node: KnowledgeNode?) = _state.update { it.copy(selected = node) }

    override fun onCleared() {
        super.onCleared()
        hermes?.close()
    }

    private fun now(): Long = System.currentTimeMillis()

    class Factory(private val container: AppContainer) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            KnowledgeGraphViewModel(
                service = container.knowledgeGraphService,
                hermes = container.hermesClientOrNull(),
            ) as T
    }
}
