package com.personalagent.android.ui

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.personalagent.android.AppContainer
import com.personalagent.android.notification.ReflectionScheduling
import com.personalagent.shared.hermes.HermesClient
import com.personalagent.shared.hermes.HermesException
import com.personalagent.shared.hermes.HermesWireMessage
import com.personalagent.shared.hermes.LifePrompts
import com.personalagent.shared.hermes.ReflectionCadence
import com.personalagent.shared.hermes.ReflectionStore
import com.personalagent.shared.util.SystemClock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Phase 4 reflection. Owns the cadence setting (one-tap Off/Weekly/Monthly) and
 * fetches a warm, memory-grounded reflection from Hermes on demand. Everything
 * here is designed to never nag: cadence is trivially changed, snooze is one tap,
 * and reflections are personalized via memory (see [LifePrompts.reflection]).
 */
class ReflectionViewModel(
    private val hermes: HermesClient,
    private val store: ReflectionStore,
    private val appContext: Context,
) : ViewModel() {

    data class State(
        val cadence: ReflectionCadence = ReflectionCadence.OFF,
        val reflection: String = "",
        val loading: Boolean = false,
        val message: String? = null,
    )

    private val _state = MutableStateFlow(State(cadence = store.load().cadence))
    val state: StateFlow<State> = _state.asStateFlow()

    fun setCadence(cadence: ReflectionCadence) {
        store.setCadence(cadence, SystemClock.nowMillis())
        _state.update { it.copy(cadence = cadence) }
        if (cadence == ReflectionCadence.OFF) {
            ReflectionScheduling.cancel(appContext)
        } else {
            ReflectionScheduling.ensureDaily(appContext)
        }
    }

    /** Fetch a reflection now (also used when opened from the notification). */
    fun reflectNow() {
        val cadence = _state.value.cadence.takeIf { it != ReflectionCadence.OFF }
            ?: ReflectionCadence.WEEKLY // allow a one-off even when scheduling is off
        _state.update { it.copy(loading = true) }
        viewModelScope.launch {
            try {
                val text = hermes.complete(
                    listOf(HermesWireMessage("user", LifePrompts.reflection(cadence.promptWord))),
                    sessionId = "lifeagent-reflection",
                )
                store.markShown(SystemClock.nowMillis())
                _state.update { it.copy(reflection = text, loading = false) }
            } catch (e: HermesException) {
                _state.update { it.copy(loading = false, message = e.message) }
            } catch (e: Throwable) {
                _state.update { it.copy(loading = false, message = e.message ?: "Couldn't load a reflection.") }
            }
        }
    }

    /** Snooze the next scheduled reflection by [days]. */
    fun snooze(days: Long) {
        val until = SystemClock.nowMillis() + days * 24 * 60 * 60 * 1000
        store.snoozeUntil(until)
        _state.update { it.copy(message = "Snoozed for $days day${if (days == 1L) "" else "s"}") }
    }

    fun consumeMessage() = _state.update { it.copy(message = null) }

    override fun onCleared() {
        super.onCleared()
        hermes.close()
    }

    class Factory(private val container: AppContainer) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            val client = container.hermesClientOrNull()
                ?: error("Hermes is not configured — Connect screen should gate this.")
            return ReflectionViewModel(client, container.reflectionStore, container.androidContext) as T
        }
    }
}
