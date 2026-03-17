package com.example.s7opcuaapp.data.api

import com.google.gson.annotations.SerializedName

/**
 * User roles enum
 */
enum class UserRole {
    @SerializedName("Admin") ADMIN,
    @SerializedName("Operator") OPERATOR,
    @SerializedName("Viewer") VIEWER
}

/**
 * Login request - Single-Session authentication
 * deviceId is REQUIRED for single-session enforcement
 */
data class LoginRequest(
    @SerializedName("Username") val username: String,
    @SerializedName("Password") val password: String,
    @SerializedName("DeviceId") val deviceId: String,        // REQUIRED - unique device identifier
    @SerializedName("DeviceName") val deviceName: String?    // Optional - display name for device
)

/**
 * Login response with tokens and session info
 */
data class LoginResponse(
    @SerializedName("Success") val success: Boolean,
    @SerializedName("AccessToken") val accessToken: String?,
    @SerializedName("RefreshToken") val refreshToken: String?,
    @SerializedName("ExpiresAt") val expiresAt: String?,
    @SerializedName("SessionId") val sessionId: String?,                           // Session ID for validate/heartbeat
    @SerializedName("PreviousSessionTerminated") val previousSessionTerminated: Boolean?, // true if another device was kicked
    @SerializedName("PreviousDeviceName") val previousDeviceName: String?,         // Name of kicked device
    @SerializedName("User") val user: UserDto?,
    @SerializedName("Error") val error: String?
)

/**
 * User DTO
 */
data class UserDto(
    @SerializedName("Id") val id: String,
    @SerializedName("Username") val username: String,
    @SerializedName("Role") val role: UserRole,
    @SerializedName("DisplayName") val displayName: String?,
    @SerializedName("IsActive") val isActive: Boolean,
    @SerializedName("CreatedAt") val createdAt: String?,
    @SerializedName("LastLoginAt") val lastLoginAt: String?
)

/**
 * Refresh token request
 */
data class RefreshTokenRequest(
    @SerializedName("RefreshToken") val refreshToken: String
)

/**
 * Change password request
 */
data class ChangePasswordRequest(
    @SerializedName("CurrentPassword") val currentPassword: String,
    @SerializedName("NewPassword") val newPassword: String
)

/**
 * Create user request (Admin only)
 */
data class CreateUserRequest(
    @SerializedName("Username") val username: String,
    @SerializedName("Password") val password: String,
    @SerializedName("Role") val role: UserRole,
    @SerializedName("DisplayName") val displayName: String?
)

/**
 * Update user request (Admin only)
 */
data class UpdateUserRequest(
    @SerializedName("Role") val role: UserRole?,
    @SerializedName("DisplayName") val displayName: String?,
    @SerializedName("IsActive") val isActive: Boolean?,
    @SerializedName("NewPassword") val newPassword: String?
)

// ============== Single-Session Models ==============

/**
 * Logout request - requires refreshToken
 */
data class LogoutRequest(
    @SerializedName("RefreshToken") val refreshToken: String
)

/**
 * Validate session request
 */
data class ValidateSessionRequest(
    @SerializedName("SessionId") val sessionId: String,
    @SerializedName("DeviceId") val deviceId: String
)

/**
 * Validate session response
 */
data class ValidateSessionResponse(
    @SerializedName("Success") val success: Boolean,
    @SerializedName("IsValid") val isValid: Boolean,
    @SerializedName("InvalidReason") val invalidReason: String?,  // LOGGED_IN_FROM_ANOTHER_DEVICE, FORCED_LOGOUT_BY_ADMIN, etc.
    @SerializedName("NewDeviceName") val newDeviceName: String?,  // Name of device that took over session
    @SerializedName("InvalidatedAt") val invalidatedAt: String?
)

/**
 * Heartbeat request - send every 30-60 seconds
 */
data class HeartbeatRequest(
    @SerializedName("SessionId") val sessionId: String,
    @SerializedName("DeviceId") val deviceId: String
)

/**
 * Heartbeat response
 */
data class HeartbeatResponse(
    @SerializedName("Success") val success: Boolean,
    @SerializedName("SessionValid") val sessionValid: Boolean,
    @SerializedName("InvalidReason") val invalidReason: String?  // null if valid, reason if kicked
)

/**
 * Session info response
 */
data class SessionInfoResponse(
    @SerializedName("Success") val success: Boolean,
    @SerializedName("Data") val data: SessionInfo?
)

/**
 * Session info data
 */
data class SessionInfo(
    @SerializedName("SessionId") val sessionId: String,
    @SerializedName("UserId") val userId: String,
    @SerializedName("Username") val username: String,
    @SerializedName("DeviceId") val deviceId: String,
    @SerializedName("DeviceName") val deviceName: String?,
    @SerializedName("LoginAt") val loginAt: String,
    @SerializedName("LastActivityAt") val lastActivityAt: String,
    @SerializedName("IsActive") val isActive: Boolean
)

/**
 * Invalid reason constants
 */
object InvalidReason {
    const val LOGGED_IN_FROM_ANOTHER_DEVICE = "LOGGED_IN_FROM_ANOTHER_DEVICE"
    const val FORCED_LOGOUT_BY_ADMIN = "FORCED_LOGOUT_BY_ADMIN"
    const val USER_LOGGED_OUT = "USER_LOGGED_OUT"
    const val SESSION_NOT_FOUND = "SESSION_NOT_FOUND"
    const val DEVICE_MISMATCH = "DEVICE_MISMATCH"
}
