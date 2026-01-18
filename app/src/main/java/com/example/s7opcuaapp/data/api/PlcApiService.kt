package com.example.s7opcuaapp.data.api

import retrofit2.Response
import retrofit2.http.*

/**
 * Retrofit interface for PLC API endpoints
 */
interface PlcApiService {

    // ============== PLC Endpoints ==============

    /**
     * Get all PLCs
     */
    @GET("api/plcs")
    suspend fun getAllPlcs(): Response<ApiResponse<List<PlcDto>>>

    /**
     * Get PLC by ID
     */
    @GET("api/plcs/{plcId}")
    suspend fun getPlcById(@Path("plcId") plcId: String): Response<ApiResponse<PlcDto>>

    /**
     * Get connection status of all PLCs
     */
    @GET("api/plcs/status")
    suspend fun getPlcStatus(): Response<ApiResponse<List<ConnectionStatusDto>>>

    /**
     * Connect to a PLC
     */
    @POST("api/plcs/{plcId}/connect")
    suspend fun connectPlc(@Path("plcId") plcId: String): Response<ApiResponse<Boolean>>

    /**
     * Disconnect from a PLC
     */
    @POST("api/plcs/{plcId}/disconnect")
    suspend fun disconnectPlc(@Path("plcId") plcId: String): Response<ApiResponse<Boolean>>

    /**
     * Connect to all PLCs
     */
    @POST("api/plcs/connect-all")
    suspend fun connectAllPlcs(): Response<ApiResponse<Int>>

    /**
     * Disconnect from all PLCs
     */
    @POST("api/plcs/disconnect-all")
    suspend fun disconnectAllPlcs(): Response<ApiResponse<Boolean>>

    // ============== Tag Endpoints ==============

    /**
     * Get all tags from all PLCs
     */
    @GET("api/tags")
    suspend fun getAllTags(): Response<ApiResponse<List<TagDto>>>

    /**
     * Get tags for a specific PLC
     */
    @GET("api/tags/plc/{plcId}")
    suspend fun getTagsByPlc(@Path("plcId") plcId: String): Response<ApiResponse<List<TagDto>>>

    /**
     * Get subscribed tags (from connected PLCs only)
     */
    @GET("api/tags/subscribed")
    suspend fun getSubscribedTags(): Response<ApiResponse<List<TagDto>>>

    /**
     * Read a tag value
     */
    @GET("api/tags/{plcId}/{nodeId}/read")
    suspend fun readTag(
        @Path("plcId") plcId: String,
        @Path("nodeId", encoded = true) nodeId: String
    ): Response<ApiResponse<TagDto>>

    /**
     * Write a tag value
     */
    @POST("api/tags/{plcId}/{nodeId}/write")
    suspend fun writeTag(
        @Path("plcId") plcId: String,
        @Path("nodeId", encoded = true) nodeId: String,
        @Body request: WriteTagRequest
    ): Response<ApiResponse<Boolean>>

    // ============== Auth Endpoints ==============

    /**
     * Login with username and password
     */
    @POST("api/auth/login")
    suspend fun login(@Body request: LoginRequest): Response<LoginResponse>

    /**
     * Refresh access token
     */
    @POST("api/auth/refresh")
    suspend fun refreshToken(@Body request: RefreshTokenRequest): Response<LoginResponse>

    /**
     * Logout (invalidate refresh token)
     */
    @POST("api/auth/logout")
    suspend fun logout(): Response<ApiResponse<Boolean>>

    /**
     * Get current user info
     */
    @GET("api/auth/me")
    suspend fun getCurrentUser(): Response<ApiResponse<UserDto>>

    /**
     * Change password
     */
    @POST("api/auth/change-password")
    suspend fun changePassword(@Body request: ChangePasswordRequest): Response<ApiResponse<Boolean>>

    // ============== User Management Endpoints (Admin only) ==============

    /**
     * Get all users
     */
    @GET("api/users")
    suspend fun getAllUsers(): Response<ApiResponse<List<UserDto>>>

    /**
     * Create new user
     */
    @POST("api/users")
    suspend fun createUser(@Body request: CreateUserRequest): Response<ApiResponse<UserDto>>

    /**
     * Update user
     */
    @PUT("api/users/{userId}")
    suspend fun updateUser(
        @Path("userId") userId: String,
        @Body request: UpdateUserRequest
    ): Response<ApiResponse<UserDto>>

    /**
     * Delete user
     */
    @DELETE("api/users/{userId}")
    suspend fun deleteUser(@Path("userId") userId: String): Response<ApiResponse<Boolean>>

    // ============== Lock Endpoints ==============

    /**
     * Get current lock status
     * Returns: { "success": true, "data": { ... } }
     */
    @GET("api/lock/status")
    suspend fun getLockStatus(): Response<LockStatusApiResponse>

    /**
     * Acquire operator lock
     */
    @POST("api/lock/acquire")
    suspend fun acquireLock(@Body request: AcquireLockRequest): Response<AcquireLockResponse>

    /**
     * Release operator lock
     */
    @POST("api/lock/release")
    suspend fun releaseLock(): Response<ReleaseLockResponse>

    /**
     * Extend lock duration
     */
    @POST("api/lock/extend")
    suspend fun extendLock(@Body request: ExtendLockRequest): Response<ExtendLockResponse>

    /**
     * Force release lock (Admin only)
     */
    @POST("api/lock/force-release")
    suspend fun forceReleaseLock(@Body request: ForceReleaseRequest): Response<ReleaseLockResponse>
}
