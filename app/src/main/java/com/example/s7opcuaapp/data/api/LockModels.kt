package com.example.s7opcuaapp.data.api

import com.google.gson.annotations.SerializedName

/**
 * Lock status API response wrapper
 * API returns: { "success": true, "data": { ... } }
 */
data class LockStatusApiResponse(
    @SerializedName("success") val success: Boolean = false,
    @SerializedName("data") val data: LockStatusData? = null
)

/**
 * Lock status data inside the response
 */
data class LockStatusData(
    @SerializedName("isLocked") val isLocked: Boolean = false,
    @SerializedName("lockId") val lockId: String? = null,
    @SerializedName("userId") val userId: String? = null,
    @SerializedName("username") val lockedByUsername: String? = null,
    @SerializedName("displayName") val displayName: String? = null,
    @SerializedName("acquiredAt") val acquiredAt: String? = null,
    @SerializedName("expiresAt") val expiresAt: String? = null,
    @SerializedName("timeRemainingSeconds") val remainingSeconds: Long? = null,
    @SerializedName("isCurrentUser") val isMyLock: Boolean = false
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
    @SerializedName("durationMinutes") val durationMinutes: Int? = null
)

/**
 * Acquire lock response
 */
data class AcquireLockResponse(
    @SerializedName("success") val success: Boolean,
    @SerializedName("expiresAt") val expiresAt: String?,
    @SerializedName("timeRemainingSeconds") val remainingSeconds: Long?,
    @SerializedName("error") val error: String?
)

/**
 * Release lock response
 */
data class ReleaseLockResponse(
    @SerializedName("success") val success: Boolean,
    @SerializedName("error") val error: String?
)

/**
 * Extend lock request
 */
data class ExtendLockRequest(
    @SerializedName("additionalMinutes") val additionalMinutes: Int? = null
)

/**
 * Extend lock response
 */
data class ExtendLockResponse(
    @SerializedName("success") val success: Boolean,
    @SerializedName("newExpiresAt") val newExpiresAt: String?,
    @SerializedName("timeRemainingSeconds") val remainingSeconds: Long?,
    @SerializedName("error") val error: String?
)

/**
 * Force release request (Admin only)
 */
data class ForceReleaseRequest(
    @SerializedName("reason") val reason: String?
)
