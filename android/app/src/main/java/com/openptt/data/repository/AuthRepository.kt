package com.openptt.data.repository

import com.google.firebase.messaging.FirebaseMessaging
import com.openptt.data.local.TokenManager
import com.openptt.data.remote.api.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository handling all authentication operations.
 * Bridges between the remote API and local token storage.
 */
@Singleton
class AuthRepository @Inject constructor(
    private val authApi: AuthApi,
    private val tokenManager: TokenManager
) {
    val isLoggedIn: Boolean get() = tokenManager.isLoggedIn
    val currentUsername: String? get() = tokenManager.username
    val currentDisplayName: String? get() = tokenManager.displayName

    suspend fun login(username: String, password: String): Result<AuthResponse> {
        return try {
            val response = authApi.login(LoginRequest(username, password))
            if (response.isSuccessful) {
                val body = response.body()!!
                tokenManager.saveAuth(
                    accessToken = body.access_token,
                    refreshToken = body.refresh_token,
                    expiresIn = body.expires_in,
                    userId = body.user.id,
                    username = body.user.username,
                    displayName = body.user.display_name
                )
                syncFcmToken()
                Result.success(body)
            } else {
                Result.failure(Exception("Login failed: ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun register(
        username: String,
        password: String,
        displayName: String
    ): Result<AuthResponse> {
        return try {
            val response = authApi.register(
                RegisterRequest(username, password, displayName)
            )
            if (response.isSuccessful) {
                val body = response.body()!!
                tokenManager.saveAuth(
                    accessToken = body.access_token,
                    refreshToken = body.refresh_token,
                    expiresIn = body.expires_in,
                    userId = body.user.id,
                    username = body.user.username,
                    displayName = body.user.display_name
                )
                syncFcmToken()
                Result.success(body)
            } else {
                val errorBody = response.errorBody()?.string() ?: "Unknown error"
                Result.failure(Exception("Registration failed: $errorBody"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun refreshToken(): Result<Unit> {
        val refreshToken = tokenManager.refreshToken
            ?: return Result.failure(Exception("No refresh token"))

        return try {
            val response = authApi.refresh(RefreshRequest(refreshToken))
            if (response.isSuccessful) {
                val body = response.body()!!
                tokenManager.saveAuth(
                    accessToken = body.access_token,
                    refreshToken = body.refresh_token,
                    expiresIn = body.expires_in,
                    userId = body.user.id,
                    username = body.user.username,
                    displayName = body.user.display_name
                )
                Result.success(Unit)
            } else {
                // Refresh failed — force logout
                logout()
                Result.failure(Exception("Session expired"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun logout() {
        try {
            val refreshToken = tokenManager.refreshToken
            if (refreshToken != null) {
                authApi.logout(RefreshRequest(refreshToken))
            }
        } catch (_: Exception) {
            // Best effort — clear tokens even if server call fails
        } finally {
            tokenManager.clearAll()
        }
    }

    suspend fun testConnection(serverUrl: String): Result<HealthResponse> {
        return kotlinx.coroutines.Dispatchers.IO.let {
            kotlinx.coroutines.withContext(it) {
                try {
                    val cleanUrl = serverUrl.trimEnd('/') + "/health"
                    val client = okhttp3.OkHttpClient()
                    val request = okhttp3.Request.Builder()
                        .url(cleanUrl)
                        .build()
                    val response = client.newCall(request).execute()
                    if (response.isSuccessful) {
                        val body = response.body?.string() ?: ""
                        val health = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }.decodeFromString<HealthResponse>(body)
                        Result.success(health)
                    } else {
                        Result.failure(Exception("Server returned ${response.code}"))
                    }
                } catch (e: Exception) {
                    Result.failure(e)
                }
            }
        }
    }

    fun syncFcmToken() {
        FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
            if (task.isSuccessful) {
                val token = task.result
                tokenManager.fcmToken = token
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        authApi.updateFcmToken(FcmTokenRequest(token))
                    } catch (_: Exception) {}
                }
            }
        }
    }
}
