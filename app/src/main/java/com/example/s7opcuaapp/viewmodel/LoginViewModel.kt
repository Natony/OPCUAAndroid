package com.example.s7opcuaapp.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.s7opcuaapp.data.api.PlcApiClient
import com.example.s7opcuaapp.data.auth.AuthManager
import com.example.s7opcuaapp.data.auth.LockManager
import com.example.s7opcuaapp.ui.screen.login.LoginUiState
import dagger.hilt.android.lifecycle.HiltViewModel
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
    private val plcApiClient: PlcApiClient,
    private val authManager: AuthManager,
    private val lockManager: LockManager
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
        // Setup lock manager callbacks
        setupLockManagerCallbacks()
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

    /**
     * Login using API authentication
     */
    fun onLoginClicked(onSuccess: () -> Unit) {
        val current = _uiState.value
        val username = current.username.trim()
        val password = current.password.trim()

        if (username.isBlank() || password.isBlank()) {
            _uiState.value = current.copy(errorMessage = "Vui lòng nhập đầy đủ thông tin")
            return
        }

        Log.d(TAG, "🔑 Login attempt: $username")
        _uiState.value = current.copy(isLoading = true, errorMessage = null)

        viewModelScope.launch {
            try {
                val response = plcApiClient.login(username, password)

                if (response?.success == true) {
                    // Save login response to AuthManager
                    authManager.saveLoginResponse(response)
                    Log.d(TAG, "✅ API login successful: ${response.user?.username}")

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
     * Logout from API
     */
    fun logout() {
        viewModelScope.launch {
            try {
                // Release lock if held
                if (lockManager.hasLock()) {
                    plcApiClient.releaseLock()
                }

                // Logout from API
                plcApiClient.logout()

                // Clear local auth state
                authManager.clearAuth()
                lockManager.clearLockState()

                // Reset UI state
                _uiState.value = LoginUiState()

                Log.d(TAG, "✅ Logout successful")
            } catch (e: Exception) {
                Log.e(TAG, "Logout error", e)
                // Still clear local state even if API call fails
                authManager.clearAuth()
                lockManager.clearLockState()
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
}
