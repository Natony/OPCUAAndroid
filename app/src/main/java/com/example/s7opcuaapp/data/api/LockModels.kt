package com.example.s7opcuaapp.data.api

import com.google.gson.annotations.SerializedName

/**
 * Lock status response - matches actual API response structure
 * API returns flat response (not wrapped in Data field)
 */
data class LockStatusResponse(
    @SerializedName("Success") val success: Boolean = false,
    @SerializedName("IsLocked") val isLocked: Boolean = false,
    @SerializedName("LockId") val lockId: String? = null,
    @SerializedName("UserId") val userId: String? = null,
    @SerializedName("Username") val lockedByUsername: String? = null,
    @SerializedName("DisplayName") val displayName: String? = null,
    @SerializedName("AcquiredAt") val acquiredAt: String? = null,
    @SerializedName("ExpiresAt") val expiresAt: String? = null,
    @SerializedName("TimeRemainingSeconds") val remainingSeconds: Int? = null,
    @SerializedName("IsCurrentUser") val isMyLock: Boolean = false
)

/**
 * Acquire lock request
 */
data class AcquireLockRequest(
    @SerializedName("DurationMinutes") val durationMinutes: Int? = null
)

/**
 * Acquire lock response
 */
data class AcquireLockResponse(
    @SerializedName("Success") val success: Boolean,
    @SerializedName("ExpiresAt") val expiresAt: String?,
    @SerializedName("TimeRemainingSeconds") val remainingSeconds: Int?,
    @SerializedName("Error") val error: String?
)

/**
 * Release lock response
 */
data class ReleaseLockResponse(
    @SerializedName("Success") val success: Boolean,
    @SerializedName("Error") val error: String?
)

/**
 * Extend lock request
 */
data class ExtendLockRequest(
    @SerializedName("AdditionalMinutes") val additionalMinutes: Int? = null
)

/**
 * Extend lock response
 */
data class ExtendLockResponse(
    @SerializedName("Success") val success: Boolean,
    @SerializedName("NewExpiresAt") val newExpiresAt: String?,
    @SerializedName("TimeRemainingSeconds") val remainingSeconds: Int?,
    @SerializedName("Error") val error: String?
)

/**
 * Force release request (Admin only)
 */
data class ForceReleaseRequest(
    @SerializedName("Reason") val reason: String?
)
