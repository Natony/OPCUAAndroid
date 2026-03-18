package com.example.s7opcuaapp.data.auth

import android.util.Log
import com.example.s7opcuaapp.data.api.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages operator lock for controlling PLC
 * Only one operator can have the lock at a time
 */
@Singleton
class LockManager @Inject constructor() {

    companion object {
        private const val TAG = "LockManager"
        // Auto-extend when 5 minutes remaining
        private const val AUTO_EXTEND_THRESHOLD_SECONDS = 300
        // Check lock status every 30 seconds
        private const val LOCK_CHECK_INTERVAL_MS = 30_000L
    }

    // Lock state
    private val _lockState = MutableStateFlow<LockState>(LockState.Unknown)
    val lockState: StateFlow<LockState> = _lockState.asStateFlow()

    // Current lock status
    private val _lockStatus = MutableStateFlow<LockStatusResponse?>(null)
    val lockStatus: StateFlow<LockStatusResponse?> = _lockStatus.asStateFlow()

    // Remaining seconds (for UI countdown)
    // Note: API may return very large values (e.g., year 9999), we cap at 30 days
    private val MAX_REMAINING_SECONDS = 30 * 24 * 60 * 60 // 30 days in seconds
    private val _remainingSeconds = MutableStateFlow<Int?>(null)
    val remainingSeconds: StateFlow<Int?> = _remainingSeconds.asStateFlow()

    /**
     * Convert Long seconds to Int, capping at reasonable max
     */
    private fun capRemainingSeconds(seconds: Long?): Int? {
        if (seconds == null) return null
        return if (seconds > MAX_REMAINING_SECONDS || seconds < 0) {
            MAX_REMAINING_SECONDS
        } else {
            seconds.toInt()
        }
    }

    // Auto-extend job
    private var autoExtendJob: Job? = null
    private var countdownJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    // Callback for API calls (set by repository)
    private var onRefreshLockStatus: (suspend () -> LockStatusResponse?)? = null
    private var onAutoExtendLock: (suspend () -> Boolean)? = null
    private var refreshStatusCallback: (() -> Unit)? = null
    private var autoExtendCallback: (() -> Unit)? = null

    /**
     * Lock states
     */
    sealed class LockState {
        object Unknown : LockState()
        object NoLock : LockState()  // No one has lock
        object MyLock : LockState()  // Current user has lock
        data class OtherLock(val username: String?) : LockState()  // Someone else has lock
        object Acquiring : LockState()  // Acquiring lock
        object Releasing : LockState()  // Releasing lock
        data class Error(val message: String) : LockState()
    }

    /**
     * Set callbacks for API operations (simple version)
     */
    fun setCallbacks(
        refreshStatus: () -> Unit,
        autoExtend: () -> Unit
    ) {
        refreshStatusCallback = refreshStatus
        autoExtendCallback = autoExtend
    }

    /**
     * Set callbacks for API operations (coroutine version)
     */
    fun setCallbacksAsync(
        refreshStatus: suspend () -> LockStatusResponse?,
        autoExtend: suspend () -> Boolean
    ) {
        onRefreshLockStatus = refreshStatus
        onAutoExtendLock = autoExtend
    }

    /**
     * Update lock status from API response
     */
    fun updateLockStatus(status: LockStatusResponse?) {
        Log.d(TAG, "📥 LockStatusResponse: success=${status?.success}, isLocked=${status?.isLocked}, isMyLock=${status?.isMyLock}, lockedBy=${status?.lockedByUsername}, remainingSeconds=${status?.remainingSeconds}")

        _lockStatus.value = status
        _remainingSeconds.value = capRemainingSeconds(status?.remainingSeconds)

        if (status == null) {
            Log.w(TAG, "⚠️ Lock status is null, setting state to Unknown")
            _lockState.value = LockState.Unknown
            return
        }

        val previousState = _lockState.value
        _lockState.value = when {
            !status.isLocked -> {
                Log.d(TAG, "🔓 Lock not held by anyone -> NoLock")
                LockState.NoLock
            }
            status.isMyLock -> {
                Log.d(TAG, "🔒 Lock held by current user -> MyLock")
                // Start auto-extend monitoring
                startAutoExtendMonitoring()
                LockState.MyLock
            }
            else -> {
                Log.d(TAG, "🔒 Lock held by ${status.lockedByUsername} -> OtherLock")
                stopAutoExtendMonitoring()
                LockState.OtherLock(status.lockedByUsername)
            }
        }

        Log.d(TAG, "🔄 Lock state changed: $previousState -> ${_lockState.value}")
    }

