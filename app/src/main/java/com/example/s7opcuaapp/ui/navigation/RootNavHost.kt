package com.example.s7opcuaapp.ui.navigation

import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.s7opcuaapp.data.api.InvalidReason
import com.example.s7opcuaapp.data.auth.AuthManager
import com.example.s7opcuaapp.ui.screen.alarm.AlarmScreen
import com.example.s7opcuaapp.ui.screen.config.ConfigScreen
import com.example.s7opcuaapp.ui.screen.login.LoginScreen
import com.example.s7opcuaapp.viewmodel.ConfigViewModel
import com.example.s7opcuaapp.viewmodel.LoginViewModel

@Composable
fun RootNavHost(navController: NavHostController) {
    // Observe auth state for session invalidation
    val loginViewModel: LoginViewModel = hiltViewModel()
    val authState by loginViewModel.authState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // Handle session invalidation
    LaunchedEffect(authState) {
        if (authState is AuthManager.AuthState.SessionInvalidated) {
            val state = authState as AuthManager.AuthState.SessionInvalidated
            val message = when (state.reason) {
                InvalidReason.LOGGED_IN_FROM_ANOTHER_DEVICE ->
                    "Tài khoản đã đăng nhập từ thiết bị: ${state.newDeviceName ?: "khác"}"
                InvalidReason.FORCED_LOGOUT_BY_ADMIN ->
                    "Bạn đã bị Admin đăng xuất"
                else ->
                    "Phiên đăng nhập đã hết hạn"
            }
            Toast.makeText(context, message, Toast.LENGTH_LONG).show()

            // Navigate to login
            navController.navigate("login") {
                popUpTo(0) { inclusive = true }
            }
        }
    }

    NavHost(
        navController = navController,
        startDestination = "login"
    ) {
        // 1. Login Screen
        composable("login") {
            val loginViewModel: LoginViewModel = hiltViewModel()
            val uiState by loginViewModel.uiState.collectAsStateWithLifecycle()

            LoginScreen(
                uiState = uiState,
                onUsernameChanged = { username -> loginViewModel.onUsernameChanged(username) },
                onPasswordChanged = { password -> loginViewModel.onPasswordChanged(password) },
                onTogglePasswordVisibility = { loginViewModel.onTogglePasswordVisibility() },
                onLoginClicked = {
                    loginViewModel.onLoginClicked {
                        // After successful login, go to config_select
                        navController.navigate("config_select") {
                            popUpTo("login") { inclusive = true }
                        }
                    }
                },
                // Demo mode callback - go directly to main (skip config)
                onDemoModeClicked = {
                    loginViewModel.enterDemoMode {
                        navController.navigate("main") {
                            popUpTo("login") { inclusive = true }
                        }
                    }
                },
                // Direct OPC UA mode — go to config_select so user can fill endpoint/credentials.
                onDirectModeClicked = {
                    loginViewModel.enterDirectMode {
                        navController.navigate("config_select") {
                            popUpTo("login") { inclusive = true }
                        }
                    }
                },
                // Server config callbacks
                onShowServerConfig = { loginViewModel.onShowServerConfig() },
                onServerIpChanged = { ip -> loginViewModel.onServerIpChanged(ip) },
                onServerPortChanged = { port -> loginViewModel.onServerPortChanged(port) },
                onSaveServerConfig = { loginViewModel.onSaveServerConfig() },
                onDismissServerConfig = { loginViewModel.onDismissServerConfig() }
            )
        }

        // 2. ConfigSelect Screen (chọn PLC từ server hoặc cấu hình Direct OPC UA)
        composable("config_select") {
            val configViewModel: ConfigViewModel = hiltViewModel()
            val uiState by configViewModel.uiState.collectAsStateWithLifecycle()

            ConfigScreen(
                uiState = uiState,
                onRefreshServerPlcs = { configViewModel.loadServerPlcs() },
                onSelectServerPlc = { plc ->
                    configViewModel.onSelectServerPlc(plc) {
                        // After selecting PLC, go to main
                        navController.navigate("main") {
                            popUpTo("config_select") { inclusive = true }
                        }
                    }
                },
                onConnectionModeChanged = { configViewModel.onConnectionModeChanged(it) },
                onDirectEndpointChanged = { configViewModel.onDirectEndpointChanged(it) },
                onDirectUsernameChanged = { configViewModel.onDirectUsernameChanged(it) },
                onDirectPasswordChanged = { configViewModel.onDirectPasswordChanged(it) },
                onDirectSecurityPolicyChanged = { configViewModel.onDirectSecurityPolicyChanged(it) },
                onSaveDirectConfig = {
                    configViewModel.onSaveDirectConfig {
                        // After saving Direct config, jump straight into Control screen.
                        navController.navigate("main") {
                            popUpTo("config_select") { inclusive = true }
                        }
                    }
                },
                onTestDirectConnection = { configViewModel.onTestDirectConnection() }
            )
        }

        // 3. MainNavGraph (có TopNavigationBar)
        composable("main") {
            MainNavGraph(rootNavController = navController)
        }

        composable("alarm") {
            AlarmScreen(
                navController = navController
            )
        }
    }
}