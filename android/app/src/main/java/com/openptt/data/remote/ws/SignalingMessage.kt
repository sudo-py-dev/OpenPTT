package com.openptt.data.remote.ws

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
sealed class SignalingMessage {
    @Serializable
    @SerialName("auth")
    data class Auth(val token: String) : SignalingMessage()

    @Serializable
    @SerialName("auth_ok")
    data class AuthOk(val user_id: String, val username: String) : SignalingMessage()

    @Serializable
    @SerialName("join_channel")
    data class JoinChannel(val channel_id: String) : SignalingMessage()

    @Serializable
    @SerialName("leave_channel")
    data class LeaveChannel(val channel_id: String) : SignalingMessage()

    @Serializable
    @SerialName("ptt_down")
    data class PttDown(val channel_id: String, val priority: Boolean? = null) : SignalingMessage()

    @Serializable
    @SerialName("ptt_up")
    data class PttUp(val channel_id: String) : SignalingMessage()

    @Serializable
    @SerialName("floor_granted")
    data class FloorGranted(
        val channel_id: String,
        val speaker_id: String,
        val speaker_name: String
    ) : SignalingMessage()

    @Serializable
    @SerialName("floor_denied")
    data class FloorDenied(
        val channel_id: String,
        val current_speaker_id: String
    ) : SignalingMessage()

    @Serializable
    @SerialName("floor_released")
    data class FloorReleased(val channel_id: String) : SignalingMessage()

    @Serializable
    @SerialName("floor_revoked")
    data class FloorRevoked(val channel_id: String) : SignalingMessage()

    @Serializable
    @SerialName("user_joined")
    data class UserJoined(
        val channel_id: String,
        val user_id: String,
        val username: String
    ) : SignalingMessage()

    @Serializable
    @SerialName("user_left")
    data class UserLeft(
        val channel_id: String,
        val user_id: String
    ) : SignalingMessage()

    @Serializable
    data class ChannelUser(
        val user_id: String,
        val username: String,
        val is_speaking: Boolean
    )

    @Serializable
    @SerialName("channel_users")
    data class ChannelUsers(
        val channel_id: String,
        val users: List<ChannelUser>
    ) : SignalingMessage()

    @Serializable
    @SerialName("ping")
    object Ping : SignalingMessage()

    @Serializable
    @SerialName("pong")
    object Pong : SignalingMessage()

    @Serializable
    @SerialName("error")
    data class Error(val message: String) : SignalingMessage()
}
