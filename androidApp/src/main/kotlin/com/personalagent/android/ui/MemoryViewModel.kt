package com.personalagent.android.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.personalagent.android.AppContainer
import com.personalagent.shared.graph.MemoryEdge
import com.personalagent.shared.graph.MemoryGraphService
import com.personalagent.shared.graph.MemoryNode
import com.personalagent.shared.graph.MemoryNodeType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** A node plus the human-readable relationships it participates in (for display). */
data class MemoryItem(
    val node: MemoryNode,
    val relations: List<String>,
)

data class MemoryUiState(
    val byType: Map<MemoryNodeType, List<MemoryItem>> = emptyMap(),
    val total: Int = 0,
    val loading: Boolean = true,
)

/**
 * Drives the Memory screen: lists graph items grouped by type with their
 * relationships, and edits/deletes/exports/imports — all via [MemoryGraphService]
 * (the encrypted on-device store). Nothing here ever touches the network.
 */
class MemoryViewModel(private val graph: MemoryGraphService) : ViewModel() {

    private val _state = MutableStateFlow(MemoryUiState())
    val state: StateFlow<MemoryUiState> = _state.asStateFlow()

    init { refresh() }

    fun refresh() = viewModelScope.launch {
        val nodes = graph.allNodes()
        val edges = graph.allEdges()
        val labelById = nodes.associate { it.id to it.label }
        val items = nodes.map { n ->
            val rels = edges.mapNotNull { e ->
                when (n.id) {
                    e.fromId -> "${e.relation.replace('_', ' ')} → ${labelById[e.toId] ?: "?"}"
                    e.toId -> "${labelById[e.fromId] ?: "?"} → ${e.relation.replace('_', ' ')}"
                    else -> null
                }
            }
            MemoryItem(n, rels)
        }
        _state.value = MemoryUiState(
            byType = items.groupBy { it.node.type }.toSortedMap(compareBy { it.name }),
            total = nodes.size,
            loading = false,
        )
    }

    fun edit(id: String, label: String, attributes: Map<String, String>) = viewModelScope.launch {
        graph.editNode(id, label, attributes)
        refresh()
    }

    fun delete(id: String) = viewModelScope.launch {
        graph.deleteNode(id)
        refresh()
    }

    fun clearAll() = viewModelScope.launch {
        graph.clear()
        refresh()
    }

    suspend fun exportJson(): String = graph.exportJson()

    fun import(text: String, onResult: (Int) -> Unit) = viewModelScope.launch {
        val count = graph.importJson(text)
        refresh()
        onResult(count)
    }

    class Factory(private val container: AppContainer) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            MemoryViewModel(container.memoryGraph) as T
    }
}
