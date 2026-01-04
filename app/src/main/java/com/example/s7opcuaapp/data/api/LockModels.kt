package com.example.s7opcuaapp.data.api

import com.google.gson.annotations.SerializedName

/**
 * Lock status response
 */
data class LockStatusResponse(
    @SerializedName("IsLocked") val isLocked: Boolean,
    @SerializedName("LockedBy") val lockedBy: String?,
    @SerializedName("LockedByUsername") val lockedByUsername: String?,
    @SerializedName("LockedAt") val lockedAt: String?,
    @SerializedName("ExpiresAt") val expiresAt: String?,
    @SerializedName("RemainingSeconds") val remainingSeconds: Int?,
    @SerializedName("IsMyLock") val isMyLock: Boolean
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
    @SerializedName("RemainingSeconds") val remainingSeconds: Int?,
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
    @SerializedName("RemainingSeconds") val remainingSeconds: Int?,
    @SerializedName("Error") val error: String?
)

/**
 * Force release request (Admin only)
 */
data class ForceReleaseRequest(
    @SerializedName("Reason") val reason: String?
)
