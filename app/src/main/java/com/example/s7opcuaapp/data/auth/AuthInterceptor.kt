package com.example.s7opcuaapp.data.auth

import android.util.Log
import okhttp3.Interceptor
import okhttp3.Response
import javax.inject.Inject
import javax.inject.Singleton

/**
 * OkHttp Interceptor that adds Authorization header to requests
 * Note: Uses synchronous token access to avoid blocking network thread
 */
@Singleton
class AuthInterceptor @Inject constructor(
    private val authManager: AuthManager
) : Interceptor {

    companion object {
        private const val TAG = "AuthInterceptor"
        private const val HEADER_AUTHORIZATION = "Authorization"
        private const val TOKEN_PREFIX = "Bearer "

        // Endpoints that don't require authentication
        private val PUBLIC_ENDPOINTS = listOf(
            "/api/auth/login",
            "/api/auth/refresh"
        )
    }

    override fun intercept(chain: Interceptor.Chain): Response {
        val originalRequest = chain.request()
        val path = originalRequest.url.encodedPath

        // Skip auth for public endpoints
        if (PUBLIC_ENDPOINTS.any { path.contains(it, ignoreCase = true) }) {
            return chain.proceed(originalRequest)
        }

        // Get access token synchronously (SharedPreferences is thread-safe for reads)
        val accessToken = authManager.getAccessTokenSync()

        // If no token, proceed without auth header
        if (accessToken.isNullOrBlank()) {
            Log.d(TAG, "No access token available")
            return chain.proceed(originalRequest)
        }

        // Add Authorization header
        val authenticatedRequest = originalRequest.newBuilder()
            .header(HEADER_AUTHORIZATION, "$TOKEN_PREFIX$accessToken")
            .build()

        Log.v(TAG, "Added auth header to: $path")

        return chain.proceed(authenticatedRequest)
    }
}
