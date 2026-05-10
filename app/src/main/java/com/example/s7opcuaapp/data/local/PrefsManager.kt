package com.example.s7opcuaapp.data.local

import android.content.Context
import androidx.preference.PreferenceManager
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.example.s7opcuaapp.data.model.DeviceEntity
import com.example.s7opcuaapp.data.model.DirectPlcConfig
import com.example.s7opcuaapp.data.model.DirectSecurityPolicy
import com.example.s7opcuaapp.data.model.UserCredentials
import com.google.gson.reflect.TypeToken
import com.google.gson.Gson
import javax.inject.Inject

class PrefsManager @Inject constructor(private val context: Context) {

    private val prefs = PreferenceManager.getDefaultSharedPreferences(context)
    private val gson = Gson()

    // Encrypted store for sensitive Direct-mode credentials (password only).
    private val securePrefs by lazy {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            "direct_creds_prefs",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    companion object {
        private const val KEY_SESSION_ID        = "session_id"
        private const val KEY_USER_ID           = "user_id"
        private const val KEY_USERNAME          = "username"
        private const val KEY_USER_ROLE         = "user_role"
        private const val KEY_REMEMBER_ME       = "remember_me"
        private const val KEY_CREDS             = "saved_credentials"
        // Single-session auth
        private const val KEY_DEVICE_ID         = "device_id"           // Unique device identifier
        private const val KEY_DEVICE_NAME       = "device_name"         // Display name for this device
        private const val KEY_REFRESH_TOKEN     = "refresh_token"       // For logout and token refresh
        private const val KEY_ACCESS_TOKEN      = "access_token"        // JWT access token
        private const val KEY_DEVICES_JSON      = "device_list"
        private const val KEY_CURRENT_DEVICE_ID = "current_device_id"
        private const val KEY_STATUS_LOCK_CONFIG = "status_lock_config"
        private const val KEY_STATUS_LOCK_OVERRIDE = "status_lock_override"
        // API Server config
        private const val KEY_API_SERVER_IP     = "api_server_ip"
        private const val KEY_API_SERVER_PORT   = "api_server_port"
        private const val DEFAULT_API_SERVER_IP = "192.168.137.1"
        private const val DEFAULT_API_SERVER_PORT = "5000"
        // Selected PLC from API server
        private const val KEY_SELECTED_PLC_ID   = "selected_plc_id"
        private const val KEY_SELECTED_PLC_NAME = "selected_plc_name"
        // Connection mode + Direct PLC config (backup connection)
        private const val KEY_CONNECTION_MODE        = "connection_mode"
        private const val KEY_DIRECT_ENDPOINT_URL    = "direct_endpoint_url"
        private const val KEY_DIRECT_SECURITY_POLICY = "direct_security_policy"
        private const val KEY_DIRECT_USERNAME        = "direct_username"
        private const val KEY_DIRECT_PUBLISHING_MS   = "direct_publishing_ms"
        private const val KEY_DIRECT_SAMPLING_MS     = "direct_sampling_ms"
        private const val KEY_DIRECT_KEEP_ALIVE_S    = "direct_keep_alive_s"
        private const val KEY_DIRECT_PASSWORD        = "direct_password"
        private const val KEY_IS_DIRECT_MODE_ACTIVE  = "is_direct_mode_active"
        private const val DEFAULT_DIRECT_ENDPOINT    = "opc.tcp://192.168.1.100:4840"
        // Button lock rules
        private const val KEY_BUTTON_LOCK_RULES = "button_lock_rules_v2"
        private const val KEY_BUTTON_LOCK_RULES_OVERRIDE = "button_lock_rules_override"
        // Response timeout settings (in milliseconds)
        private const val KEY_CONNECTION_TIMEOUT = "connection_timeout"
        private const val KEY_REQUEST_TIMEOUT = "request_timeout"
        private const val KEY_POLLING_INTERVAL = "polling_interval"
        private const val KEY_BUTTON_RESPONSE_TIMEOUT = "button_response_timeout"
        private const val KEY_BUTTON_DEBOUNCE_TIME = "button_debounce_time"
        private const val DEFAULT_CONNECTION_TIMEOUT = 10000L // 10 seconds
        private const val DEFAULT_REQUEST_TIMEOUT = 5000L    // 5 seconds
        private const val DEFAULT_POLLING_INTERVAL = 500L    // 500ms
        private const val DEFAULT_BUTTON_RESPONSE_TIMEOUT = 3000L // 3 seconds
        private const val DEFAULT_BUTTON_DEBOUNCE_TIME = 300L    // 300ms
        // Per-button timeout settings (legacy - global)
        private const val KEY_BUTTON_TIMEOUTS = "button_timeouts_config"
        // Per-device button timeout settings
        private const val KEY_DEVICE_BUTTON_TIMEOUTS_PREFIX = "device_button_timeouts_"
    }

    fun saveSession(sessionId: String, userId: String, username: String, role: String) {
        prefs.edit()
            .putString(KEY_SESSION_ID, sessionId)
            .putString(KEY_USER_ID, userId)
            .putString(KEY_USERNAME, username)
            .putString(KEY_USER_ROLE, role)
            .apply()
    }

    fun clearSession() {
        prefs.edit()
            .remove(KEY_SESSION_ID)
            .remove(KEY_USER_ID)
            .remove(KEY_USERNAME)
            .remove(KEY_USER_ROLE)
            .apply()
    }

    fun getSessionId(): String? = prefs.getString(KEY_SESSION_ID, null)
    fun getUserId(): String? = prefs.getString(KEY_USER_ID, null)
    fun getUsername(): String? = prefs.getString(KEY_USERNAME, null)
    fun getUserRole(): String? = prefs.getString(KEY_USER_ROLE, null)

    // ============== Single-Session Auth Methods ==============

    /**
     * Get or generate unique device ID
     * Uses Android ID as base, persists for consistency
     */
    fun getDeviceId(context: Context): String {
        var deviceId = prefs.getString(KEY_DEVICE_ID, null)
        if (deviceId == null) {
            // Generate unique device ID based on Android ID + random suffix
            val androidId = android.provider.Settings.Secure.getString(
                context.contentResolver,
                android.provider.Settings.Secure.ANDROID_ID
            )
            deviceId = "$androidId-${System.currentTimeMillis()}"
            prefs.edit().putString(KEY_DEVICE_ID, deviceId).apply()
            android.util.Log.d("PrefsManager", "🆔 Generated new device ID: $deviceId")
        }
        return deviceId
    }

    /**
     * Get device name (display name)
     */
    fun getDeviceName(): String {
        return prefs.getString(KEY_DEVICE_NAME, null)
            ?: "${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}"
    }

    /**
     * Set device name
     */
    fun setDeviceName(name: String) {
        prefs.edit().putString(KEY_DEVICE_NAME, name).apply()
    }

    /**
     * Save tokens from login response
     */
    fun saveTokens(accessToken: String, refreshToken: String) {
        prefs.edit()
            .putString(KEY_ACCESS_TOKEN, accessToken)
            .putString(KEY_REFRESH_TOKEN, refreshToken)
            .apply()
    }

    /**
     * Get access token
     */
    fun getAccessToken(): String? = prefs.getString(KEY_ACCESS_TOKEN, null)

    /**
     * Get refresh token
     */
    fun getRefreshToken(): String? = prefs.getString(KEY_REFRESH_TOKEN, null)

    /**
     * Clear tokens (on logout)
     */
    fun clearTokens() {
        prefs.edit()
            .remove(KEY_ACCESS_TOKEN)
            .remove(KEY_REFRESH_TOKEN)
            .apply()
    }

    /**
     * Check if user is logged in (has valid session)
     */
    fun isLoggedIn(): Boolean {
        return getSessionId() != null && getAccessToken() != null
    }

    fun setRememberMe(remember: Boolean) {
        prefs.edit().putBoolean(KEY_REMEMBER_ME, remember).apply()
    }

    fun getRememberMe(): Boolean = prefs.getBoolean(KEY_REMEMBER_ME, false)

    fun saveCredentials(creds: UserCredentials) {
        prefs.edit()
            .putString(KEY_CREDS, gson.toJson(creds))
            .apply()
    }
    fun clearCredentials() {
        prefs.edit()
            .remove(KEY_CREDS)
            .apply()
    }

    fun getSavedCredentials(): UserCredentials? {
        val json = prefs.getString(KEY_CREDS, null) ?: return null
        return gson.fromJson(json, UserCredentials::class.java)
    }

    fun saveDeviceList(list: List<DeviceEntity>) {
        prefs.edit()
            .putString(KEY_DEVICES_JSON, gson.toJson(list))
            .apply()
    }
    fun getAllDevices(): List<DeviceEntity> {
        val json = prefs.getString(KEY_DEVICES_JSON, null) ?: return emptyList()
        val type = object : TypeToken<List<DeviceEntity>>() {}.type
        return gson.fromJson(json, type)
    }

    fun setCurrentDevice(device: DeviceEntity) {
        prefs.edit()
            .putString(KEY_CURRENT_DEVICE_ID, device.id)
            .apply()
    }
    fun getCurrentDevice(): DeviceEntity? {
        val all = getAllDevices()
        val currentId = prefs.getString(KEY_CURRENT_DEVICE_ID, null) ?: return null
        return all.find { it.id == currentId }
    }
    fun clearCurrentDevice() {
        prefs.edit()
            .remove(KEY_CURRENT_DEVICE_ID)
            .apply()
    }

    fun setStatusLockOverride(enabled: Boolean) {
        prefs.edit()
            .putBoolean(KEY_STATUS_LOCK_OVERRIDE, enabled)
            .apply()
    }

    fun getStatusLockOverride(): Boolean {
        return prefs.getBoolean(KEY_STATUS_LOCK_OVERRIDE, false)
    }

    fun saveStatusLockConfig(config: String) {
        prefs.edit()
            .putString(KEY_STATUS_LOCK_CONFIG, config)
            .apply()
    }

    fun getStatusLockConfig(): String? {
        return prefs.getString(KEY_STATUS_LOCK_CONFIG, null)
    }

    // API Server config
    fun saveApiServerConfig(ip: String, port: String) {
        prefs.edit()
            .putString(KEY_API_SERVER_IP, ip)
            .putString(KEY_API_SERVER_PORT, port)
            .apply()
    }

    fun getApiServerIp(): String {
        return prefs.getString(KEY_API_SERVER_IP, DEFAULT_API_SERVER_IP) ?: DEFAULT_API_SERVER_IP
    }

    fun getApiServerPort(): String {
        return prefs.getString(KEY_API_SERVER_PORT, DEFAULT_API_SERVER_PORT) ?: DEFAULT_API_SERVER_PORT
    }

    fun getApiServerUrl(): String {
        return "http://${getApiServerIp()}:${getApiServerPort()}"
    }

    // Connection mode (API vs Direct OPC UA backup)
    fun getConnectionMode(): ConnectionMode {
        val raw = prefs.getString(KEY_CONNECTION_MODE, ConnectionMode.API.name)
        return runCatching { ConnectionMode.valueOf(raw ?: ConnectionMode.API.name) }
            .getOrDefault(ConnectionMode.API)
    }

    fun saveConnectionMode(mode: ConnectionMode) {
        prefs.edit().putString(KEY_CONNECTION_MODE, mode.name).apply()
    }

    fun isDirectModeActive(): Boolean {
        return prefs.getBoolean(KEY_IS_DIRECT_MODE_ACTIVE, false)
    }

    fun setDirectModeActive(active: Boolean) {
        prefs.edit().putBoolean(KEY_IS_DIRECT_MODE_ACTIVE, active).apply()
    }

    // Direct PLC connection config
    fun getDirectPlcConfig(): DirectPlcConfig {
        val policyRaw = prefs.getString(KEY_DIRECT_SECURITY_POLICY, DirectSecurityPolicy.None.name)
        val policy = runCatching { DirectSecurityPolicy.valueOf(policyRaw ?: DirectSecurityPolicy.None.name) }
            .getOrDefault(DirectSecurityPolicy.None)
        return DirectPlcConfig(
            endpointUrl = prefs.getString(KEY_DIRECT_ENDPOINT_URL, DEFAULT_DIRECT_ENDPOINT) ?: DEFAULT_DIRECT_ENDPOINT,
            securityPolicy = policy,
            username = prefs.getString(KEY_DIRECT_USERNAME, "") ?: "",
            password = securePrefs.getString(KEY_DIRECT_PASSWORD, "") ?: "",
            publishingIntervalMs = java.lang.Double.longBitsToDouble(
                prefs.getLong(KEY_DIRECT_PUBLISHING_MS, java.lang.Double.doubleToRawLongBits(250.0))
            ),
            samplingIntervalMs = java.lang.Double.longBitsToDouble(
                prefs.getLong(KEY_DIRECT_SAMPLING_MS, java.lang.Double.doubleToRawLongBits(250.0))
            ),
            keepAliveSeconds = prefs.getInt(KEY_DIRECT_KEEP_ALIVE_S, 10)
        )
    }

    fun saveDirectPlcConfig(config: DirectPlcConfig) {
        prefs.edit()
            .putString(KEY_DIRECT_ENDPOINT_URL, config.endpointUrl)
            .putString(KEY_DIRECT_SECURITY_POLICY, config.securityPolicy.name)
            .putString(KEY_DIRECT_USERNAME, config.username)
            .putLong(KEY_DIRECT_PUBLISHING_MS, java.lang.Double.doubleToRawLongBits(config.publishingIntervalMs))
            .putLong(KEY_DIRECT_SAMPLING_MS, java.lang.Double.doubleToRawLongBits(config.samplingIntervalMs))
            .putInt(KEY_DIRECT_KEEP_ALIVE_S, config.keepAliveSeconds)
            .apply()
        securePrefs.edit().putString(KEY_DIRECT_PASSWORD, config.password).apply()
    }

    // Selected PLC from API server
    fun saveSelectedPlc(plcId: String, plcName: String) {
        android.util.Log.d("PrefsManager", "💾 Saving selected PLC: id=$plcId, name=$plcName")
        prefs.edit()
            .putString(KEY_SELECTED_PLC_ID, plcId)
            .putString(KEY_SELECTED_PLC_NAME, plcName)
            .apply()
    }

    fun getSelectedPlcId(): String? {
        return prefs.getString(KEY_SELECTED_PLC_ID, null)
    }

    fun getSelectedPlcName(): String? {
        return prefs.getString(KEY_SELECTED_PLC_NAME, null)
    }

    fun clearSelectedPlc() {
        android.util.Log.d("PrefsManager", "🗑️ Clearing selected PLC")
        prefs.edit()
            .remove(KEY_SELECTED_PLC_ID)
            .remove(KEY_SELECTED_PLC_NAME)
            .apply()
    }

    // Button lock rules
    fun saveButtonLockRules(config: String) {
        prefs.edit()
            .putString(KEY_BUTTON_LOCK_RULES, config)
            .apply()
    }

    fun getButtonLockRules(): String? {
        return prefs.getString(KEY_BUTTON_LOCK_RULES, null)
    }

    fun setButtonLockRulesOverride(enabled: Boolean) {
        prefs.edit()
            .putBoolean(KEY_BUTTON_LOCK_RULES_OVERRIDE, enabled)
            .apply()
    }

    fun getButtonLockRulesOverride(): Boolean {
        return prefs.getBoolean(KEY_BUTTON_LOCK_RULES_OVERRIDE, false)
    }

    // Response timeout settings
    fun saveTimeoutSettings(
        connectionTimeout: Long,
        requestTimeout: Long,
        pollingInterval: Long,
        buttonResponseTimeout: Long,
        buttonDebounceTime: Long
    ) {
        prefs.edit()
            .putLong(KEY_CONNECTION_TIMEOUT, connectionTimeout)
            .putLong(KEY_REQUEST_TIMEOUT, requestTimeout)
            .putLong(KEY_POLLING_INTERVAL, pollingInterval)
            .putLong(KEY_BUTTON_RESPONSE_TIMEOUT, buttonResponseTimeout)
            .putLong(KEY_BUTTON_DEBOUNCE_TIME, buttonDebounceTime)
            .apply()
    }

    fun getConnectionTimeout(): Long {
        return prefs.getLong(KEY_CONNECTION_TIMEOUT, DEFAULT_CONNECTION_TIMEOUT)
    }

    fun getRequestTimeout(): Long {
        return prefs.getLong(KEY_REQUEST_TIMEOUT, DEFAULT_REQUEST_TIMEOUT)
    }

    fun getPollingInterval(): Long {
        return prefs.getLong(KEY_POLLING_INTERVAL, DEFAULT_POLLING_INTERVAL)
    }

    fun getButtonResponseTimeout(): Long {
        return prefs.getLong(KEY_BUTTON_RESPONSE_TIMEOUT, DEFAULT_BUTTON_RESPONSE_TIMEOUT)
    }

    fun getButtonDebounceTime(): Long {
        return prefs.getLong(KEY_BUTTON_DEBOUNCE_TIME, DEFAULT_BUTTON_DEBOUNCE_TIME)
    }

    fun resetTimeoutSettings() {
        prefs.edit()
            .putLong(KEY_CONNECTION_TIMEOUT, DEFAULT_CONNECTION_TIMEOUT)
            .putLong(KEY_REQUEST_TIMEOUT, DEFAULT_REQUEST_TIMEOUT)
            .putLong(KEY_POLLING_INTERVAL, DEFAULT_POLLING_INTERVAL)
            .putLong(KEY_BUTTON_RESPONSE_TIMEOUT, DEFAULT_BUTTON_RESPONSE_TIMEOUT)
            .putLong(KEY_BUTTON_DEBOUNCE_TIME, DEFAULT_BUTTON_DEBOUNCE_TIME)
            .remove(KEY_BUTTON_TIMEOUTS)
            .apply()
    }

    // Per-button timeout settings
    data class ButtonTimeoutConfig(
        val buttonIndex: Int,
        val responseTimeout: Long = DEFAULT_BUTTON_RESPONSE_TIMEOUT,
        val debounceTime: Long = DEFAULT_BUTTON_DEBOUNCE_TIME
    )

    fun saveButtonTimeouts(configs: Map<Int, ButtonTimeoutConfig>) {
        val json = gson.toJson(configs)
        prefs.edit()
            .putString(KEY_BUTTON_TIMEOUTS, json)
            .apply()
    }

    fun getButtonTimeouts(): Map<Int, ButtonTimeoutConfig> {
        val json = prefs.getString(KEY_BUTTON_TIMEOUTS, null) ?: return emptyMap()
        return try {
            val type = object : com.google.gson.reflect.TypeToken<Map<Int, ButtonTimeoutConfig>>() {}.type
            gson.fromJson(json, type) ?: emptyMap()
        } catch (e: Exception) {
            emptyMap()
        }
    }

    fun getButtonTimeout(buttonIndex: Int): ButtonTimeoutConfig {
        val configs = getButtonTimeouts()
        return configs[buttonIndex] ?: ButtonTimeoutConfig(
            buttonIndex = buttonIndex,
            responseTimeout = getButtonResponseTimeout(),
            debounceTime = getButtonDebounceTime()
        )
    }

    fun saveButtonTimeout(config: ButtonTimeoutConfig) {
        val configs = getButtonTimeouts().toMutableMap()
        configs[config.buttonIndex] = config
        saveButtonTimeouts(configs)
    }

    // ==================== Per-Device Button Timeout Settings ====================

    /**
     * Get the key for storing button timeouts for a specific device
     */
    private fun getDeviceButtonTimeoutsKey(deviceId: String): String {
        return "$KEY_DEVICE_BUTTON_TIMEOUTS_PREFIX$deviceId"
    }

    /**
     * Save button timeouts for a specific device
     */
    fun saveDeviceButtonTimeouts(deviceId: String, configs: Map<Int, ButtonTimeoutConfig>) {
        val json = gson.toJson(configs)
        prefs.edit()
            .putString(getDeviceButtonTimeoutsKey(deviceId), json)
            .apply()
    }

    /**
     * Get button timeouts for a specific device
     * Falls back to global settings if no device-specific settings exist
     */
    fun getDeviceButtonTimeouts(deviceId: String): Map<Int, ButtonTimeoutConfig> {
        val json = prefs.getString(getDeviceButtonTimeoutsKey(deviceId), null)
        if (json == null) {
            // No device-specific settings, return empty (will use global defaults)
            return emptyMap()
        }
        return try {
            val type = object : TypeToken<Map<Int, ButtonTimeoutConfig>>() {}.type
            gson.fromJson(json, type) ?: emptyMap()
        } catch (e: Exception) {
            emptyMap()
        }
    }

    /**
     * Get timeout for a specific button on a specific device
     */
    fun getDeviceButtonTimeout(deviceId: String, buttonIndex: Int): ButtonTimeoutConfig {
        val configs = getDeviceButtonTimeouts(deviceId)
        return configs[buttonIndex] ?: ButtonTimeoutConfig(
            buttonIndex = buttonIndex,
            responseTimeout = getButtonResponseTimeout(),
            debounceTime = getButtonDebounceTime()
        )
    }

    /**
     * Save timeout for a specific button on a specific device
     */
    fun saveDeviceButtonTimeout(deviceId: String, config: ButtonTimeoutConfig) {
        val configs = getDeviceButtonTimeouts(deviceId).toMutableMap()
        configs[config.buttonIndex] = config
        saveDeviceButtonTimeouts(deviceId, configs)
    }

    /**
     * Reset all button timeouts for a specific device to global defaults
     */
    fun resetDeviceButtonTimeouts(deviceId: String) {
        prefs.edit()
            .remove(getDeviceButtonTimeoutsKey(deviceId))
            .apply()
    }

    /**
     * Get list of all device IDs that have custom button timeout settings
     */
    fun getDevicesWithCustomButtonTimeouts(): List<String> {
        return prefs.all.keys
            .filter { it.startsWith(KEY_DEVICE_BUTTON_TIMEOUTS_PREFIX) }
            .map { it.removePrefix(KEY_DEVICE_BUTTON_TIMEOUTS_PREFIX) }
    }
}