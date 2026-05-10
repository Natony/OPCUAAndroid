package com.example.s7opcuaapp.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.s7opcuaapp.data.api.PlcApiClient
import com.example.s7opcuaapp.data.api.PlcDto
import com.example.s7opcuaapp.data.auth.AuthManager
import com.example.s7opcuaapp.data.auth.LockManager
import com.example.s7opcuaapp.data.local.ConnectionMode
import com.example.s7opcuaapp.data.local.PrefsManager
import com.example.s7opcuaapp.data.model.DirectPlcConfig
import com.example.s7opcuaapp.data.model.DirectSecurityPolicy
import com.example.s7opcuaapp.data.opcua.DirectOpcUaClient
import com.example.s7opcuaapp.data.repository.RepositoryProvider
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * UI State for PLC selection screen
 */
data class ConfigUiState(
    val serverPlcs: List<PlcDto> = emptyList(),
    val selectedPlcId: String? = null,
    val selectedPlcName: String? = null,
    val isLoadingPlcs: Boolean = false,
    val errorMessage: String? = null,
    // Direct OPC UA backup mode
    val connectionMode: ConnectionMode = ConnectionMode.API,
    val directConfig: DirectPlcConfig = DirectPlcConfig(),
    val directTestState: DirectTestState = DirectTestState.Idle
) {
    sealed class DirectTestState {
        object Idle : DirectTestState()
        object Testing : DirectTestState()
        data class Ok(val message: String) : DirectTestState()
        data class Failed(val message: String) : DirectTestState()
    }
}

/**
 * ViewModel for PLC selection (API mode) and Direct OPC UA backup configuration.
 * The connection-mode toggle drives which subsection of the UI is shown.
 */
