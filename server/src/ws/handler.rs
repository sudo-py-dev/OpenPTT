use axum::{
    extract::{
        ws::{Message, WebSocket, WebSocketUpgrade},
        State,
    },
    response::IntoResponse,
};
use futures::{SinkExt, StreamExt};
use std::collections::HashMap;
use std::sync::Arc;
use tokio::sync::{broadcast, RwLock};
use uuid::Uuid;

use crate::auth::jwt::verify_access_token;
use crate::ws::floor_control::FloorControl;
use crate::ws::messages::SignalingMessage;
use crate::AppState;

/// Connected client info
#[derive(Debug, Clone)]
pub struct ConnectedClient {
    pub connection_id: Uuid,
    pub user_id: Uuid,
    pub username: String,
    pub channel_id: Option<Uuid>,
    pub tx: broadcast::Sender<String>,
}

/// Shared state for all WebSocket connections (keyed by connection_id)
pub type Clients = Arc<RwLock<HashMap<Uuid, ConnectedClient>>>;

/// Create a new shared clients map
pub fn new_clients() -> Clients {
    Arc::new(RwLock::new(HashMap::new()))
}

/// Shared lazy-static clients map (initialized once)
static CLIENTS: std::sync::LazyLock<Clients> = std::sync::LazyLock::new(new_clients);

/// WebSocket upgrade handler
/// GET /ws
pub async fn ws_handler(
    ws: WebSocketUpgrade,
    State(state): State<AppState>,
) -> impl IntoResponse {
    let clients = CLIENTS.clone();
    ws.on_upgrade(move |socket| handle_socket(socket, state, clients))
}

/// Handle a single WebSocket connection
async fn handle_socket(socket: WebSocket, state: AppState, clients: Clients) {
    let (mut ws_tx, mut ws_rx) = socket.split();

    // Wait for auth message
    let auth_msg = match ws_rx.next().await {
        Some(Ok(Message::Text(text))) => text,
        _ => return,
    };

    // Parse and verify auth
    let msg: SignalingMessage = match serde_json::from_str(&auth_msg) {
        Ok(msg) => msg,
        Err(_) => return,
    };

    let (user_id, username) = match msg {
        SignalingMessage::Auth { token } => {
            match verify_access_token(&token, &state.config.auth) {
                Ok(data) => (data.claims.sub, data.claims.username),
                Err(_) => {
                    let _ = ws_tx
                        .send(Message::Text(
                            serde_json::to_string(&SignalingMessage::Error {
                                message: "Invalid token".to_string(),
                            })
                            .unwrap()
                            .into(),
                        ))
                        .await;
                    return;
                }
            }
        }
        _ => return,
    };

    // Send auth OK
    let auth_ok = serde_json::to_string(&SignalingMessage::AuthOk {
        user_id,
        username: username.clone(),
    })
    .unwrap();
    if ws_tx.send(Message::Text(auth_ok.into())).await.is_err() {
        return;
    }

    // Register client
    let connection_id = Uuid::new_v4();
    let (tx, mut rx) = broadcast::channel(256);
    {
        let mut clients_guard = clients.write().await;
        clients_guard.insert(
            connection_id,
            ConnectedClient {
                connection_id,
                user_id,
                username: username.clone(),
                channel_id: None,
                tx: tx.clone(),
            },
        );
    }

    tracing::info!("Client connected: {} ({}) [Conn: {}]", username, user_id, connection_id);

    // Spawn task to forward broadcast messages to this client's WebSocket
    let mut send_task = tokio::spawn(async move {
        while let Ok(msg) = rx.recv().await {
            if ws_tx.send(Message::Text(msg.into())).await.is_err() {
                break;
            }
        }
    });

    // Process incoming messages
    let mut recv_task = tokio::spawn({
        let clients = clients.clone();
        let state = state.clone();
        let user_id = user_id;
        let username = username.clone();
        let connection_id = connection_id;

        async move {
            while let Some(Ok(msg)) = ws_rx.next().await {
                match msg {
                    Message::Text(text) => {
                        if let Ok(signaling_msg) =
                            serde_json::from_str::<SignalingMessage>(&text)
                        {
                            handle_message(
                                &state,
                                &clients,
                                connection_id,
                                user_id,
                                &username,
                                signaling_msg,
                            )
                            .await;
                        }
                    }
                    Message::Close(_) => break,
                    _ => {}
                }
            }
        }
    });

    // Wait for either task to finish
    tokio::select! {
        _ = &mut send_task => recv_task.abort(),
        _ = &mut recv_task => send_task.abort(),
    }

    // Cleanup: remove client and release any held floor
    {
        let mut clients_guard = clients.write().await;
        if let Some(client) = clients_guard.remove(&connection_id) {
            if let Some(channel_id) = client.channel_id {
                let mut redis = state.redis.clone();
                let _ = FloorControl::release(&mut redis, channel_id, user_id).await;

                // Notify others in channel
                let mut redis = state.redis.clone();
                publish_ws_event(
                    &mut redis,
                    channel_id,
                    SignalingMessage::UserLeft {
                        channel_id,
                        user_id,
                    },
                    Some(user_id),
                ).await;
            }
        }
    }

    tracing::info!("Client disconnected: {} ({}) [Conn: {}]", username, user_id, connection_id);
}

