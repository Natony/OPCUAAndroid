package com.example.s7opcuaapp.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.s7opcuaapp.data.api.LockStatusResponse
import com.example.s7opcuaapp.data.api.PlcApiClient
import com.example.s7opcuaapp.data.auth.AuthManager
import com.example.s7opcuaapp.data.auth.HeartbeatManager
import com.example.s7opcuaapp.data.auth.LockManager
import com.example.s7opcuaapp.data.local.PrefsManager
import com.example.s7opcuaapp.ui.screen.login.LoginUiState
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for login screen - uses API authentication only
 * Local user management has been removed in favor of server-side authentication
 */
@HiltViewModel
class LoginViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val plcApiClient: PlcApiClient,
    private val authManager: AuthManager,
    private val lockManager: LockManager,
    private val heartbeatManager: HeartbeatManager,
    private val prefsManager: PrefsManager
) : ViewModel() {

    companion object {
        private const val TAG = "LoginViewModel"
    }

    private val _uiState = MutableStateFlow(LoginUiState())
    val uiState: StateFlow<LoginUiState> = _uiState

    // Expose auth state for UI
    val authState = authManager.authState
    val currentUser = authManager.currentUser
    val lockState = lockManager.lockState
    val lockStatus = lockManager.lockStatus
    val remainingLockTime = lockManager.remainingSeconds

    init {
        // Load saved server config
        loadServerConfig()
        // Restore auth token if previously logged in
        restoreAuthToken()
        // Setup lock manager callbacks
        setupLockManagerCallbacks()
    }

    /**
     * Load saved API server config from preferences
     */
    private fun loadServerConfig() {
        val savedIp = prefsManager.getApiServerIp()
        val savedPort = prefsManager.getApiServerPort()
        _uiState.value = _uiState.value.copy(
            serverIp = savedIp,
            serverPort = savedPort
        )
        // Update PlcApiClient with saved config
        val serverUrl = "http://$savedIp:$savedPort"
        plcApiClient.updateServerUrl(serverUrl)
        Log.d(TAG, "Loaded server config: $serverUrl")
    }

    /**
     * Restore auth token from AuthManager if previously logged in
     */
    private fun restoreAuthToken() {
        viewModelScope.launch {
            val token = authManager.getAccessToken()
            if (token != null && authManager.isAuthenticated()) {
                plcApiClient.setAuthToken(token)
                Log.d(TAG, "Restored auth token from previous session")
            }
        }
    }

    /**
     * Setup LockManager callbacks for auto-refresh and auto-extend
     */
    private fun setupLockManagerCallbacks() {
        lockManager.setCallbacks(
            refreshStatus = {
                viewModelScope.launch {
                    try {
                        val status = plcApiClient.getLockStatus()
                        lockManager.updateLockStatus(status)
                    } catch (e: Exception) {
                        Log.e(TAG, "Error refreshing lock status", e)
                    }
                }
            },
            autoExtend = {
                viewModelScope.launch {
                    try {
                        val result = plcApiClient.extendLock()
                        if (result?.success == true) {
                            lockManager.updateFromExtendResponse(result)
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error extending lock", e)
                    }
                }
            }
        )
    }

    fun onUsernameChanged(newUsername: String) {
        _uiState.value = _uiState.value.copy(
            username = newUsername.trim(),
            errorMessage = null
        )
    }

    fun onPasswordChanged(newPassword: String) {
        _uiState.value = _uiState.value.copy(password = newPassword, errorMessage = null)
    }

    fun onTogglePasswordVisibility() {
        _uiState.value = _uiState.value.copy(
            isPasswordVisible = !_uiState.value.isPasswordVisible
        )
    }

    // ============== Server Config ==============

    fun onShowServerConfig() {
        _uiState.value = _uiState.value.copy(showServerConfigDialog = true)
    }

    fun onDismissServerConfig() {
        // Reset to saved values
        _uiState.value = _uiState.value.copy(
            showServerConfigDialog = false,
            serverIp = prefsManager.getApiServerIp(),
            serverPort = prefsManager.getApiServerPort()
        )
    }

    fun onServerIpChanged(newIp: String) {
        _uiState.value = _uiState.value.copy(serverIp = newIp)
    }

    fun onServerPortChanged(newPort: String) {
        _uiState.value = _uiState.value.copy(serverPort = newPort)
    }

    fun onSaveServerConfig() {
        val current = _uiState.value
        val ip = current.serverIp.trim()
        val port = current.serverPort.trim()

        if (ip.isBlank() || port.isBlank()) {
            return
        }

        // Save to preferences
        prefsManager.saveApiServerConfig(ip, port)

        // Update PlcApiClient
        val serverUrl = "http://$ip:$port"
        plcApiClient.updateServerUrl(serverUrl)
        Log.d(TAG, "Server config saved: $serverUrl")

        // Close dialog
        _uiState.value = current.copy(showServerConfigDialog = false)
    }

    /**
     * Login using API authentication with Single-Session
     */
    fun onLoginClicked(onSuccess: () -> Unit) {
        val current = _uiState.value
        val username = current.username.trim()
        val password = current.password.trim()

        if (username.isBlank() || password.isBlank()) {
            _uiState.value = current.copy(errorMessage = "Vui lòng nhập đầy đủ thông tin")
            return
        }

        // Get device info for single-session authentication
        val deviceId = prefsManager.getDeviceId(context)
        val deviceName = prefsManager.getDeviceName()

        Log.d(TAG, "🔑 Login attempt: $username on device: $deviceName ($deviceId)")
        _uiState.value = current.copy(isLoading = true, errorMessage = null)

        viewModelScope.launch {
            try {
                val response = plcApiClient.login(username, password, deviceId, deviceName)

                if (response?.success == true && response.accessToken != null) {
                    // Set auth token for subsequent API requests
                    plcApiClient.setAuthToken(response.accessToken)

                    // Save tokens for logout and refresh
                    if (response.refreshToken != null) {
                        prefsManager.saveTokens(response.accessToken, response.refreshToken)
                    }

                    // Save login response to AuthManager (includes sessionId)
                    authManager.saveLoginResponse(response)
                    Log.d(TAG, "✅ API login successful: ${response.user?.username}, sessionId: ${response.sessionId}")

                    // Start heartbeat timer for Single-Session
                    heartbeatManager.startHeartbeat()

                    // Check if another device was kicked
                    if (response.previousSessionTerminated == true) {
                        Log.w(TAG, "⚠️ Previous session on '${response.previousDeviceName}' was terminated")
                        // Could show a toast or info message here
                    }

                    // Refresh lock status after login
                    refreshLockStatus()

                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        password = "" // Clear password for security
                    )
                    onSuccess()
                } else {
                    Log.e(TAG, "❌ Login failed: ${response?.error}")
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        errorMessage = response?.error ?: "Đăng nhập thất bại"
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "💥 Login error", e)
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = "Lỗi kết nối: ${e.message}"
                )
            }
        }
    }

    /**
     * Logout from API with refreshToken
     */
    fun logout() {
        viewModelScope.launch {
            try {
                // Stop heartbeat timer
                heartbeatManager.stopHeartbeat()

                // Release lock if held
                if (lockManager.hasLock()) {
                    plcApiClient.releaseLock()
                }

                // Logout from API with refreshToken
                val refreshToken = prefsManager.getRefreshToken()
                if (refreshToken != null) {
                    plcApiClient.logout(refreshToken)
                }

                // Clear auth token
                plcApiClient.setAuthToken(null)

                // Clear local auth state and tokens
                authManager.clearAuth()
                lockManager.clearLockState()
                prefsManager.clearTokens()

                // Reset UI state
                _uiState.value = LoginUiState()

                Log.d(TAG, "✅ Logout successful")
            } catch (e: Exception) {
                Log.e(TAG, "Logout error", e)
                // Still clear local state even if API call fails
                heartbeatManager.stopHeartbeat()
                plcApiClient.setAuthToken(null)
                authManager.clearAuth()
                lockManager.clearLockState()
                prefsManager.clearTokens()
                _uiState.value = LoginUiState()
            }
        }
    }

    // ============== Lock Management ==============

    /**
     * Acquire operator lock
     */
    fun acquireLock(durationMinutes: Int? = null) {
        viewModelScope.launch {
            lockManager.setAcquiring()
            try {
                val response = plcApiClient.acquireLock(durationMinutes)
                if (response?.success == true) {
                    lockManager.updateFromAcquireResponse(response)
                    Log.d(TAG, "✅ Lock acquired")
                } else {
                    lockManager.setError(response?.error ?: "Không thể nhận quyền điều khiển")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Acquire lock error", e)
                lockManager.setError(e.message ?: "Lỗi")
            }
        }
    }

    /**
     * Release operator lock
     */
    fun releaseLock() {
        viewModelScope.launch {
            lockManager.setReleasing()
            try {
                val success = plcApiClient.releaseLock()
                if (success) {
                    lockManager.updateFromReleaseResponse()
                    Log.d(TAG, "✅ Lock released")
                } else {
                    lockManager.setError("Không thể nhả quyền điều khiển")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Release lock error", e)
                lockManager.setError(e.message ?: "Lỗi")
            }
        }
    }

    /**
     * Force release lock (Admin only)
     */
    fun forceReleaseLock(reason: String? = null) {
        if (!authManager.isAdmin()) {
            lockManager.setError("Chỉ Admin mới có quyền này")
            return
        }

        viewModelScope.launch {
            try {
                val success = plcApiClient.forceReleaseLock(reason)
                if (success) {
                    refreshLockStatus()
                    Log.d(TAG, "✅ Force release successful")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Force release error", e)
            }
        }
    }

    /**
     * Refresh lock status from server
     */
    fun refreshLockStatus() {
        viewModelScope.launch {
            try {
                val status = plcApiClient.getLockStatus()
                lockManager.updateLockStatus(status)
            } catch (e: Exception) {
                Log.e(TAG, "Refresh lock status error", e)
            }
        }
    }

    /**
     * Check if user can control (has lock)
     */
    fun canControl(): Boolean = authManager.canControl() && lockManager.hasLock()

    /**
     * Check if user has lock
     */
    fun hasLock(): Boolean = lockManager.hasLock()

    /**
     * Get formatted lock time remaining
     */
    fun getFormattedLockTime(): String = lockManager.formatRemainingTime()

    /**
     * Check if authenticated
     */
    fun isAuthenticated(): Boolean = authManager.isAuthenticated()

    /**
     * Check if admin
     */
    fun isAdmin(): Boolean = authManager.isAdmin()

    /**
     * Check if in demo mode
     */
    fun isDemoMode(): Boolean = authManager.isDemoMode()

    // ============== Session Validation ==============

    /**
     * Validate session when app resumes from background
     * Call this from Activity.onResume()
     */
    fun validateSessionOnResume() {
        if (!isDemoMode() && isAuthenticated()) {
            heartbeatManager.validateSessionOnResume()
        }
    }

    /**
     * Observe session validation state
     */
    val isValidatingSession = heartbeatManager.isValidating

    // ============== Demo Mode ==============

    /**
     * Enter demo mode (offline/local mode)
     * Allows quick access to control screen without API server
     */
    fun enterDemoMode(onSuccess: () -> Unit) {
        Log.d(TAG, "🎮 Entering demo mode...")
        authManager.enterDemoMode()

        // Set lock state to "have lock" in demo mode
        lockManager.updateLockStatus(
            LockStatusResponse(
                success = true,
                isLocked = true,
                isMyLock = true,
                lockedByUsername = "demo",
                displayName = "Demo User",
                remainingSeconds = 30 * 24 * 60 * 60L // 30 days
            )
        )

        _uiState.value = _uiState.value.copy(
            isLoading = false,
            errorMessage = null
        )

        Log.d(TAG, "✅ Demo mode activated")
        onSuccess()
    }
}
