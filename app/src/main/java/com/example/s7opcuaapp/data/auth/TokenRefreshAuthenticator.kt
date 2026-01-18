package com.example.s7opcuaapp.data.auth

import android.util.Log
import com.example.s7opcuaapp.data.api.PlcApiService
import com.example.s7opcuaapp.data.api.RefreshTokenRequest
import kotlinx.coroutines.runBlocking
import okhttp3.Authenticator
import okhttp3.Request
import okhttp3.Response
import okhttp3.Route
import java.util.concurrent.locks.ReentrantLock
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton
import kotlin.concurrent.withLock

/**
 * OkHttp Authenticator that handles 401 responses by refreshing the token
 * Note: Uses ReentrantLock instead of coroutine Mutex to minimize runBlocking usage
 */
@Singleton
class TokenRefreshAuthenticator @Inject constructor(
    private val authManager: AuthManager,
    // Use Provider to avoid circular dependency
    private val apiServiceProvider: Provider<PlcApiService>
) : Authenticator {

    companion object {
        private const val TAG = "TokenRefreshAuth"
        private const val MAX_RETRY_COUNT = 2
    }

    // Use Java ReentrantLock instead of coroutine Mutex
    private val refreshLock = ReentrantLock()

    override fun authenticate(route: Route?, response: Response): Request? {
        Log.d(TAG, "Received 401 for: ${response.request.url}")

        // Check retry count to avoid infinite loop
        val retryCount = response.request.header("X-Retry-Count")?.toIntOrNull() ?: 0
        if (retryCount >= MAX_RETRY_COUNT) {
            Log.w(TAG, "Max retry count reached, giving up")
            return null
        }

        return refreshLock.withLock {
            // Double-check if token was already refreshed by another request
            val currentToken = authManager.getAccessTokenSync()
            val requestToken = response.request.header("Authorization")
                ?.removePrefix("Bearer ")

            if (currentToken != null && currentToken != requestToken) {
                // Token was already refreshed, retry with new token
                Log.d(TAG, "Token was already refreshed, retrying")
                return@withLock response.request.newBuilder()
                    .header("Authorization", "Bearer $currentToken")
                    .header("X-Retry-Count", (retryCount + 1).toString())
                    .build()
            }

            // Try to refresh the token
            val refreshToken = authManager.getRefreshTokenSync()
            if (refreshToken == null) {
                Log.w(TAG, "No refresh token available")
                runBlocking { authManager.clearAuth() }
                return@withLock null
            }

            try {
                Log.d(TAG, "Attempting to refresh token")
                // Only use runBlocking for the actual network call
                val refreshResponse = runBlocking {
                    apiServiceProvider.get().refreshToken(RefreshTokenRequest(refreshToken))
                }

                if (refreshResponse.isSuccessful) {
                    val body = refreshResponse.body()
                    if (body?.success == true && body.accessToken != null) {
                        // Save new tokens (must use runBlocking for suspend function)
                        runBlocking {
                            authManager.updateTokens(
                                accessToken = body.accessToken,
                                refreshToken = body.refreshToken,
                                expiresAt = body.expiresAt
                            )
                        }

                        Log.d(TAG, "Token refreshed successfully")

                        // Retry the original request with new token
                        return@withLock response.request.newBuilder()
                            .header("Authorization", "Bearer ${body.accessToken}")
                            .header("X-Retry-Count", (retryCount + 1).toString())
                            .build()
                    }
                }

                // Refresh failed
                Log.w(TAG, "Token refresh failed: ${refreshResponse.code()}")
                runBlocking { authManager.clearAuth() }
                return@withLock null

            } catch (e: Exception) {
                Log.e(TAG, "Error refreshing token", e)
                runBlocking { authManager.clearAuth() }
                return@withLock null
            }
        }
    }
}
