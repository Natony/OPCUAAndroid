package com.example.s7opcuaapp.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.s7opcuaapp.data.api.PlcApiClient
import com.example.s7opcuaapp.data.api.PlcDto
import com.example.s7opcuaapp.data.local.PrefsManager
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
    val errorMessage: String? = null
)

/**
 * ViewModel for PLC selection
 * Fetches PLCs from WPF server and allows user to select one
 */
@HiltViewModel
class ConfigViewModel @Inject constructor(
    private val prefsManager: PrefsManager,
    private val plcApiClient: PlcApiClient
) : ViewModel() {

    companion object {
        private const val TAG = "ConfigViewModel"
    }

    private val _uiState = MutableStateFlow(ConfigUiState())
    val uiState: StateFlow<ConfigUiState> = _uiState

    init {
        Log.d(TAG, "🚀 ConfigViewModel initialized")
        loadSavedSelection()
        loadServerPlcs()
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
}
