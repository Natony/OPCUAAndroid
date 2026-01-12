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
        private const val CONNECT_TIMEOUT = 30L
        private const val READ_TIMEOUT = 30L
        private const val WRITE_TIMEOUT = 30L
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
            // Use BODY level to see actual JSON response for debugging
            level = HttpLoggingInterceptor.Level.BODY
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
     * Set the auth token for API requests
     */
    fun setAuthToken(token: String?) {
        authToken = token
        Log.d(TAG, if (token != null) "Auth token set" else "Auth token cleared")
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
     * Connect to the server
     */
    suspend fun connect(plcId: String? = null): Boolean = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Connecting to server: $serverUrl")

            // First check if server is reachable via REST and get PLC list
            val statusResponse = apiService.getPlcStatus()
            if (!statusResponse.isSuccessful) {
                Log.e(TAG, "Server not reachable: ${statusResponse.code()}")
                return@withContext false
            }

            // Get the first connected PLC's ID from server (not local device ID)
            val plcList = statusResponse.body()?.data ?: emptyList()
            val serverPlcId = plcList.firstOrNull()?.plcId

            if (serverPlcId != null) {
                Log.d(TAG, "Found server PLC ID: $serverPlcId")
                currentPlcId = serverPlcId

                // Connect to the PLC using server's PLC ID
                val connectResponse = apiService.connectPlc(serverPlcId)
                if (!connectResponse.isSuccessful || connectResponse.body()?.success != true) {
                    Log.w(TAG, "Connect PLC returned: ${connectResponse.body()?.error}, but continuing...")
                    // Don't fail - PLC might already be connected
                }
            } else {
                Log.w(TAG, "No PLCs found on server, using local ID")
                currentPlcId = plcId
            }

            // Connect to SignalR for real-time updates
            val signalRConnected = signalRClient.connect()
            if (!signalRConnected) {
                Log.w(TAG, "SignalR connection failed, will use polling")
            }

            // Subscribe to specific PLC if we have an ID
            currentPlcId?.let { id ->
                signalRClient.subscribeToPlc(id)
            }

            _isConnected.value = true
            onConnectionRestored?.invoke()
            Log.d(TAG, "Connected successfully with PLC ID: $currentPlcId")
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
     */
    suspend fun disconnect() = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Disconnecting...")

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

            currentPlcId = null
            _isConnected.value = false
            Log.d(TAG, "Disconnected")

        } catch (e: Exception) {
            Log.e(TAG, "Error during disconnect", e)
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

        return try {
            // URL encode the nodeId
            val encodedNodeId = java.net.URLEncoder.encode(nodeId, "UTF-8")

            val response = apiService.writeTag(
                plcId = plcId,
                nodeId = encodedNodeId,
                request = WriteTagRequest(value)
            )

            if (response.isSuccessful && response.body()?.success == true) {
                Log.d(TAG, "Write successful: $nodeId = $value")
                true
            } else {
                Log.e(TAG, "Write failed: ${response.body()?.error}")
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error writing tag: $nodeId", e)
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
     */
    suspend fun getLockStatus(): LockStatusResponse? = withContext(Dispatchers.IO) {
        try {
            val response = apiService.getLockStatus()
            Log.d(TAG, "Lock status response: code=${response.code()}, success=${response.body()?.success}, data=${response.body()?.data}")
            if (response.isSuccessful && response.body()?.success == true) {
                val lockStatus = response.body()?.data
                Log.d(TAG, "Lock status: isLocked=${lockStatus?.isLocked}, isMyLock=${lockStatus?.isMyLock}, lockedBy=${lockStatus?.lockedByUsername}")
                lockStatus
            } else {
                Log.w(TAG, "Lock status failed: code=${response.code()}, error=${response.body()?.error}")
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
            val response = apiService.acquireLock(AcquireLockRequest(durationMinutes))
            if (response.isSuccessful) {
                response.body()
            } else {
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
