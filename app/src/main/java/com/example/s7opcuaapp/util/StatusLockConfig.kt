package com.example.s7opcuaapp.util

import com.example.s7opcuaapp.data.local.PrefsManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import android.util.Log
/**
 * Configuration for status-based button locking
 * Manages which buttons should be locked based on PLC status value
 */
@Singleton
class StatusLockConfig @Inject constructor(
    private val prefsManager: PrefsManager
) {

    private val gson = Gson()

    companion object {
        private const val PREFS_KEY_STATUS_LOCK_CONFIG = "status_lock_config"
        const val SEND_ALL_BUTTON_INDEX = 999  // Special index cho nút Send All

        // Default status descriptions - Mapping với các nút chức năng
        // 0: Chưa sẵn sàng (default/uninitialized)
        // 1: Đã sẵn sàng (ready - unlock buttons)
        // 2: Dừng khẩn cấp (emergency stop)
        // 3-16: Đang thực hiện các chức năng (lock buttons)
        val DEFAULT_STATUS_DESCRIPTIONS = mapOf(
            0 to "Chưa sẵn sàng",
            1 to "Đã sẵn sàng",
            2 to "Dừng khẩn cấp",
            3 to "Đang nhập n pallet",       // Nút Pallets Plus (int[3])
            4 to "Đang xuất n pallet",       // Nút Pallets Minus (int[4])
            5 to "Đang nhập pallet",         // Nút Pallet Plus (bool[7])
            // Note: Status 6 không sử dụng
            7 to "Đang xuất pallet",         // Nút Pallet Minus (bool[6])
            8 to "Đang Stack A",             // Nút Stack A (bool[8])
            9 to "Đang Stack B",             // Nút Stack B (bool[9])
            10 to "Đang tiến",               // Nút Forward (bool[0])
            11 to "Đang lùi",                // Nút Reverse (bool[1])
            12 to "Đang nâng",               // Nút Up (bool[2])
            13 to "Đang hạ",                 // Nút Down (bool[3])
            14 to "Đang thực hiện chức năng 1",  // Function 1
            15 to "Đang thực hiện chức năng 2",  // Function 2
            16 to "Đang thực hiện chức năng 3"   // Function 3
        )
    }

    data class StatusLockRule(
        val statusValue: Int,
        val description: String,
        val lockAllButtons: Boolean = true,
        val isEnabled: Boolean = true,
        val exemptButtons: Set<Int> = emptySet() // Buttons that are NOT locked in this status
    )

    // Default configuration
    // Logic: Chỉ mở khóa khi status = 1 (Đã sẵn sàng) VÀ Power ON
    // Tất cả các status khác đều lock buttons
    private val defaultRules = DEFAULT_STATUS_DESCRIPTIONS.mapValues { (status, description) ->
        when (status) {
            0 -> StatusLockRule(
                statusValue = status,
                description = description,
                lockAllButtons = true, // Chưa sẵn sàng - LOCK all
                isEnabled = true,
                exemptButtons = setOf(4, 5) // Chỉ cho Power (4) và Reset (5)
            )
            1 -> StatusLockRule(
                statusValue = status,
                description = description,
                lockAllButtons = false, // Đã sẵn sàng - UNLOCK all
                isEnabled = true,
                exemptButtons = emptySet()
            )
            2 -> StatusLockRule(
                statusValue = status,
                description = description,
                lockAllButtons = true, // Dừng khẩn cấp - LOCK all
                isEnabled = true,
                exemptButtons = setOf(5) // Chỉ cho Reset (5)
            )
            in 3..5, 7, in 8..16 -> StatusLockRule(
                statusValue = status,
                description = description,
                lockAllButtons = true, // Đang thực hiện - LOCK all
                isEnabled = true,
                exemptButtons = setOf(10) // Chỉ cho Emergency Stop (10)
            )
            else -> StatusLockRule(
                statusValue = status,
                description = description,
                lockAllButtons = true, // Unknown status - LOCK all for safety
                isEnabled = true,
                exemptButtons = setOf(10) // Emergency stop always available
            )
        }
    }

    // Current rules (can be modified by admin)
    private val _currentRules = MutableStateFlow<Map<Int, StatusLockRule>>(defaultRules)
    val currentRules: StateFlow<Map<Int, StatusLockRule>> = _currentRules.asStateFlow()

    // Track if override is active
    private val _overrideActive = MutableStateFlow(false)
    val overrideActive: StateFlow<Boolean> = _overrideActive.asStateFlow()

    init {
        // Load override state từ prefs
        _overrideActive.value = prefsManager.getStatusLockOverride()
        loadConfiguration()
    }


    /**
     * Get buttons that should be locked for given status
     * @param statusValue Current PLC status value
     * @return Set of button indices that should be locked
     */
    fun getLockedButtonsForStatus(statusValue: Int): Set<Int> {
        // If override is active, return empty (no locks)
        if (_overrideActive.value) {
            return emptySet()
        }

        val rule = _currentRules.value[statusValue]

        // If no rule defined or rule is disabled, default to no locks
        if (rule == null || !rule.isEnabled) {
            return emptySet()
        }

        // If lockAllButtons is true, lock all except exempt buttons
        return if (rule.lockAllButtons) {
            // All possible button indices
            val allButtons = (0..14).toSet() + // Bool buttons
                    (203..204).toSet() + // Int buttons 3,4 with offset
                    setOf(SEND_ALL_BUTTON_INDEX)

            // Remove exempt buttons
            allButtons - rule.exemptButtons
        } else {
            // No locks for this status
            emptySet()
        }
    }

    /**
     * Check if a specific button should be locked in given status
     */
    fun isButtonLockedInStatus(buttonIndex: Int, statusValue: Int): Boolean {
        return buttonIndex in getLockedButtonsForStatus(statusValue)
    }

    /**
     * Update lock rule for a specific status (Admin only)
     */
    fun updateStatusLockRule(rule: StatusLockRule) {
        _currentRules.value = _currentRules.value.toMutableMap().apply {
            put(rule.statusValue, rule)
        }
        saveConfiguration()
    }

    /**
     * Enable/disable lock for specific status
     */
    fun setStatusLockEnabled(statusValue: Int, enabled: Boolean) {
        val currentRule = _currentRules.value[statusValue] ?: return
        updateStatusLockRule(currentRule.copy(isEnabled = enabled))
    }

    /**
     * Add/remove exempt button for a status
     */
    fun toggleExemptButton(statusValue: Int, buttonIndex: Int) {
        val currentRule = _currentRules.value[statusValue] ?: return
        val newExemptButtons = if (buttonIndex in currentRule.exemptButtons) {
            currentRule.exemptButtons - buttonIndex
        } else {
            currentRule.exemptButtons + buttonIndex
        }
        updateStatusLockRule(currentRule.copy(exemptButtons = newExemptButtons))
    }

    /**
     * Activate/deactivate emergency override (disables all locks)
     */
    fun setOverrideActive(active: Boolean) {
        _overrideActive.value = active
        prefsManager.setStatusLockOverride(active) // Lưu vào prefs
        android.util.Log.w("StatusLockConfig",
            "Emergency override ${if (active) "ACTIVATED" else "DEACTIVATED"}")
    }


    /**
     * Reset to default configuration
     */
    fun resetToDefaults() {
        _currentRules.value = defaultRules
        _overrideActive.value = false
        saveConfiguration()
    }

    /**
     * Get description for status
     */
    fun getStatusDescription(statusValue: Int): String {
        return _currentRules.value[statusValue]?.description
            ?: DEFAULT_STATUS_DESCRIPTIONS[statusValue]
            ?: "Unknown Status $statusValue"
    }

    /**
     * Save configuration to persistent storage
     */
    private fun saveConfiguration() {
        val rulesJson = _currentRules.value.map { (status, rule) ->
            mapOf(
                "status" to status,
                "description" to rule.description,
                "lockAll" to rule.lockAllButtons,
                "enabled" to rule.isEnabled,
                "exempt" to rule.exemptButtons.toList()
            )
        }

        val configString = gson.toJson(rulesJson)
        prefsManager.saveStatusLockConfig(configString)
    }

    /**
     * Load configuration from persistent storage
     */
    private fun loadConfiguration() {
        val saved = prefsManager.getStatusLockConfig()
        if (saved != null) {
            try {
                val type = object : TypeToken<List<Map<String, Any>>>() {}.type
                val rulesJson: List<Map<String, Any>> = gson.fromJson(saved, type)

                val rules = rulesJson.associate { map ->
                    val status = (map["status"] as Double).toInt()
                    val rule = StatusLockRule(
                        statusValue = status,
                        description = map["description"] as String,
                        lockAllButtons = map["lockAll"] as Boolean,
                        isEnabled = map["enabled"] as Boolean,
                        exemptButtons = (map["exempt"] as? List<*>)?.let { list ->
                            list.mapNotNull { item ->
                                when (item) {
                                    is Double -> item.toInt()
                                    is Int -> item
                                    is Number -> item.toInt()
                                    else -> null
                                }
                            }.toSet()
                        } ?: emptySet()
                    )
                    status to rule
                }

                _currentRules.value = rules
            } catch (e: Exception) {
                Log.e("StatusLockConfig", "Failed to load configuration", e)
                // Keep default rules on error
            }
        }
    }
    /**
     * Export current configuration as string (for backup)
     */
    fun exportConfiguration(): String {
        // Implementation for exporting config
        return ""
    }

    /**
     * Import configuration from string
     */
    fun importConfiguration(configString: String): Boolean {
        // Implementation for importing config
        return false
    }
}