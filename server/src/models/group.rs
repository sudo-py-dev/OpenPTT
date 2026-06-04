use chrono::{DateTime, Utc};
use serde::{Deserialize, Serialize};
use sqlx::FromRow;
use uuid::Uuid;

/// Group model matching the `groups` database table.
#[derive(Debug, Clone, FromRow, Serialize, Deserialize)]
pub struct Group {
    pub id: Uuid,
    pub name: String,
    pub description: Option<String>,
    pub avatar_url: Option<String>,
    pub owner_id: Option<Uuid>,
    pub is_public: bool,
    pub invite_code: Option<String>,
    pub max_members: i32,
    pub created_at: DateTime<Utc>,
}

/// Group membership model.
#[derive(Debug, Clone, FromRow, Serialize, Deserialize)]
pub struct GroupMembership {
    pub id: Uuid,
    pub user_id: Uuid,
    pub group_id: Uuid,
    pub role: String,
    pub joined_at: DateTime<Utc>,
}
