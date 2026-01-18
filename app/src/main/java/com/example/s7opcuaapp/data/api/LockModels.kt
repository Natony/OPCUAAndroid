package com.example.s7opcuaapp.data.api

import com.google.gson.annotations.SerializedName

/**
 * Lock status API response wrapper
 * API returns: { "Success": true, "Data": { ... } }
 * Note: API uses PascalCase (C# style)
 */
data class LockStatusApiResponse(
    @SerializedName("Success") val success: Boolean = false,
    @SerializedName("Data") val data: LockStatusData? = null
)

/**
 * Lock status data inside the response
 */
data class LockStatusData(
    @SerializedName("IsLocked") val isLocked: Boolean = false,
    @SerializedName("LockId") val lockId: String? = null,
    @SerializedName("UserId") val userId: String? = null,
    @SerializedName("Username") val lockedByUsername: String? = null,
    @SerializedName("DisplayName") val displayName: String? = null,
    @SerializedName("AcquiredAt") val acquiredAt: String? = null,
    @SerializedName("ExpiresAt") val expiresAt: String? = null,
    @SerializedName("TimeRemainingSeconds") val remainingSeconds: Long? = null,
    @SerializedName("IsCurrentUser") val isMyLock: Boolean = false
)

/**
 * Convenience class for internal use (flattened)
 */
data class LockStatusResponse(
    val success: Boolean = false,
    val isLocked: Boolean = false,
    val lockId: String? = null,
    val userId: String? = null,
    val lockedByUsername: String? = null,
    val displayName: String? = null,
    val acquiredAt: String? = null,
    val expiresAt: String? = null,
    val remainingSeconds: Long? = null,
    val isMyLock: Boolean = false
) {
    companion object {
        fun fromApiResponse(response: LockStatusApiResponse): LockStatusResponse {
            return LockStatusResponse(
                success = response.success,
                isLocked = response.data?.isLocked ?: false,
                lockId = response.data?.lockId,
                userId = response.data?.userId,
                lockedByUsername = response.data?.lockedByUsername,
                displayName = response.data?.displayName,
                acquiredAt = response.data?.acquiredAt,
                expiresAt = response.data?.expiresAt,
                remainingSeconds = response.data?.remainingSeconds,
                isMyLock = response.data?.isMyLock ?: false
            )
        }
    }
}

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
    @SerializedName("TimeRemainingSeconds") val remainingSeconds: Long?,
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
    @SerializedName("TimeRemainingSeconds") val remainingSeconds: Long?,
    @SerializedName("Error") val error: String?
)

/**
 * Force release request (Admin only)
 */
data class ForceReleaseRequest(
    @SerializedName("Reason") val reason: String?
)
