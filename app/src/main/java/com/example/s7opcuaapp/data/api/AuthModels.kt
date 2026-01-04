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
 * Login request
 */
data class LoginRequest(
    @SerializedName("Username") val username: String,
    @SerializedName("Password") val password: String
)

/**
 * Login response with tokens
 */
data class LoginResponse(
    @SerializedName("Success") val success: Boolean,
    @SerializedName("AccessToken") val accessToken: String?,
    @SerializedName("RefreshToken") val refreshToken: String?,
    @SerializedName("ExpiresAt") val expiresAt: String?,
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
