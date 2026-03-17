package com.example.s7opcuaapp

import android.os.Bundle
import android.util.Log
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.lifecycleScope
import androidx.navigation.compose.rememberNavController
import com.example.s7opcuaapp.data.local.PrefsManager
import com.example.s7opcuaapp.data.auth.AuthManager
import com.example.s7opcuaapp.data.auth.HeartbeatManager
import com.example.s7opcuaapp.ui.navigation.RootNavHost
import com.example.s7opcuaapp.ui.theme.S7Theme
import com.example.s7opcuaapp.util.PerformanceMonitor
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject
import com.example.s7opcuaapp.data.buffer.PlcDataBuffer

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    companion object {
        private const val TAG = "MainActivity"
    }

    @Inject
    lateinit var prefsManager: PrefsManager

    @Inject
    lateinit var authManager: AuthManager

    @Inject
    lateinit var heartbeatManager: HeartbeatManager

    @Inject
    lateinit var performanceMonitor: PerformanceMonitor

    @Inject
    lateinit var plcDataBuffer: PlcDataBuffer

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Enable hardware acceleration
        window.setFlags(
            WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED
        )

        setContent {
            S7Theme {
                val navController = rememberNavController()

                // Check for existing auth session
                LaunchedEffect(Unit) {
                    if (authManager.isAuthenticated()) {
                        Log.d(TAG, "User already authenticated: ${authManager.currentUser.value?.username}")
                        // Session valid, check if device selected
                        if (prefsManager.getCurrentDevice() != null) {
                            navController.navigate("main") {
                                popUpTo("login") { inclusive = true }
                            }
                        } else {
                            navController.navigate("config_select") {
                                popUpTo("login") { inclusive = true }
                            }
                        }
                    } else {
                        Log.d(TAG, "No authenticated session, staying on login screen")
                    }
                }

                RootNavHost(navController = navController)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Validate session when app resumes from background
        if (authManager.isAuthenticated() && !authManager.isDemoMode()) {
            Log.d(TAG, "🔍 App resumed, validating session...")
            heartbeatManager.validateSessionOnResume()
        }
    }

    override fun onPause() {
        super.onPause()
        // Stop heartbeat when app goes to background
        if (authManager.isAuthenticated() && !authManager.isDemoMode()) {
            Log.d(TAG, "💤 App paused, stopping heartbeat...")
            heartbeatManager.stopHeartbeat()
        }
    }

    override fun onDestroy() {
        // Cleanup resources
        lifecycleScope.launch {
            try {
                // Stop all connections
                plcDataBuffer.dispose()
                performanceMonitor.cleanup()
            } catch (e: Exception) {
                Log.e(TAG, "Error during cleanup", e)
            }
        }
        super.onDestroy()
    }
}
