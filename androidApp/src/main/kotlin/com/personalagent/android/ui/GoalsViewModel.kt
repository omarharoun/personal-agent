package com.personalagent.android.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.personalagent.android.AppContainer
import com.personalagent.shared.hermes.HermesClient
import com.personalagent.shared.hermes.HermesException
import com.personalagent.shared.hermes.HermesWireMessage
import com.personalagent.shared.hermes.LifePrompts
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * The life-improvement layer (Phase 3). Goals + personalized nudges are pure
 * **prompt/interaction design on top of Hermes** (see [LifePrompts]) — the app
 * frames prompts that make the agent draw on its real memory of the user. There
 * is no second AI and no local copy of goal content: goals live in Hermes memory.
 */
class GoalsViewModel(
    private val hermes: HermesClient,
) : ViewModel() {

    data class State(
        val goalsSummary: String = "",
        val loadingGoals: Boolean = false,
        val saving: Boolean = false,
        val message: String? = null,
    )

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    private val session = "lifeagent-goals"

    fun refreshGoals() {
        _state.update { it.copy(loadingGoals = true) }
        viewModelScope.launch {
            try {
                val summary = hermes.complete(user(LifePrompts.listGoals()), session)
                _state.update { it.copy(goalsSummary = summary, loadingGoals = false) }
            } catch (e: HermesException) {
                _state.update { it.copy(loadingGoals = false, message = e.message) }
            } catch (e: Throwable) {
                _state.update { it.copy(loadingGoals = false, message = e.message ?: "Couldn't load goals.") }
            }
        }
    }

    fun addGoal(category: String, goal: String) {
        val text = goal.trim()
        if (text.isBlank() || _state.value.saving) return
        _state.update { it.copy(saving = true) }
        viewModelScope.launch {
            try {
                hermes.complete(user(LifePrompts.saveGoal(category, text)), session)
                _state.update { it.copy(saving = false, message = "Goal saved") }
                refreshGoals()
            } catch (e: HermesException) {
                _state.update { it.copy(saving = false, message = e.message) }
            } catch (e: Throwable) {
                _state.update { it.copy(saving = false, message = e.message ?: "Couldn't save the goal.") }
            }
        }
    }

    fun consumeMessage() = _state.update { it.copy(message = null) }

    private fun user(content: String) = listOf(HermesWireMessage("user", content))

    override fun onCleared() {
        super.onCleared()
        hermes.close()
    }

    class Factory(private val container: AppContainer) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            val client = container.hermesClientOrNull()
                ?: error("Hermes is not configured — Connect screen should gate this.")
            return GoalsViewModel(client) as T
        }
    }
}
