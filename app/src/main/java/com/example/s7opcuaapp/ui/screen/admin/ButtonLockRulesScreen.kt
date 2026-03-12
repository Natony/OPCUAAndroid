package com.example.s7opcuaapp.ui.screen.admin

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.s7opcuaapp.util.ButtonLockRules
import com.example.s7opcuaapp.util.ButtonLockRules.Condition
import com.example.s7opcuaapp.util.ButtonLockRules.LockRule
import com.example.s7opcuaapp.util.ButtonLockRules.LogicOperator
import com.example.s7opcuaapp.viewmodel.ButtonLockRulesViewModel
import com.example.s7opcuaapp.viewmodel.ButtonLockRulesViewModel.ConditionType

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ButtonLockRulesScreen(
    viewModel: ButtonLockRulesViewModel = hiltViewModel(),
    onBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Button Lock Rules") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, "Back")
                    }
                },
                actions = {
                    TextButton(onClick = { viewModel.showResetConfirmation() }) {
                        Text("Reset", color = MaterialTheme.colorScheme.error)
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { viewModel.showAddRuleDialog() },
                containerColor = MaterialTheme.colorScheme.primary
            ) {
                Icon(Icons.Default.Add, "Add Rule")
            }
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Master Override
            item {
                MasterOverrideCard(
                    isActive = uiState.overrideActive,
                    onToggle = { viewModel.toggleOverride() }
                )
            }

            item {
                Text(
                    text = "Lock Rules (${uiState.rules.size})",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 8.dp)
                )
                Text(
                    text = "Mỗi rule định nghĩa điều kiện khóa cho 1 nút",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            itemsIndexed(
                items = uiState.rules,
                key = { index, _ -> index }
            ) { index, rule ->
                LockRuleCard(
                    rule = rule,
                    enabled = !uiState.overrideActive,
                    onToggle = { viewModel.toggleRuleEnabled(index) },
                    onEdit = { viewModel.editRule(index) },
                    onDelete = { viewModel.deleteRule(index) },
                    describeCondition = { viewModel.describeCondition(it) }
                )
            }

            if (uiState.rules.isEmpty()) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = null,
                                modifier = Modifier.size(48.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "Chưa có rule nào",
                                style = MaterialTheme.typography.bodyLarge
                            )
                            Text(
                                text = "Nhấn + để thêm rule mới",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }

    // Add/Edit Rule Dialog
    if (uiState.showAddRuleDialog) {
        AddEditRuleDialog(
            isEdit = uiState.editingRuleIndex != null,
            selectedTargetButton = uiState.selectedTargetButton,
            conditions = uiState.conditions,
            selectedOperator = uiState.selectedOperator,
            description = uiState.ruleDescription,
            onTargetButtonChange = { viewModel.setTargetButton(it) },
            onOperatorChange = { viewModel.setOperator(it) },
            onDescriptionChange = { viewModel.setDescription(it) },
            onAddCondition = { viewModel.showAddConditionDialog() },
            onRemoveCondition = { viewModel.removeCondition(it) },
            onSave = { viewModel.saveRule() },
            onDismiss = { viewModel.hideAddRuleDialog() },
            describeCondition = { viewModel.describeCondition(it) }
        )
    }

    // Add Condition Dialog
    if (uiState.showAddConditionDialog) {
        AddConditionDialog(
            conditionType = uiState.conditionType,
            boolIndex = uiState.conditionBoolIndex,
            boolValue = uiState.conditionBoolValue,
            intIndex = uiState.conditionIntIndex,
            intValue = uiState.conditionIntValue,
            intValue2 = uiState.conditionIntValue2,
            onTypeChange = { viewModel.setConditionType(it) },
            onBoolIndexChange = { viewModel.setConditionBoolIndex(it) },
            onBoolValueChange = { viewModel.setConditionBoolValue(it) },
            onIntIndexChange = { viewModel.setConditionIntIndex(it) },
            onIntValueChange = { viewModel.setConditionIntValue(it) },
            onIntValue2Change = { viewModel.setConditionIntValue2(it) },
            onSave = { viewModel.addCondition() },
            onDismiss = { viewModel.hideAddConditionDialog() }
        )
    }

    // Reset Confirmation
    if (uiState.showResetConfirmation) {
        AlertDialog(
            onDismissRequest = { viewModel.hideResetConfirmation() },
            title = { Text("Reset to Default?") },
            text = { Text("Xóa tất cả rules hiện tại và tạo lại rules mặc định?") },
            confirmButton = {
                TextButton(onClick = { viewModel.resetToDefaults() }) {
                    Text("Reset", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.hideResetConfirmation() }) {
                    Text("Hủy")
                }
            }
        )
    }
}

@Composable
private fun MasterOverrideCard(
    isActive: Boolean,
    onToggle: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isActive)
                MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
            else MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = null,
                        tint = if (isActive) MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Master Override",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
                Text(
                    text = if (isActive) "Tất cả khóa bị VÔ HIỆU HÓA"
                    else "Khóa nút hoạt động theo rules",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(checked = isActive, onCheckedChange = { onToggle() })
        }
    }
}