/// Handle a parsed signaling message from a client
async fn handle_message(
    state: &AppState,
    clients: &Clients,
    connection_id: Uuid,
    user_id: Uuid,
    username: &str,
    msg: SignalingMessage,
) {
    match msg {
        SignalingMessage::JoinChannel { channel_id } => {
            let mut users_in_channel = Vec::new();
            let mut tx_opt = None;
            
            // Update client's channel
            {
                let mut clients_guard = clients.write().await;
                if let Some(client) = clients_guard.get_mut(&connection_id) {
                    client.channel_id = Some(channel_id);
                    tx_opt = Some(client.tx.clone());
                }

                // Get list of current users in channel
                let mut seen_users = std::collections::HashSet::new();
                for client in clients_guard.values() {
                    if client.channel_id == Some(channel_id) && seen_users.insert(client.user_id) {
                        users_in_channel.push(crate::ws::messages::ChannelUser {
                            user_id: client.user_id,
                            username: client.username.clone(),
                            is_speaking: false,
                        });
                    }
                }
            } // drop guard

            // Send list of users to the joined client
            if let Some(tx) = tx_opt {
                let _ = tx.send(
                    serde_json::to_string(&SignalingMessage::ChannelUsers {
                        channel_id,
                        users: users_in_channel,
                    })
                    .unwrap()
                );
            }

            // Notify others
            let mut redis_conn = state.redis.clone();
            publish_ws_event(
                &mut redis_conn,
                channel_id,
                SignalingMessage::UserJoined {
                    channel_id,
                    user_id,
                    username: username.to_string(),
                },
                Some(user_id),
            ).await;
        }

        SignalingMessage::LeaveChannel { channel_id } => {
            {
                let mut clients_guard = clients.write().await;
                if let Some(client) = clients_guard.get_mut(&connection_id) {
                    client.channel_id = None;
                }

                broadcast_to_channel(
                    &clients_guard,
                    channel_id,
                    &SignalingMessage::UserLeft {
                        channel_id,
                        user_id,
                    },
                    Some(user_id),
                );
            }

            // Release floor if held
            let mut redis = state.redis.clone();
            let _ = FloorControl::release(&mut redis, channel_id, user_id).await;
        }

        SignalingMessage::PttDown { channel_id, priority } => {
            let mut redis = state.redis.clone();
            let max_duration = state.config.audio.max_talk_duration_secs;

            if priority.unwrap_or(false) {
                // Priority override (admin/moderator)
                if let Ok(Some(prev_speaker)) =
                    FloorControl::force_acquire(&mut redis, channel_id, user_id, max_duration).await
                {
                    let clients_guard = clients.read().await;
                    // Notify previous speaker
                    for client in clients_guard.values() {
                        if client.user_id == prev_speaker {
                            let _ = client.tx.send(
                                serde_json::to_string(&SignalingMessage::FloorRevoked { channel_id })
                                    .unwrap(),
                            );
                        }
                    }
                }

                publish_ws_event(
                    &mut redis,
                    channel_id,
                    SignalingMessage::FloorGranted {
                        channel_id,
                        speaker_id: user_id,
                        speaker_name: username.to_string(),
                    },
                    None,
                ).await;
                trigger_fcm_for_channel(state, channel_id, user_id, username).await;
            } else {
                // Normal floor request
                match FloorControl::acquire(&mut redis, channel_id, user_id, max_duration).await {
                    Ok(true) => {
                        publish_ws_event(
                            &mut redis,
                            channel_id,
                            SignalingMessage::FloorGranted {
                                channel_id,
                                speaker_id: user_id,
                                speaker_name: username.to_string(),
                            },
                            None,
                        ).await;
                        trigger_fcm_for_channel(state, channel_id, user_id, username).await;
                    }
                    Ok(false) => {
                        // Floor is busy
                        if let Ok(Some(current)) =
                            FloorControl::current_speaker(&mut redis, channel_id).await
                        {
                            let clients_guard = clients.read().await;
                            if let Some(client) = clients_guard.get(&connection_id) {
                                let _ = client.tx.send(
                                    serde_json::to_string(&SignalingMessage::FloorDenied {
                                        channel_id,
                                        current_speaker_id: current,
                                    })
                                    .unwrap(),
                                );
                            }
                        }
                    }
                    Err(e) => {
                        tracing::error!("Floor acquire error: {}", e);
                    }
                }
            }
        }

        SignalingMessage::PttUp { channel_id } => {
            let mut redis = state.redis.clone();
            if let Ok(true) = FloorControl::release(&mut redis, channel_id, user_id).await {
                publish_ws_event(
                    &mut redis,
                    channel_id,
                    SignalingMessage::FloorReleased { channel_id },
                    None,
                ).await;
            }
        }

        SignalingMessage::Ping => {
            let clients_guard = clients.read().await;
            if let Some(client) = clients_guard.get(&connection_id) {
                let _ = client.tx.send(
                    serde_json::to_string(&SignalingMessage::Pong).unwrap(),
                );
            }
        }

        _ => {} // Ignore server-to-client messages
    }
}

