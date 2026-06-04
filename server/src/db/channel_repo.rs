use sqlx::PgPool;
use uuid::Uuid;

use crate::models::Channel;
use crate::AppError;

/// Repository for channel-related database operations.
pub struct ChannelRepo;

impl ChannelRepo {
    /// Create a new channel in a group.
    pub async fn create(
        pool: &PgPool,
        group_id: Uuid,
        name: &str,
        description: Option<&str>,
        channel_type: &str,
        max_users: i32,
    ) -> Result<Channel, AppError> {
        let channel = sqlx::query_as::<_, Channel>(
            r#"
            INSERT INTO channels (group_id, name, description, channel_type, max_users)
            VALUES ($1, $2, $3, $4, $5)
            RETURNING *
            "#,
        )
        .bind(group_id)
        .bind(name)
        .bind(description)
        .bind(channel_type)
        .bind(max_users)
        .fetch_one(pool)
        .await?;

        Ok(channel)
    }

    /// List all channels in a group.
    pub async fn list_by_group(pool: &PgPool, group_id: Uuid) -> Result<Vec<Channel>, AppError> {
        let channels = sqlx::query_as::<_, Channel>(
            "SELECT * FROM channels WHERE group_id = $1 ORDER BY name",
        )
        .bind(group_id)
        .fetch_all(pool)
        .await?;

        Ok(channels)
    }

    /// Find a channel by ID.
    pub async fn find_by_id(pool: &PgPool, id: Uuid) -> Result<Option<Channel>, AppError> {
        let channel = sqlx::query_as::<_, Channel>("SELECT * FROM channels WHERE id = $1")
            .bind(id)
            .fetch_optional(pool)
            .await?;

        Ok(channel)
    }

    /// Delete a channel.
    pub async fn delete(pool: &PgPool, id: Uuid) -> Result<(), AppError> {
        let result = sqlx::query("DELETE FROM channels WHERE id = $1")
            .bind(id)
            .execute(pool)
            .await?;

        if result.rows_affected() == 0 {
            return Err(AppError::NotFound("Channel not found".to_string()));
        }

        Ok(())
    }
}