    /**
     * Set acquiring state
     */
    fun setAcquiring() {
        _lockState.value = LockState.Acquiring
    }

    /**
     * Alias for setAcquiring
     */
    fun startAcquiring() = setAcquiring()

    /**
     * Set releasing state
     */
    fun setReleasing() {
        _lockState.value = LockState.Releasing
    }

    /**
     * Alias for setReleasing
     */
    fun startReleasing() = setReleasing()

    /**
     * Set error state
     */
    fun setError(message: String) {
        _lockState.value = LockState.Error(message)
    }

    /**
     * Update from acquire lock response
     */
    fun updateFromAcquireResponse(response: AcquireLockResponse) {
        Log.d(TAG, "📥 AcquireLockResponse: success=${response.success}, remainingSeconds=${response.remainingSeconds}, expiresAt=${response.expiresAt}, error=${response.error}")
        if (response.success) {
            val cappedSeconds = capRemainingSeconds(response.remainingSeconds)
            _remainingSeconds.value = cappedSeconds
            _lockState.value = LockState.MyLock
            startAutoExtendMonitoring()
            Log.d(TAG, "✅ Lock acquired, expires in ${cappedSeconds}s (raw: ${response.remainingSeconds}s)")
        } else {
            _lockState.value = LockState.Error(response.error ?: "Failed to acquire lock")
            Log.e(TAG, "❌ Failed to acquire lock: ${response.error}")
        }
    }

    /**
     * Update from extend lock response
     */
    fun updateFromExtendResponse(response: ExtendLockResponse) {
        if (response.success) {
            _remainingSeconds.value = capRemainingSeconds(response.remainingSeconds)
            Log.d(TAG, "Lock extended, ${capRemainingSeconds(response.remainingSeconds)}s remaining")
        }
    }

    /**
     * Update from release lock response
     */
    fun updateFromReleaseResponse() {
        stopAutoExtendMonitoring()
        _lockState.value = LockState.NoLock
        _remainingSeconds.value = null
        _lockStatus.value = null
        Log.d(TAG, "Lock released")
    }

    /**
     * Check if current user has the lock
     */
    fun hasLock(): Boolean {
        return _lockState.value is LockState.MyLock
    }

    /**
     * Check if can acquire lock (no one has it or we already have it)
     */
    fun canAcquireLock(): Boolean {
        val state = _lockState.value
        return state is LockState.NoLock || state is LockState.MyLock
    }