@Composable
private fun LockRuleCard(
    rule: LockRule,
    enabled: Boolean,
    onToggle: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    describeCondition: (Condition) -> String
) {
    val buttonName = ButtonLockRules.BUTTON_NAMES[rule.targetButtonIndex]
        ?: "Button ${rule.targetButtonIndex}"

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = when {
                !enabled -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                rule.isEnabled -> MaterialTheme.colorScheme.surface
                else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
            }
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Khóa: $buttonName",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium
                    )
                    if (rule.description.isNotBlank()) {
                        Text(
                            text = rule.description,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Row {
                    IconButton(onClick = onEdit) {
                        Icon(Icons.Default.Edit, "Edit", modifier = Modifier.size(20.dp))
                    }
                    IconButton(onClick = onDelete) {
                        Icon(
                            Icons.Default.Delete, "Delete",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Switch(
                        checked = rule.isEnabled,
                        onCheckedChange = { onToggle() },
                        enabled = enabled
                    )
                }
            }

            if (rule.conditions.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    )
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = "Điều kiện (${rule.operator.name}):",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                        rule.conditions.forEach { condition ->
                            Text(
                                text = "• ${describeCondition(condition)}",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddEditRuleDialog(
    isEdit: Boolean,
    selectedTargetButton: Int,
    conditions: List<Condition>,
    selectedOperator: LogicOperator,
    description: String,
    onTargetButtonChange: (Int) -> Unit,
    onOperatorChange: (LogicOperator) -> Unit,
    onDescriptionChange: (String) -> Unit,
    onAddCondition: () -> Unit,
    onRemoveCondition: (Int) -> Unit,
    onSave: () -> Unit,
    onDismiss: () -> Unit,
    describeCondition: (Condition) -> String
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isEdit) "Sửa Rule" else "Thêm Rule") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Target button selector
                var expanded by remember { mutableStateOf(false) }
                ExposedDropdownMenuBox(
                    expanded = expanded,
                    onExpandedChange = { expanded = it }
                ) {
                    OutlinedTextField(
                        value = ButtonLockRules.BUTTON_NAMES[selectedTargetButton]
                            ?: "Button $selectedTargetButton",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Nút bị khóa") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                        modifier = Modifier
                            .menuAnchor()
                            .fillMaxWidth()
                    )
                    ExposedDropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false }
                    ) {
                        ButtonLockRules.BUTTON_NAMES.forEach { (index, name) ->
                            DropdownMenuItem(
                                text = { Text(name) },
                                onClick = {
                                    onTargetButtonChange(index)
                                    expanded = false
                                }
                            )
                        }
                    }
                }

                // Logic operator
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = selectedOperator == LogicOperator.OR,
                        onClick = { onOperatorChange(LogicOperator.OR) },
                        label = { Text("OR (bất kỳ)") }
                    )
                    FilterChip(
                        selected = selectedOperator == LogicOperator.AND,
                        onClick = { onOperatorChange(LogicOperator.AND) },
                        label = { Text("AND (tất cả)") }
                    )
                }

                // Description
                OutlinedTextField(
                    value = description,
                    onValueChange = onDescriptionChange,
                    label = { Text("Mô tả (tùy chọn)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                // Conditions
                Text("Điều kiện:", style = MaterialTheme.typography.labelMedium)

                conditions.forEachIndexed { index, condition ->
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = describeCondition(condition),
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(
                                onClick = { onRemoveCondition(index) },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    Icons.Default.Close, "Remove",
                                    modifier = Modifier.size(16.dp),
                                    tint = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }
                }

                OutlinedButton(
                    onClick = onAddCondition,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Add, null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Thêm điều kiện")
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = onSave,
                enabled = conditions.isNotEmpty()
            ) {
                Text("Lưu")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Hủy")
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddConditionDialog(
    conditionType: ConditionType,
    boolIndex: Int,
    boolValue: Boolean,
    intIndex: Int,
    intValue: Int,
    intValue2: Int,
    onTypeChange: (ConditionType) -> Unit,
    onBoolIndexChange: (Int) -> Unit,
    onBoolValueChange: (Boolean) -> Unit,
    onIntIndexChange: (Int) -> Unit,
    onIntValueChange: (Int) -> Unit,
    onIntValue2Change: (Int) -> Unit,
    onSave: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Thêm điều kiện") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Condition type selector
                var typeExpanded by remember { mutableStateOf(false) }
                ExposedDropdownMenuBox(
                    expanded = typeExpanded,
                    onExpandedChange = { typeExpanded = it }
                ) {
                    OutlinedTextField(
                        value = when (conditionType) {
                            ConditionType.BOOL_EQUALS -> "Bool = giá trị"
                            ConditionType.INT_EQUALS -> "Int = giá trị"
                            ConditionType.INT_NOT_EQUALS -> "Int != giá trị"
                            ConditionType.INT_IN_RANGE -> "Int trong khoảng"
                        },
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Loại điều kiện") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(typeExpanded) },
                        modifier = Modifier
                            .menuAnchor()
                            .fillMaxWidth()
                    )
                    ExposedDropdownMenu(
                        expanded = typeExpanded,
                        onDismissRequest = { typeExpanded = false }
                    ) {
                        ConditionType.values().forEach { type ->
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        when (type) {
                                            ConditionType.BOOL_EQUALS -> "Bool = giá trị"
                                            ConditionType.INT_EQUALS -> "Int = giá trị"
                                            ConditionType.INT_NOT_EQUALS -> "Int != giá trị"
                                            ConditionType.INT_IN_RANGE -> "Int trong khoảng"
                                        }
                                    )
                                },
                                onClick = {
                                    onTypeChange(type)
                                    typeExpanded = false
                                }
                            )
                        }
                    }
                }

                when (conditionType) {
                    ConditionType.BOOL_EQUALS -> {
                        // Bool selector
                        var boolExpanded by remember { mutableStateOf(false) }
                        ExposedDropdownMenuBox(
                            expanded = boolExpanded,
                            onExpandedChange = { boolExpanded = it }
                        ) {
                            OutlinedTextField(
                                value = ButtonLockRules.BUTTON_NAMES[boolIndex]
                                    ?: "Bool[$boolIndex]",
                                onValueChange = {},
                                readOnly = true,
                                label = { Text("Chọn nút Bool") },
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(boolExpanded) },
                                modifier = Modifier
                                    .menuAnchor()
                                    .fillMaxWidth()
                            )
                            ExposedDropdownMenu(
                                expanded = boolExpanded,
                                onDismissRequest = { boolExpanded = false }
                            ) {
                                (0..14).forEach { index ->
                                    val name = ButtonLockRules.BUTTON_NAMES[index]
                                        ?: "Bool[$index]"
                                    DropdownMenuItem(
                                        text = { Text(name) },
                                        onClick = {
                                            onBoolIndexChange(index)
                                            boolExpanded = false
                                        }
                                    )
                                }
                            }
                        }

                        // Bool value
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            FilterChip(
                                selected = boolValue,
                                onClick = { onBoolValueChange(true) },
                                label = { Text("ON (true)") },
                                modifier = Modifier.weight(1f)
                            )
                            FilterChip(
                                selected = !boolValue,
                                onClick = { onBoolValueChange(false) },
                                label = { Text("OFF (false)") },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    ConditionType.INT_EQUALS, ConditionType.INT_NOT_EQUALS -> {
                        // Int selector
                        var intExpanded by remember { mutableStateOf(false) }
                        ExposedDropdownMenuBox(
                            expanded = intExpanded,
                            onExpandedChange = { intExpanded = it }
                        ) {
                            OutlinedTextField(
                                value = ButtonLockRules.INT_NAMES[intIndex]
                                    ?: "Int[$intIndex]",
                                onValueChange = {},
                                readOnly = true,
                                label = { Text("Chọn Int") },
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(intExpanded) },
                                modifier = Modifier
                                    .menuAnchor()
                                    .fillMaxWidth()
                            )
                            ExposedDropdownMenu(
                                expanded = intExpanded,
                                onDismissRequest = { intExpanded = false }
                            ) {
                                ButtonLockRules.INT_NAMES.forEach { (index, name) ->
                                    DropdownMenuItem(
                                        text = { Text(name) },
                                        onClick = {
                                            onIntIndexChange(index)
                                            intExpanded = false
                                        }
                                    )
                                }
                            }
                        }

                        OutlinedTextField(
                            value = intValue.toString(),
                            onValueChange = { onIntValueChange(it.toIntOrNull() ?: 0) },
                            label = { Text("Giá trị") },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    ConditionType.INT_IN_RANGE -> {
                        // Int selector
                        var intExpanded by remember { mutableStateOf(false) }
                        ExposedDropdownMenuBox(
                            expanded = intExpanded,
                            onExpandedChange = { intExpanded = it }
                        ) {
                            OutlinedTextField(
                                value = ButtonLockRules.INT_NAMES[intIndex]
                                    ?: "Int[$intIndex]",
                                onValueChange = {},
                                readOnly = true,
                                label = { Text("Chọn Int") },
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(intExpanded) },
                                modifier = Modifier
                                    .menuAnchor()
                                    .fillMaxWidth()
                            )
                            ExposedDropdownMenu(
                                expanded = intExpanded,
                                onDismissRequest = { intExpanded = false }
                            ) {
                                ButtonLockRules.INT_NAMES.forEach { (index, name) ->
                                    DropdownMenuItem(
                                        text = { Text(name) },
                                        onClick = {
                                            onIntIndexChange(index)
                                            intExpanded = false
                                        }
                                    )
                                }
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedTextField(
                                value = intValue.toString(),
                                onValueChange = { onIntValueChange(it.toIntOrNull() ?: 0) },
                                label = { Text("Từ") },
                                modifier = Modifier.weight(1f)
                            )
                            OutlinedTextField(
                                value = intValue2.toString(),
                                onValueChange = { onIntValue2Change(it.toIntOrNull() ?: 0) },
                                label = { Text("Đến") },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onSave) {
                Text("Thêm")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Hủy")
            }
        }
    )
}
