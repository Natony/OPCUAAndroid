package com.example.s7opcuaapp.ui.screen.config

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Circle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.s7opcuaapp.data.api.PlcDto

/**
 * Connection state colors and icons
 */
private data class ConnectionStateInfo(
    val icon: ImageVector,
    val color: Color,
    val label: String
)

private fun getConnectionStateInfo(state: String): ConnectionStateInfo {
    return when (state.lowercase()) {
        "connected" -> ConnectionStateInfo(
            icon = Icons.Default.CheckCircle,
            color = Color(0xFF4CAF50), // Green
            label = "Connected"
        )
        "connecting", "reconnecting" -> ConnectionStateInfo(
            icon = Icons.Default.Refresh,
            color = Color(0xFFFFC107), // Yellow/Amber
            label = if (state.equals("reconnecting", true)) "Reconnecting..." else "Connecting..."
        )
        "error", "failed" -> ConnectionStateInfo(
            icon = Icons.Default.Error,
            color = Color(0xFFF44336), // Red
            label = "Error"
        )
        "disconnected" -> ConnectionStateInfo(
            icon = Icons.Default.Circle,
            color = Color(0xFF9E9E9E), // Gray
            label = "Disconnected"
        )
        else -> ConnectionStateInfo(
            icon = Icons.Default.Circle,
            color = Color(0xFFBDBDBD), // Light gray
            label = state
        )
    }
}

/**
 * Displays a PLC from the server list
 */
@Composable
fun ServerPlcItem(
    plc: PlcDto,
    isSelected: Boolean,
    onSelect: () -> Unit
) {
    val borderColor = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent
    val containerColor = if (isSelected) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceVariant
    }

    val stateInfo = getConnectionStateInfo(plc.connectionState)

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = if (isSelected) 2.dp else 0.dp,
                color = borderColor,
                shape = MaterialTheme.shapes.small
            )
            .clickable { onSelect() },
        shape = MaterialTheme.shapes.small,
        color = containerColor
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                // Status indicator based on connectionState
                Icon(
                    imageVector = stateInfo.icon,
                    contentDescription = stateInfo.label,
                    modifier = Modifier.size(16.dp),
                    tint = stateInfo.color
                )

                Spacer(modifier = Modifier.width(8.dp))

                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = plc.name,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Medium,
                            color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer
                            else MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(Modifier.width(8.dp))
                        // Connection state badge
                        Surface(
                            shape = MaterialTheme.shapes.extraSmall,
                            color = stateInfo.color.copy(alpha = 0.15f)
                        ) {
                            Text(
                                text = stateInfo.label,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                style = MaterialTheme.typography.labelSmall,
                                color = stateInfo.color,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }

                    Text(
                        text = "ID: ${plc.id} • Tags: ${plc.tagCount}",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Text(
                        text = plc.endpointUrl,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Selection indicator
            if (isSelected) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = "Selected",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
            } else {
                FilledTonalButton(
                    onClick = onSelect,
                    modifier = Modifier.height(32.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp)
                ) {
                    Text("Chọn", style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}
