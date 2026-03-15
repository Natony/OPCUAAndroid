package com.example.s7opcuaapp.viewmodel

import androidx.lifecycle.ViewModel
import com.example.s7opcuaapp.data.local.PrefsManager
import com.example.s7opcuaapp.util.ButtonLockRules
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject

data class ButtonTimeoutUiState(
    val currentDeviceId: String? = null,
    val currentDeviceName: String? = null,
    val buttons: List<ButtonTimeoutItem> = emptyList(),
    val selectedButton: ButtonTimeoutItem? = null,
    val showEditDialog: Boolean = false,
    val isSaved: Boolean = false,
    val errorMessage: String? = null
)

data class ButtonTimeoutItem(
    val buttonIndex: Int,
    val buttonName: String,
    val responseTimeout: Long,
    val debounceTime: Long,
    val isCustom: Boolean // true if different from global default
)

@HiltViewModel
class ButtonTimeoutViewModel @Inject constructor(
    private val prefsManager: PrefsManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(ButtonTimeoutUiState())
    val uiState = _uiState.asStateFlow()

    // Only show control buttons (not status displays)
    private val controlButtons = listOf(0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 13, 14)

    init {
        loadCurrentDevice()
        loadButtonTimeouts()
    }

    private fun loadCurrentDevice() {
        val deviceId = prefsManager.getSelectedPlcId()
        val deviceName = prefsManager.getSelectedPlcName()
        _uiState.update {
            it.copy(
                currentDeviceId = deviceId,
                currentDeviceName = deviceName
            )
        }
    }

    private fun loadButtonTimeouts() {
        val deviceId = _uiState.value.currentDeviceId
        val globalResponseTimeout = prefsManager.getButtonResponseTimeout()
        val globalDebounceTime = prefsManager.getButtonDebounceTime()

        // Get device-specific configs if device is selected, otherwise use global
        val customConfigs = if (deviceId != null) {
            prefsManager.getDeviceButtonTimeouts(deviceId)
        } else {
            prefsManager.getButtonTimeouts()
        }

        val buttons = controlButtons.map { index ->
            val config = customConfigs[index]
            val name = ButtonLockRules.BUTTON_NAMES[index] ?: "Button $index"

            ButtonTimeoutItem(
                buttonIndex = index,
                buttonName = name,
                responseTimeout = config?.responseTimeout ?: globalResponseTimeout,
                debounceTime = config?.debounceTime ?: globalDebounceTime,
                isCustom = config != null
            )
        }

        _uiState.update { it.copy(buttons = buttons, isSaved = false) }
    }

    fun onButtonClick(button: ButtonTimeoutItem) {
        _uiState.update { it.copy(selectedButton = button, showEditDialog = true) }
    }

    fun onDismissDialog() {
        _uiState.update { it.copy(showEditDialog = false, selectedButton = null) }
    }

    fun onSaveButtonTimeout(buttonIndex: Int, responseTimeout: Long, debounceTime: Long) {
        // Validate
        if (responseTimeout !in 500..15000) {
            _uiState.update { it.copy(errorMessage = "Response timeout must be 500-15000ms") }
            return
        }
        if (debounceTime !in 50..3000) {
            _uiState.update { it.copy(errorMessage = "Debounce time must be 50-3000ms") }
            return
        }

        val config = PrefsManager.ButtonTimeoutConfig(
            buttonIndex = buttonIndex,
            responseTimeout = responseTimeout,
            debounceTime = debounceTime
        )

        val deviceId = _uiState.value.currentDeviceId
        if (deviceId != null) {
            // Save per-device
            prefsManager.saveDeviceButtonTimeout(deviceId, config)
        } else {
            // Save global
            prefsManager.saveButtonTimeout(config)
        }

        loadButtonTimeouts()
        _uiState.update {
            it.copy(
                showEditDialog = false,
                selectedButton = null,
                isSaved = true,
                errorMessage = null
            )
        }
    }

    fun onResetButton(buttonIndex: Int) {
        val deviceId = _uiState.value.currentDeviceId
        if (deviceId != null) {
            val configs = prefsManager.getDeviceButtonTimeouts(deviceId).toMutableMap()
            configs.remove(buttonIndex)
            prefsManager.saveDeviceButtonTimeouts(deviceId, configs)
        } else {
            val configs = prefsManager.getButtonTimeouts().toMutableMap()
            configs.remove(buttonIndex)
            prefsManager.saveButtonTimeouts(configs)
        }

        loadButtonTimeouts()
        _uiState.update { it.copy(showEditDialog = false, selectedButton = null) }
    }

    fun onResetAllToDefault() {
        val deviceId = _uiState.value.currentDeviceId
        if (deviceId != null) {
            prefsManager.resetDeviceButtonTimeouts(deviceId)
        } else {
            prefsManager.saveButtonTimeouts(emptyMap())
        }
        loadButtonTimeouts()
        _uiState.update { it.copy(isSaved = true) }
    }

    fun dismissMessage() {
        _uiState.update { it.copy(isSaved = false, errorMessage = null) }
    }
}
