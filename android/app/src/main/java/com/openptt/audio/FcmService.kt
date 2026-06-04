package com.openptt.audio

import android.content.Intent
import android.util.Log
import androidx.core.content.ContextCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.openptt.data.local.SettingsDataStore
import com.openptt.data.local.TokenManager
import com.openptt.data.repository.AuthRepository
import com.openptt.data.remote.ws.ConnectionState
import com.openptt.data.remote.ws.SignalingClient
import com.openptt.data.remote.ws.SignalingMessage
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class FcmService : FirebaseMessagingService() {

    @Inject lateinit var tokenManager: TokenManager
    @Inject lateinit var signalingClient: SignalingClient
    @Inject lateinit var audioEngine: AudioEngine
    @Inject lateinit var settingsDataStore: SettingsDataStore
    @Inject lateinit var authRepository: AuthRepository

    private val scope = CoroutineScope(Dispatchers.IO + Job())

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d("FcmService", "New FCM Token: $token")
        // TODO: Send to server if user is logged in
        scope.launch {
            tokenManager.fcmToken = token
            if (tokenManager.isLoggedIn) {
                authRepository.syncFcmToken()
            }
        }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)
        Log.d("FcmService", "FCM Message received: ${message.data}")
        
        val type = message.data["type"]
        val channelId = message.data["channel_id"]
        val speakerName = message.data["speaker_name"] ?: "Someone"

        if (type == "ptt_start" && channelId != null) {
            // Start PttService to keep us alive and show notification
            val startIntent = Intent(this, PttService::class.java).apply {
                action = PttService.ACTION_START
                putExtra(PttService.EXTRA_CHANNEL_NAME, "Incoming transmission from $speakerName")
            }
            ContextCompat.startForegroundService(this, startIntent)

            // Auto connect and listen
            scope.launch {
                val userId = tokenManager.userId ?: return@launch
                val serverUrl = settingsDataStore.serverUrlFlow.first().ifEmpty { "http://localhost:8443" }
                val audioQuality = settingsDataStore.audioQualityFlow.first().ifEmpty { "normal" }
                val host = serverUrl.replace("http://", "").replace("https://", "").substringBefore(":")
                
                audioEngine.init(host, 9000, channelId, userId, audioQuality)
                
                signalingClient.connect()
                
                // Once connected, join channel
                signalingClient.connectionState.collect { state ->
                    if (state == ConnectionState.CONNECTED) {
                        signalingClient.sendMessage(SignalingMessage.JoinChannel(channelId))
                    }
                }
            }

            scope.launch {
                signalingClient.messages.collect { msg ->
                    when (msg) {
                        is SignalingMessage.FloorGranted -> audioEngine.startPlaying()
                        is SignalingMessage.FloorReleased, is SignalingMessage.FloorRevoked -> {
                            audioEngine.stopPlaying()
                            // Could stop service here if we want to shut down immediately after
                        }
                        else -> {}
                    }
                }
            }
        }
    }
}
