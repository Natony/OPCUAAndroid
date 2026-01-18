package com.example.s7opcuaapp.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.s7opcuaapp.data.api.PlcApiClient
import com.example.s7opcuaapp.data.api.PlcDto
import com.example.s7opcuaapp.data.local.PrefsManager
import com.example.s7opcuaapp.data.model.DeviceEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ConfigUiState(
    val deviceList: List<DeviceEntity> = emptyList(),
    val currentDevice: DeviceEntity? = null,
    // Server PLCs from API
    val serverPlcs: List<PlcDto> = emptyList(),
    val selectedPlcId: String? = null,
    val isLoadingPlcs: Boolean = false,
    // Form fields
    val newDeviceName: String = "",
    val newDeviceIp: String = "",
    val newDevicePort: String = "4840",
    val newDeviceApiPort: String = "5000",
    val newDeviceUsername: String = "",
    val newDevicePassword: String = "",
    val errorMessage: String? = null,
    val isEditMode: Boolean = false,
    val editingDevice: DeviceEntity? = null
)

/**
 * ViewModel for device configuration
 * Note: Device IP/port is for OPC UA server connection (handled by WPF server)
 * API server config is managed separately via Login screen
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
        loadDevices()
        loadServerPlcs()
    }

    private fun loadDevices() {
        viewModelScope.launch {
            val list = prefsManager.getAllDevices()
            val current = prefsManager.getCurrentDevice()
            val savedPlcId = prefsManager.getSelectedPlcId()
            _uiState.value = _uiState.value.copy(
                deviceList = list,
                currentDevice = current,
                selectedPlcId = savedPlcId
            )
        }
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

    fun onNewDeviceNameChanged(new: String) {
        _uiState.value = _uiState.value.copy(newDeviceName = new)
    }

    fun onNewDeviceIpChanged(new: String) {
        _uiState.value = _uiState.value.copy(newDeviceIp = new)
    }

    fun onNewDevicePortChanged(new: String) {
        _uiState.value = _uiState.value.copy(newDevicePort = new)
    }

    fun onNewDeviceApiPortChanged(new: String) {
        _uiState.value = _uiState.value.copy(newDeviceApiPort = new)
    }

    fun onNewDeviceUsernameChanged(new: String) {
        _uiState.value = _uiState.value.copy(newDeviceUsername = new)
    }

    fun onNewDevicePasswordChanged(new: String) {
        _uiState.value = _uiState.value.copy(newDevicePassword = new)
    }

    fun onAddDevice() {
        val state = _uiState.value
        val name = state.newDeviceName.trim()
        val ip = state.newDeviceIp.trim()
        val portStr = state.newDevicePort.trim()
        val apiPortStr = state.newDeviceApiPort.trim()
        val username = state.newDeviceUsername.trim()
        val password = state.newDevicePassword.trim()

        if (name.isEmpty() || ip.isEmpty() || portStr.isEmpty() || apiPortStr.isEmpty()) {
            _uiState.value = state.copy(errorMessage = "Tên, IP, Port, API Port không được để trống")
            return
        }

        val port = portStr.toIntOrNull()
        val apiPort = apiPortStr.toIntOrNull()
        if (port == null || port <= 0 || apiPort == null || apiPort <= 0) {
            _uiState.value = state.copy(errorMessage = "Port không hợp lệ")
            return
        }

        viewModelScope.launch {
            if (state.isEditMode && state.editingDevice != null) {
                // Update existing device
                val updatedDevice = state.editingDevice.copy(
                    name = name,
                    ipAddress = ip,
                    port = port,
                    apiPort = apiPort,
                    opcUsername = username,
                    opcPassword = password
                )

                val updatedList = state.deviceList.map {
                    if (it.id == updatedDevice.id) updatedDevice else it
                }

                prefsManager.saveDeviceList(updatedList)

                // If this was the current device, update it
                if (state.currentDevice?.id == updatedDevice.id) {
                    prefsManager.setCurrentDevice(updatedDevice)
                }

                _uiState.value = state.copy(
                    deviceList = updatedList,
                    currentDevice = if (state.currentDevice?.id == updatedDevice.id) updatedDevice else state.currentDevice,
                    newDeviceName = "",
                    newDeviceIp = "",
                    newDevicePort = "4840",
                    newDeviceApiPort = "5000",
                    newDeviceUsername = "",
                    newDevicePassword = "",
                    errorMessage = null,
                    isEditMode = false,
                    editingDevice = null
                )
            } else {
                // Add new device
                val id = System.currentTimeMillis().toString()
                val newDevice = DeviceEntity(
                    id = id,
                    name = name,
                    ipAddress = ip,
                    port = port,
                    apiPort = apiPort,
                    opcUsername = username,
                    opcPassword = password,
                    useOpcUa = true
                )

                val updatedList = state.deviceList + newDevice
                prefsManager.saveDeviceList(updatedList)

                _uiState.value = state.copy(
                    deviceList = updatedList,
                    newDeviceName = "",
                    newDeviceIp = "",
                    newDevicePort = "4840",
                    newDeviceApiPort = "5000",
                    newDeviceUsername = "",
                    newDevicePassword = "",
                    errorMessage = null
                )
            }
        }
    }

    fun onEditDevice(device: DeviceEntity) {
        _uiState.value = _uiState.value.copy(
            isEditMode = true,
            editingDevice = device,
            newDeviceName = device.name,
            newDeviceIp = device.ipAddress,
            newDevicePort = device.port.toString(),
            newDeviceApiPort = device.apiPort.toString(),
            newDeviceUsername = device.opcUsername,
            newDevicePassword = device.opcPassword
        )
    }

    fun onCancelEdit() {
        _uiState.value = _uiState.value.copy(
            isEditMode = false,
            editingDevice = null,
            newDeviceName = "",
            newDeviceIp = "",
            newDevicePort = "4840",
            newDeviceApiPort = "5000",
            newDeviceUsername = "",
            newDevicePassword = "",
            errorMessage = null
        )
    }

    fun onRemoveDevice(device: DeviceEntity) {
        viewModelScope.launch {
            val updatedList = _uiState.value.deviceList.filterNot { it.id == device.id }
            prefsManager.saveDeviceList(updatedList)

            val current = prefsManager.getCurrentDevice()
            if (current?.id == device.id) {
                prefsManager.clearCurrentDevice()
                _uiState.value = _uiState.value.copy(
                    deviceList = updatedList,
                    currentDevice = null
                )
            } else {
                _uiState.value = _uiState.value.copy(deviceList = updatedList)
            }
        }
    }

    fun onSelectDevice(device: DeviceEntity, onSuccess: () -> Unit) {
        viewModelScope.launch {
            prefsManager.setCurrentDevice(device)
            _uiState.value = _uiState.value.copy(currentDevice = device)
            Log.d(TAG, "Selected device: ${device.name} (OPC UA: ${device.ipAddress}:${device.port})")
            onSuccess()
        }
    }
}
