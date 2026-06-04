use std::collections::HashMap;
use std::net::SocketAddr;
use std::sync::Arc;
use tokio::net::UdpSocket;
use tokio::sync::RwLock;
use uuid::Uuid;

/// Audio packet header size in bytes
const HEADER_SIZE: usize = 44; // 16 (channel_id) + 16 (user_id) + 4 (seq) + 4 (timestamp) + 4 (flags)

/// Mapping: channel_id -> list of (user_id, socket_addr)
type ChannelClients = Arc<RwLock<HashMap<Uuid, Vec<(Uuid, SocketAddr)>>>>;

/// UDP audio relay server.
/// Receives Opus audio packets and fans them out to all other clients
/// in the same channel.
pub struct UdpRelay {
    socket: Arc<UdpSocket>,
    channel_clients: ChannelClients,
}

impl UdpRelay {
    /// Start the UDP relay on the given port.
    pub async fn start(port: u16) -> Result<Self, std::io::Error> {
        let addr = format!("0.0.0.0:{}", port);
        let socket = UdpSocket::bind(&addr).await?;
        tracing::info!("UDP audio relay listening on {}", addr);

        Ok(Self {
            socket: Arc::new(socket),
            channel_clients: Arc::new(RwLock::new(HashMap::new())),
        })
    }

    /// Run the relay loop (call this in a tokio::spawn)
    pub async fn run(&self) {
        let mut buf = vec![0u8; 1500]; // Max UDP payload

        loop {
            match self.socket.recv_from(&mut buf).await {
                Ok((len, src_addr)) => {
                    if len < HEADER_SIZE {
                        continue; // Invalid packet
                    }

                    // Parse header
                    let channel_id = Uuid::from_slice(&buf[0..16]).unwrap_or_default();
                    let user_id = Uuid::from_slice(&buf[16..32]).unwrap_or_default();
                    // seq and timestamp at [32..40], flags at [40..44]

                    // Register/update this client's address
                    {
                        let mut clients = self.channel_clients.write().await;
                        let entry = clients.entry(channel_id).or_insert_with(Vec::new);
                        // Update or add client
                        if let Some(client) = entry.iter_mut().find(|(uid, _)| *uid == user_id) {
                            client.1 = src_addr;
                        } else {
                            entry.push((user_id, src_addr));
                        }
                    }

                    if len >= HEADER_SIZE {
                        // We could do a debug/info log here but we don't want to spam for EVERY packet.
                        // However, per the user's request, we'll log every 50th packet roughly.
                        let seq = u32::from_be_bytes([buf[32], buf[33], buf[34], buf[35]]);
                        let flags = u32::from_be_bytes([buf[40], buf[41], buf[42], buf[43]]);
                        
                        if seq % 50 == 0 && flags == 0 {
                            tracing::info!("Relaying audio packet: seq={} size={} bytes from {} to channel {}", seq, len, user_id, channel_id);
                        }
                    }

                    // Fan-out to other clients in the same channel
                    let packet = buf[..len].to_vec();
                    let clients = self.channel_clients.read().await;
                    if let Some(channel_members) = clients.get(&channel_id) {
                        for (uid, addr) in channel_members {
                            if *uid != user_id {
                                let _ = self.socket.send_to(&packet, addr).await;
                            }
                        }
                    }
                }
                Err(e) => {
                    tracing::error!("UDP recv error: {}", e);
                }
            }
        }
    }

    /// Register a client for a channel (called when they join via WebSocket)
    pub async fn register_client(&self, channel_id: Uuid, user_id: Uuid, addr: SocketAddr) {
        let mut clients = self.channel_clients.write().await;
        let entry = clients.entry(channel_id).or_insert_with(Vec::new);
        entry.push((user_id, addr));
    }

    /// Remove a client from a channel
    pub async fn remove_client(&self, channel_id: Uuid, user_id: Uuid) {
        let mut clients = self.channel_clients.write().await;
        if let Some(entry) = clients.get_mut(&channel_id) {
            entry.retain(|(uid, _)| *uid != user_id);
            if entry.is_empty() {
                clients.remove(&channel_id);
            }
        }
    }
}
