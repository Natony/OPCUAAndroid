package com.example.s7opcuaapp.data.auth

import android.content.Context
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.example.s7opcuaapp.data.api.*
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Instant
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages authentication state and JWT tokens
 */
@Singleton
class AuthManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val TAG = "AuthManager"
        private const val PREFS_NAME = "auth_prefs"
        private const val KEY_ACCESS_TOKEN = "access_token"
        private const val KEY_REFRESH_TOKEN = "refresh_token"
        private const val KEY_EXPIRES_AT = "expires_at"
        private const val KEY_USER_ID = "user_id"
        private const val KEY_USERNAME = "username"
        private const val KEY_ROLE = "role"
        private const val KEY_DISPLAY_NAME = "display_name"

        // Refresh token 1 minute before expiry
        private const val TOKEN_REFRESH_THRESHOLD_SECONDS = 60
    }

    // Encrypted SharedPreferences for secure token storage
    private val prefs by lazy {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

        EncryptedSharedPreferences.create(
            context,
            PREFS_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    // Auth state
    private val _authState = MutableStateFlow<AuthState>(AuthState.Unknown)
    val authState: StateFlow<AuthState> = _authState.asStateFlow()

    // Current user
    private val _currentUser = MutableStateFlow<UserDto?>(null)
    val currentUser: StateFlow<UserDto?> = _currentUser.asStateFlow()

    // Mutex for thread-safe token operations
    private val tokenMutex = Mutex()

    init {
        // Load saved auth state
        loadSavedAuth()
    }

    /**
     * Auth states
     */
    sealed class AuthState {
        object Unknown : AuthState()
        object NotAuthenticated : AuthState()
        data class Authenticated(val user: UserDto) : AuthState()
        object TokenExpired : AuthState()
    }

    /**
     * Load saved authentication from encrypted storage
     */
    private fun loadSavedAuth() {
        val accessToken = prefs.getString(KEY_ACCESS_TOKEN, null)
        val expiresAt = prefs.getString(KEY_EXPIRES_AT, null)
        val userId = prefs.getString(KEY_USER_ID, null)
        val username = prefs.getString(KEY_USERNAME, null)
        val role = prefs.getString(KEY_ROLE, null)

        if (accessToken != null && userId != null && username != null && role != null) {
            val user = UserDto(
                id = userId,
                username = username,
                role = UserRole.valueOf(role),
                displayName = prefs.getString(KEY_DISPLAY_NAME, null),
                isActive = true,
                createdAt = null,
                lastLoginAt = null
            )

            // Check if token is expired
            if (isTokenExpired(expiresAt)) {
                Log.d(TAG, "Saved token is expired, need refresh")
                _authState.value = AuthState.TokenExpired
                _currentUser.value = user
            } else {
                Log.d(TAG, "Loaded saved auth for user: $username")
                _authState.value = AuthState.Authenticated(user)
                _currentUser.value = user
            }
        } else {
            _authState.value = AuthState.NotAuthenticated
        }
    }

    /**
     * Save login response
     */
    suspend fun saveLoginResponse(response: LoginResponse) = tokenMutex.withLock {
        if (response.success && response.accessToken != null && response.user != null) {
            prefs.edit().apply {
                putString(KEY_ACCESS_TOKEN, response.accessToken)
                putString(KEY_REFRESH_TOKEN, response.refreshToken)
                putString(KEY_EXPIRES_AT, response.expiresAt)
                putString(KEY_USER_ID, response.user.id)
                putString(KEY_USERNAME, response.user.username)
                putString(KEY_ROLE, response.user.role.name)
                putString(KEY_DISPLAY_NAME, response.user.displayName)
                apply()
            }

            _currentUser.value = response.user
            _authState.value = AuthState.Authenticated(response.user)

            Log.d(TAG, "Saved login for user: ${response.user.username}")
        }
    }

    /**
     * Get current access token
     */
    suspend fun getAccessToken(): String? = tokenMutex.withLock {
        prefs.getString(KEY_ACCESS_TOKEN, null)
    }

    /**
     * Get refresh token
     */
    suspend fun getRefreshToken(): String? = tokenMutex.withLock {
        prefs.getString(KEY_REFRESH_TOKEN, null)
    }

    /**
     * Check if token needs refresh
     */
    fun needsTokenRefresh(): Boolean {
        val expiresAt = prefs.getString(KEY_EXPIRES_AT, null) ?: return true
        return try {
            val expiry = Instant.parse(expiresAt)
            val now = Instant.now()
            val remainingSeconds = expiry.epochSecond - now.epochSecond
            remainingSeconds <= TOKEN_REFRESH_THRESHOLD_SECONDS
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing expiry time", e)
            true
        }
    }

    /**
     * Check if token is expired
     */
    private fun isTokenExpired(expiresAt: String?): Boolean {
        if (expiresAt == null) return true
        return try {
            val expiry = Instant.parse(expiresAt)
            Instant.now().isAfter(expiry)
        } catch (e: Exception) {
            true
        }
    }

    /**
     * Update tokens after refresh
     */
    suspend fun updateTokens(accessToken: String, refreshToken: String?, expiresAt: String?) = tokenMutex.withLock {
        prefs.edit().apply {
            putString(KEY_ACCESS_TOKEN, accessToken)
            if (refreshToken != null) {
                putString(KEY_REFRESH_TOKEN, refreshToken)
            }
            if (expiresAt != null) {
                putString(KEY_EXPIRES_AT, expiresAt)
            }
            apply()
        }

        // Update state if was expired
        if (_authState.value is AuthState.TokenExpired) {
            _currentUser.value?.let {
                _authState.value = AuthState.Authenticated(it)
            }
        }

        Log.d(TAG, "Tokens updated")
    }

    /**
     * Clear all auth data (logout)
     */
    suspend fun clearAuth() = tokenMutex.withLock {
        prefs.edit().clear().apply()
        _authState.value = AuthState.NotAuthenticated
        _currentUser.value = null
        Log.d(TAG, "Auth cleared (logout)")
    }

    /**
     * Check if user has admin role
     */
    fun isAdmin(): Boolean {
        return _currentUser.value?.role == UserRole.ADMIN
    }

    /**
     * Check if user can control (Admin or Operator)
     */
    fun canControl(): Boolean {
        val role = _currentUser.value?.role
        return role == UserRole.ADMIN || role == UserRole.OPERATOR
    }

    /**
     * Check if authenticated
     */
    fun isAuthenticated(): Boolean {
        return _authState.value is AuthState.Authenticated
    }
}
