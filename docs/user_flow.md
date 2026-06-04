# OpenPTT: Secure User Implementation Flow

This document outlines the complete lifecycle of a user interacting with the OpenPTT backend from registration to Push-To-Talk streaming, detailing the exact security mechanisms enforced at each step.

## 1. Registration & Provisioning
- **Client Action**: The Android client connects to the configurable server URL and submits a `POST /api/v1/auth/register` request containing a raw `username`, `password`, and `display_name`.
- **Server Action**: 
    - **Validation**: Enforces 8-128 chars for passwords and 3-50 chars for usernames to prevent overflow attacks and ensure complexity.
    - **Hashing**: Passes the password to a blocking `tokio::task` thread where `Argon2id` (a memory-hard hashing algorithm resistant to GPU cracking) generates a salt and hashes the password.
    - **Storage**: Safe `sqlx` parameterized queries insert the user, fully mitigating SQL Injection (SQLi) attacks.
    - **Tokens**: The server issues a short-lived **JWT Access Token** (RSA-256 signed) and an opaque **Refresh Token** which is hashed (SHA-256) before being stored in PostgreSQL.

## 2. Authentication (Login)
- **Client Action**: Submits `POST /api/v1/auth/login`.
- **Server Action**: 
    - Verifies the `Argon2id` hash. 
    - Rate limits (ConcurrencyLimitLayer) restrict brute-force attempts globally.
- **Client Storage**: The Android app stores the JWT securely using Jetpack Security's `EncryptedSharedPreferences`, utilizing the Android Keystore (hardware-backed encryption when available) to ensure tokens cannot be extracted by malicious apps.

## 3. WebSocket Signaling & Authorization
- **Connection Setup**: Client connects to `wss://server/ws`. 
- **Authentication Handshake**: The very first message sent must be `{"type": "auth", "token": "<JWT>"}`.
- **Server Validation**: The server parses the JWT using its RSA public key. If valid, it associates the TCP connection with the verified `user_id`. If invalid or not sent promptly, the socket is dropped.
- **Presence Tracking**: When a user emits a `JoinChannel` event, the server registers their presence and broadcasts a `UserJoined` event. The server also replies with the current active `ChannelUsers` list.

## 4. Floor Control (Redis)
- **The Problem**: Multiple users attempting to speak simultaneously in the same UDP channel causes garbled audio or race conditions.
- **The Solution (Atomic Locks)**: When a user taps the PTT button, the server executes an atomic `SETNX` (Set if Not eXists) command in Redis for `channel:<id>:speaker`.
    - If successful, the user is granted the floor (`FloorGranted`).
    - If failed, the user is rejected (`FloorDenied`).
- **Priority Override**: Server admins or moderators can issue a priority PTT request. The server will `DEL` the existing lock, notify the current speaker (`FloorRevoked`), and grant the lock to the priority user.

## 5. High-Performance UDP Audio Relay
- **Client Action**: The Android client starts the `AudioRecord` service, collecting 16kHz PCM audio. It wraps the audio in a custom 20-byte UDP header containing `channel_id`, `user_id`, `seq` (Sequence Number), and `timestamp`.
- **Server Action**:
    - **Validation**: The UDP server verifies that the incoming packet's `user_id` matches the user currently holding the Redis floor lock for that `channel_id`. (Unauthorized packets are silently dropped to prevent UDP flood DoS).
    - **Replay Protection**: The `seq` number is tracked to discard delayed or replayed packets.
    - **Fanout**: The server iterates through its `Arc<RwLock<HashMap>>` of connected clients for that channel and reflects the packet to their registered IP/Port tuples instantly.

## 6. Token Rotation (Security Hardening)
- **Refresh Mechanism**: When the short-lived JWT expires, the client uses the Refresh Token to get a new session via `POST /api/v1/auth/refresh`.
- **Hash Verification**: The server hashes the incoming token and checks the DB. Once used, the token is marked `revoked=true` to prevent replay attacks if a refresh token is leaked.
