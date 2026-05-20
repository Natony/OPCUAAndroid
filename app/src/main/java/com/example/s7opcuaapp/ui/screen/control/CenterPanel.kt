package com.example.s7opcuaapp.ui.screen.control

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.s7opcuaapp.data.PlcConfig
import com.example.s7opcuaapp.ui.components.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CenterPanel(
    uiState: ControlUiState,
    isAuto: Boolean,
    onToggleBoolean: (Int, Boolean) -> Unit,
    onFunctionSelect: (Int) -> Unit,
    onTextChange: (Int, String) -> Unit,
    onSendAll: () -> Unit,
    modifier: Modifier = Modifier
) {
    val data = uiState.plcData
    val isWriting = uiState.isWriting

    val SEND_ALL_BUTTON_INDEX = 999
    val isSendAllLocked = SEND_ALL_BUTTON_INDEX in uiState.lockedButtons

    Column(
        modifier = modifier
            .fillMaxHeight()
            .padding(horizontal = 4.dp, vertical = 2.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Positions row
        Row(
            modifier = Modifier
                .weight(0.25f)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(1.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val positionCount = rememberPositionCount()
            (1..positionCount).forEach { posNum ->
                val intIndex = PlcConfig.POSITION_INT_OFFSET + posNum - 1
                MultiStateStatusItem(
                    "Pos$posNum",
                    data.ints.getOrNull(intIndex) ?: 0,
                    listOf(
                        rememberPositionIcon(posNum, 0),
                        rememberPositionIcon(posNum, 1),
                        rememberPositionIcon(posNum, 2)
                    ),
                    modifier = Modifier.size(36.dp)
                )
            }
        }

        // Input controls row
        Row(
            modifier = Modifier
                .weight(0.5f)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Start point card
            CoordinateInputCard(
                title = "Bắt đầu",
                titleColor = Color(0xFF2196F3),
                xValue = uiState.intInputs[5] ?: (data.ints.getOrNull(5)?.toString() ?: "0"),
                yValue = uiState.intInputs[6] ?: (data.ints.getOrNull(6)?.toString() ?: "0"),
                zValue = uiState.intInputs[7] ?: (data.ints.getOrNull(7)?.toString() ?: "0"),
                onXChange = { onTextChange(5, it) },
                onYChange = { onTextChange(6, it) },
                onZChange = { onTextChange(7, it) },
                isWriting = isWriting,
                isEditable = true,
                modifier = Modifier.weight(1f)
            )

            // End point card
            CoordinateInputCard(
                title = "Kết thúc",
                titleColor = Color(0xFF4CAF50),
                xValue = uiState.intInputs[8] ?: (data.ints.getOrNull(8)?.toString() ?: "0"),
                yValue = uiState.intInputs[9] ?: (data.ints.getOrNull(9)?.toString() ?: "0"),
                zValue = uiState.intInputs[10] ?: (data.ints.getOrNull(10)?.toString() ?: "0"),
                onXChange = { onTextChange(8, it) },
                onYChange = { onTextChange(9, it) },
                onZChange = { onTextChange(10, it) },
                isWriting = isWriting,
                isEditable = true,
                modifier = Modifier.weight(1f)
            )

            // Actual position card (read-only)
            CoordinateDisplayCard(
                title = "Thực tế",
                titleColor = Color(0xFFFF9800),
                xValue = data.ints.getOrNull(11) ?: 0,
                yValue = data.ints.getOrNull(12) ?: 0,
                zValue = data.ints.getOrNull(13) ?: 0,
                modifier = Modifier.weight(1f)
            )

            // Function + Send column
            Column(
                modifier = Modifier
                    .weight(1.2f)
                    .fillMaxHeight(),
                verticalArrangement = Arrangement.spacedBy(4.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Function selector
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    ),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(4.dp),
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = "Chức năng",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(start = 4.dp, bottom = 2.dp)
                        )
                        CompactFunctionSelector(
                            entries = listOf(
                                "Func 1" to 1,
                                "Func 2" to 2,
                                "Func 3" to 3
                            ),
                            selectedCode = uiState.selectedFunction,
                            onSelect = onFunctionSelect,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                // Send button
                CompactSendButton(
                    isAutoMode = isAuto,
                    isWriting = isWriting,
                    isLocked = isSendAllLocked,
                    onClick = onSendAll,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(0.8f)
                )
            }
        }
    }
}

@Composable
private fun CoordinateInputCard(
    title: String,
    titleColor: Color,
    xValue: String,
    yValue: String,
    zValue: String,
    onXChange: (String) -> Unit,
    onYChange: (String) -> Unit,
    onZChange: (String) -> Unit,
    isWriting: Boolean,
    isEditable: Boolean,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxHeight(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        border = BorderStroke(1.dp, titleColor.copy(alpha = 0.5f)),
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(6.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            // Title
            Text(
                text = title,
                style = MaterialTheme.typography.labelMedium,
                color = titleColor,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center
            )

            // X, Y, Z inputs in row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                CoordinateField("X", xValue, onXChange, isWriting, titleColor, Modifier.weight(1f))
                CoordinateField("Y", yValue, onYChange, isWriting, titleColor, Modifier.weight(1f))
                CoordinateField("Z", zValue, onZChange, isWriting, titleColor, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun CoordinateField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    isWriting: Boolean,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxHeight(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Label
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = accentColor,
            fontWeight = FontWeight.Bold,
            fontSize = 10.sp
        )

        // Input field
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(4.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
            border = BorderStroke(1.dp, accentColor.copy(alpha = 0.3f))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp, horizontal = 2.dp),
                contentAlignment = Alignment.Center
            ) {
                androidx.compose.foundation.text.BasicTextField(
                    value = value,
                    onValueChange = { new ->
                        if (new.isEmpty() || new.matches(Regex("^-?\\d*\\.?\\d*$"))) {
                            onValueChange(new)
                        }
                    },
                    textStyle = androidx.compose.ui.text.TextStyle(
                        fontSize = 14.sp,
                        color = if (isWriting) Color.Gray else MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center,
                        fontWeight = FontWeight.Medium
                    ),
                    singleLine = true,
                    enabled = !isWriting,
                    modifier = Modifier.fillMaxWidth()
                )

                if (isWriting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(12.dp),
                        strokeWidth = 1.dp,
                        color = accentColor
                    )
                }
            }
        }
    }
}

@Composable
private fun CoordinateDisplayCard(
    title: String,
    titleColor: Color,
    xValue: Int,
    yValue: Int,
    zValue: Int,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxHeight(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        border = BorderStroke(1.dp, titleColor.copy(alpha = 0.5f)),
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(6.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            // Title
            Text(
                text = title,
                style = MaterialTheme.typography.labelMedium,
                color = titleColor,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center
            )

            // X, Y, Z displays in row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                CoordinateDisplay("X", xValue, titleColor, Modifier.weight(1f))
                CoordinateDisplay("Y", yValue, titleColor, Modifier.weight(1f))
                CoordinateDisplay("Z", zValue, titleColor, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun CoordinateDisplay(
    label: String,
    value: Int,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxHeight(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Label
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = accentColor,
            fontWeight = FontWeight.Bold,
            fontSize = 10.sp
        )

        // Value display
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(4.dp),
            color = accentColor.copy(alpha = 0.1f)
        ) {
            Text(
                text = value.toString(),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CompactFunctionSelector(
    entries: List<Pair<String, Int>>,
    selectedCode: Int?,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedLabel = entries.find { it.second == selectedCode }?.first ?: "Chọn"

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded },
        modifier = modifier
    ) {
        Surface(
            modifier = Modifier
                .menuAnchor()
                .fillMaxWidth(),
            shape = RoundedCornerShape(6.dp),
            color = MaterialTheme.colorScheme.primaryContainer
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = selectedLabel,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                ExposedDropdownMenuDefaults.TrailingIcon(expanded)
            }
        }

        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            entries.forEach { (label, code) ->
                DropdownMenuItem(
                    text = {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.bodySmall
                        )
                    },
                    onClick = {
                        onSelect(code)
                        expanded = false
                    },
                    leadingIcon = if (code == selectedCode) {
                        {
                            Icon(
                                Icons.Default.PlayArrow,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    } else null
                )
            }
        }
    }
}

@Composable
private fun CompactSendButton(
    isAutoMode: Boolean,
    isWriting: Boolean,
    isLocked: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isEnabled = !isAutoMode && !isWriting && !isLocked

    val buttonColor = when {
        isLocked -> MaterialTheme.colorScheme.errorContainer
        isAutoMode -> MaterialTheme.colorScheme.surfaceVariant
        isWriting -> MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
        else -> MaterialTheme.colorScheme.primary
    }

    val contentColor = when {
        isLocked -> MaterialTheme.colorScheme.onErrorContainer
        isAutoMode -> MaterialTheme.colorScheme.onSurfaceVariant
        else -> MaterialTheme.colorScheme.onPrimary
    }

    Button(
        onClick = onClick,
        enabled = isEnabled,
        modifier = modifier.alpha(if (isEnabled) 1f else 0.6f),
        colors = ButtonDefaults.buttonColors(
            containerColor = buttonColor,
            disabledContainerColor = buttonColor.copy(alpha = 0.5f)
        ),
        shape = RoundedCornerShape(8.dp),
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Row(
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (isWriting) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    color = contentColor,
                    strokeWidth = 2.dp
                )
                Spacer(Modifier.width(4.dp))
            } else {
                Icon(
                    imageVector = if (isLocked) Icons.Default.Lock else Icons.Default.PlayArrow,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = contentColor
                )
                Spacer(Modifier.width(4.dp))
            }

            Text(
                text = when {
                    isLocked -> "KHÓA"
                    isWriting -> "..."
                    isAutoMode -> "AUTO"
                    else -> "CHẠY"
                },
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = contentColor
            )
        }
    }
}