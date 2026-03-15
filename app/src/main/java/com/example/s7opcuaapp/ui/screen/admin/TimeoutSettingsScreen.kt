package com.example.s7opcuaapp.ui.screen.admin

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.s7opcuaapp.viewmodel.TimeoutSettingsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimeoutSettingsScreen(
    viewModel: TimeoutSettingsViewModel = hiltViewModel(),
    onBack: () -> Unit,
    onNavigateToButtonTimeouts: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState.isSaved) {
        if (uiState.isSaved) {
            snackbarHostState.showSnackbar("Settings saved successfully")
            viewModel.dismissSavedMessage()
        }
    }

    LaunchedEffect(uiState.errorMessage) {
        uiState.errorMessage?.let {
            snackbarHostState.showSnackbar(it)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Response Timeout Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.resetToDefault() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Reset to default")
                    }
                    IconButton(onClick = { viewModel.saveSettings() }) {
                        Icon(Icons.Default.Save, contentDescription = "Save")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // Info card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Timeout Configuration",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Text(
                        text = "Configure connection and request timeouts for the app. Changes take effect on the next connection.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }

            // Connection Timeout
            TimeoutSettingCard(
                title = "Connection Timeout",
                description = "Maximum time to wait when connecting to PLC server (1-60 seconds)",
                value = uiState.connectionTimeout,
                minValue = 1000,
                maxValue = 60000,
                step = 1000,
                unit = "ms",
                onValueChange = { viewModel.onConnectionTimeoutChange(it) }
            )

            // Request Timeout
            TimeoutSettingCard(
                title = "Request Timeout",
                description = "Maximum time to wait for a response from PLC server (1-30 seconds)",
                value = uiState.requestTimeout,
                minValue = 1000,
                maxValue = 30000,
                step = 1000,
                unit = "ms",
                onValueChange = { viewModel.onRequestTimeoutChange(it) }
            )

            // Polling Interval
            TimeoutSettingCard(
                title = "Polling Interval",
                description = "How often to fetch data from PLC (100-5000ms)",
                value = uiState.pollingInterval,
                minValue = 100,
                maxValue = 5000,
                step = 100,
                unit = "ms",
                onValueChange = { viewModel.onPollingIntervalChange(it) }
            )

            // Section header for button settings
            Text(
                text = "Button Control Settings",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 8.dp)
            )

            // Button Response Timeout
            TimeoutSettingCard(
                title = "Button Response Timeout",
                description = "Maximum time to wait for button action confirmation (1-10 seconds)",
                value = uiState.buttonResponseTimeout,
                minValue = 1000,
                maxValue = 10000,
                step = 500,
                unit = "ms",
                onValueChange = { viewModel.onButtonResponseTimeoutChange(it) }
            )

            // Button Debounce Time
            TimeoutSettingCard(
                title = "Button Debounce Time",
                description = "Minimum time between button presses to prevent double-tap (100-2000ms)",
                value = uiState.buttonDebounceTime,
                minValue = 100,
                maxValue = 2000,
                step = 100,
                unit = "ms",
                onValueChange = { viewModel.onButtonDebounceTimeChange(it) }
            )

            // Per-button settings link
            Card(
                onClick = onNavigateToButtonTimeouts,
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Tune,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "Per-Button Settings",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                            Text(
                                text = "Configure timeout for each button individually (per device)",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                    }
                    Icon(
                        Icons.Default.ChevronRight,
                        contentDescription = "Go",
                        tint = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
            }

            // Save button
            Button(
                onClick = { viewModel.saveSettings() },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Save, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Save Settings")
            }
        }
    }
}

@Composable
private fun TimeoutSettingCard(
    title: String,
    description: String,
    value: Long,
    minValue: Long,
    maxValue: Long,
    step: Long,
    unit: String,
    onValueChange: (Long) -> Unit
) {
    var textValue by remember(value) { mutableStateOf(value.toString()) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )

            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Input field
                OutlinedTextField(
                    value = textValue,
                    onValueChange = { newText ->
                        textValue = newText
                        newText.toLongOrNull()?.let { onValueChange(it) }
                    },
                    label = { Text("Value ($unit)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f),
                    singleLine = true
                )

                // Display in seconds
                Text(
                    text = "= ${value / 1000.0}s",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Medium
                )
            }

            // Slider
            Slider(
                value = value.toFloat(),
                onValueChange = {
                    val newValue = ((it / step) * step).toLong()
                    onValueChange(newValue)
                },
                valueRange = minValue.toFloat()..maxValue.toFloat(),
                steps = ((maxValue - minValue) / step).toInt() - 1,
                modifier = Modifier.fillMaxWidth()
            )

            // Min/Max labels
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "${minValue}$unit",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "${maxValue}$unit",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
