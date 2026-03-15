package com.example.s7opcuaapp.ui.screen.admin

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.s7opcuaapp.viewmodel.ButtonTimeoutItem
import com.example.s7opcuaapp.viewmodel.ButtonTimeoutViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ButtonTimeoutSettingsScreen(
    viewModel: ButtonTimeoutViewModel = hiltViewModel(),
    onBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState.isSaved) {
        if (uiState.isSaved) {
            snackbarHostState.showSnackbar("Settings saved")
            viewModel.dismissMessage()
        }
    }

    LaunchedEffect(uiState.errorMessage) {
        uiState.errorMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.dismissMessage()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Button Timeout Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.onResetAllToDefault() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Reset all to default")
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
        ) {
            // Device info card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Settings,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "Per-Button Timeout Configuration",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }

                    // Show current device
                    if (uiState.currentDeviceName != null) {
                        Surface(
                            shape = MaterialTheme.shapes.small,
                            color = MaterialTheme.colorScheme.primary
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.Memory,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                    tint = MaterialTheme.colorScheme.onPrimary
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    text = "Device: ${uiState.currentDeviceName}",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onPrimary
                                )
                            }
                        }
                    } else {
                        Text(
                            text = "No device selected - settings will apply globally",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }

                    Text(
                        text = "Tap a button to customize its response timeout and debounce time. Settings are saved per device.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }

            // Button list
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(uiState.buttons) { button ->
                    ButtonTimeoutCard(
                        button = button,
                        onClick = { viewModel.onButtonClick(button) }
                    )
                }
            }
        }

        // Edit dialog
        if (uiState.showEditDialog && uiState.selectedButton != null) {
            EditButtonTimeoutDialog(
                button = uiState.selectedButton!!,
                onSave = { responseTimeout, debounceTime ->
                    viewModel.onSaveButtonTimeout(
                        uiState.selectedButton!!.buttonIndex,
                        responseTimeout,
                        debounceTime
                    )
                },
                onReset = { viewModel.onResetButton(uiState.selectedButton!!.buttonIndex) },
                onDismiss = { viewModel.onDismissDialog() }
            )
        }
    }
}

@Composable
private fun ButtonTimeoutCard(
    button: ButtonTimeoutItem,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        colors = CardDefaults.cardColors(
            containerColor = if (button.isCustom)
                MaterialTheme.colorScheme.secondaryContainer
            else
                MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
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
                    Text(
                        text = button.buttonName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    if (button.isCustom) {
                        Spacer(Modifier.width(8.dp))
                        Surface(
                            shape = MaterialTheme.shapes.small,
                            color = MaterialTheme.colorScheme.primary
                        ) {
                            Text(
                                text = "Custom",
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                        }
                    }
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "Response: ${button.responseTimeout}ms | Debounce: ${button.debounceTime}ms",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = "Edit",
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun EditButtonTimeoutDialog(
    button: ButtonTimeoutItem,
    onSave: (responseTimeout: Long, debounceTime: Long) -> Unit,
    onReset: () -> Unit,
    onDismiss: () -> Unit
) {
    var responseTimeout by remember { mutableStateOf(button.responseTimeout.toString()) }
    var debounceTime by remember { mutableStateOf(button.debounceTime.toString()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = button.buttonName,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Response timeout
                OutlinedTextField(
                    value = responseTimeout,
                    onValueChange = { responseTimeout = it },
                    label = { Text("Response Timeout (ms)") },
                    supportingText = { Text("500 - 15000ms") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                // Response timeout slider
                responseTimeout.toLongOrNull()?.let { value ->
                    Slider(
                        value = value.coerceIn(500, 15000).toFloat(),
                        onValueChange = { responseTimeout = it.toLong().toString() },
                        valueRange = 500f..15000f,
                        steps = 28,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                HorizontalDivider()

                // Debounce time
                OutlinedTextField(
                    value = debounceTime,
                    onValueChange = { debounceTime = it },
                    label = { Text("Debounce Time (ms)") },
                    supportingText = { Text("50 - 3000ms") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                // Debounce slider
                debounceTime.toLongOrNull()?.let { value ->
                    Slider(
                        value = value.coerceIn(50, 3000).toFloat(),
                        onValueChange = { debounceTime = it.toLong().toString() },
                        valueRange = 50f..3000f,
                        steps = 58,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                if (button.isCustom) {
                    TextButton(
                        onClick = onReset,
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = null)
                        Spacer(Modifier.width(4.dp))
                        Text("Reset to Global Default")
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val rt = responseTimeout.toLongOrNull() ?: button.responseTimeout
                    val dt = debounceTime.toLongOrNull() ?: button.debounceTime
                    onSave(rt, dt)
                }
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
