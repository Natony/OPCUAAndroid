package com.example.s7opcuaapp.data.auth

import android.content.Context
import android.util.Log
import com.example.s7opcuaapp.data.api.InvalidReason
import com.example.s7opcuaapp.data.api.PlcApiClient
import com.example.s7opcuaapp.data.local.PrefsManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages heartbeat timer and session validation for Single-Session authentication.
 *
 * Responsibilities:
 * - Send heartbeat every 30-60 seconds while app is active
 * - Validate session when app resumes from background
 * - Handle session invalidation (kicked by another device)
 */
@Singleton
class HeartbeatManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val authManager: AuthManager,
    private val plcApiClient: PlcApiClient,
    private val prefsManager: PrefsManager
) {
    companion object {
        private const val TAG = "HeartbeatManager"
        private const val HEARTBEAT_INTERVAL_MS = 45_000L  // 45 seconds
        private const val VALIDATE_ON_RESUME_DELAY_MS = 500L  // Small delay after resume
    }

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var heartbeatJob: Job? = null

    // Session validation state
    private val _isValidating = MutableStateFlow(false)
    val isValidating: StateFlow<Boolean> = _isValidating.asStateFlow()

    // Last heartbeat result
    private val _lastHeartbeatSuccess = MutableStateFlow(true)
    val lastHeartbeatSuccess: StateFlow<Boolean> = _lastHeartbeatSuccess.asStateFlow()

    /**
     * Start heartbeat timer
     * Call this after successful login
     */
    fun startHeartbeat() {
        stopHeartbeat()  // Stop any existing timer

        val sessionId = authManager.getSessionId()
        val deviceId = prefsManager.getDeviceId(context)

        if (sessionId == null) {
            Log.w(TAG, "Cannot start heartbeat: no sessionId")
            return
        }

        Log.d(TAG, "💓 Starting heartbeat timer (interval: ${HEARTBEAT_INTERVAL_MS}ms)")

        heartbeatJob = scope.launch {
            while (isActive) {
                delay(HEARTBEAT_INTERVAL_MS)
                sendHeartbeat(sessionId, deviceId)
            }
        }
    }

    /**
     * Stop heartbeat timer
     * Call this on logout or app pause
     */
    fun stopHeartbeat() {
        heartbeatJob?.cancel()
        heartbeatJob = null
        Log.d(TAG, "💔 Heartbeat timer stopped")
    }

    /**
     * Send a single heartbeat
     */
    private suspend fun sendHeartbeat(sessionId: String, deviceId: String) {
        try {
            val response = plcApiClient.heartbeat(sessionId, deviceId)

            if (response == null) {
                Log.w(TAG, "Heartbeat failed: no response")
                _lastHeartbeatSuccess.value = false
                return
            }

            if (response.sessionValid) {
                _lastHeartbeatSuccess.value = true
                // Heartbeat OK - no need to log every time
            } else {
                // Session invalidated
                Log.w(TAG, "💔 Heartbeat: session invalid - ${response.invalidReason}")
                _lastHeartbeatSuccess.value = false
                stopHeartbeat()
                handleInvalidSession(response.invalidReason, null)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Heartbeat error", e)
            _lastHeartbeatSuccess.value = false
        }
    }

    /**
     * Validate session when app resumes from background
     * Call this in Activity.onResume() or when app comes to foreground
     */
    fun validateSessionOnResume() {
        val sessionId = authManager.getSessionId()
        val deviceId = prefsManager.getDeviceId(context)

        if (sessionId == null || !authManager.isAuthenticated()) {
            Log.d(TAG, "Skip validation: not authenticated")
            return
        }

        scope.launch {
            _isValidating.value = true
            delay(VALIDATE_ON_RESUME_DELAY_MS)

            try {
                Log.d(TAG, "🔍 Validating session on resume...")
                val response = plcApiClient.validateSession(sessionId, deviceId)

                if (response == null) {
                    Log.w(TAG, "Validate session failed: no response")
                    _isValidating.value = false
                    return@launch
                }

                if (response.isValid) {
                    Log.d(TAG, "✅ Session still valid")
                    // Restart heartbeat if needed
                    if (heartbeatJob?.isActive != true) {
                        startHeartbeat()
                    }
                } else {
                    Log.w(TAG, "❌ Session invalid: ${response.invalidReason}")
                    stopHeartbeat()
                    handleInvalidSession(response.invalidReason, response.newDeviceName)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Validate session error", e)
            } finally {
                _isValidating.value = false
            }
        }
    }

    /**
     * Handle invalid session (kicked by another device)
     */
    private suspend fun handleInvalidSession(reason: String?, newDeviceName: String?) {
        val invalidReason = reason ?: InvalidReason.SESSION_NOT_FOUND

        // Update auth state
        authManager.handleSessionInvalidated(invalidReason, newDeviceName)

        // Clear tokens
        prefsManager.clearTokens()

        // Clear API client token
        plcApiClient.setAuthToken(null)

        Log.w(TAG, "⚠️ Session invalidated, user will be redirected to login")
    }

    /**
     * Check if session might be invalid
     * Quick check without network call
     */
    fun mightBeInvalid(): Boolean {
        return !_lastHeartbeatSuccess.value || authManager.getSessionId() == null
    }

    /**
     * Cleanup resources
     */
    fun cleanup() {
        stopHeartbeat()
        scope.cancel()
    }
}
