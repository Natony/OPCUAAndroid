package com.example.s7opcuaapp.viewmodel

import androidx.lifecycle.ViewModel
import com.example.s7opcuaapp.data.auth.AuthManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

/**
 * Login history is now managed on the server
 * This ViewModel is simplified since local history tracking has been removed
 */
data class LoginHistoryUiState(
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val infoMessage: String = "Lịch sử đăng nhập hiện được lưu trữ trên máy chủ.\nVui lòng kiểm tra trên giao diện quản trị server."
)

@HiltViewModel
class LoginHistoryViewModel @Inject constructor(
    private val authManager: AuthManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(LoginHistoryUiState())
    val uiState: StateFlow<LoginHistoryUiState> = _uiState.asStateFlow()

    fun isAdmin(): Boolean = authManager.isAdmin()
}
