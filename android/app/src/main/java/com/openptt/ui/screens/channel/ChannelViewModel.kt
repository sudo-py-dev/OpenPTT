package com.openptt.ui.screens.channel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.openptt.audio.AudioEngine
import com.openptt.data.local.SettingsDataStore
import com.openptt.data.local.TokenManager
import com.openptt.data.remote.ws.ConnectionState
import com.openptt.data.remote.ws.SignalingClient
import com.openptt.data.remote.ws.SignalingMessage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ChannelState(
    val channelId: String = "",
    val channelName: String = "General",
    val onlineUsers: Int = 0,
    val pttState: PttState = PttState.IDLE,
    val activeSpeaker: String? = null,
    val connectionStatus: String = "disconnected" // connected, connecting, disconnected
)

@HiltViewModel
class ChannelViewModel @Inject constructor(
    private val signalingClient: SignalingClient,
    private val tokenManager: TokenManager,
    private val audioEngine: AudioEngine,
    private val settingsDataStore: SettingsDataStore
) : ViewModel() {

    private val _uiState = MutableStateFlow(ChannelState())
    val uiState: StateFlow<ChannelState> = _uiState.asStateFlow()

    fun initChannel(channelId: String) {
        _uiState.value = _uiState.value.copy(channelId = channelId)
        
        signalingClient.connect()

        viewModelScope.launch {
            val serverUrl = settingsDataStore.serverUrlFlow.first().ifEmpty { "http://localhost:8443" }
            val audioQuality = settingsDataStore.audioQualityFlow.first().ifEmpty { "normal" }
            val host = serverUrl.replace("http://", "").replace("https://", "").substringBefore(":")
            val port = 9000 // Server UDP port
            val myUserIdStr = tokenManager.userId ?: ""
            audioEngine.init(host, port, channelId, myUserIdStr, audioQuality)
        }

        viewModelScope.launch {
            signalingClient.connectionState.collect { state ->
                val statusStr = when (state) {
                    ConnectionState.CONNECTED -> "connected"
                    ConnectionState.CONNECTING -> "connecting"
                    ConnectionState.DISCONNECTED -> "disconnected"
                }
                _uiState.value = _uiState.value.copy(connectionStatus = statusStr)
                
                if (state == ConnectionState.CONNECTED) {
                    // Join the channel once connected
                    signalingClient.sendMessage(SignalingMessage.JoinChannel(channelId))
                }
            }
        }

        viewModelScope.launch {
            signalingClient.messages.collect { msg ->
                val myUserId = tokenManager.userId
                when (msg) {
                    is SignalingMessage.FloorGranted -> {
                        if (msg.speaker_id == myUserId) {
                            _uiState.value = _uiState.value.copy(pttState = PttState.TRANSMITTING, activeSpeaker = "You")
                            audioEngine.startRecording()
                        } else {
                            _uiState.value = _uiState.value.copy(activeSpeaker = msg.speaker_name, pttState = PttState.IDLE)
                            audioEngine.startPlaying()
                        }
                    }
                    is SignalingMessage.FloorDenied -> {
                        _uiState.value = _uiState.value.copy(pttState = PttState.DENIED)
                    }
                    is SignalingMessage.FloorReleased -> {
                        _uiState.value = _uiState.value.copy(pttState = PttState.IDLE, activeSpeaker = null)
                        audioEngine.stopRecording()
                        audioEngine.stopPlaying()
                    }
                    is SignalingMessage.FloorRevoked -> {
                        _uiState.value = _uiState.value.copy(pttState = PttState.IDLE, activeSpeaker = null)
                        audioEngine.stopRecording()
                        audioEngine.stopPlaying()
                    }
                    is SignalingMessage.ChannelUsers -> {
                        _uiState.value = _uiState.value.copy(onlineUsers = msg.users.size)
                        
                        // Check if someone is already speaking
                        val speaker = msg.users.find { it.is_speaking }
                        if (speaker != null) {
                            if (speaker.user_id == myUserId) {
                                _uiState.value = _uiState.value.copy(pttState = PttState.TRANSMITTING, activeSpeaker = "You")
                                audioEngine.startRecording()
                            } else {
                                _uiState.value = _uiState.value.copy(activeSpeaker = speaker.username, pttState = PttState.IDLE)
                                audioEngine.startPlaying()
                            }
                        }
                    }
                    is SignalingMessage.UserJoined -> {
                        _uiState.value = _uiState.value.copy(onlineUsers = _uiState.value.onlineUsers + 1)
                    }
                    is SignalingMessage.UserLeft -> {
                        _uiState.value = _uiState.value.copy(onlineUsers = maxOf(0, _uiState.value.onlineUsers - 1))
                    }
                    else -> {}
                }
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        signalingClient.sendMessage(SignalingMessage.LeaveChannel(_uiState.value.channelId))
        signalingClient.disconnect()
        audioEngine.destroy()
    }

    fun pttDown() {
        _uiState.value = _uiState.value.copy(pttState = PttState.REQUESTING)
        signalingClient.sendMessage(SignalingMessage.PttDown(_uiState.value.channelId))
    }

    fun pttUp() {
        _uiState.value = _uiState.value.copy(pttState = PttState.IDLE)
        signalingClient.sendMessage(SignalingMessage.PttUp(_uiState.value.channelId))
    }
}
