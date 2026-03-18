package com.example.s7opcuaapp.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.s7opcuaapp.data.api.PlcApiClient
import com.example.s7opcuaapp.data.auth.AuthManager
import com.example.s7opcuaapp.data.auth.HeartbeatManager
import com.example.s7opcuaapp.data.auth.LockManager
import com.example.s7opcuaapp.data.local.PrefsManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class LogoutViewModel @Inject constructor(
    private val plcApiClient: PlcApiClient,
    private val authManager: AuthManager,
    private val lockManager: LockManager,
    private val heartbeatManager: HeartbeatManager,
    private val prefsManager: PrefsManager
) : ViewModel() {

    companion object {
        private const val TAG = "LogoutViewModel"
    }

    fun logout(onComplete: () -> Unit) {
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

                Log.d(TAG, "✅ Logout successful")
            } catch (e: Exception) {
                Log.e(TAG, "Logout error", e)
                // Still clear local state
                heartbeatManager.stopHeartbeat()
                plcApiClient.setAuthToken(null)
                authManager.clearAuth()
                lockManager.clearLockState()
                prefsManager.clearTokens()
            }
            onComplete()
        }
    }
}
