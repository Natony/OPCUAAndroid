package com.example.s7opcuaapp.ui.screen.login

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.s7opcuaapp.R
import com.example.s7opcuaapp.ui.components.CommonTextField

@Composable
fun LoginScreen(
    uiState: LoginUiState,
    onUsernameChanged: (String) -> Unit,
    onPasswordChanged: (String) -> Unit,
    onTogglePasswordVisibility: () -> Unit,
    onLoginClicked: () -> Unit,
    onDemoModeClicked: () -> Unit = {},
    onShowServerConfig: () -> Unit = {},
    onServerIpChanged: (String) -> Unit = {},
    onServerPortChanged: (String) -> Unit = {},
    onSaveServerConfig: () -> Unit = {},
    onDismissServerConfig: () -> Unit = {}
) {
    Box(
        modifier = Modifier.fillMaxSize()
    ) {
        // Settings icon at top right
        IconButton(
            onClick = onShowServerConfig,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(8.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Settings,
                contentDescription = "API Server Settings",
                tint = MaterialTheme.colorScheme.primary
            )
        }

        // Main login card - scrollable for landscape/small screens
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(top = 48.dp), // Space for settings icon
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Card(
                modifier = Modifier
                    .widthIn(max = 400.dp)
                    .padding(16.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Logo or App Icon
                    Image(
                        painter = painterResource(id = R.drawable.ic_logo1),
                        contentDescription = "App Logo",
                        modifier = Modifier.size(80.dp)
                    )

                    // Title
                    Text(
                        text = "AVS Shuttle",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )

                    // Server info
                    Text(
                        text = "Server: ${uiState.serverIp}:${uiState.serverPort}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // Username field
                    CommonTextField(
                        label = "Username",
                        value = uiState.username,
                        onValueChange = onUsernameChanged
                    )

                    // Password field
                    CommonTextField(
                        label = "Password",
                        value = uiState.password,
                        onValueChange = onPasswordChanged,
                        isPassword = true,
                        isPasswordVisible = uiState.isPasswordVisible,
                        onTogglePasswordVisibility = onTogglePasswordVisibility
                    )

                    // Error message
                    uiState.errorMessage?.let { msg ->
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = msg,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.padding(12.dp),
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Login button
                    Button(
                        onClick = onLoginClicked,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                        enabled = !uiState.isLoading
                    ) {
                        if (uiState.isLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                color = MaterialTheme.colorScheme.onPrimary,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Text("LOGIN")
                        }
                    }

                    // Demo mode button
                    OutlinedButton(
                        onClick = onDemoModeClicked,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                        enabled = !uiState.isLoading
                    ) {
                        Text("DEMO MODE (Offline)")
                    }
                }
            }
        }

        // Server config dialog
        if (uiState.showServerConfigDialog) {
            ServerConfigDialog(
                serverIp = uiState.serverIp,
                serverPort = uiState.serverPort,
                onServerIpChanged = onServerIpChanged,
                onServerPortChanged = onServerPortChanged,
                onSave = onSaveServerConfig,
                onDismiss = onDismissServerConfig
            )
        }
    }
}

@Composable
private fun ServerConfigDialog(
    serverIp: String,
    serverPort: String,
    onServerIpChanged: (String) -> Unit,
    onServerPortChanged: (String) -> Unit,
    onSave: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "API Server Configuration",
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "Cấu hình địa chỉ API Server (WPF OPC UA Engine)",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                OutlinedTextField(
                    value = serverIp,
                    onValueChange = onServerIpChanged,
                    label = { Text("Server IP") },
                    placeholder = { Text("192.168.137.1") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = serverPort,
                    onValueChange = onServerPortChanged,
                    label = { Text("API Port") },
                    placeholder = { Text("5000") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )

                Text(
                    text = "URL: http://$serverIp:$serverPort",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        },
        confirmButton = {
            Button(onClick = onSave) {
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

// ========== PREVIEW SECTION ==========
@Preview(showBackground = true, widthDp = 1920, heightDp = 1200)
@Composable
fun LoginScreenLoadingPreview() {
    val sampleState = LoginUiState(
        username = "admin",
        password = "password",
        isPasswordVisible = false,
        isLoading = true,
        errorMessage = null
    )

    LoginScreen(
        uiState = sampleState,
        onUsernameChanged = {},
        onPasswordChanged = {},
        onTogglePasswordVisibility = {},
        onLoginClicked = {}
    )
}

@Preview(showBackground = true, widthDp = 1920, heightDp = 1200)
@Composable
fun LoginScreenErrorPreview() {
    val sampleState = LoginUiState(
        username = "admin",
        password = "",
        isPasswordVisible = false,
        isLoading = false,
        errorMessage = "Invalid credentials. Please try again."
    )

    LoginScreen(
        uiState = sampleState,
        onUsernameChanged = {},
        onPasswordChanged = {},
        onTogglePasswordVisibility = {},
        onLoginClicked = {}
    )
}

@Preview(showBackground = true)
@Composable
fun ServerConfigDialogPreview() {
    ServerConfigDialog(
        serverIp = "192.168.137.1",
        serverPort = "5000",
        onServerIpChanged = {},
        onServerPortChanged = {},
        onSave = {},
        onDismiss = {}
    )
}