    /**
     * Start monitoring for auto-extend
     */
    private fun startAutoExtendMonitoring() {
        stopAutoExtendMonitoring()

        autoExtendJob = scope.launch {
            while (isActive) {
                delay(LOCK_CHECK_INTERVAL_MS)

                try {
                    // Use simple callback if available
                    if (refreshStatusCallback != null) {
                        refreshStatusCallback?.invoke()
                    } else {
                        // Refresh status using coroutine callback
                        val status = onRefreshLockStatus?.invoke()
                        if (status != null) {
                            _lockStatus.value = status
                            _remainingSeconds.value = capRemainingSeconds(status.remainingSeconds)

                            // Check if we still have the lock
                            if (!status.isMyLock) {
                                Log.w(TAG, "Lock lost!")
                                _lockState.value = if (status.isLocked) {
                                    LockState.OtherLock(status.lockedByUsername)
                                } else {
                                    LockState.NoLock
                                }
                                break
                            }
                        }
                    }

                    // Auto-extend if near expiry
                    val remaining = _remainingSeconds.value ?: 0
                    if (remaining in 1..AUTO_EXTEND_THRESHOLD_SECONDS) {
                        Log.d(TAG, "Auto-extending lock (${remaining}s remaining)")
                        if (autoExtendCallback != null) {
                            autoExtendCallback?.invoke()
                        } else {
                            val extended = onAutoExtendLock?.invoke() ?: false
                            if (!extended) {
                                Log.w(TAG, "Failed to auto-extend lock")
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error in auto-extend monitoring", e)
                }
            }
        }

        // Start countdown job for UI
        countdownJob = scope.launch {
            while (isActive) {
                delay(1000)
                val current = _remainingSeconds.value
                if (current != null && current > 0) {
                    _remainingSeconds.value = current - 1
                }
            }
        }

        Log.d(TAG, "Started auto-extend monitoring")
    }

    /**
     * Stop auto-extend monitoring
     */
    private fun stopAutoExtendMonitoring() {
        autoExtendJob?.cancel()
        autoExtendJob = null
        countdownJob?.cancel()
        countdownJob = null
        Log.d(TAG, "Stopped auto-extend monitoring")
    }

    /**
     * Clear lock state (on logout or disconnect)
     */
    fun clearLockState() {
        stopAutoExtendMonitoring()
        _lockState.value = LockState.Unknown
        _lockStatus.value = null
        _remainingSeconds.value = null
    }

    /**
     * Format remaining time for display
     */
    fun formatRemainingTime(): String {
        val seconds = _remainingSeconds.value ?: return "--:--"
        val minutes = seconds / 60
        val secs = seconds % 60
        return String.format("%02d:%02d", minutes, secs)
    }

    // ============== SignalR Event Handlers ==============

    // Current user ID (set after login)
    private var currentUserId: String? = null

    /**
     * Set current user ID for identifying own lock events
     */
    fun setCurrentUserId(userId: String?) {
        currentUserId = userId
        Log.d(TAG, "Current user ID set: $userId")
    }

    /**
     * Handle LockAcquired event from SignalR
     * Called when someone acquires the lock (real-time notification)
     */
    fun handleLockAcquiredEvent(event: LockEventDto) {
        Log.d(TAG, "📡 SignalR LockAcquired: user=${event.username}, userId=${event.userId}")

        val isMyLock = currentUserId != null && event.userId == currentUserId

        if (isMyLock) {
            // We acquired the lock
            _lockState.value = LockState.MyLock
            _remainingSeconds.value = capRemainingSeconds(event.remainingSeconds)
            startAutoExtendMonitoring()
            Log.d(TAG, "✅ [SignalR] Our lock acquired")
        } else {
            // Someone else acquired the lock
            stopAutoExtendMonitoring()
            _lockState.value = LockState.OtherLock(event.username)
            _remainingSeconds.value = capRemainingSeconds(event.remainingSeconds)
            Log.d(TAG, "🔒 [SignalR] Lock acquired by ${event.username}")
        }
    }

    /**
     * Handle LockReleased event from SignalR
     * Called when someone releases the lock (real-time notification)
     */
    fun handleLockReleasedEvent(event: LockEventDto) {
        Log.d(TAG, "📡 SignalR LockReleased: user=${event.username}, userId=${event.userId}")

        val wasMyLock = _lockState.value is LockState.MyLock

        stopAutoExtendMonitoring()
        _lockState.value = LockState.NoLock
        _remainingSeconds.value = null
        _lockStatus.value = null

        if (wasMyLock) {
            Log.d(TAG, "🔓 [SignalR] Our lock was released")
        } else {
            Log.d(TAG, "🔓 [SignalR] Lock released by ${event.username}")
        }
    }

    /**
     * Handle LockExtended event from SignalR
     * Called when someone extends the lock (real-time notification)
     */
    fun handleLockExtendedEvent(event: LockEventDto) {
        Log.d(TAG, "📡 SignalR LockExtended: user=${event.username}, remainingSeconds=${event.remainingSeconds}")

        val isMyLock = currentUserId != null && event.userId == currentUserId

        _remainingSeconds.value = capRemainingSeconds(event.remainingSeconds)

        if (isMyLock) {
            Log.d(TAG, "⏱️ [SignalR] Our lock extended to ${event.remainingSeconds}s")
        } else {
            Log.d(TAG, "⏱️ [SignalR] Lock extended by ${event.username}")
        }
    }
}
