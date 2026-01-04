package com.example.s7opcuaapp.viewmodel

import androidx.lifecycle.ViewModel
import com.example.s7opcuaapp.data.api.UserDto
import com.example.s7opcuaapp.data.api.UserRole
import com.example.s7opcuaapp.data.auth.AuthManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val authManager: AuthManager
) : ViewModel() {

    val currentUser: StateFlow<UserDto?> = authManager.currentUser

    fun isAdmin(): Boolean = authManager.isAdmin()

    fun canModifyDevices(): Boolean = authManager.canControl()

    fun isAuthenticated(): Boolean = authManager.isAuthenticated()
}
