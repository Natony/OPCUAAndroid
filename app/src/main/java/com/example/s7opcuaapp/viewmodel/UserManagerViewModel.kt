package com.example.s7opcuaapp.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.s7opcuaapp.data.api.PlcApiClient
import com.example.s7opcuaapp.data.api.UserDto
import com.example.s7opcuaapp.data.api.UserRole
import com.example.s7opcuaapp.data.auth.AuthManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * User management is now handled on the server via API
 * This ViewModel shows a message that user management should be done via server
 */
data class UserManagerUiState(
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val infoMessage: String = "Quản lý người dùng hiện được thực hiện trên máy chủ.\nVui lòng sử dụng giao diện quản trị trên server để quản lý tài khoản."
)

@HiltViewModel
class UserManagerViewModel @Inject constructor(
    private val authManager: AuthManager
) : ViewModel() {

    companion object {
        private const val TAG = "UserManagerViewModel"
    }

    private val _uiState = MutableStateFlow(UserManagerUiState())
    val uiState: StateFlow<UserManagerUiState> = _uiState.asStateFlow()

    fun isAdmin(): Boolean = authManager.isAdmin()

    fun clearMessages() {
        _uiState.update {
            it.copy(errorMessage = null)
        }
    }
}
