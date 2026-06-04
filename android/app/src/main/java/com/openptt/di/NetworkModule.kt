package com.openptt.di

import com.openptt.data.local.SettingsDataStore
import com.openptt.data.local.TokenManager
import com.openptt.data.remote.api.AuthApi
import com.openptt.data.remote.api.GroupApi
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Qualifier
import javax.inject.Singleton

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class BaseUrl

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideJson(): Json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        encodeDefaults = true
    }

    @Provides
    @Singleton
    fun provideOkHttpClient(
        tokenManager: TokenManager,
        settingsDataStore: SettingsDataStore,
        json: Json
    ): OkHttpClient {
        val authInterceptor = Interceptor { chain ->
            val request = chain.request()
            val token = tokenManager.accessToken
            val newRequest = if (token != null) {
                request.newBuilder()
                    .addHeader("Authorization", "Bearer $token")
                    .build()
            } else {
                request
            }
            chain.proceed(newRequest)
        }

        val dynamicUrlInterceptor = Interceptor { chain ->
            val request = chain.request()
            val serverUrl = runBlocking { settingsDataStore.serverUrlFlow.first() }
            if (serverUrl.isNotEmpty()) {
                val parsedUrl = serverUrl.toHttpUrlOrNull()
                if (parsedUrl != null) {
                    val newUrl = request.url.newBuilder()
                        .scheme(parsedUrl.scheme)
                        .host(parsedUrl.host)
                        .port(parsedUrl.port)
                        .build()
                    return@Interceptor chain.proceed(request.newBuilder().url(newUrl).build())
                }
            }
            chain.proceed(request)
        }

        val authenticator = okhttp3.Authenticator { _, response ->
            // Prevent infinite loops if refresh fails and returns 401
            if (response.request.url.encodedPath.contains("/auth/refresh")) {
                return@Authenticator null
            }
            
            // Check if we even have a refresh token
            val refreshToken = tokenManager.refreshToken
            if (refreshToken == null) {
                tokenManager.clearAll()
                return@Authenticator null
            }

            // Sync fetch server URL
            val serverUrl = runBlocking { settingsDataStore.serverUrlFlow.first() }.ifEmpty { "http://localhost:8443" }

            val refreshRequest = okhttp3.Request.Builder()
                .url("$serverUrl/api/v1/auth/refresh")
                .post(okhttp3.RequestBody.create(
                    "application/json".toMediaType(),
                    """{"refresh_token":"$refreshToken"}"""
                ))
                .build()

            try {
                // Use a new client without interceptors to avoid loops
                val clientWithoutAuth = OkHttpClient()
                val refreshResponse = clientWithoutAuth.newCall(refreshRequest).execute()

                if (refreshResponse.isSuccessful) {
                    val body = refreshResponse.body?.string()
                    if (body != null) {
                        // We use kotlinx.serialization to parse the AuthResponse manually
                        // Actually, we can just extract values manually since we just need the tokens
                        val jsonElement = json.parseToJsonElement(body)
                        val access = jsonElement.jsonObject["access_token"]?.jsonPrimitive?.content
                        val refresh = jsonElement.jsonObject["refresh_token"]?.jsonPrimitive?.content
                        val expires = jsonElement.jsonObject["expires_in"]?.jsonPrimitive?.content?.toLongOrNull() ?: 900L
                        
                        val userObj = jsonElement.jsonObject["user"]?.jsonObject
                        val uid = userObj?.get("id")?.jsonPrimitive?.content
                        val uname = userObj?.get("username")?.jsonPrimitive?.content
                        val dname = userObj?.get("display_name")?.jsonPrimitive?.content

                        if (access != null && refresh != null && uid != null && uname != null && dname != null) {
                            tokenManager.saveAuth(access, refresh, expires, uid, uname, dname)
                            
                            // Retry the failed request with the new token
                            return@Authenticator response.request.newBuilder()
                                .header("Authorization", "Bearer $access")
                                .build()
                        }
                    }
                } else {
                    // Refresh token is invalid/expired
                    tokenManager.clearAll()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
            null
        }

        val loggingInterceptor = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        }

        return OkHttpClient.Builder()
            .addInterceptor(dynamicUrlInterceptor)
            .addInterceptor(authInterceptor)
            .addInterceptor(loggingInterceptor)
            .authenticator(authenticator)
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .build()
    }

    @Provides
    @Singleton
    fun provideRetrofit(
        client: OkHttpClient,
        json: Json
    ): Retrofit {
        return Retrofit.Builder()
            .baseUrl("http://localhost:8443/") // Dummy URL, overwritten by interceptor
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
    }

    @Provides
    @Singleton
    fun provideAuthApi(retrofit: Retrofit): AuthApi =
        retrofit.create(AuthApi::class.java)

    @Provides
    @Singleton
    fun provideGroupApi(retrofit: Retrofit): GroupApi =
        retrofit.create(GroupApi::class.java)
}
