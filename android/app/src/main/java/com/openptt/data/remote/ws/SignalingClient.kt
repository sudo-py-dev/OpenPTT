package com.openptt.data.remote.ws

import android.util.Log
import com.openptt.data.local.SettingsDataStore
import com.openptt.data.local.TokenManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import javax.inject.Inject
import javax.inject.Singleton

enum class ConnectionState {
    DISCONNECTED,
    CONNECTING,
    CONNECTED
}

@Singleton
class SignalingClient @Inject constructor(
    private val client: OkHttpClient,
    private val json: Json,
    private val tokenManager: TokenManager,
    private val settingsDataStore: SettingsDataStore
) {
    private val scope = CoroutineScope(Dispatchers.IO + Job())
    private var webSocket: WebSocket? = null

    private val _connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private val _messages = MutableSharedFlow<SignalingMessage>()
    val messages: SharedFlow<SignalingMessage> = _messages.asSharedFlow()

    fun connect() {
        if (_connectionState.value != ConnectionState.DISCONNECTED) return

        scope.launch {
            _connectionState.value = ConnectionState.CONNECTING

            val serverUrl = settingsDataStore.serverUrlFlow.first().ifEmpty { "http://localhost:8443" }
            val cleanServerUrl = serverUrl.removeSuffix("/")
            val wsUrl = cleanServerUrl.replace("http://", "ws://").replace("https://", "wss://") + "/ws"

            val request = Request.Builder()
                .url(wsUrl)
                .build()

            webSocket = client.newWebSocket(request, object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) {
                    Log.d("SignalingClient", "WebSocket connected")
                    
                    // Send auth token upon connection
                    val token = tokenManager.accessToken
                    if (token != null) {
                        val authMsg = SignalingMessage.Auth(token)
                        sendMessage(authMsg)
                    }
                }

                override fun onMessage(webSocket: WebSocket, text: String) {
                    try {
                        val message = json.decodeFromString<SignalingMessage>(text)
                        
                        // Handle AuthOk to transition to CONNECTED state
                        if (message is SignalingMessage.AuthOk) {
                            _connectionState.value = ConnectionState.CONNECTED
                        }

                        scope.launch {
                            _messages.emit(message)
                        }
                    } catch (e: Exception) {
                        Log.e("SignalingClient", "Failed to parse message: $text", e)
                    }
                }

                override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                    Log.d("SignalingClient", "WebSocket closed: $reason")
                    _connectionState.value = ConnectionState.DISCONNECTED
                }

                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                    Log.e("SignalingClient", "WebSocket failure", t)
                    _connectionState.value = ConnectionState.DISCONNECTED
                    
                    // Auto-reconnect logic could be added here
                    scope.launch {
                        delay(3000)
                        if (tokenManager.isLoggedIn) {
                            connect()
                        }
                    }
                }
            })
        }
    }

    fun sendMessage(message: SignalingMessage) {
        val text = json.encodeToString(message)
        webSocket?.send(text)
    }

    fun disconnect() {
        webSocket?.close(1000, "User disconnected")
        webSocket = null
        _connectionState.value = ConnectionState.DISCONNECTED
    }
}