@HiltViewModel
class ConfigViewModel @Inject constructor(
    private val prefsManager: PrefsManager,
    private val plcApiClient: PlcApiClient,
    private val repositoryProvider: RepositoryProvider,
    private val authManager: AuthManager,
    private val lockManager: LockManager
) : ViewModel() {

    companion object {
        private const val TAG = "ConfigViewModel"
    }

    private val _uiState = MutableStateFlow(ConfigUiState())
    val uiState: StateFlow<ConfigUiState> = _uiState

    init {
        Log.d(TAG, "🚀 ConfigViewModel initialized")
        loadSavedSelection()
        loadConnectionModeAndDirectConfig()
        if (_uiState.value.connectionMode == ConnectionMode.API) {
            loadServerPlcs()
        }
    }

    private fun loadConnectionModeAndDirectConfig() {
        val mode = prefsManager.getConnectionMode()
        val direct = prefsManager.getDirectPlcConfig()
        Log.d(TAG, "📂 Loaded connection mode: $mode, direct endpoint: ${direct.endpointUrl}")
        _uiState.value = _uiState.value.copy(
            connectionMode = mode,
            directConfig = direct
        )
    }

    /**
     * Load previously selected PLC from preferences
     */
    private fun loadSavedSelection() {
        val savedPlcId = prefsManager.getSelectedPlcId()
        val savedPlcName = prefsManager.getSelectedPlcName()
        Log.d(TAG, "📂 Loaded saved PLC: id=$savedPlcId, name=$savedPlcName")
        _uiState.value = _uiState.value.copy(
            selectedPlcId = savedPlcId,
            selectedPlcName = savedPlcName
        )
    }

    /**
     * Load PLCs from API server
     */
    fun loadServerPlcs() {
        viewModelScope.launch {
            Log.d(TAG, "🔄 Loading PLCs from server...")
            _uiState.value = _uiState.value.copy(isLoadingPlcs = true, errorMessage = null)
            try {
                val plcs = plcApiClient.getAllPlcs()
                Log.d(TAG, "✅ Loaded ${plcs.size} PLCs from server:")
                plcs.forEachIndexed { index, plc ->
                    Log.d(TAG, "   [$index] ${plc.name} (ID: ${plc.id}, Endpoint: ${plc.endpointUrl}, State: ${plc.connectionState})")
                }
                _uiState.value = _uiState.value.copy(
                    serverPlcs = plcs,
                    isLoadingPlcs = false
                )
            } catch (e: Exception) {
                Log.e(TAG, "❌ Error loading PLCs", e)
                _uiState.value = _uiState.value.copy(
                    isLoadingPlcs = false,
                    errorMessage = "Không thể tải danh sách PLC: ${e.message}"
                )
            }
        }
    }

    /**
     * Select a PLC from server list
     */
    fun onSelectServerPlc(plc: PlcDto, onSuccess: () -> Unit) {
        viewModelScope.launch {
            Log.d(TAG, "═══════════════════════════════════════════════")
            Log.d(TAG, "🎯 USER SELECTED PLC:")
            Log.d(TAG, "   Name: ${plc.name}")
            Log.d(TAG, "   ID: ${plc.id}")
            Log.d(TAG, "   Endpoint: ${plc.endpointUrl}")
            Log.d(TAG, "   Connection State: ${plc.connectionState}")
            Log.d(TAG, "   Tag Count: ${plc.tagCount}")
            Log.d(TAG, "═══════════════════════════════════════════════")

            // Save selected PLC (both ID and name)
            prefsManager.saveSelectedPlc(plc.id, plc.name)

            // Set on PlcApiClient for connection
            plcApiClient.setCurrentPlcId(plc.id)
            Log.d(TAG, "✅ PLC ID set on PlcApiClient: ${plcApiClient.getCurrentPlcId()}")

            _uiState.value = _uiState.value.copy(
                selectedPlcId = plc.id,
                selectedPlcName = plc.name
            )

            Log.d(TAG, "📍 Navigating to main screen...")
            onSuccess()
        }
    }

    // ============== Direct OPC UA backup mode ==============

    /** Toggle between API and Direct connection modes. Persists prefs and rebuilds the active repo. */
    fun onConnectionModeChanged(mode: ConnectionMode) {
        if (_uiState.value.connectionMode == mode) return
        Log.d(TAG, "🔁 Connection mode -> $mode")
        _uiState.value = _uiState.value.copy(
            connectionMode = mode,
            directTestState = ConfigUiState.DirectTestState.Idle,
            errorMessage = null
        )
        repositoryProvider.switchTo(mode)

        if (mode == ConnectionMode.Direct) {
            // Synthesise an authenticated state so existing UI/checks keep working.
            authManager.enterDirectMode()
            lockManager.setBypassed()
        } else {
            authManager.exitDirectMode()
            // Refresh the PLC list so the API section repopulates.
            loadServerPlcs()
        }
    }

    fun onDirectEndpointChanged(value: String) {
        _uiState.value = _uiState.value.copy(
            directConfig = _uiState.value.directConfig.copy(endpointUrl = value),
            directTestState = ConfigUiState.DirectTestState.Idle
        )
    }

    fun onDirectUsernameChanged(value: String) {
        _uiState.value = _uiState.value.copy(
            directConfig = _uiState.value.directConfig.copy(username = value),
            directTestState = ConfigUiState.DirectTestState.Idle
        )
    }

    fun onDirectPasswordChanged(value: String) {
        _uiState.value = _uiState.value.copy(
            directConfig = _uiState.value.directConfig.copy(password = value),
            directTestState = ConfigUiState.DirectTestState.Idle
        )
    }

    fun onDirectSecurityPolicyChanged(policy: DirectSecurityPolicy) {
        _uiState.value = _uiState.value.copy(
            directConfig = _uiState.value.directConfig.copy(securityPolicy = policy),
            directTestState = ConfigUiState.DirectTestState.Idle
        )
    }

    /** Save Direct config to prefs and rebuild the active repo so the new endpoint takes effect. */
    fun onSaveDirectConfig(onSaved: () -> Unit = {}) {
        val cfg = _uiState.value.directConfig
        Log.d(TAG, "💾 Saving direct PLC config: endpoint=${cfg.endpointUrl}")
        prefsManager.saveDirectPlcConfig(cfg)
        if (_uiState.value.connectionMode == ConnectionMode.Direct) {
            repositoryProvider.rebuildActive()
        }
        onSaved()
    }

    /** Spin up a throwaway DirectOpcUaClient to verify the endpoint/credentials before saving. */
    fun onTestDirectConnection() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(directTestState = ConfigUiState.DirectTestState.Testing)
            val cfg = _uiState.value.directConfig
            val tester = DirectOpcUaClient(cfg)
            try {
                val ok = tester.connect()
                if (ok) {
                    _uiState.value = _uiState.value.copy(
                        directTestState = ConfigUiState.DirectTestState.Ok("Kết nối thành công tới ${cfg.endpointUrl}")
                    )
                } else {
                    val reason = (tester.connectionState.value as? DirectOpcUaClient.DirectState.Faulted)?.reason
                        ?: "Không thể kết nối"
                    _uiState.value = _uiState.value.copy(
                        directTestState = ConfigUiState.DirectTestState.Failed(reason)
                    )
                }
            } catch (t: Throwable) {
                _uiState.value = _uiState.value.copy(
                    directTestState = ConfigUiState.DirectTestState.Failed(t.message ?: "unknown")
                )
            } finally {
                runCatching { tester.disconnect() }
            }
        }
    }
}
