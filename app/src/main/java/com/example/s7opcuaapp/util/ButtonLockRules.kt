package com.example.s7opcuaapp.util

import android.util.Log
import com.example.s7opcuaapp.data.local.PrefsManager
import com.example.s7opcuaapp.data.model.PlcData
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Flexible button lock configuration system
 * Allows locking buttons based on:
 * - Boolean values of other buttons (e.g., lock when Emergency = true)
 * - Integer/status values (e.g., lock when status = 5)
 * - Combination of multiple conditions (AND/OR)
 */
@Singleton
class ButtonLockRules @Inject constructor(
    private val prefsManager: PrefsManager
) {
    companion object {
        private const val TAG = "ButtonLockRules"
        private const val PREFS_KEY = "button_lock_rules_v2"

        // Button name mapping for UI
        val BUTTON_NAMES = mapOf(
            0 to "Shuttle Forward",
            1 to "Shuttle Reverse",
            2 to "Fork Up",
            3 to "Fork Down",
            4 to "Power",
            5 to "Reset",
            6 to "Pallet Minus",
            7 to "Pallet Plus",
            8 to "Stack A",
            9 to "Stack B",
            10 to "Emergency Stop",
            11 to "Mode Switch",
            12 to "Alarm Ack",
            13 to "Direction",
            14 to "Count Pallet",
            203 to "Số pallet lấy ra",
            204 to "Số pallet đưa vào",
            999 to "Send All"
        )

        val INT_NAMES = mapOf(
            0 to "Status",
            1 to "Alarm Code",
            2 to "Position",
            3 to "Pallet Out",
            4 to "Pallet In",
            5 to "X Position",
            6 to "Y Position",
            7 to "Z Position",
            8 to "Function Code"
        )
    }

    private val gson = Gson()

    // Condition types
    sealed class Condition {
        abstract fun evaluate(plcData: PlcData): Boolean
        abstract fun toMap(): Map<String, Any>

        /**
         * Lock when a boolean has specific value
         * Example: Lock when Emergency (bool[10]) = true
         */
        data class BoolEquals(
            val boolIndex: Int,
            val expectedValue: Boolean
        ) : Condition() {
            override fun evaluate(plcData: PlcData): Boolean {
                val actualValue = plcData.bools.getOrNull(boolIndex) ?: false
                return actualValue == expectedValue
            }

            override fun toMap() = mapOf(
                "type" to "bool_equals",
                "boolIndex" to boolIndex,
                "expectedValue" to expectedValue
            )
        }

        /**
         * Lock when an integer equals specific value
         * Example: Lock when status (int[0]) = 5
         */
        data class IntEquals(
            val intIndex: Int,
            val expectedValue: Int
        ) : Condition() {
            override fun evaluate(plcData: PlcData): Boolean {
                val actualValue = plcData.ints.getOrNull(intIndex) ?: 0
                return actualValue == expectedValue
            }

            override fun toMap() = mapOf(
                "type" to "int_equals",
                "intIndex" to intIndex,
                "expectedValue" to expectedValue
            )
        }

        /**
         * Lock when an integer is NOT equal to specific value
         * Example: Lock when Power status != 1 (not ready)
         */
        data class IntNotEquals(
            val intIndex: Int,
            val notExpectedValue: Int
        ) : Condition() {
            override fun evaluate(plcData: PlcData): Boolean {
                val actualValue = plcData.ints.getOrNull(intIndex) ?: 0
                return actualValue != notExpectedValue
            }

            override fun toMap() = mapOf(
                "type" to "int_not_equals",
                "intIndex" to intIndex,
                "notExpectedValue" to notExpectedValue
            )
        }

        /**
         * Lock when an integer is in a range
         * Example: Lock when status in 2..6 (executing)
         */
        data class IntInRange(
            val intIndex: Int,
            val minValue: Int,
            val maxValue: Int
        ) : Condition() {
            override fun evaluate(plcData: PlcData): Boolean {
                val actualValue = plcData.ints.getOrNull(intIndex) ?: 0
                return actualValue in minValue..maxValue
            }

            override fun toMap() = mapOf(
                "type" to "int_in_range",
                "intIndex" to intIndex,
                "minValue" to minValue,
                "maxValue" to maxValue
            )
        }

        companion object {
            fun fromMap(map: Map<String, Any>): Condition? {
                return try {
                    when (map["type"] as? String) {
                        "bool_equals" -> BoolEquals(
                            boolIndex = (map["boolIndex"] as Number).toInt(),
                            expectedValue = map["expectedValue"] as Boolean
                        )
                        "int_equals" -> IntEquals(
                            intIndex = (map["intIndex"] as Number).toInt(),
                            expectedValue = (map["expectedValue"] as Number).toInt()
                        )
                        "int_not_equals" -> IntNotEquals(
                            intIndex = (map["intIndex"] as Number).toInt(),
                            notExpectedValue = (map["notExpectedValue"] as Number).toInt()
                        )
                        "int_in_range" -> IntInRange(
                            intIndex = (map["intIndex"] as Number).toInt(),
                            minValue = (map["minValue"] as Number).toInt(),
                            maxValue = (map["maxValue"] as Number).toInt()
                        )
                        else -> null
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error parsing condition", e)
                    null
                }
            }
        }
    }

    /**
     * Logic operator for combining conditions
     */
    enum class LogicOperator {
        AND,  // All conditions must be true
        OR    // Any condition can be true
    }

    /**
     * Lock rule for a specific button
     */
    data class LockRule(
        val targetButtonIndex: Int,        // Button to be locked
        val conditions: List<Condition>,    // Conditions to evaluate
        val operator: LogicOperator = LogicOperator.OR,  // How to combine conditions
        val isEnabled: Boolean = true,      // Enable/disable rule
        val description: String = ""        // User-friendly description
    ) {
        /**
         * Check if button should be locked based on current PLC data
         */
        fun shouldLock(plcData: PlcData): Boolean {
            if (!isEnabled || conditions.isEmpty()) return false

            return when (operator) {
                LogicOperator.AND -> conditions.all { it.evaluate(plcData) }
                LogicOperator.OR -> conditions.any { it.evaluate(plcData) }
            }
        }

        fun toMap(): Map<String, Any> = mapOf(
            "targetButtonIndex" to targetButtonIndex,
            "conditions" to conditions.map { it.toMap() },
            "operator" to operator.name,
            "isEnabled" to isEnabled,
            "description" to description
        )

        companion object {
            @Suppress("UNCHECKED_CAST")
            fun fromMap(map: Map<String, Any>): LockRule? {
                return try {
                    val conditionsRaw = map["conditions"] as? List<Map<String, Any>> ?: emptyList()
                    val conditions = conditionsRaw.mapNotNull { Condition.fromMap(it) }

                    LockRule(
                        targetButtonIndex = (map["targetButtonIndex"] as Number).toInt(),
                        conditions = conditions,
                        operator = LogicOperator.valueOf(map["operator"] as? String ?: "OR"),
                        isEnabled = map["isEnabled"] as? Boolean ?: true,
                        description = map["description"] as? String ?: ""
                    )
                } catch (e: Exception) {
                    Log.e(TAG, "Error parsing lock rule", e)
                    null
                }
            }
        }
    }

    // Current rules
    private val _rules = MutableStateFlow<List<LockRule>>(emptyList())
    val rules: StateFlow<List<LockRule>> = _rules.asStateFlow()

    // Override (disable all rules)
    private val _overrideActive = MutableStateFlow(false)
    val overrideActive: StateFlow<Boolean> = _overrideActive.asStateFlow()

    init {
        loadRules()
        // Load default rules if empty
        if (_rules.value.isEmpty()) {
            loadDefaultRules()
        }
    }

    /**
     * Get all buttons that should be locked based on current PLC data
     */
    fun getLockedButtons(plcData: PlcData): Set<Int> {
        if (_overrideActive.value) {
            return emptySet()
        }

        val locked = mutableSetOf<Int>()
        for (rule in _rules.value) {
            if (rule.shouldLock(plcData)) {
                locked.add(rule.targetButtonIndex)
            }
        }

        return locked
    }

    /**
     * Check if a specific button should be locked
     */
    fun isButtonLocked(buttonIndex: Int, plcData: PlcData): Boolean {
        if (_overrideActive.value) return false

        return _rules.value
            .filter { it.targetButtonIndex == buttonIndex }
            .any { it.shouldLock(plcData) }
    }

    /**
     * Add a new lock rule
     */
    fun addRule(rule: LockRule) {
        _rules.value = _rules.value + rule
        saveRules()
        Log.d(TAG, "Added rule for button ${rule.targetButtonIndex}: ${rule.description}")
    }

    /**
     * Update an existing rule
     */
    fun updateRule(index: Int, rule: LockRule) {
        val current = _rules.value.toMutableList()
        if (index in current.indices) {
            current[index] = rule
            _rules.value = current
            saveRules()
        }
    }

    /**
     * Remove a rule
     */
    fun removeRule(index: Int) {
        val current = _rules.value.toMutableList()
        if (index in current.indices) {
            current.removeAt(index)
            _rules.value = current
            saveRules()
        }
    }

    /**
     * Remove all rules for a specific button
     */
    fun removeRulesForButton(buttonIndex: Int) {
        _rules.value = _rules.value.filter { it.targetButtonIndex != buttonIndex }
        saveRules()
    }

    /**
     * Enable/disable override (unlock all buttons)
     */
    fun setOverrideActive(active: Boolean) {
        _overrideActive.value = active
        prefsManager.setButtonLockRulesOverride(active)
        Log.w(TAG, "Override ${if (active) "ACTIVATED" else "DEACTIVATED"}")
    }

    /**
     * Load default rules
     */
    private fun loadDefaultRules() {
        val defaults = mutableListOf<LockRule>()

        // Rule 1: Lock Power Off (4) when Emergency (10) is active
        // Nghĩa là: Khi đang Emergency thì không được tắt Power
        defaults.add(LockRule(
            targetButtonIndex = 4,
            conditions = listOf(Condition.BoolEquals(boolIndex = 10, expectedValue = true)),
            operator = LogicOperator.OR,
            isEnabled = true,
            description = "Khóa nút Power khi Emergency đang kích hoạt"
        ))

        // Rule 2: Lock auto buttons (6,7,8,9) when Power (4) is OFF
        // Nghĩa là: Các nút tự động chỉ hoạt động khi Power đã bật
        listOf(6, 7, 8, 9).forEach { btnIndex ->
            defaults.add(LockRule(
                targetButtonIndex = btnIndex,
                conditions = listOf(Condition.BoolEquals(boolIndex = 4, expectedValue = false)),
                operator = LogicOperator.OR,
                isEnabled = true,
                description = "Khóa nút Auto khi Power chưa bật"
            ))
        }

        // Rule 3: Lock manual buttons (0,1,2,3) when Power (4) is OFF
        listOf(0, 1, 2, 3).forEach { btnIndex ->
            defaults.add(LockRule(
                targetButtonIndex = btnIndex,
                conditions = listOf(Condition.BoolEquals(boolIndex = 4, expectedValue = false)),
                operator = LogicOperator.OR,
                isEnabled = true,
                description = "Khóa nút Manual khi Power chưa bật"
            ))
        }

        // Rule 4: Lock all except Emergency when status is 2-6 (executing)
        val executingButtons = listOf(0, 1, 2, 3, 4, 6, 7, 8, 9, 14, 203, 204, 999)
        executingButtons.forEach { btnIndex ->
            defaults.add(LockRule(
                targetButtonIndex = btnIndex,
                conditions = listOf(Condition.IntInRange(intIndex = 0, minValue = 2, maxValue = 6)),
                operator = LogicOperator.OR,
                isEnabled = true,
                description = "Khóa khi đang thực hiện (status 2-6)"
            ))
        }

        // Rule 5: Lock Send All when Emergency is active
        defaults.add(LockRule(
            targetButtonIndex = 999,
            conditions = listOf(Condition.BoolEquals(boolIndex = 10, expectedValue = true)),
            operator = LogicOperator.OR,
            isEnabled = true,
            description = "Khóa Send All khi Emergency"
        ))

        _rules.value = defaults
        saveRules()
        Log.d(TAG, "Loaded ${defaults.size} default rules")
    }

    /**
     * Reset to default rules
     */
    fun resetToDefaults() {
        _rules.value = emptyList()
        _overrideActive.value = false
        loadDefaultRules()
    }

    /**
     * Save rules to persistent storage
     */
    private fun saveRules() {
        try {
            val rulesJson = _rules.value.map { it.toMap() }
            val jsonString = gson.toJson(rulesJson)
            prefsManager.saveButtonLockRules(jsonString)
        } catch (e: Exception) {
            Log.e(TAG, "Error saving rules", e)
        }
    }

    /**
     * Load rules from persistent storage
     */
    @Suppress("UNCHECKED_CAST")
    private fun loadRules() {
        try {
            val jsonString = prefsManager.getButtonLockRules() ?: return
            val type = object : TypeToken<List<Map<String, Any>>>() {}.type
            val rulesJson: List<Map<String, Any>> = gson.fromJson(jsonString, type)
            _rules.value = rulesJson.mapNotNull { LockRule.fromMap(it) }
            Log.d(TAG, "Loaded ${_rules.value.size} rules")
        } catch (e: Exception) {
            Log.e(TAG, "Error loading rules", e)
        }

        // Load override state
        _overrideActive.value = prefsManager.getButtonLockRulesOverride()
    }

    /**
     * Get rules for a specific button
     */
    fun getRulesForButton(buttonIndex: Int): List<LockRule> {
        return _rules.value.filter { it.targetButtonIndex == buttonIndex }
    }

    /**
     * Get human-readable description of a condition
     */
    fun describeCondition(condition: Condition): String {
        return when (condition) {
            is Condition.BoolEquals -> {
                val btnName = BUTTON_NAMES[condition.boolIndex] ?: "Bool[${condition.boolIndex}]"
                val state = if (condition.expectedValue) "ON" else "OFF"
                "$btnName = $state"
            }
            is Condition.IntEquals -> {
                val intName = INT_NAMES[condition.intIndex] ?: "Int[${condition.intIndex}]"
                "$intName = ${condition.expectedValue}"
            }
            is Condition.IntNotEquals -> {
                val intName = INT_NAMES[condition.intIndex] ?: "Int[${condition.intIndex}]"
                "$intName != ${condition.notExpectedValue}"
            }
            is Condition.IntInRange -> {
                val intName = INT_NAMES[condition.intIndex] ?: "Int[${condition.intIndex}]"
                "$intName trong ${condition.minValue}..${condition.maxValue}"
            }
        }
    }
}
