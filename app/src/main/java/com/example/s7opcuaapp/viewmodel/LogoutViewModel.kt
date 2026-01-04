package com.example.s7opcuaapp.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.s7opcuaapp.data.api.PlcApiClient
import com.example.s7opcuaapp.data.auth.AuthManager
import com.example.s7opcuaapp.data.auth.LockManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class LogoutViewModel @Inject constructor(
    private val plcApiClient: PlcApiClient,
    private val authManager: AuthManager,
    private val lockManager: LockManager
) : ViewModel() {

    companion object {
        private const val TAG = "LogoutViewModel"
    }

    fun logout(onComplete: () -> Unit) {
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

                Log.d(TAG, "✅ Logout successful")
            } catch (e: Exception) {
                Log.e(TAG, "Logout error", e)
                // Still clear local state
                authManager.clearAuth()
                lockManager.clearLockState()
            }
            onComplete()
        }
    }
}
