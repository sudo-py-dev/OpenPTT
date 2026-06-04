use chrono::{DateTime, Utc};
use serde::{Deserialize, Serialize};
use sqlx::FromRow;
use uuid::Uuid;

/// Channel model matching the `channels` database table.
#[derive(Debug, Clone, FromRow, Serialize, Deserialize)]
pub struct Channel {
    pub id: Uuid,
    pub group_id: Uuid,
    pub name: String,
    pub description: Option<String>,
    pub channel_type: String,
    pub max_users: i32,
    pub created_at: DateTime<Utc>,
}
