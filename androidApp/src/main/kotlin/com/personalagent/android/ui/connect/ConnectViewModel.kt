package com.personalagent.android.ui.connect

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.personalagent.android.AppContainer
import com.personalagent.shared.hermes.HermesConfig
import com.personalagent.shared.hermes.HermesException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Drives the Connect screen: test a user-entered Hermes (base URL + API key)
 * against `GET /health`, and on success persist it via [AppContainer.hermesConfigStore].
 *
 * 🔒 REVIEW REQUIRED (trust boundary + credentials). The base URL the user types
 * IS the backend — there is no default. The API key is handed straight to the
 * secure store on success and is never logged. On failure we surface the
 * client's plain-language reason so the user can fix *their own* server.
 */
class ConnectViewModel(private val container: AppContainer) : ViewModel() {

    data class State(
        val baseUrl: String = "",
        val apiKey: String = "",
        val testing: Boolean = false,
        val error: String? = null,
        /** Non-null when the normalized URL is a plaintext remote host (warn, don't block). */
        val plaintextWarning: String? = null,
        val connectedVersion: String? = null,
    )

    private val _state = MutableStateFlow(
        State(baseUrl = container.hermesConfigStore.load()?.baseUrl ?: "http://")
    )
    val state: StateFlow<State> = _state.asStateFlow()

    fun onBaseUrlChange(v: String) = _state.update {
        it.copy(baseUrl = v, error = null, plaintextWarning = plaintextWarningFor(v))
    }

    fun onApiKeyChange(v: String) = _state.update { it.copy(apiKey = v, error = null) }

    private fun plaintextWarningFor(raw: String): String? {
        val norm = HermesConfig.normalizeBaseUrl(raw) ?: return null
        val probe = HermesConfig(norm, apiKey = "x", sessionKey = "x")
        return if (probe.isPlaintextRemote) {
            "This is a plaintext (http://) address on a remote host. Your key and data " +
                "would cross the network unencrypted. Prefer https, a VPN, or a local/LAN address."
        } else null
    }

    /**
     * Normalize + test the connection. Calls [onConnected] with the saved config
     * on success. Never throws to the UI — failures land in [State.error].
     */
    fun testAndConnect(onConnected: () -> Unit) {
        val s = _state.value
        val norm = HermesConfig.normalizeBaseUrl(s.baseUrl)
        if (norm == null) {
            _state.update { it.copy(error = "Enter your Hermes address, e.g. http://192.168.1.20:8642") }
            return
        }
        if (s.apiKey.isBlank()) {
            _state.update { it.copy(error = "Enter the API key you set on your Hermes (API_SERVER_KEY).") }
            return
        }
        _state.update { it.copy(testing = true, error = null) }
        viewModelScope.launch {
            val config = HermesConfig(
                baseUrl = norm,
                apiKey = s.apiKey.trim(),
                sessionKey = container.hermesConfigStore.sessionKey(),
            )
            val client = container.hermesClientFor(config)
            try {
                val health = client.health()
                if (health.status?.equals("ok", ignoreCase = true) != true) {
                    _state.update {
                        it.copy(testing = false, error = "Your Hermes replied but not with status ok. Is the API server healthy?")
                    }
                    return@launch
                }
                // Verified — persist (base URL normalized, key sealed) and enter.
                container.hermesConfigStore.save(norm, s.apiKey.trim())
                _state.update { it.copy(testing = false, connectedVersion = health.version, error = null) }
                onConnected()
            } catch (e: HermesException) {
                _state.update { it.copy(testing = false, error = e.message ?: "Couldn't connect.") }
            } catch (e: Throwable) {
                _state.update { it.copy(testing = false, error = "Couldn't connect: ${e.message ?: e::class.simpleName}") }
            } finally {
                client.close()
            }
        }
    }

    class Factory(private val container: AppContainer) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            ConnectViewModel(container) as T
    }
}
