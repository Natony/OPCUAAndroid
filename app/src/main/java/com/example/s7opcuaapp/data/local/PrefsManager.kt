package com.example.s7opcuaapp.data.local

import android.content.Context
import androidx.preference.PreferenceManager
import com.example.s7opcuaapp.data.model.DeviceEntity
import com.example.s7opcuaapp.data.model.UserCredentials
import com.google.gson.reflect.TypeToken
import com.google.gson.Gson
import javax.inject.Inject

class PrefsManager @Inject constructor(context: Context) {

    private val prefs = PreferenceManager.getDefaultSharedPreferences(context)
    private val gson = Gson()

    companion object {
        private const val KEY_SESSION_ID        = "session_id"
        private const val KEY_USER_ID           = "user_id"
        private const val KEY_USERNAME          = "username"
        private const val KEY_USER_ROLE         = "user_role"
        private const val KEY_REMEMBER_ME       = "remember_me"
        private const val KEY_CREDS             = "saved_credentials"
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
        // Button lock rules
        private const val KEY_BUTTON_LOCK_RULES = "button_lock_rules_v2"
        private const val KEY_BUTTON_LOCK_RULES_OVERRIDE = "button_lock_rules_override"
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
}