package com.personalagent.android.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.personalagent.android.AppContainer
import com.personalagent.shared.hermes.HermesClient
import com.personalagent.shared.hermes.HermesException
import com.personalagent.shared.hermes.HermesWireMessage
import com.personalagent.shared.hermes.LearningPrompts
import com.personalagent.shared.hermes.WebToolAvailability
import com.personalagent.shared.learning.LearningGoal
import com.personalagent.shared.learning.LearningRecommendationParser
import com.personalagent.shared.learning.LearningResource
import com.personalagent.shared.learning.LearningStore
import com.personalagent.shared.model.Ids
import com.personalagent.shared.util.SystemClock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Phase 6 — the Learning Guide. Goals are declared here (and mirrored to Hermes
 * memory as the current focus), while the AUTHORITATIVE list of goals + resources
 * lives in the local [LearningStore] so the screen is instant/offline. The
 * recommendation loop (Step 2) and status/adaptation (Step 3) build on this.
 */
class LearningViewModel(
    private val hermes: HermesClient,
    private val store: LearningStore,
) : ViewModel() {

    /** Rough starting levels — asked ONCE when a goal is created, then remembered. */
    val levels = listOf("Beginner", "Some experience", "Advanced")

    data class State(
        val goals: List<LearningGoal> = emptyList(),
        // Resources per goal id (authoritative, from the local store).
        val resources: Map<String, List<LearningResource>> = emptyMap(),
        val saving: Boolean = false,
        // Goal id currently being asked "what's next" for (shows a spinner).
        val recommendingGoalId: String? = null,
        val message: String? = null,
        // Web-search backend availability (checked against /v1/toolsets). Null = unknown.
        val webAvailable: Boolean? = null,
    )

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    private val session = "lifeagent-learning"

    init {
        reload()
        checkWebAvailability()
    }

    /** Reload the local (authoritative) goals + their resources. */
    fun reload() {
        val goals = store.goals()
        _state.update {
            it.copy(goals = goals, resources = goals.associate { g -> g.id to store.resources(g.id) })
        }
    }

    /**
     * Detect whether the user's Hermes actually has a web-search backend enabled,
     * so Step 2 can say "web search unavailable — enable a backend" rather than
     * failing silently.
     */
    fun checkWebAvailability() {
        viewModelScope.launch {
            val available = try {
                WebToolAvailability.isWebSearchAvailable(hermes.toolsets())
            } catch (e: Throwable) {
                null // unknown (offline / older Hermes) — don't block goal-setting
            }
            _state.update { it.copy(webAvailable = available) }
        }
    }

    /**
     * Declare a learning goal: store it locally (authoritative) and ask the agent
     * to remember it as the current focus. Level is asked once; style is optional
     * and only recorded if the user volunteered it.
     */
    fun addGoal(topic: String, why: String, level: String?, style: String?) {
        val t = topic.trim()
        if (t.isBlank() || _state.value.saving) return
        val now = SystemClock.nowMillis()
        val goal = LearningGoal(
            id = Ids.next(now),
            topic = t,
            why = why.trim().ifBlank { null },
            level = level?.trim()?.ifBlank { null },
            style = style?.trim()?.ifBlank { null },
            createdAt = now,
        )
        store.addGoal(goal)
        reload()
        _state.update { it.copy(saving = true) }
        viewModelScope.launch {
            try {
                hermes.complete(user(LearningPrompts.saveLearningGoal(goal)), session)
                _state.update { it.copy(saving = false, message = "Learning goal saved") }
            } catch (e: HermesException) {
                // The goal is already saved locally; memory sync is best-effort.
                _state.update { it.copy(saving = false, message = e.message) }
            } catch (e: Throwable) {
                _state.update { it.copy(saving = false, message = "Saved locally; couldn't sync to your agent's memory.") }
            }
        }
    }

    /**
     * Step 2 — ask the agent for the next right free-open-web resource(s) for a
     * goal, filtered against what's already been seen/finished/abandoned. Writes
     * each as RECOMMENDED into the authoritative local store.
     */
    fun recommend(goalId: String) {
        val goal = store.goal(goalId) ?: return
        if (_state.value.recommendingGoalId != null) return
        if (_state.value.webAvailable == false) {
            _state.update { it.copy(message = WebToolAvailability.UNAVAILABLE_MESSAGE) }
            return
        }
        _state.update { it.copy(recommendingGoalId = goalId, message = null) }
        viewModelScope.launch {
            try {
                val avoid = store.resources(goalId)
                val prompt = LearningPrompts.recommendNext(goal, avoid, adaptationHint = null)
                val reply = hermes.complete(user(prompt), session)
                val now = SystemClock.nowMillis()
                val parsed = LearningRecommendationParser.parse(reply, goalId, now)
                val added = store.addRecommendations(goalId, parsed)
                reload()
                val msg = when {
                    added.isNotEmpty() -> "Added ${added.size} suggestion${if (added.size == 1) "" else "s"}."
                    parsed.isEmpty() -> "No new free resources found right now — try again later."
                    else -> "Nothing new — you've already got those."
                }
                _state.update { it.copy(recommendingGoalId = null, message = msg) }
            } catch (e: HermesException) {
                _state.update { it.copy(recommendingGoalId = null, message = e.message) }
            } catch (e: Throwable) {
                _state.update { it.copy(recommendingGoalId = null, message = "Couldn't get a recommendation just now.") }
            }
        }
    }

    fun archiveGoal(id: String) {
        store.setGoalActive(id, active = false)
        reload()
    }

    fun removeGoal(id: String) {
        store.removeGoal(id)
        reload()
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
            return LearningViewModel(client, container.learningStore) as T
        }
    }
}
