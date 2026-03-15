package com.example.s7opcuaapp.viewmodel

import android.util.Log
import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.s7opcuaapp.data.api.PlcApiClient
import com.example.s7opcuaapp.data.auth.AuthManager
import com.example.s7opcuaapp.data.auth.LockManager
import com.example.s7opcuaapp.data.local.PrefsManager
import com.example.s7opcuaapp.data.model.PlcData
import com.example.s7opcuaapp.data.repository.ApiRepositoryImpl
import com.example.s7opcuaapp.data.repository.S7Repository
import com.example.s7opcuaapp.ui.screen.control.CancelConfirmDialog
import com.example.s7opcuaapp.ui.screen.control.ControlUiState
import com.example.s7opcuaapp.util.ButtonLockConfig
import com.example.s7opcuaapp.util.ButtonLockRules
import com.example.s7opcuaapp.util.ConnectionTimeoutManager
import com.example.s7opcuaapp.util.PerformanceMonitor
import com.example.s7opcuaapp.util.StatusLockConfig
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import kotlin.time.Duration.Companion.milliseconds

@HiltViewModel
class ControlViewModel @Inject constructor(
    private val prefsManager: PrefsManager,
    repository: S7Repository,
    private val performanceMonitor: PerformanceMonitor,
    private val buttonLockConfig: ButtonLockConfig,
    private val statusLockConfig: StatusLockConfig,
    private val buttonLockRules: ButtonLockRules,
    private val connectionTimeoutManager: ConnectionTimeoutManager,
    private val authManager: AuthManager,
    private val lockManager: LockManager,
    private val plcApiClient: PlcApiClient
) : ViewModel() {

    private val _uiState = MutableStateFlow(ControlUiState())
    val uiState: StateFlow<ControlUiState> = _uiState.asStateFlow()

    // Auth and Lock state exposed to UI
    val authState: StateFlow<AuthManager.AuthState> = authManager.authState
    val lockState: StateFlow<LockManager.LockState> = lockManager.lockState
    val lockRemainingSeconds: StateFlow<Int?> = lockManager.remainingSeconds

    // Lock error message
    private val _lockError = MutableSharedFlow<String>(replay = 0)
    val lockError: SharedFlow<String> = _lockError.asSharedFlow()

    private val repoImpl = repository as ApiRepositoryImpl
    private val functionCodeNodeIndex = 14

    // Retry tracking
    private var connectionAttempts = 0
    private val maxRetryAttempts = 3
    private var isOfflineMode = false

    // Connection monitoring
    private var connectionMonitorJob: Job? = null
    private var lastSuccessfulPing = 0L
    private val connectionCheckInterval = 5000L // 5 seconds

    // Connection management
    private val connectionMutex = Mutex()
    private var connectionStarted = false
    private var dataObservationJob: Job? = null

    // UI update throttling - reduced for faster response
    private val uiUpdateThrottle = 50L
    private var lastUiUpdateTime = 0L

    // THREAD-SAFE: Use ConcurrentHashMap for button states
    private val buttonStates = ConcurrentHashMap<Int, ButtonState>()

    // Global processing lock - chỉ cho phép 1 operation tại 1 thời điểm
    // IMPORTANT: Sử dụng CHUNG cho tất cả các loại nút để tránh race condition
    private val globalProcessingLock = Mutex()

    private val pressedButtons = mutableSetOf<Int>()

    // THREAD-SAFE: Mutex for critical sections
    private val buttonOperationMutex = Mutex()
    // NOTE: globalProcessingLock được thay bằng globalProcessingLock để tránh race condition
    // giữa các loại nút khác nhau (auto bool, int buttons, manual buttons)

    // control auto-retry
    private var autoRetryEnabled = true
    private var isShowingTimeoutDialog = false

    // THREAD-SAFE: Atomic reference for current processing button
    @Volatile
    private var currentProcessingButton: Int? = null

    sealed class ConnectionState {
        object Idle : ConnectionState()
        data class Connecting(val attempt: Int = 1) : ConnectionState()
        object Connected : ConnectionState()
        data class Failed(val error: String, val attempt: Int = 0) : ConnectionState()
        object Timeout : ConnectionState()
        object Offline : ConnectionState()
        data class MaxRetriesExceeded(val reason: String) : ConnectionState()
    }

    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Idle)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    // Button state tracking
    private data class ButtonState(
        val index: Int,
        val isPressed: Boolean,
        val lastActionTime: Long,
        val operationJob: Job? = null
    )

    companion object {
        const val BOOL_OFFSET = 0
        const val INT_OFFSET = 200
        const val MIN_BUTTON_ACTION_INTERVAL = 100L // Minimum 100ms between actions
        const val CONNECTION_TIMEOUT_MS = 30000L
    }

    init {
        // Setup LockManager callbacks
        setupLockManagerCallbacks()

        // Load saved PLC ID and set on PlcApiClient
        val savedPlcId = prefsManager.getSelectedPlcId()
        val savedPlcName = prefsManager.getSelectedPlcName()
        Log.d("ControlVM", "═══════════════════════════════════════════════")
        Log.d("ControlVM", "🚀 ControlViewModel initialized")
        Log.d("ControlVM", "   Saved PLC ID: $savedPlcId")
        Log.d("ControlVM", "   Saved PLC Name: $savedPlcName")
        if (savedPlcId != null) {
            plcApiClient.setCurrentPlcId(savedPlcId)
            Log.d("ControlVM", "   ✅ PLC ID set on PlcApiClient")
        } else {
            Log.w("ControlVM", "   ⚠️ No PLC ID saved - user needs to select a PLC")
        }
        Log.d("ControlVM", "═══════════════════════════════════════════════")

        // Monitor UI state changes
        viewModelScope.launch {
            snapshotFlow { uiState.value }
                .collect {
                    performanceMonitor.recordUiRecomposition()
                }
        }

        // Monitor connection health
        viewModelScope.launch {
            while (true) {
                delay(5000) // Check every 5 seconds
                if (connectionStarted && _uiState.value.loadingPercent == 100) {
                    if (!repoImpl.isConnected()) {
                        Log.w("ControlVM", "Connection lost detected")
                        _uiState.update {
                            it.copy(
                                errorMessage = "Connection lost to PLC",
                                loadingPercent = -1
                            )
                        }
                        _connectionState.value = ConnectionState.Failed("Connection lost", 0)
                    }
                }
            }
        }

        // Observe loading percent
        viewModelScope.launch {
            repoImpl.observeLoadingPercent()
                .catch { err ->
                    Log.e("ControlVM", "Error observing loading percent", err)
                }
                .collect { pct ->
                    Log.d("ControlVM", "Loading percent = $pct")
                    _uiState.update { it.copy(loadingPercent = pct) }

                    // IMPROVED: Update connection state based on loading progress
                    when {
                        pct == -1 -> {
                            // Error state - but check if we're already handling it
                            if (_connectionState.value !is ConnectionState.Failed &&
                                _connectionState.value !is ConnectionState.MaxRetriesExceeded
                            ) {
                                _connectionState.value = ConnectionState.Failed(
                                    "Connection failed",
                                    connectionAttempts
                                )
                            }
                        }

                        pct == 100 -> {
                            // IMPORTANT: Always update to Connected when 100%
                            Log.d("ControlVM", "✅ Loading complete, setting state to Connected")
                            _connectionState.value = ConnectionState.Connected
                            connectionStarted = true
                            lastSuccessfulPing = System.currentTimeMillis()
                        }

                        pct in 1..99 -> {
                            // Loading in progress
                            if (_connectionState.value !is ConnectionState.Connecting) {
                                _connectionState.value =
                                    ConnectionState.Connecting(connectionAttempts)
                            }
                        }

                        pct == 0 -> {
                            // Starting or idle
                            if (_connectionState.value is ConnectionState.Failed ||
                                _connectionState.value is ConnectionState.Idle
                            ) {
                                // Keep current state
                            }
                        }
                    }
                }
        }

        // Observe server-side connection status from SignalR
        viewModelScope.launch {
            repoImpl.serverConnectionStatus
                .filterNotNull()
                .collect { status ->
                    Log.d("ControlVM", "📡 Server connection status update: plcId=${status.plcId}, state=${status.state}, isConnected=${status.isConnected}")

                    // Check if this status is for our selected PLC
                    val selectedPlcId = prefsManager.getSelectedPlcId()
                    val selectedPlcName = prefsManager.getSelectedPlcName()

                    if (status.plcId == selectedPlcId || status.plcName == selectedPlcName) {
                        // Update connection state based on server status
                        when {
                            status.isConnected && _connectionState.value !is ConnectionState.Connected -> {
                                Log.d("ControlVM", "🟢 Server reports PLC connected - syncing state")
                                _connectionState.value = ConnectionState.Connected
                            }
                            !status.isConnected -> {
                                val newState = when (status.state.lowercase()) {
                                    "reconnecting", "connecting" -> {
                                        Log.d("ControlVM", "🟡 Server reports PLC reconnecting")
                                        ConnectionState.Connecting(connectionAttempts)
                                    }
                                    "error", "failed" -> {
                                        Log.d("ControlVM", "🔴 Server reports PLC error: ${status.state}")
                                        ConnectionState.Failed(status.state, connectionAttempts)
                                    }
                                    else -> {
                                        Log.d("ControlVM", "⚪ Server reports PLC disconnected: ${status.state}")
                                        ConnectionState.Failed("Disconnected", connectionAttempts)
                                    }
                                }
                                if (_connectionState.value is ConnectionState.Connected) {
                                    _connectionState.value = newState
                                }
                            }
                        }
                    }
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
                        val lockStatus = plcApiClient.getLockStatus()
                        lockStatus?.let { lockManager.updateLockStatus(it) }
                    } catch (e: Exception) {
                        Log.e("ControlVM", "Error refreshing lock status", e)
                    }
                }
            },
            autoExtend = {
                viewModelScope.launch {
                    try {
                        val extendResponse = plcApiClient.extendLock(additionalMinutes = 30)
                        extendResponse?.let {
                            if (it.success) {
                                lockManager.updateFromExtendResponse(it)
                                Log.d("ControlVM", "✅ Lock auto-extended")
                            }
                        }
                    } catch (e: Exception) {
                        Log.e("ControlVM", "Error auto-extending lock", e)
                    }
                }
            }
        )
    }

    /**
     * Check if user can control PLC (has lock or is ADMIN/OPERATOR)
     * @return true if can control, false otherwise
     */
    private fun canControl(): Boolean {
        // Check if authenticated
        if (authManager.authState.value !is AuthManager.AuthState.Authenticated) {
            return false
        }

        // Viewers cannot control at all
        if (!authManager.canControl()) {
            return false
        }

        // Check if we have the lock
        return lockManager.hasLock()
    }

    /**
     * Check lock before write operations
     * @return true if OK to proceed, false if blocked
     */
    private suspend fun checkLockBeforeWrite(operationName: String): Boolean {
        // Check auth first
        if (authManager.authState.value !is AuthManager.AuthState.Authenticated) {
            _lockError.emit("Chưa đăng nhập. Vui lòng đăng nhập để điều khiển.")
            _uiState.update { it.copy(errorMessage = "Chưa đăng nhập") }
            return false
        }

        // Check if user can control
        if (!authManager.canControl()) {
            _lockError.emit("Tài khoản không có quyền điều khiển.")
            _uiState.update { it.copy(errorMessage = "Không có quyền điều khiển") }
            return false
        }

        // Check lock
        if (!lockManager.hasLock()) {
            val currentLockState = lockManager.lockState.value
            val message = when (currentLockState) {
                is LockManager.LockState.OtherLock -> "PLC đang được điều khiển bởi ${currentLockState.username}. Vui lòng chờ hoặc yêu cầu họ nhường quyền."
                is LockManager.LockState.NoLock -> "Bạn cần nhận quyền điều khiển trước khi thao tác."
                else -> "Không có quyền điều khiển. Vui lòng nhận lock."
            }
            _lockError.emit(message)
            _uiState.update { it.copy(errorMessage = message) }
            Log.w("ControlVM", "❌ $operationName blocked - no lock: $currentLockState")
            return false
        }

        return true
    }

    /**
     * Acquire operator lock
     */
    fun acquireLock() {
        viewModelScope.launch {
            lockManager.startAcquiring()
            try {
                val result = plcApiClient.acquireLock(durationMinutes = 30)
                if (result != null) {
                    if (result.success) {
                        lockManager.updateFromAcquireResponse(result)
                        Log.d("ControlVM", "✅ Lock acquired")
                    } else {
                        lockManager.setError(result.error ?: "Không thể nhận lock")
                        _lockError.emit(result.error ?: "Không thể nhận lock")
                    }
                } else {
                    lockManager.setError("Lỗi kết nối server")
                    _lockError.emit("Lỗi kết nối server")
                }
            } catch (e: Exception) {
                Log.e("ControlVM", "Error acquiring lock", e)
                lockManager.setError(e.message ?: "Lỗi không xác định")
                _lockError.emit("Lỗi: ${e.message}")
            }
        }
    }

    /**
     * Release operator lock
     */
    fun releaseLock() {
        viewModelScope.launch {
            lockManager.startReleasing()
            try {
                val success = plcApiClient.releaseLock()
                if (success) {
                    lockManager.updateFromReleaseResponse()
                    Log.d("ControlVM", "✅ Lock released")
                } else {
                    lockManager.setError("Không thể nhả lock")
                }
            } catch (e: Exception) {
                Log.e("ControlVM", "Error releasing lock", e)
                lockManager.setError(e.message ?: "Lỗi không xác định")
            }
        }
    }

    /**
     * Refresh lock status from server
     */
    fun refreshLockStatus() {
        viewModelScope.launch {
            try {
                Log.d("ControlVM", "🔒 Fetching lock status from server...")
                val lockStatus = plcApiClient.getLockStatus()
                if (lockStatus != null) {
                    lockManager.updateLockStatus(lockStatus)
                    Log.d("ControlVM", "🔒 Lock status updated: isLocked=${lockStatus.isLocked}, isMyLock=${lockStatus.isMyLock}")
                } else {
                    Log.w("ControlVM", "🔒 Lock status is null - API may have failed or returned empty response")
                }
            } catch (e: Exception) {
                Log.e("ControlVM", "Error refreshing lock status", e)
            }
        }
    }

        fun startConnection() {
            viewModelScope.launch {
                connectionMutex.withLock {
                    // Check if already connected AND actually connected to PLC
                    if (connectionStarted &&
                        _connectionState.value is ConnectionState.Connected &&
                        repoImpl.isConnected()) {
                        Log.d("ControlVM", "Already connected and PLC connection valid")
                        return@withLock
                    }

                    // Reset states for fresh connection
                    isOfflineMode = false
                    isShowingTimeoutDialog = false

                    // Only increment attempts if not already at max
                    if (connectionAttempts < maxRetryAttempts) {
                        connectionAttempts++
                    }

                    Log.d("ControlVM", "🔄 Starting connection attempt $connectionAttempts/$maxRetryAttempts")

                    try {
                        val currentDevice = prefsManager.getCurrentDevice()
                        if (currentDevice == null) {
                            _connectionState.value = ConnectionState.MaxRetriesExceeded("No device configured")
                            return@withLock
                        }

                        // Set connecting state immediately
                        _connectionState.value = ConnectionState.Connecting(connectionAttempts)

                        // Clear any error state
                        _uiState.update {
                            it.copy(
                                loadingPercent = 0,
                                errorMessage = null
                            )
                        }

                        // Update device if needed
                        if (!connectionStarted) {
                            repoImpl.updateDevice(currentDevice)
                        }

                        // Start connection with timeout
                        withTimeout(CONNECTION_TIMEOUT_MS) {
                            repoImpl.start()

                            // Wait for connection to establish
                            var connected = false
                            var attempts = 0
                            val maxWaitAttempts = 60 // Increase to 60 seconds

                            while (!connected && attempts < maxWaitAttempts) {
                                delay(500) // Check every 500ms instead of 1000ms
                                attempts++

                                val percent = _uiState.value.loadingPercent

                                if (attempts % 4 == 0) { // Log every 2 seconds
                                    Log.d("ControlVM", "Waiting for connection... $percent% (${attempts/2}s)")
                                }

                                when {
                                    percent == 100 -> {
                                        connected = true
                                        connectionStarted = true
                                        connectionAttempts = 0 // Reset on success
                                        _connectionState.value = ConnectionState.Connected
                                        lastSuccessfulPing = System.currentTimeMillis()

                                        Log.d("ControlVM", "✅ Connection established successfully")

                                        // Start monitoring and data observation
                                        startConnectionMonitoring()
                                        startOptimizedDataObservation()

                                        // Fetch lock status from server
                                        refreshLockStatus()
                                    }
                                    percent == -1 -> {
                                        throw Exception("Connection error from repository")
                                    }
                                }
                            }

                            if (!connected) {
                                throw Exception("Connection timeout - loading incomplete after ${maxWaitAttempts/2}s")
                            }
                        }

                    } catch (e: Exception) {
                        handleConnectionError(e)
                    }
                }
            }
        }

    private fun handleConnectionError(error: Exception) {
        Log.e("ControlVM", "Connection error on attempt $connectionAttempts", error)
        connectionStarted = false

        viewModelScope.launch {
            // DON'T show duplicate dialogs
            if (connectionAttempts < maxRetryAttempts) {
                _connectionState.value = ConnectionState.Failed(
                    error.message ?: "Connection failed",
                    connectionAttempts
                )

                // Show error in UI but don't show dialog
                _uiState.update {
                    it.copy(
                        errorMessage = "Connection failed (attempt $connectionAttempts/$maxRetryAttempts)",
                        loadingPercent = -1
                    )
                }

                // Auto retry
                delay(3000)
                if (!isOfflineMode && !isShowingTimeoutDialog) {
                    startConnection()
                }
            } else {
                // Max retries - show timeout dialog
                _connectionState.value = ConnectionState.MaxRetriesExceeded(
                    "Failed after $maxRetryAttempts attempts"
                )
                isShowingTimeoutDialog = true
            }
        }
    }

    private fun handleConnectionTimeout() {
        Log.e("ControlVM", "Connection timeout on attempt $connectionAttempts")
        connectionStarted = false
        connectionTimeoutManager.cancelTimeout()

        // Disable auto-retry khi showing timeout dialog
        autoRetryEnabled = false
        isShowingTimeoutDialog = true

        viewModelScope.launch {
            if (connectionAttempts < maxRetryAttempts) {
                _connectionState.value = ConnectionState.Failed(
                    "Connection timeout (attempt $connectionAttempts/$maxRetryAttempts)",
                    connectionAttempts
                )
                // KHÔNG auto retry ở đây
            } else {
                _connectionState.value = ConnectionState.MaxRetriesExceeded(
                    "Failed to connect after $maxRetryAttempts attempts"
                )
            }
        }
    }

    private fun startConnectionMonitoring() {
        connectionMonitorJob?.cancel()
        connectionMonitorJob = viewModelScope.launch {
            Log.d("ControlVM", "📡 Starting connection monitoring")

            var consecutiveFailures = 0
            val maxFailures = 3

            while (isActive && connectionStarted) {
                delay(5000) // Check every 5 seconds

                try {
                    // Only monitor if we think we're connected
                    if (_connectionState.value !is ConnectionState.Connected) {
                        Log.d("ControlVM", "Skipping monitor - not in Connected state")
                        continue
                    }

                    val isConnected = withTimeoutOrNull(2000L) {
                        repoImpl.isConnected()
                    } ?: false

                    if (!isConnected) {
                        consecutiveFailures++
                        Log.w("ControlVM", "Connection check failed ($consecutiveFailures/$maxFailures)")

                        if (consecutiveFailures >= maxFailures) {
                            handleConnectionLost()
                            break
                        }
                    } else {
                        // Connection is good
                        if (consecutiveFailures > 0) {
                            Log.d("ControlVM", "Connection recovered")
                        }
                        consecutiveFailures = 0
                        lastSuccessfulPing = System.currentTimeMillis()

                        // Ensure state is Connected
                        if (_connectionState.value !is ConnectionState.Connected) {
                            Log.d("ControlVM", "Correcting state to Connected")
                            _connectionState.value = ConnectionState.Connected
                        }
                    }
                } catch (e: Exception) {
                    Log.e("ControlVM", "Monitor error", e)
                    consecutiveFailures++
                    if (consecutiveFailures >= maxFailures) {
                        handleConnectionLost()
                        break
                    }
                }
            }

            Log.d("ControlVM", "📡 Connection monitoring stopped")
        }
    }

    private fun handleConnectionLost() {
        Log.w("ControlVM", "🔌 Connection lost detected!")

        // Cancel monitoring first
        connectionMonitorJob?.cancel()
        connectionMonitorJob = null

        // Update states
        connectionStarted = false

        // Set Failed state with clear message
        _connectionState.value = ConnectionState.Failed("Connection lost", 0)

        _uiState.update {
            it.copy(
                errorMessage = "Connection to PLC lost - Reconnecting...",
                loadingPercent = -1
            )
        }

        // Auto retry if not in offline mode
        if (!isOfflineMode && !isShowingTimeoutDialog) {
            viewModelScope.launch {
                Log.d("ControlVM", "🔄 Auto-reconnecting in 3 seconds...")
                delay(3000)

                // Reset attempts for reconnection
                connectionAttempts = 0

                // Clear old connection state
                _connectionState.value = ConnectionState.Idle
                delay(500)

                // Start fresh connection
                startConnection()
            }
        }
    }


    // THÊM: Cho phép tiếp tục ở chế độ offline
    fun continueOffline() {
        Log.d("ControlVM", "🔌 Continuing in offline mode")

        viewModelScope.launch {
            // Stop everything first
            stopConnection()
            delay(500)

            // Set offline state
            isOfflineMode = true
            connectionStarted = false
            connectionAttempts = 0
            autoRetryEnabled = false
            isShowingTimeoutDialog = false

            _connectionState.value = ConnectionState.Offline

            // Ensure we have default data to show
            _uiState.update {
                it.copy(
                    loadingPercent = 100, // Important: Set to 100 to show UI
                    errorMessage = null,
                    lockedButtons = (0..14).toSet() + (203..204).toSet() + setOf(999),
                    busyButtons = emptySet(),
                    plcData = it.plcData.takeIf { data ->
                        // Keep existing data if any
                        data.bools.isNotEmpty() || data.ints.isNotEmpty()
                    } ?: PlcData(
                        // Otherwise use default data
                        bools = List(15) { false },
                        ints = List(31) { 0 }
                    ),
                    selectedFunction = it.selectedFunction,
                    intInputs = it.intInputs,
                    openDialogForIndex = null,
                    dialogTitle = "",
                    isWriting = false,
                    isProcessing = false,
                    controlsBlockedByAlarm = false
                )
            }
        }
    }

    fun refreshConnectionState() {
        viewModelScope.launch {
            val isConnected = repoImpl.isConnected()
            val loadingPercent = _uiState.value.loadingPercent

            Log.d("ControlVM", "Refreshing connection state: connected=$isConnected, loading=$loadingPercent")

            _connectionState.value = when {
                isConnected && loadingPercent == 100 -> ConnectionState.Connected
                loadingPercent in 1..99 -> ConnectionState.Connecting(connectionAttempts)
                isOfflineMode -> ConnectionState.Offline
                else -> ConnectionState.Failed("Not connected", connectionAttempts)
            }
        }
    }


    fun dismissTimeoutDialog() {
        isShowingTimeoutDialog = false
        autoRetryEnabled = true
    }

    // THÊM: Reset connection attempts
    fun resetConnectionAttempts() {
        connectionAttempts = 0
        isOfflineMode = false
    }

    // Update error handling trong startConnection
    private suspend fun startOptimizedDataObservation() {
        dataObservationJob?.cancel()

        dataObservationJob = viewModelScope.launch {
            var consecutiveErrors = 0
            val maxConsecutiveErrors = 3

            repoImpl.observePlcData()
                .flowOn(Dispatchers.Default)
                .distinctUntilChanged()
                .sample(uiUpdateThrottle.milliseconds)
                .onEach {
                    // Reset error count on successful data
                    consecutiveErrors = 0
                }
                .catch { err ->
                    consecutiveErrors++
                    Log.e("ControlVM", "Data observation error ($consecutiveErrors/$maxConsecutiveErrors)", err)

                    if (consecutiveErrors >= maxConsecutiveErrors) {
                        // Set proper error state
                        _uiState.update {
                            it.copy(
                                loadingPercent = -1,
                                errorMessage = "Connection lost: ${err.message}"
                            )
                        }

                        _connectionState.value = ConnectionState.Failed("Connection lost", 0)
                        connectionStarted = false

                        // Try to reconnect unless in offline mode
                        if (!isOfflineMode) {
                            delay(2000)
                            startConnection()
                        }
                    }
                }
                .collect { data ->
                    val now = System.currentTimeMillis()
                    if (now - lastUiUpdateTime >= uiUpdateThrottle) {
                        lastUiUpdateTime = now
                        updateUIWithPlcData(data)
                    }
                }
        }
    }

    private suspend fun updateUIWithPlcData(data: PlcData) {
        // Check if we're still connected - now using the method correctly
        if (!connectionStarted || !repoImpl.isConnected()) {
            _uiState.update {
                it.copy(
                    errorMessage = "Connection lost to PLC",
                    loadingPercent = -1
                )
            }
            return
        }

        // Lấy active buttons từ PLC data
        val activeButtons = getActiveButtons(data)

        // Lấy status value từ PLC data (ints[0])
        val statusValue = data.ints.getOrNull(0) ?: 0

        // Tính toán locked buttons
        val lockedButtons = if (currentProcessingButton != null) {
            // Nếu đang xử lý, khóa tất cả nút trừ nút đang xử lý
            val allButtons = (0..14).toSet() + (203..230).toSet()
            currentProcessingButton?.let {
                allButtons - it
            } ?: allButtons
        } else {
            // Kết hợp cả buttonLockRules VÀ statusLockConfig
            val ruleBasedLocks = buttonLockRules.getLockedButtons(data)
            val statusBasedLocks = statusLockConfig.getLockedButtonsForStatus(statusValue)

            // Union của cả 2 loại lock
            ruleBasedLocks + statusBasedLocks
        }

        // Debug logging for button lock rules
        if (lockedButtons.isNotEmpty()) {
            Log.d("ControlVM", "📋 Locked buttons (status=$statusValue): ${lockedButtons.size} buttons: $lockedButtons")
        }

        _uiState.update {
            it.copy(
                plcData = data,
                errorMessage = null,
                lockedButtons = lockedButtons,
                busyButtons = if (currentProcessingButton != null) setOf(currentProcessingButton!!) else emptySet()
            )
        }
    }

    /**
     * Internal thread-safe button release implementation
     * Optimized: Update state immediately, API call async
     */
    private suspend fun performButtonRelease(index: Int): Boolean {
        val startTime = System.currentTimeMillis()

        val currentState = buttonStates[index]
        if (currentState?.isPressed != true) {
            Log.w("ControlVM", "Button $index not pressed, ignoring release")
            return false
        }

        try {
            // Cancel any ongoing operation
            currentState.operationJob?.cancel()

            // Immediately update local state (optimistic update)
            pressedButtons.remove(index)
            updateButtonStates { it - index }

            // Update button state immediately
            buttonStates[index] = currentState.copy(
                isPressed = false,
                lastActionTime = System.currentTimeMillis(),
                operationJob = null
            )

            // Clear current processing if it's this button
            if (currentProcessingButton == index) {
                currentProcessingButton = null
            }

            val stateTime = System.currentTimeMillis() - startTime
            Log.d("ControlVM", "🔄 Button $index state updated in ${stateTime}ms")

            // Write to PLC async (fire-and-forget for faster response)
            if (connectionStarted) {
                viewModelScope.launch(Dispatchers.IO) {
                    val apiStartTime = System.currentTimeMillis()
                    try {
                        repoImpl.writeBoolean(index, false)
                        val apiTime = System.currentTimeMillis() - apiStartTime
                        Log.d("ControlVM", "✅ Button $index release API took ${apiTime}ms")
                    } catch (e: Exception) {
                        val apiTime = System.currentTimeMillis() - apiStartTime
                        Log.e("ControlVM", "❌ Button $index release API failed after ${apiTime}ms", e)
                    }
                }
            }

            val totalTime = System.currentTimeMillis() - startTime
            Log.d("ControlVM", "✅ Button $index released in ${totalTime}ms (API async)")
            return true

        } catch (e: Exception) {
            Log.e("ControlVM", "Error releasing button $index", e)
            return false
        }
    }

    fun resetConnection() {
        viewModelScope.launch {
            Log.d("ControlVM", "♻️ Resetting connection completely...")

            // Stop everything first
            connectionMutex.withLock {
                connectionStarted = false
                connectionMonitorJob?.cancel()
                dataObservationJob?.cancel()

                // Clear all states
                connectionAttempts = 0
                isOfflineMode = false
                isShowingTimeoutDialog = false

                // Set to Idle state
                _connectionState.value = ConnectionState.Idle

                // Stop repository
                try {
                    repoImpl.stop()
                } catch (e: Exception) {
                    Log.e("ControlVM", "Error stopping repository", e)
                }
            }

            // Wait for cleanup
            delay(2000)

            // Start fresh connection
            startConnection()
        }
    }

    /**
     * THREAD-SAFE: Release all buttons in a group except one
     */
    private suspend fun releaseButtonGroup(group: Set<Int>, except: Int? = null) {
        coroutineScope {
            group.filter { it != except && pressedButtons.contains(it) }
                .map { buttonIndex ->
                    async {
                        performButtonRelease(buttonIndex)
                    }
                }
                .awaitAll()
        }
    }

    /**
     * THREAD-SAFE: Update UI button states
     */
    private suspend fun updateButtonStates(transform: (Set<Int>) -> Set<Int>) {
        _uiState.update { currentState ->
            currentState.copy(
                busyButtons = transform(currentState.busyButtons)
            )
        }
    }

    /**
     * THREAD-SAFE: Get all active buttons
     */
    private fun getActiveButtons(data: PlcData): Set<Int> {
        val active = Collections.synchronizedSet(mutableSetOf<Int>())

        // Check bool buttons
        data.bools.forEachIndexed { index, value ->
            if (value) active.add(index)
        }

        // Check int buttons
        listOf(3, 4).forEach { index ->
            if ((data.ints.getOrNull(index) ?: 0) != 0) {
                active.add(index + INT_OFFSET)
            }
        }

        return active.toSet() // Return immutable copy
    }

    /**
     * Generic button action với global lock
     */
    private suspend fun executeButtonAction(
        buttonIndex: Int,
        actionName: String,
        action: suspend () -> Unit
    ): Boolean {
        // Check operator lock first (before acquiring global lock)
        if (!checkLockBeforeWrite(actionName)) {
            return false
        }

        // Try to acquire global lock
        if (!globalProcessingLock.tryLock()) {
            Log.d("ControlVM", "❌ Cannot $actionName button $buttonIndex - another operation in progress")
            return false
        }

        try {
            val state = _uiState.value

            // Check basic conditions
            if (!connectionStarted) {
                Log.d("ControlVM", "❌ Cannot $actionName - not connected")
                return false
            }

            // Check if button is already locked
            if (buttonIndex in state.lockedButtons) {
                Log.d("ControlVM", "❌ Button $buttonIndex is locked")
                return false
            }

            // Mark this button as processing
            currentProcessingButton = buttonIndex

            // Update UI immediately to show all other buttons as locked
            _uiState.update { currentState ->
                val allButtons = (0..14).toSet() + (203..230).toSet()
                currentState.copy(
                    isWriting = true,
                    busyButtons = setOf(buttonIndex),
                    lockedButtons = allButtons - buttonIndex // Lock all except current
                )
            }

            Log.d("ControlVM", "🔄 Starting $actionName for button $buttonIndex")

            // Execute the action
            action()

            Log.d("ControlVM", "✅ Completed $actionName for button $buttonIndex")
            return true

        } catch (e: Exception) {
            Log.e("ControlVM", "❌ Error in $actionName for button $buttonIndex", e)
            _uiState.update { it.copy(errorMessage = "Operation failed: ${e.message}") }
            return false

        } finally {
            // Clear processing state
            currentProcessingButton = null

            // Update UI state
            _uiState.update { it.copy(isWriting = false) }

            // Release global lock
            globalProcessingLock.unlock()

            // Force immediate UI update
            updateUIWithPlcData(_uiState.value.plcData)
        }
    }

    /**
     * THREAD-SAFE: Toggle boolean with proper locking
     * Auto buttons (6, 7, 8, 9) sử dụng hàm này
     */
    fun onToggleBoolean(index: Int, newValue: Boolean) {
        // Check if in offline mode
        if (_connectionState.value is ConnectionState.Offline) {
            Log.w("ControlVM", "Cannot toggle boolean in offline mode")
            _uiState.update {
                it.copy(errorMessage = "Controls disabled in offline mode")
            }
            return
        }

        if (_uiState.value.controlsBlockedByAlarm) {
            _uiState.update {
                it.copy(errorMessage = "Điều khiển bị khóa do cảnh báo hệ thống")
            }
            return
        }

        // Check if connected first
        if (!connectionStarted || _uiState.value.loadingPercent != 100) {
            Log.w("ControlVM", "Cannot toggle boolean - not connected")
            return
        }

        // Kiểm tra nếu đang tắt nút (newValue = false) thì hiện dialog xác nhận
        // Tương tự như manual buttons
        val currentPlcData = _uiState.value.plcData
        val isButtonCurrentlyActive = currentPlcData.bools.getOrNull(index) == true

        if (isButtonCurrentlyActive && !newValue) {
            // Đang muốn hủy chức năng -> hiện dialog xác nhận
            val buttonName = StatusLockConfig.BUTTON_INDEX_TO_NAME[index] ?: "Nút $index"
            Log.d("ControlVM", "🔔 Auto button $index is active, showing cancel confirmation dialog")
            _uiState.update {
                it.copy(
                    cancelConfirmDialog = CancelConfirmDialog(
                        buttonIndex = index,
                        buttonName = buttonName
                    )
                )
            }
            return
        }

        viewModelScope.launch {
            // Check operator lock first
            if (!checkLockBeforeWrite("toggle boolean $index")) {
                return@launch
            }

            // Sử dụng tryLock để không block - nếu đang có operation khác thì từ chối
            if (!globalProcessingLock.tryLock()) {
                Log.d("ControlVM", "❌ Cannot toggle boolean $index - another operation in progress")
                return@launch
            }

            try {
                // Update UI to show processing
                _uiState.update { it.copy(busyButtons = it.busyButtons + index) }

                // Write to PLC
                repoImpl.writeBoolean(index, newValue)

            } catch (e: Exception) {
                Log.e("ControlVM", "Error toggling boolean $index", e)
                _uiState.update { it.copy(errorMessage = e.message) }

                // Check if connection lost
                if (e.message?.contains("connection", ignoreCase = true) == true ||
                    e.message?.contains("timeout", ignoreCase = true) == true) {
                    handleConnectionLost()
                }
            } finally {
                // Clear busy state
                _uiState.update { it.copy(busyButtons = it.busyButtons - index) }
                globalProcessingLock.unlock()
            }
        }
    }

    // Thêm hàm mới để xử lý Emergency Stop
    private suspend fun executeEmergencyStop(activate: Boolean) {
        try {
            // Không lock emergency stop button
            _uiState.update { currentState ->
                currentState.copy(
                    isWriting = true,
                    busyButtons = setOf(10)
                )
            }

            // Write to PLC
            repoImpl.writeBoolean(10, activate)

            // Delay nhỏ để PLC xử lý
            delay(100)

        } catch (e: Exception) {
            Log.e("ControlVM", "Error in emergency stop", e)
            _uiState.update { it.copy(errorMessage = "Emergency stop failed: ${e.message}") }
        } finally {
            _uiState.update { it.copy(isWriting = false) }
        }
    }

    /**
     * THREAD-SAFE: Press button with proper synchronization
     */
    fun onPressButton(index: Int) {
        // Check if in offline mode
        if (_connectionState.value is ConnectionState.Offline) {
            Log.w("ControlVM", "Cannot press button in offline mode")
            return
        }

        // Kiểm tra nếu nút đang active (value = true trong PLC data) thì hiện dialog xác nhận
        val currentPlcData = _uiState.value.plcData
        val isButtonCurrentlyActive = when {
            index in 0..13 -> currentPlcData.bools.getOrNull(index) == true
            index >= 200 -> {
                // Int buttons: check if int value > 0
                val intIndex = index - 200
                (currentPlcData.ints.getOrNull(intIndex) ?: 0) > 0
            }
            else -> false
        }

        if (isButtonCurrentlyActive) {
            // Hiện dialog xác nhận hủy
            val buttonName = StatusLockConfig.BUTTON_INDEX_TO_NAME[index] ?: "Nút $index"
            Log.d("ControlVM", "🔔 Button $index is active, showing cancel confirmation dialog")
            _uiState.update {
                it.copy(
                    cancelConfirmDialog = CancelConfirmDialog(
                        buttonIndex = index,
                        buttonName = buttonName
                    )
                )
            }
            return
        }

        viewModelScope.launch {
            val success = buttonOperationMutex.withLock {
                performButtonPress(index)
            }

            if (!success) {
                Log.w("ControlVM", "Button $index press rejected")
            }
        }
    }

    /**
     * Xác nhận hủy chức năng - ghi false vào PLC
     */
    fun onConfirmCancelFunction() {
        val dialog = _uiState.value.cancelConfirmDialog ?: return
        val index = dialog.buttonIndex

        Log.d("ControlVM", "✅ User confirmed cancel for button $index")

        // Đóng dialog
        _uiState.update { it.copy(cancelConfirmDialog = null) }

        // Ghi false vào PLC để hủy chức năng
        viewModelScope.launch {
            try {
                if (index in 0..13) {
                    repoImpl.writeBoolean(index, false)
                    Log.d("ControlVM", "✅ Cancelled function: wrote false to bool[$index]")
                } else if (index >= 200) {
                    val intIndex = index - 200
                    repoImpl.writeInt(intIndex, 0)
                    Log.d("ControlVM", "✅ Cancelled function: wrote 0 to int[$intIndex]")
                }
            } catch (e: Exception) {
                Log.e("ControlVM", "❌ Failed to cancel function for button $index", e)
            }
        }
    }

    /**
     * Hủy dialog xác nhận (không hủy chức năng)
     */
    fun onDismissCancelDialog() {
        Log.d("ControlVM", "❌ User dismissed cancel confirmation dialog")
        _uiState.update { it.copy(cancelConfirmDialog = null) }
    }

    /**
     * Internal thread-safe button press implementation
     * Optimized: Fast checks first, API call done async (fire-and-forget for manual buttons)
     */
    private suspend fun performButtonPress(index: Int): Boolean {
        val startTime = System.currentTimeMillis()

        // Quick checks first (no blocking)
        if (_connectionState.value is ConnectionState.Offline) {
            Log.w("ControlVM", "Cannot press button in offline mode")
            return false
        }

        // Check operator lock before write
        if (!checkLockBeforeWrite("press button $index")) {
            return false
        }

        // Check if connected
        if (!connectionStarted || _uiState.value.loadingPercent != 100) {
            Log.w("ControlVM", "Cannot press button - not connected")
            return false
        }

        // Check if button is already pressed
        val currentState = buttonStates[index]
        if (currentState?.isPressed == true) {
            Log.w("ControlVM", "Button $index already pressed")
            return false
        }

        // Check minimum interval between actions
        val now = System.currentTimeMillis()
        if (currentState != null && (now - currentState.lastActionTime) < MIN_BUTTON_ACTION_INTERVAL) {
            Log.w("ControlVM", "Button $index action too fast")
            return false
        }

        // Check if another button is being processed globally
        if (!globalProcessingLock.tryLock()) {
            Log.w("ControlVM", "Another button operation in progress")
            return false
        }

        try {
            // For manual movement buttons, release others in group (async)
            val manualButtons = setOf(0, 1, 2, 3)
            val isManualButton = index in manualButtons
            if (isManualButton) {
                // Fire-and-forget release for other manual buttons
                viewModelScope.launch {
                    releaseButtonGroup(manualButtons, except = index)
                }
            }

            // Immediately update local state (optimistic update)
            pressedButtons.add(index)
            updateButtonStates { it + index }

            val checkTime = System.currentTimeMillis() - startTime
            Log.d("ControlVM", "🔄 Button $index checks done in ${checkTime}ms")

            // Create button state with async API call
            val newState = ButtonState(
                index = index,
                isPressed = true,
                lastActionTime = now,
                operationJob = viewModelScope.launch(Dispatchers.IO) {
                    val apiStartTime = System.currentTimeMillis()
                    try {
                        // Write to PLC (this is the slow part)
                        repoImpl.writeBoolean(index, true)

                        val apiTime = System.currentTimeMillis() - apiStartTime
                        Log.d("ControlVM", "✅ Button $index API call took ${apiTime}ms")
                    } catch (e: Exception) {
                        val apiTime = System.currentTimeMillis() - apiStartTime
                        Log.e("ControlVM", "❌ Button $index API failed after ${apiTime}ms", e)

                        // Cleanup on error
                        pressedButtons.remove(index)
                        updateButtonStates { it - index }

                        if (e.message?.contains("Not connected", ignoreCase = true) == true ||
                            e.message?.contains("connection", ignoreCase = true) == true) {
                            handleConnectionLost()
                        }
                    }
                }
            )

            // Store button state
            buttonStates[index] = newState
            currentProcessingButton = index

            val totalTime = System.currentTimeMillis() - startTime
            Log.d("ControlVM", "✅ Button $index press initiated in ${totalTime}ms (API call async)")
            return true

        } catch (e: Exception) {
            Log.e("ControlVM", "Failed to press button $index", e)
            return false
        } finally {
            globalProcessingLock.unlock()
        }
    }

    /**
     * THREAD-SAFE: Release button with proper synchronization
     */
    fun onReleaseButton(index: Int) {
        viewModelScope.launch {
            buttonOperationMutex.withLock {
                performButtonRelease(index)
            }
        }
    }

    fun resetProcessingState() {
        viewModelScope.launch {
            Log.d("ControlVM", "🔄 Resetting processing state")

            currentProcessingButton = null

            // Unlock global lock nếu đang bị lock
            if (globalProcessingLock.isLocked) {
                try {
                    globalProcessingLock.unlock()
                } catch (e: Exception) {
                    // Ignore
                }
            }

            _uiState.update {
                it.copy(
                    isWriting = false,
                    busyButtons = emptySet(),
                    lockedButtons = emptySet()
                )
            }

            // Force UI update
            updateUIWithPlcData(_uiState.value.plcData)
        }
    }

    /**
     * THREAD-SAFE: Release all pressed buttons
     */
    fun releaseAllButtons() {
        viewModelScope.launch {
            buttonOperationMutex.withLock {
                Log.d("ControlVM", "🔄 Releasing all pressed buttons")

                val buttonsCopy = pressedButtons.toList() // Thread-safe copy

                coroutineScope {
                    buttonsCopy.map { index ->
                        async {
                            try {
                                performButtonRelease(index)
                            } catch (e: Exception) {
                                Log.e("ControlVM", "Error releasing button $index", e)
                            }
                        }
                    }.awaitAll()
                }

                // Clear all states
                buttonStates.clear()
                pressedButtons.clear()
                currentProcessingButton = null
            }
        }
    }

    fun confirmNumber(index: Int, value: Int) {
        viewModelScope.launch {
            val buttonIndex = index + INT_OFFSET
            executeButtonAction(buttonIndex, "write int") {
                repoImpl.writeInt(index, value)
            }
            _uiState.update { it.copy(openDialogForIndex = null) }
        }
    }

    fun onSendAll() {
        viewModelScope.launch {
            // Check if Send All button is locked
            val sendAllIndex = 999
            if (sendAllIndex in _uiState.value.lockedButtons) {
                Log.w("ControlVM", "Send All button is locked by status")
                return@launch
            }

            // Use special index for "send all" operation
            executeButtonAction(sendAllIndex, "send all") {
                // Write function code
                repoImpl.writeInt(functionCodeNodeIndex, uiState.value.selectedFunction)

                // Write coordinate values
                listOf(5, 6, 7, 8, 9, 10).forEach { idx ->
                    val txt = uiState.value.intInputs[idx]
                        ?: uiState.value.plcData.ints.getOrNull(idx)?.toString() ?: "0"
                    val value = txt.toIntOrNull() ?: 0
                    repoImpl.writeInt(idx, value)
                }
            }
        }
    }

    fun onFunctionSelected(code: Int) {
        _uiState.update { it.copy(selectedFunction = code) }
    }

    fun onInlineValueChange(index: Int, text: String) {
        _uiState.update {
            it.copy(intInputs = it.intInputs.toMutableMap().apply { put(index, text) })
        }
    }

    fun openNumberDialog(title: String, index: Int) {
        // Check if we can open dialog
        if (currentProcessingButton != null) {
            Log.d("ControlVM", "Cannot open dialog - operation in progress")
            return
        }
        _uiState.update { state ->
            state.copy(
                openDialogForIndex = index,
                dialogTitle = title
            )
        }
    }

    fun dismissDialog() {
        _uiState.update { it.copy(openDialogForIndex = null) }
    }

    fun stopConnection() {
        viewModelScope.launch {
            connectionMutex.withLock {
                Log.d("ControlVM", "🛑 Stopping connection...")

                connectionStarted = false
                isOfflineMode = false
                connectionTimeoutManager.cancelTimeout()
                connectionMonitorJob?.cancel()
                dataObservationJob?.cancel()

                try {
                    releaseAllButtons()
                    repoImpl.stop()
                    delay(1000)

                    _connectionState.value = ConnectionState.Idle
                    currentProcessingButton = null
                    _uiState.update {
                        it.copy(
                            loadingPercent = 0,
                            errorMessage = null,
                            busyButtons = emptySet(),
                            lockedButtons = emptySet()
                        )
                    }

                    Log.d("ControlVM", "✅ Connection stopped")
                } catch (e: Exception) {
                    Log.e("ControlVM", "Error stopping connection", e)
                }
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        connectionTimeoutManager.cancelTimeout()
        connectionMonitorJob?.cancel()
        dataObservationJob?.cancel()
        GlobalScope.launch {
            try {
                repoImpl.stop()
            } catch (e: Exception) {
                Log.e("ControlVM", "Error stopping in onCleared", e)
            }
        }
    }
}