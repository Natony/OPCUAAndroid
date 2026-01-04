package com.example.s7opcuaapp.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.s7opcuaapp.data.api.*
import com.example.s7opcuaapp.data.auth.AuthManager
import com.example.s7opcuaapp.data.auth.LockManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val authManager: AuthManager,
    private val lockManager: LockManager
) : ViewModel() {

    companion object {
        private const val TAG = "AuthViewModel"
    }

    // UI State
    data class LoginUiState(
        val username: String = "",
        val password: String = "",
        val isLoading: Boolean = false,
        val error: String? = null,
        val isLoggedIn: Boolean = false
    )

    private val _uiState = MutableStateFlow(LoginUiState())
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    // Auth state from manager
    val authState = authManager.authState
    val currentUser = authManager.currentUser

    // Lock state from manager
    val lockState = lockManager.lockState
    val lockStatus = lockManager.lockStatus
    val remainingLockTime = lockManager.remainingSeconds

    // API client reference (set from repository)
    private var apiClient: PlcApiClient? = null

    init {
        // Check initial auth state
        viewModelScope.launch {
            authManager.authState.collect { state ->
                _uiState.update {
                    it.copy(isLoggedIn = state is AuthManager.AuthState.Authenticated)
                }
            }
        }
    }

    /**
     * Set API client (called from repository/DI)
     */
    fun setApiClient(client: PlcApiClient) {
        apiClient = client

        // Setup lock manager callbacks
        lockManager.setCallbacks(
            refreshStatus = { client.getLockStatus() },
            autoExtend = {
                val result = client.extendLock()
                result?.success == true
            }
        )
    }

    /**
     * Update username
     */
    fun onUsernameChanged(value: String) {
        _uiState.update { it.copy(username = value, error = null) }
    }

    /**
     * Update password
     */
    fun onPasswordChanged(value: String) {
        _uiState.update { it.copy(password = value, error = null) }
    }

    /**
     * Login
     */
    fun login() {
        val state = _uiState.value
        if (state.username.isBlank() || state.password.isBlank()) {
            _uiState.update { it.copy(error = "Username and password are required") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }

            try {
                val response = apiClient?.login(state.username, state.password)

                if (response?.success == true) {
                    authManager.saveLoginResponse(response)
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            isLoggedIn = true,
                            password = "" // Clear password
                        )
                    }
                    Log.d(TAG, "Login successful: ${response.user?.username}")

                    // Fetch lock status after login
                    refreshLockStatus()
                } else {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            error = response?.error ?: "Login failed"
                        )
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Login error", e)
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = e.message ?: "Login error"
                    )
                }
            }
        }
    }

    /**
     * Logout
     */
    fun logout() {
        viewModelScope.launch {
            try {
                // Release lock if we have it
                if (lockManager.hasLock()) {
                    apiClient?.releaseLock()
                }

                // Logout from server
                apiClient?.logout()

                // Clear local auth
                authManager.clearAuth()
                lockManager.clearLockState()

                _uiState.update {
                    LoginUiState() // Reset to initial state
                }

                Log.d(TAG, "Logout successful")
            } catch (e: Exception) {
                Log.e(TAG, "Logout error", e)
                // Still clear local auth even if server call fails
                authManager.clearAuth()
                lockManager.clearLockState()
            }
        }
    }

    /**
     * Acquire operator lock
     */
    fun acquireLock(durationMinutes: Int? = null) {
        viewModelScope.launch {
            lockManager.setAcquiring()

            try {
                val response = apiClient?.acquireLock(durationMinutes)

                if (response?.success == true) {
                    // Refresh to get full status
                    refreshLockStatus()
                    Log.d(TAG, "Lock acquired successfully")
                } else {
                    lockManager.setError(response?.error ?: "Failed to acquire lock")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Acquire lock error", e)
                lockManager.setError(e.message ?: "Error acquiring lock")
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
                    Log.d(TAG, "Lock released successfully")
                } else {
                    lockManager.setError("Failed to release lock")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Release lock error", e)
                lockManager.setError(e.message ?: "Error releasing lock")
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
                    Log.d(TAG, "Force release successful")
                } else {
                    lockManager.setError("Failed to force release")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Force release error", e)
                lockManager.setError(e.message ?: "Error")
            }
        }
    }

    /**
     * Refresh lock status
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
     * Check if current user can control
     */
    fun canControl(): Boolean {
        return authManager.canControl() && lockManager.hasLock()
    }

    /**
     * Check if user has lock
     */
    fun hasLock(): Boolean = lockManager.hasLock()

    /**
     * Get formatted remaining lock time
     */
    fun getFormattedLockTime(): String = lockManager.formatRemainingTime()

    /**
     * Clear error
     */
    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }
}
