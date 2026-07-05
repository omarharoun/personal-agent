package com.personalagent.android.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.personalagent.android.AppContainer
import com.personalagent.shared.hermes.HermesClient
import com.personalagent.shared.hermes.HermesException
import com.personalagent.shared.hermes.HermesSkill
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * The skills gallery — the real `/v1/skills` list (68 skills, name/description/
 * category). No mocks; if it's empty we say so. Category icons are OUR mapping
 * (Hermes ships no skill icons), applied in the screen.
 */
class SkillsViewModel(
    private val hermes: HermesClient,
) : ViewModel() {

    data class State(
        val loading: Boolean = true,
        val error: String? = null,
        val all: List<HermesSkill> = emptyList(),
        val query: String = "",
    ) {
        /** Skills matching the query, grouped by category (uncategorised last). */
        val grouped: List<Pair<String, List<HermesSkill>>>
            get() {
                val q = query.trim().lowercase()
                val filtered = if (q.isBlank()) all else all.filter {
                    it.name.lowercase().contains(q) ||
                        it.description.lowercase().contains(q) ||
                        (it.category ?: "").lowercase().contains(q)
                }
                return filtered
                    .groupBy { it.category?.takeIf { c -> c.isNotBlank() } ?: "other" }
                    .toList()
                    .sortedWith(compareBy({ it.first == "other" }, { it.first }))
                    .map { (cat, skills) -> cat to skills.sortedBy { it.name } }
            }
    }

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    init { refresh() }

    fun refresh() {
        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            try {
                val skills = hermes.skills()
                _state.update { it.copy(loading = false, all = skills) }
            } catch (e: HermesException) {
                _state.update { it.copy(loading = false, error = e.message) }
            } catch (e: Throwable) {
                _state.update { it.copy(loading = false, error = e.message ?: "Couldn't load skills.") }
            }
        }
    }

    fun onQueryChange(q: String) = _state.update { it.copy(query = q) }

    override fun onCleared() {
        super.onCleared()
        hermes.close()
    }

    class Factory(private val container: AppContainer) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            val client: HermesClient = container.hermesClientOrNull()
                ?: error("Hermes is not configured — Connect screen should gate this.")
            return SkillsViewModel(client) as T
        }
    }
}
