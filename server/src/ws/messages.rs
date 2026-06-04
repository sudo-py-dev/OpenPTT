use serde::{Deserialize, Serialize};
use uuid::Uuid;

/// Signaling messages exchanged over WebSocket.
#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(tag = "type", rename_all = "snake_case")]
pub enum SignalingMessage {
    /// Client authenticates with JWT
    Auth { token: String },

    /// Server confirms authentication
    AuthOk { user_id: Uuid, username: String },

    /// Client joins a channel
    JoinChannel { channel_id: Uuid },

    /// Client leaves a channel
    LeaveChannel { channel_id: Uuid },

    /// Client presses PTT button
    PttDown { channel_id: Uuid, priority: Option<bool> },

    /// Client releases PTT button
    PttUp { channel_id: Uuid },

    /// Server grants floor to a user
    FloorGranted { channel_id: Uuid, speaker_id: Uuid, speaker_name: String },

    /// Server denies floor (someone else is speaking)
    FloorDenied { channel_id: Uuid, current_speaker_id: Uuid },

    /// Server releases floor
    FloorReleased { channel_id: Uuid },

    /// Server revokes floor from a user (priority override)
    FloorRevoked { channel_id: Uuid },

    /// Presence update: user joined channel
    UserJoined { channel_id: Uuid, user_id: Uuid, username: String },

    /// Presence update: user left channel
    UserLeft { channel_id: Uuid, user_id: Uuid },

    /// Presence: list of users in channel
    ChannelUsers { channel_id: Uuid, users: Vec<ChannelUser> },

    /// Heartbeat ping
    Ping,

    /// Heartbeat pong
    Pong,

    /// Error message
    Error { message: String },
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct ChannelUser {
    pub user_id: Uuid,
    pub username: String,
    pub is_speaking: bool,
}
