package com.example.s7opcuaapp.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.s7opcuaapp.util.ButtonLockRules
import com.example.s7opcuaapp.util.ButtonLockRules.Condition
import com.example.s7opcuaapp.util.ButtonLockRules.LockRule
import com.example.s7opcuaapp.util.ButtonLockRules.LogicOperator
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ButtonLockRulesViewModel @Inject constructor(
    private val buttonLockRules: ButtonLockRules
) : ViewModel() {

    data class UiState(
        val rules: List<LockRule> = emptyList(),
        val overrideActive: Boolean = false,
        val showAddRuleDialog: Boolean = false,
        val editingRuleIndex: Int? = null,
        val showResetConfirmation: Boolean = false,
        // Add rule dialog state
        val selectedTargetButton: Int = 0,
        val conditions: List<Condition> = emptyList(),
        val selectedOperator: LogicOperator = LogicOperator.OR,
        val ruleDescription: String = "",
        // Add condition dialog
        val showAddConditionDialog: Boolean = false,
        val conditionType: ConditionType = ConditionType.BOOL_EQUALS,
        val conditionBoolIndex: Int = 0,
        val conditionBoolValue: Boolean = true,
        val conditionIntIndex: Int = 0,
        val conditionIntValue: Int = 0,
        val conditionIntValue2: Int = 0  // For range
    )

    enum class ConditionType {
        BOOL_EQUALS,
        INT_EQUALS,
        INT_NOT_EQUALS,
        INT_IN_RANGE
    }

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            buttonLockRules.rules.collect { rules ->
                _uiState.update { it.copy(rules = rules) }
            }
        }
        viewModelScope.launch {
            buttonLockRules.overrideActive.collect { active ->
                _uiState.update { it.copy(overrideActive = active) }
            }
        }
    }

    fun toggleOverride() {
        buttonLockRules.setOverrideActive(!_uiState.value.overrideActive)
    }

    fun showAddRuleDialog() {
        _uiState.update {
            it.copy(
                showAddRuleDialog = true,
                editingRuleIndex = null,
                selectedTargetButton = 0,
                conditions = emptyList(),
                selectedOperator = LogicOperator.OR,
                ruleDescription = ""
            )
        }
    }

    fun editRule(index: Int) {
        val rule = _uiState.value.rules.getOrNull(index) ?: return
        _uiState.update {
            it.copy(
                showAddRuleDialog = true,
                editingRuleIndex = index,
                selectedTargetButton = rule.targetButtonIndex,
                conditions = rule.conditions,
                selectedOperator = rule.operator,
                ruleDescription = rule.description
            )
        }
    }

    fun hideAddRuleDialog() {
        _uiState.update { it.copy(showAddRuleDialog = false, editingRuleIndex = null) }
    }

    fun setTargetButton(buttonIndex: Int) {
        _uiState.update { it.copy(selectedTargetButton = buttonIndex) }
    }

    fun setOperator(operator: LogicOperator) {
        _uiState.update { it.copy(selectedOperator = operator) }
    }

    fun setDescription(description: String) {
        _uiState.update { it.copy(ruleDescription = description) }
    }

    // Condition dialog
    fun showAddConditionDialog() {
        _uiState.update {
            it.copy(
                showAddConditionDialog = true,
                conditionType = ConditionType.BOOL_EQUALS,
                conditionBoolIndex = 0,
                conditionBoolValue = true,
                conditionIntIndex = 0,
                conditionIntValue = 0,
                conditionIntValue2 = 0
            )
        }
    }

    fun hideAddConditionDialog() {
        _uiState.update { it.copy(showAddConditionDialog = false) }
    }

    fun setConditionType(type: ConditionType) {
        _uiState.update { it.copy(conditionType = type) }
    }

    fun setConditionBoolIndex(index: Int) {
        _uiState.update { it.copy(conditionBoolIndex = index) }
    }

    fun setConditionBoolValue(value: Boolean) {
        _uiState.update { it.copy(conditionBoolValue = value) }
    }

    fun setConditionIntIndex(index: Int) {
        _uiState.update { it.copy(conditionIntIndex = index) }
    }

    fun setConditionIntValue(value: Int) {
        _uiState.update { it.copy(conditionIntValue = value) }
    }

    fun setConditionIntValue2(value: Int) {
        _uiState.update { it.copy(conditionIntValue2 = value) }
    }

    fun addCondition() {
        val state = _uiState.value
        val newCondition: Condition = when (state.conditionType) {
            ConditionType.BOOL_EQUALS -> Condition.BoolEquals(
                boolIndex = state.conditionBoolIndex,
                expectedValue = state.conditionBoolValue
            )
            ConditionType.INT_EQUALS -> Condition.IntEquals(
                intIndex = state.conditionIntIndex,
                expectedValue = state.conditionIntValue
            )
            ConditionType.INT_NOT_EQUALS -> Condition.IntNotEquals(
                intIndex = state.conditionIntIndex,
                notExpectedValue = state.conditionIntValue
            )
            ConditionType.INT_IN_RANGE -> Condition.IntInRange(
                intIndex = state.conditionIntIndex,
                minValue = state.conditionIntValue,
                maxValue = state.conditionIntValue2
            )
        }

        _uiState.update {
            it.copy(
                conditions = it.conditions + newCondition,
                showAddConditionDialog = false
            )
        }
    }

    fun removeCondition(index: Int) {
        _uiState.update {
            it.copy(conditions = it.conditions.toMutableList().apply { removeAt(index) })
        }
    }

    fun saveRule() {
        val state = _uiState.value
        if (state.conditions.isEmpty()) return

        val rule = LockRule(
            targetButtonIndex = state.selectedTargetButton,
            conditions = state.conditions,
            operator = state.selectedOperator,
            isEnabled = true,
            description = state.ruleDescription.ifBlank {
                "Khóa nút ${ButtonLockRules.BUTTON_NAMES[state.selectedTargetButton] ?: state.selectedTargetButton}"
            }
        )

        if (state.editingRuleIndex != null) {
            buttonLockRules.updateRule(state.editingRuleIndex, rule)
        } else {
            buttonLockRules.addRule(rule)
        }

        hideAddRuleDialog()
    }

    fun deleteRule(index: Int) {
        buttonLockRules.removeRule(index)
    }

    fun toggleRuleEnabled(index: Int) {
        val rule = _uiState.value.rules.getOrNull(index) ?: return
        buttonLockRules.updateRule(index, rule.copy(isEnabled = !rule.isEnabled))
    }

    fun showResetConfirmation() {
        _uiState.update { it.copy(showResetConfirmation = true) }
    }

    fun hideResetConfirmation() {
        _uiState.update { it.copy(showResetConfirmation = false) }
    }

    fun resetToDefaults() {
        buttonLockRules.resetToDefaults()
        hideResetConfirmation()
    }

    fun describeCondition(condition: Condition): String {
        return buttonLockRules.describeCondition(condition)
    }
}
