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
        loadSavedSelection()
        loadServerPlcs()
    }

    /**
     * Load previously selected PLC ID from preferences
     */
    private fun loadSavedSelection() {
        val savedPlcId = prefsManager.getSelectedPlcId()
        _uiState.value = _uiState.value.copy(selectedPlcId = savedPlcId)
    }

    /**
     * Load PLCs from API server
     */
    fun loadServerPlcs() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoadingPlcs = true, errorMessage = null)
            try {
                val plcs = plcApiClient.getAllPlcs()
                Log.d(TAG, "Loaded ${plcs.size} PLCs from server")
                _uiState.value = _uiState.value.copy(
                    serverPlcs = plcs,
                    isLoadingPlcs = false
                )
            } catch (e: Exception) {
                Log.e(TAG, "Error loading PLCs", e)
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
            Log.d(TAG, "Selected server PLC: ${plc.name} (ID: ${plc.id})")

            // Save selected PLC ID
            prefsManager.saveSelectedPlcId(plc.id)
            plcApiClient.setCurrentPlcId(plc.id)

            _uiState.value = _uiState.value.copy(selectedPlcId = plc.id)
            onSuccess()
        }
    }
}
