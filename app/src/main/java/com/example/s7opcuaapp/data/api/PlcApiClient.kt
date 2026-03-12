package com.example.s7opcuaapp.data.api

import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

/**
 * Main API client that manages both REST API and SignalR connections
 * Supports dynamic URL updates when device configuration changes
 */
class PlcApiClient(
    initialServerUrl: String
) {
    companion object {
        private const val TAG = "PlcApiClient"
        private const val CONNECT_TIMEOUT = 10L  // Reduced from 30s
        private const val READ_TIMEOUT = 10L     // Reduced from 30s
        private const val WRITE_TIMEOUT = 5L     // Reduced to 5s for faster manual control
    }

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // Current server URL - can be updated
    @Volatile
    private var serverUrl: String = initialServerUrl

    // Lock for thread-safe URL updates
    private val urlLock = Any()

    // Auth token for API requests
    @Volatile
    private var authToken: String? = null

    /**
     * Interceptor that adds Authorization header to all requests
     */
    private val authInterceptor = Interceptor { chain ->
        val originalRequest = chain.request()
        val token = authToken

        val newRequest = if (token != null) {
            originalRequest.newBuilder()
                .header("Authorization", "Bearer $token")
                .build()
        } else {
            originalRequest
        }

        chain.proceed(newRequest)
    }

    // HTTP client with auth interceptor
    private val okHttpClient: OkHttpClient by lazy {
        val logging = HttpLoggingInterceptor { message ->
            Log.d(TAG, message)
        }.apply {
            level = HttpLoggingInterceptor.Level.BASIC
        }

        OkHttpClient.Builder()
            .connectTimeout(CONNECT_TIMEOUT, TimeUnit.SECONDS)
            .readTimeout(READ_TIMEOUT, TimeUnit.SECONDS)
            .writeTimeout(WRITE_TIMEOUT, TimeUnit.SECONDS)
            .addInterceptor(authInterceptor)  // Add auth interceptor first
            .addInterceptor(logging)
            .build()
    }

    // Retrofit instance - recreated when URL changes
    @Volatile
    private var retrofit: Retrofit = createRetrofit(initialServerUrl)

    // API service - recreated when URL changes
    @Volatile
    private var _apiService: PlcApiService = retrofit.create(PlcApiService::class.java)
    val apiService: PlcApiService get() = _apiService

    // SignalR client - recreated when URL changes
    @Volatile
    private var _signalRClient: PlcSignalRClient = PlcSignalRClient(initialServerUrl)
    val signalRClient: PlcSignalRClient get() = _signalRClient

    private fun createRetrofit(url: String): Retrofit {
        return Retrofit.Builder()
            .baseUrl(url)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    /**
     * Set the auth token for API requests and SignalR
     */
    fun setAuthToken(token: String?) {
        authToken = token
        // Also set token for SignalR client
        _signalRClient.setAccessToken(token)
        Log.d(TAG, "═══════════════════════════════════════════════")
        Log.d(TAG, "🔑 AUTH TOKEN ${if (token != null) "SET" else "CLEARED"}")
        if (token != null) {
            Log.d(TAG, "   Token: ${token.take(30)}...")
        }
        Log.d(TAG, "═══════════════════════════════════════════════")
    }

    /**
     * Get the current auth token
     */
    fun getAuthToken(): String? = authToken

    /**
     * Update the server URL and recreate API clients
     */
    fun updateServerUrl(newServerUrl: String) {
        if (newServerUrl == serverUrl) return

        synchronized(urlLock) {
            Log.d(TAG, "Updating server URL from $serverUrl to $newServerUrl")

            // Cleanup old SignalR connection
            _signalRClient.cleanup()

            // Update URL and recreate clients
            serverUrl = newServerUrl
            retrofit = createRetrofit(newServerUrl)
            _apiService = retrofit.create(PlcApiService::class.java)
            _signalRClient = PlcSignalRClient(newServerUrl)

            // Pass current auth token to new SignalR client
            authToken?.let { _signalRClient.setAccessToken(it) }

            // Reset connection state
            _isConnected.value = false
            currentPlcId = null

            Log.d(TAG, "Server URL updated successfully")
        }
    }

    /**
     * Get current server URL
     */
    fun getServerUrl(): String = serverUrl

    // Connection state
    private val _isConnected = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> = _isConnected

    // Current PLC ID
    private var currentPlcId: String? = null

    // Callbacks
    private var onConnectionLost: (() -> Unit)? = null
    private var onConnectionRestored: (() -> Unit)? = null

    /**
     * Set connection callbacks
     */
    fun setConnectionCallbacks(
        onLost: (() -> Unit)? = null,
        onRestored: (() -> Unit)? = null
    ) {
        onConnectionLost = onLost
        onConnectionRestored = onRestored
    }

    /**
     * Connect to the server with a specific PLC
     * @param plcId The PLC ID to connect to (required - user must select a PLC first)
     */
    suspend fun connect(plcId: String? = null): Boolean = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "═══════════════════════════════════════════════")
            Log.d(TAG, "🔌 CONNECTING TO PLC")
            Log.d(TAG, "   Server URL: $serverUrl")
            Log.d(TAG, "   Provided plcId param: $plcId")
            Log.d(TAG, "   Stored currentPlcId: $currentPlcId")
            Log.d(TAG, "═══════════════════════════════════════════════")

            // First check if server is reachable
            val statusResponse = apiService.getPlcStatus()
            if (!statusResponse.isSuccessful) {
                Log.e(TAG, "Server not reachable: ${statusResponse.code()}")
                return@withContext false
            }

            // Use the provided plcId, or currentPlcId (set by user selection), or fall back to first PLC
            val targetPlcId = plcId ?: currentPlcId ?: run {
                // Final fallback: get first PLC from server (for backward compatibility)
                val plcList = statusResponse.body()?.data ?: emptyList()
                val firstPlcId = plcList.firstOrNull()?.plcId
                if (firstPlcId != null) {
                    Log.w(TAG, "No PLC ID set, using first PLC: $firstPlcId")
                } else {
                    Log.e(TAG, "No PLCs available on server")
                }
                firstPlcId
            }

            if (plcId != null) {
                Log.d(TAG, "Using provided PLC ID: $plcId")
            } else if (currentPlcId != null) {
                Log.d(TAG, "Using user-selected PLC ID: $currentPlcId")
            }

            if (targetPlcId == null) {
                Log.e(TAG, "No PLC ID available to connect")
                return@withContext false
            }

            currentPlcId = targetPlcId

            // Connect to the specific PLC
            val connectResponse = apiService.connectPlc(targetPlcId)
            if (!connectResponse.isSuccessful || connectResponse.body()?.success != true) {
                Log.w(TAG, "Connect PLC returned: ${connectResponse.body()?.error}, but continuing...")
                // Don't fail - PLC might already be connected
            }

            // Connect to SignalR for real-time updates
            val signalRConnected = signalRClient.connect()
            if (!signalRConnected) {
                Log.w(TAG, "SignalR connection failed, will use polling")
            }

            // Subscribe to specific PLC
            signalRClient.subscribeToPlc(targetPlcId)

            _isConnected.value = true
            onConnectionRestored?.invoke()
            Log.d(TAG, "═══════════════════════════════════════════════")
            Log.d(TAG, "✅ CONNECTION SUCCESSFUL")
            Log.d(TAG, "   Connected to PLC ID: $currentPlcId")
            Log.d(TAG, "   SignalR: ${if (signalRConnected) "Connected" else "Polling mode"}")
            Log.d(TAG, "═══════════════════════════════════════════════")
            true

        } catch (e: Exception) {
            Log.e(TAG, "Connection failed", e)
            _isConnected.value = false
            onConnectionLost?.invoke()
            false
        }
    }

    /**
     * Disconnect from the server
     * Note: Does NOT clear currentPlcId - the user's PLC selection should persist
     */
    suspend fun disconnect() = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Disconnecting from PLC: $currentPlcId...")

            // Disconnect PLC if connected
            currentPlcId?.let { plcId ->
                try {
                    apiService.disconnectPlc(plcId)
                } catch (e: Exception) {
                    Log.w(TAG, "Error disconnecting PLC", e)
                }
            }

            // Disconnect SignalR
            signalRClient.disconnect()

            // Don't clear currentPlcId - keep user's selection
            _isConnected.value = false
            Log.d(TAG, "Disconnected (currentPlcId preserved: $currentPlcId)")

        } catch (e: Exception) {
            Log.e(TAG, "Error during disconnect", e)
        }
    }

    /**
     * Get current PLC ID
     */
    fun getCurrentPlcId(): String? = currentPlcId

    /**
     * Set current PLC ID (for use after user selects a PLC)
     */
    fun setCurrentPlcId(plcId: String?) {
        val oldPlcId = currentPlcId
        currentPlcId = plcId
        Log.d(TAG, "═══════════════════════════════════════════════")
        Log.d(TAG, "🔄 PLC ID CHANGED")
        Log.d(TAG, "   Old: $oldPlcId")
        Log.d(TAG, "   New: $plcId")
        Log.d(TAG, "═══════════════════════════════════════════════")
    }

    /**
     * Get all PLCs from server
     */
    suspend fun getAllPlcs(): List<PlcDto> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "📋 Getting all PLCs - authToken: ${if (authToken != null) "SET (${authToken?.take(20)}...)" else "NULL"}")
            val response = apiService.getAllPlcs()
            if (response.isSuccessful && response.body()?.success == true) {
                val plcs = response.body()?.data ?: emptyList()
                Log.d(TAG, "Got ${plcs.size} PLCs from server")
                plcs
            } else {
                Log.e(TAG, "Failed to get PLCs: code=${response.code()}, error=${response.body()?.error}")
                emptyList()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting PLCs", e)
            emptyList()
        }
    }

    /**
     * Read all tags from current PLC
     */
    suspend fun readAllTags(): List<TagDto> = withContext(Dispatchers.IO) {
        try {
            val response = if (currentPlcId != null) {
                apiService.getTagsByPlc(currentPlcId!!)
            } else {
                apiService.getAllTags()
            }

            if (response.isSuccessful && response.body()?.success == true) {
                response.body()?.data ?: emptyList()
            } else {
                Log.e(TAG, "Failed to read tags: ${response.body()?.error}")
                emptyList()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error reading tags", e)
            emptyList()
        }
    }

    /**
     * Write a boolean value
     */
    suspend fun writeBoolean(nodeId: String, value: Boolean): Boolean = withContext(Dispatchers.IO) {
        writeValue(nodeId, value)
    }

    /**
     * Write an integer value
     */
    suspend fun writeInt(nodeId: String, value: Int): Boolean = withContext(Dispatchers.IO) {
        writeValue(nodeId, value)
    }

    /**
     * Write a value to a tag
     */
    private suspend fun writeValue(nodeId: String, value: Any): Boolean {
        val plcId = currentPlcId ?: return false

        val startTime = System.currentTimeMillis()
        return try {
            // URL encode the nodeId
            val encodedNodeId = java.net.URLEncoder.encode(nodeId, "UTF-8")

            val response = apiService.writeTag(
                plcId = plcId,
                nodeId = encodedNodeId,
                request = WriteTagRequest(value)
            )

            val elapsed = System.currentTimeMillis() - startTime
            if (response.isSuccessful && response.body()?.success == true) {
                Log.d(TAG, "✅ Write OK: $nodeId=$value in ${elapsed}ms")
                true
            } else {
                Log.e(TAG, "❌ Write failed: ${response.body()?.error} (${elapsed}ms)")
                false
            }
        } catch (e: Exception) {
            val elapsed = System.currentTimeMillis() - startTime
            Log.e(TAG, "❌ Write error: $nodeId (${elapsed}ms)", e)
            false
        }
    }

    /**
     * Check connection health
     */
    suspend fun checkConnectionHealth(): Boolean = withContext(Dispatchers.IO) {
        try {
            val response = apiService.getPlcStatus()
            val isHealthy = response.isSuccessful

            if (isHealthy && !_isConnected.value) {
                _isConnected.value = true
                onConnectionRestored?.invoke()
            } else if (!isHealthy && _isConnected.value) {
                _isConnected.value = false
                onConnectionLost?.invoke()
            }

            isHealthy
        } catch (e: Exception) {
            if (_isConnected.value) {
                _isConnected.value = false
                onConnectionLost?.invoke()
            }
            false
        }
    }

    /**
     * Get tag value updates flow (from SignalR)
     */
    fun observeTagUpdates(): SharedFlow<TagValueUpdate> {
        return signalRClient.tagValueUpdates
    }

    /**
     * Get connection state updates flow
     */
    fun observeConnectionStatus(): SharedFlow<ConnectionStatusDto> {
        return signalRClient.connectionStatusUpdates
    }

    /**
     * Cleanup resources
     */
    fun cleanup() {
        signalRClient.cleanup()
        scope.cancel()
    }

    // ============== Auth Methods ==============

    /**
     * Login with username and password
     */
    suspend fun login(username: String, password: String): LoginResponse? = withContext(Dispatchers.IO) {
        try {
            val response = apiService.login(LoginRequest(username, password))
            if (response.isSuccessful) {
                response.body()
            } else {
                Log.e(TAG, "Login failed: ${response.code()}")
                LoginResponse(
                    success = false,
                    accessToken = null,
                    refreshToken = null,
                    expiresAt = null,
                    user = null,
                    error = "Login failed: ${response.code()}"
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Login error", e)
            LoginResponse(
                success = false,
                accessToken = null,
                refreshToken = null,
                expiresAt = null,
                user = null,
                error = e.message
            )
        }
    }

    /**
     * Logout
     */
    suspend fun logout(): Boolean = withContext(Dispatchers.IO) {
        try {
            val response = apiService.logout()
            response.isSuccessful && response.body()?.success == true
        } catch (e: Exception) {
            Log.e(TAG, "Logout error", e)
            false
        }
    }

    /**
     * Get current user info
     */
    suspend fun getCurrentUser(): UserDto? = withContext(Dispatchers.IO) {
        try {
            val response = apiService.getCurrentUser()
            if (response.isSuccessful && response.body()?.success == true) {
                response.body()?.data
            } else {
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Get current user error", e)
            null
        }
    }

    // ============== Lock Methods ==============

    /**
     * Get lock status
     * Returns: { "success": true, "data": { ... } }
     */
    suspend fun getLockStatus(): LockStatusResponse? = withContext(Dispatchers.IO) {
        try {
            val response = apiService.getLockStatus()
            val apiResponse = response.body()
            Log.d(TAG, "Lock status response: code=${response.code()}, success=${apiResponse?.success}, data=${apiResponse?.data}")
            if (response.isSuccessful && apiResponse?.success == true) {
                val lockStatus = LockStatusResponse.fromApiResponse(apiResponse)
                Log.d(TAG, "Lock status: isLocked=${lockStatus.isLocked}, isMyLock=${lockStatus.isMyLock}, lockedBy=${lockStatus.lockedByUsername}, remaining=${lockStatus.remainingSeconds}")
                lockStatus
            } else {
                Log.w(TAG, "Lock status failed: code=${response.code()}, body=${apiResponse}")
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Get lock status error", e)
            null
        }
    }

    /**
     * Acquire operator lock
     */
    suspend fun acquireLock(durationMinutes: Int? = null): AcquireLockResponse? = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "📤 Requesting lock with durationMinutes=$durationMinutes")
            val response = apiService.acquireLock(AcquireLockRequest(durationMinutes))
            Log.d(TAG, "📥 Acquire lock response: code=${response.code()}, body=${response.body()}")
            if (response.isSuccessful) {
                val body = response.body()
                Log.d(TAG, "📥 Parsed AcquireLockResponse: success=${body?.success}, remainingSeconds=${body?.remainingSeconds}, expiresAt=${body?.expiresAt}, error=${body?.error}")
                body
            } else {
                val errorBody = response.errorBody()?.string()
                Log.e(TAG, "❌ Acquire lock failed: code=${response.code()}, error=$errorBody")
                AcquireLockResponse(
                    success = false,
                    expiresAt = null,
                    remainingSeconds = null,
                    error = "Failed to acquire lock: ${response.code()}"
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Acquire lock error", e)
            AcquireLockResponse(
                success = false,
                expiresAt = null,
                remainingSeconds = null,
                error = e.message
            )
        }
    }

    /**
     * Release operator lock
     */
    suspend fun releaseLock(): Boolean = withContext(Dispatchers.IO) {
        try {
            val response = apiService.releaseLock()
            response.isSuccessful && response.body()?.success == true
        } catch (e: Exception) {
            Log.e(TAG, "Release lock error", e)
            false
        }
    }

    /**
     * Extend lock duration
     */
    suspend fun extendLock(additionalMinutes: Int? = null): ExtendLockResponse? = withContext(Dispatchers.IO) {
        try {
            val response = apiService.extendLock(ExtendLockRequest(additionalMinutes))
            if (response.isSuccessful) {
                response.body()
            } else {
                ExtendLockResponse(
                    success = false,
                    newExpiresAt = null,
                    remainingSeconds = null,
                    error = "Failed to extend lock: ${response.code()}"
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Extend lock error", e)
            ExtendLockResponse(
                success = false,
                newExpiresAt = null,
                remainingSeconds = null,
                error = e.message
            )
        }
    }

    /**
     * Force release lock (Admin only)
     */
    suspend fun forceReleaseLock(reason: String?): Boolean = withContext(Dispatchers.IO) {
        try {
            val response = apiService.forceReleaseLock(ForceReleaseRequest(reason))
            response.isSuccessful && response.body()?.success == true
        } catch (e: Exception) {
            Log.e(TAG, "Force release lock error", e)
            false
        }
    }
}
