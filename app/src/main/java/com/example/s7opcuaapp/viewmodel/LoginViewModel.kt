package com.example.s7opcuaapp.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.s7opcuaapp.data.api.PlcApiClient
import com.example.s7opcuaapp.data.auth.AuthManager
import com.example.s7opcuaapp.data.auth.LockManager
import com.example.s7opcuaapp.data.repository.UserRepository
import com.example.s7opcuaapp.ui.screen.login.LoginUiState
import com.example.s7opcuaapp.util.SessionManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val userRepository: UserRepository,
    private val sessionManager: SessionManager,
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
    val apiUser = authManager.currentUser
    val lockState = lockManager.lockState
    val lockStatus = lockManager.lockStatus
    val remainingLockTime = lockManager.remainingSeconds

    // API client for server auth
    private var apiClient: PlcApiClient? = null

    /**
     * Set API client for server authentication
     */
    fun setApiClient(client: PlcApiClient) {
        apiClient = client
        lockManager.setCallbacks(
            refreshStatus = { client.getLockStatus() },
            autoExtend = { client.extendLock()?.success == true }
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
                // Try API login first if client is available
                val apiLoginSuccess = apiClient?.let { client ->
                    val response = client.login(username, password)
                    if (response?.success == true) {
                        authManager.saveLoginResponse(response)
                        Log.d(TAG, "✅ API login successful")
                        // Refresh lock status after login
                        refreshLockStatus()
                        true
                    } else {
                        Log.w(TAG, "API login failed: ${response?.error}")
                        false
                    }
                } ?: false

                // Also do local authentication for session management
                userRepository.authenticate(username, password)
                    .fold(
                        onSuccess = { user ->
                            Log.d(TAG, "✅ Local auth successful: ${user.username}")
                            val sessionId = sessionManager.login(user)
                            Log.d(TAG, "✅ Session created: $sessionId")

                            _uiState.value = _uiState.value.copy(isLoading = false)
                            onSuccess()
                        },
                        onFailure = { exception ->
                            // If API login succeeded but local failed, still proceed
                            if (apiLoginSuccess) {
                                Log.w(TAG, "Local auth failed but API succeeded, proceeding")
                                _uiState.value = _uiState.value.copy(isLoading = false)
                                onSuccess()
                            } else {
                                Log.e(TAG, "❌ Auth failed: ${exception.message}")
                                _uiState.value = _uiState.value.copy(
                                    isLoading = false,
                                    errorMessage = exception.message ?: "Đăng nhập thất bại"
                                )
                            }
                        }
                    )
            } catch (e: Exception) {
                Log.e(TAG, "💥 Login error", e)
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = "Lỗi hệ thống: ${e.message}"
                )
            }
        }
    }

    /**
     * Logout from both API and local session
     */
    fun logout() {
        viewModelScope.launch {
            try {
                // Release lock if held
                if (lockManager.hasLock()) {
                    apiClient?.releaseLock()
                }

                // Logout from API
                apiClient?.logout()
                authManager.clearAuth()
                lockManager.clearLockState()

                // Clear local session
                sessionManager.logout()

                Log.d(TAG, "✅ Logout successful")
            } catch (e: Exception) {
                Log.e(TAG, "Logout error", e)
                // Still clear local state
                authManager.clearAuth()
                lockManager.clearLockState()
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
                val response = apiClient?.acquireLock(durationMinutes)
                if (response?.success == true) {
                    refreshLockStatus()
                    Log.d(TAG, "✅ Lock acquired")
                } else {
                    lockManager.setError(response?.error ?: "Failed to acquire lock")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Acquire lock error", e)
                lockManager.setError(e.message ?: "Error")
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
                val success = apiClient?.releaseLock() ?: false
                if (success) {
                    refreshLockStatus()
                    Log.d(TAG, "✅ Lock released")
                } else {
                    lockManager.setError("Failed to release lock")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Release lock error", e)
                lockManager.setError(e.message ?: "Error")
            }
        }
    }

    /**
     * Force release lock (Admin only)
     */
    fun forceReleaseLock(reason: String? = null) {
        if (!authManager.isAdmin()) {
            lockManager.setError("Admin only")
            return
        }

        viewModelScope.launch {
            try {
                val success = apiClient?.forceReleaseLock(reason) ?: false
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
                val status = apiClient?.getLockStatus()
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
}
