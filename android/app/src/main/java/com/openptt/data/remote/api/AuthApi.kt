package com.openptt.data.remote.api

import kotlinx.serialization.Serializable
import retrofit2.Response
import retrofit2.http.*

// ---------- Request/Response DTOs ----------

@Serializable
data class LoginRequest(val username: String, val password: String)

@Serializable
data class RegisterRequest(val username: String, val password: String, val display_name: String)

@Serializable
data class RefreshRequest(val refresh_token: String)

@Serializable
data class FcmTokenRequest(val token: String)

@Serializable
data class AuthResponse(
    val access_token: String,
    val refresh_token: String,
    val expires_in: Long,
    val user: UserDto
)

@Serializable
data class UserDto(
    val id: String,
    val username: String,
    val display_name: String,
    val avatar_url: String? = null,
    val role: String,
    val created_at: String
)

@Serializable
data class HealthResponse(
    val status: String,
    val version: String,
    val http_port: Int,
    val udp_port: Int
)

// ---------- API Interface ----------

interface AuthApi {
    @GET("/health")
    suspend fun health(): Response<HealthResponse>

    @POST("/api/v1/auth/register")
    suspend fun register(@Body request: RegisterRequest): Response<AuthResponse>

    @POST("/api/v1/auth/login")
    suspend fun login(@Body request: LoginRequest): Response<AuthResponse>

    @POST("/api/v1/auth/refresh")
    suspend fun refresh(@Body request: RefreshRequest): Response<AuthResponse>

    @POST("/api/v1/auth/logout")
    suspend fun logout(@Body request: RefreshRequest): Response<Unit>

    @GET("/api/v1/auth/me")
    suspend fun me(): Response<UserDto>

    @POST("/api/v1/auth/fcm-token")
    suspend fun updateFcmToken(@Body request: FcmTokenRequest): Response<Unit>
}
