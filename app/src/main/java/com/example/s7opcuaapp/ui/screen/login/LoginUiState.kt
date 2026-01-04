package com.example.s7opcuaapp.ui.screen.login

import androidx.compose.runtime.Immutable

@Immutable
data class LoginUiState(
    val username: String = "",
    val password: String = "",
    val isPasswordVisible: Boolean = false,
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    // API Server config
    val serverIp: String = "192.168.137.1",
    val serverPort: String = "5000",
    val showServerConfigDialog: Boolean = false
)
