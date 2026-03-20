package com.example.s7opcuaapp.ui.screen.config

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.s7opcuaapp.data.api.PlcDto
import com.example.s7opcuaapp.viewmodel.ConfigUiState

@Composable
fun ConfigScreen(
    uiState: ConfigUiState,
    onRefreshServerPlcs: () -> Unit = {},
    onSelectServerPlc: (PlcDto) -> Unit = {},
    onReLogin: () -> Unit = {}
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Header
        item {
            Text(
                text = "Chọn PLC",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 8.dp)
            )
        }

        // Server PLCs Section
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "PLCs trên Server",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "${uiState.serverPlcs.size} PLC${if (uiState.serverPlcs.size != 1) "s" else ""} available",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (uiState.selectedPlcId != null) {
                                Surface(
                                    shape = MaterialTheme.shapes.small,
                                    color = MaterialTheme.colorScheme.primaryContainer
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.CheckCircle,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp),
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = uiState.serverPlcs.find { it.id == uiState.selectedPlcId }?.name
                                                ?: "Selected",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                            }

                            IconButton(
                                onClick = onRefreshServerPlcs,
                                enabled = !uiState.isLoadingPlcs
                            ) {
                                if (uiState.isLoadingPlcs) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(24.dp),
                                        strokeWidth = 2.dp
                                    )
                                } else {
                                    Icon(
                                        imageVector = Icons.Default.Refresh,
                                        contentDescription = "Refresh PLCs"
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    HorizontalDivider()
                    Spacer(modifier = Modifier.height(12.dp))

                    if (uiState.isLoadingPlcs) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                CircularProgressIndicator()
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "Đang tải danh sách PLC...",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    } else if (uiState.serverPlcs.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    imageVector = Icons.Default.Info,
                                    contentDescription = null,
                                    modifier = Modifier.size(48.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "Không có PLC nào",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Nhấn Refresh để tải lại danh sách",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            uiState.serverPlcs.forEach { plc ->
                                ServerPlcItem(
                                    plc = plc,
                                    isSelected = plc.id == uiState.selectedPlcId,
                                    onSelect = { onSelectServerPlc(plc) }
                                )
                            }
                        }
                    }

                    uiState.errorMessage?.let { msg ->
                        Spacer(modifier = Modifier.height(12.dp))
                        Surface(
                            shape = MaterialTheme.shapes.small,
                            color = MaterialTheme.colorScheme.errorContainer
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.Error,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = msg,
                                        color = MaterialTheme.colorScheme.onErrorContainer,
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                                // Show re-login button if session expired
                                if (msg.contains("đăng nhập", ignoreCase = true) ||
                                    msg.contains("hết hạn", ignoreCase = true) ||
                                    msg.contains("401") ||
                                    msg.contains("403")) {
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Button(
                                        onClick = onReLogin,
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = MaterialTheme.colorScheme.error
                                        ),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Logout,
                                            contentDescription = null,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("Đăng nhập lại")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Info card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                )
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "Chọn PLC để kết nối",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = "Sau khi chọn PLC, bạn sẽ được chuyển đến màn hình điều khiển",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

// ========== PREVIEW SECTION ==========

@Preview(showBackground = true, widthDp = 400, heightDp = 800)
@Composable
fun ConfigScreenPreview() {
    val samplePlcs = listOf(
        PlcDto(
            id = "plc-1",
            name = "PLC-Production-01",
            endpointUrl = "opc.tcp://192.168.1.100:4840",
            connectionState = "Connected",
            tagCount = 46,
            lastConnected = "2024-01-15T10:30:00Z"
        ),
        PlcDto(
            id = "plc-2",
            name = "PLC-Testing-02",
            endpointUrl = "opc.tcp://192.168.1.101:4840",
            connectionState = "Disconnected",
            tagCount = 32,
            lastConnected = null
        )
    )

    val sampleState = ConfigUiState(
        serverPlcs = samplePlcs,
        selectedPlcId = "plc-1",
        isLoadingPlcs = false
    )

    MaterialTheme {
        ConfigScreen(
            uiState = sampleState,
            onRefreshServerPlcs = {},
            onSelectServerPlc = {}
        )
    }
}

@Preview(showBackground = true, widthDp = 400, heightDp = 800)
@Composable
fun ConfigScreenEmptyPreview() {
    val sampleState = ConfigUiState(
        serverPlcs = emptyList(),
        selectedPlcId = null,
        isLoadingPlcs = false
    )

    MaterialTheme {
        ConfigScreen(
            uiState = sampleState,
            onRefreshServerPlcs = {},
            onSelectServerPlc = {}
        )
    }
}
