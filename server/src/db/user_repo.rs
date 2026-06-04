use sqlx::PgPool;
use uuid::Uuid;

use crate::models::User;
use crate::AppError;

/// Repository for user-related database operations.
pub struct UserRepo;

impl UserRepo {
    /// Create a new user.
    pub async fn create(
        pool: &PgPool,
        username: &str,
        display_name: &str,
        password_hash: &str,
    ) -> Result<User, AppError> {
        let user = sqlx::query_as::<_, User>(
            r#"
            INSERT INTO users (username, display_name, password_hash)
            VALUES ($1, $2, $3)
            RETURNING *
            "#,
        )
        .bind(username)
        .bind(display_name)
        .bind(password_hash)
        .fetch_one(pool)
        .await?;

        Ok(user)
    }

    /// Find a user by username.
    pub async fn find_by_username(pool: &PgPool, username: &str) -> Result<Option<User>, AppError> {
        let user = sqlx::query_as::<_, User>(
            "SELECT * FROM users WHERE username = $1 AND is_active = true",
        )
        .bind(username)
        .fetch_optional(pool)
        .await?;

        Ok(user)
    }

    /// Find a user by ID.
    pub async fn find_by_id(pool: &PgPool, id: Uuid) -> Result<Option<User>, AppError> {
        let user = sqlx::query_as::<_, User>(
            "SELECT * FROM users WHERE id = $1 AND is_active = true",
        )
        .bind(id)
        .fetch_optional(pool)
        .await?;

        Ok(user)
    }

    /// Update user display name and/or avatar.
    pub async fn update(
        pool: &PgPool,
        id: Uuid,
        display_name: Option<&str>,
        avatar_url: Option<&str>,
    ) -> Result<User, AppError> {
        let user = sqlx::query_as::<_, User>(
            r#"
            UPDATE users
            SET display_name = COALESCE($2, display_name),
                avatar_url = COALESCE($3, avatar_url),
                updated_at = now()
            WHERE id = $1 AND is_active = true
            RETURNING *
            "#,
        )
        .bind(id)
        .bind(display_name)
        .bind(avatar_url)
        .fetch_optional(pool)
        .await?
        .ok_or_else(|| AppError::NotFound("User not found".to_string()))?;

        Ok(user)
    }

    /// Search users by username prefix.
    pub async fn search(pool: &PgPool, query: &str, limit: i64) -> Result<Vec<User>, AppError> {
        let users = sqlx::query_as::<_, User>(
            r#"
            SELECT * FROM users
            WHERE is_active = true AND username ILIKE $1
            ORDER BY username
            LIMIT $2
            "#,
        )
        .bind(format!("{}%", query))
        .bind(limit)
        .fetch_all(pool)
        .await?;

        Ok(users)
    }
}
