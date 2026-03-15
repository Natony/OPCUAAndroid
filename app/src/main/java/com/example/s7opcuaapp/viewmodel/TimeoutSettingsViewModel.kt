package com.example.s7opcuaapp.viewmodel

import androidx.lifecycle.ViewModel
import com.example.s7opcuaapp.data.local.PrefsManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject

data class TimeoutSettingsUiState(
    val connectionTimeout: Long = 10000L,  // ms
    val requestTimeout: Long = 5000L,       // ms
    val pollingInterval: Long = 500L,       // ms
    val isSaved: Boolean = false,
    val errorMessage: String? = null
)

@HiltViewModel
class TimeoutSettingsViewModel @Inject constructor(
    private val prefsManager: PrefsManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(TimeoutSettingsUiState())
    val uiState = _uiState.asStateFlow()

    init {
        loadSettings()
    }

    private fun loadSettings() {
        _uiState.update {
            it.copy(
                connectionTimeout = prefsManager.getConnectionTimeout(),
                requestTimeout = prefsManager.getRequestTimeout(),
                pollingInterval = prefsManager.getPollingInterval(),
                isSaved = false
            )
        }
    }

    fun onConnectionTimeoutChange(value: Long) {
        if (value in 1000..60000) { // 1s to 60s
            _uiState.update { it.copy(connectionTimeout = value, isSaved = false, errorMessage = null) }
        } else {
            _uiState.update { it.copy(errorMessage = "Connection timeout must be between 1-60 seconds") }
        }
    }

    fun onRequestTimeoutChange(value: Long) {
        if (value in 1000..30000) { // 1s to 30s
            _uiState.update { it.copy(requestTimeout = value, isSaved = false, errorMessage = null) }
        } else {
            _uiState.update { it.copy(errorMessage = "Request timeout must be between 1-30 seconds") }
        }
    }

    fun onPollingIntervalChange(value: Long) {
        if (value in 100..5000) { // 100ms to 5s
            _uiState.update { it.copy(pollingInterval = value, isSaved = false, errorMessage = null) }
        } else {
            _uiState.update { it.copy(errorMessage = "Polling interval must be between 100-5000ms") }
        }
    }

    fun saveSettings() {
        val state = _uiState.value
        prefsManager.saveTimeoutSettings(
            connectionTimeout = state.connectionTimeout,
            requestTimeout = state.requestTimeout,
            pollingInterval = state.pollingInterval
        )
        _uiState.update { it.copy(isSaved = true, errorMessage = null) }
    }

    fun resetToDefault() {
        prefsManager.resetTimeoutSettings()
        loadSettings()
    }

    fun dismissSavedMessage() {
        _uiState.update { it.copy(isSaved = false) }
    }
}
