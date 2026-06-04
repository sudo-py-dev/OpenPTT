use redis::AsyncCommands;
use uuid::Uuid;

use crate::AppError;

/// Floor control manages the half-duplex "who is talking" lock per channel.
/// Uses Redis SETNX for atomic lock acquisition.
pub struct FloorControl;

impl FloorControl {
    /// Try to acquire the floor for a channel.
    /// Returns Ok(true) if the floor was acquired, Ok(false) if someone else holds it.
    pub async fn acquire(
        redis: &mut redis::aio::ConnectionManager,
        channel_id: Uuid,
        user_id: Uuid,
        max_duration_secs: u64,
    ) -> Result<bool, AppError> {
        let key = format!("floor:{}", channel_id);
        let value = user_id.to_string();

        // SETNX — atomic set-if-not-exists
        let acquired: bool = redis::cmd("SET")
            .arg(&key)
            .arg(&value)
            .arg("NX") // Only set if not exists
            .arg("EX") // Set expiry
            .arg(max_duration_secs)
            .query_async(redis)
            .await
            .map(|v: Option<String>| v.is_some())
            .unwrap_or(false);

        Ok(acquired)
    }

    /// Release the floor for a channel.
    /// Only releases if the requesting user currently holds the floor.
    pub async fn release(
        redis: &mut redis::aio::ConnectionManager,
        channel_id: Uuid,
        user_id: Uuid,
    ) -> Result<bool, AppError> {
        let key = format!("floor:{}", channel_id);

        // Check current holder
        let current: Option<String> = redis.get(&key).await?;

        if current.as_deref() == Some(&user_id.to_string()) {
            redis.del::<_, ()>(&key).await?;
            Ok(true)
        } else {
            Ok(false)
        }
    }

    /// Force-acquire the floor (priority override by admin/moderator).
    pub async fn force_acquire(
        redis: &mut redis::aio::ConnectionManager,
        channel_id: Uuid,
        user_id: Uuid,
        max_duration_secs: u64,
    ) -> Result<Option<Uuid>, AppError> {
        let key = format!("floor:{}", channel_id);

        // Get current holder before overriding
        let previous: Option<String> = redis.get(&key).await?;
        let previous_id = previous.and_then(|s| Uuid::parse_str(&s).ok());

        // Force set
        redis
            .set_ex::<_, _, ()>(&key, user_id.to_string(), max_duration_secs)
            .await?;

        Ok(previous_id)
    }

    /// Check who currently holds the floor.
    pub async fn current_speaker(
        redis: &mut redis::aio::ConnectionManager,
        channel_id: Uuid,
    ) -> Result<Option<Uuid>, AppError> {
        let key = format!("floor:{}", channel_id);
        let holder: Option<String> = redis.get(&key).await?;

        Ok(holder.and_then(|s| Uuid::parse_str(&s).ok()))
    }
}