/// Publish an event to Redis PubSub for all instances
async fn publish_ws_event(
    redis: &mut redis::aio::ConnectionManager,
    channel_id: Uuid,
    msg: SignalingMessage,
    exclude: Option<Uuid>,
) {
    if let Ok(payload) = serde_json::to_string(&(channel_id, msg, exclude)) {
        let _: Result<(), _> = redis::cmd("PUBLISH")
            .arg("ws_events")
            .arg(payload)
            .query_async(redis)
            .await;
    }
}

/// Start Redis PubSub subscriber loop
pub async fn start_redis_subscriber(client: redis::Client) {
    if let Ok(mut pubsub) = client.get_async_pubsub().await {
        if pubsub.subscribe("ws_events").await.is_ok() {
            tracing::info!("Subscribed to Redis ws_events pubsub");
            let mut stream = pubsub.on_message();
            while let Some(msg) = stream.next().await {
                if let Ok(payload) = msg.get_payload::<String>() {
                    if let Ok((channel_id, sig_msg, exclude)) = 
                        serde_json::from_str::<(Uuid, SignalingMessage, Option<Uuid>)>(&payload) 
                    {
                        let clients_guard = CLIENTS.read().await;
                        local_broadcast_to_channel(&clients_guard, channel_id, &sig_msg, exclude);
                    }
                }
            }
        }
    }
    tracing::error!("Redis PubSub subscriber stopped!");
}

/// Broadcast a message to all local clients in a channel
fn local_broadcast_to_channel(
    clients: &HashMap<Uuid, ConnectedClient>,
    channel_id: Uuid,
    msg: &SignalingMessage,
    exclude: Option<Uuid>,
) {
    if let Ok(json) = serde_json::to_string(msg) {
        for (uid, client) in clients.iter() {
            if client.channel_id == Some(channel_id) && exclude.map_or(true, |ex| *uid != ex) {
                let _ = client.tx.send(json.clone());
            }
        }
    }
}

async fn trigger_fcm_for_channel(state: &AppState, channel_id: Uuid, speaker_id: Uuid, speaker_name: &str) {
    let rows: Result<Vec<(Option<String>,)>, _> = sqlx::query_as(
        r#"
        SELECT u.fcm_token 
        FROM users u
        JOIN memberships m ON u.id = m.user_id
        JOIN channels c ON m.group_id = c.group_id
        WHERE c.id = $1 AND u.id != $2 AND u.fcm_token IS NOT NULL
        "#
    )
    .bind(channel_id)
    .bind(speaker_id)
    .fetch_all(&state.db)
    .await;

    if let Ok(users) = rows {
        for (fcm_token_opt,) in users {
            if let Some(token) = fcm_token_opt {
                let speaker_name = speaker_name.to_string();
                tokio::spawn(async move {
                    let _ = crate::fcm::send_fcm_message(&token, &channel_id.to_string(), &speaker_name).await;
                });
            }
        }
    }
}
