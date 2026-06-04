use sqlx::PgPool;
use uuid::Uuid;

use crate::models::{Group, GroupMembership};
use crate::AppError;

/// Repository for group-related database operations.
pub struct GroupRepo;

impl GroupRepo {
    /// Create a new group and add the creator as owner.
    pub async fn create(
        pool: &PgPool,
        name: &str,
        description: Option<&str>,
        is_public: bool,
        owner_id: Uuid,
    ) -> Result<Group, AppError> {
        // Generate a random invite code
        let invite_code = generate_invite_code();

        let mut tx = pool.begin().await?;

        let group = sqlx::query_as::<_, Group>(
            r#"
            INSERT INTO groups (name, description, is_public, owner_id, invite_code)
            VALUES ($1, $2, $3, $4, $5)
            RETURNING *
            "#,
        )
        .bind(name)
        .bind(description)
        .bind(is_public)
        .bind(owner_id)
        .bind(&invite_code)
        .fetch_one(&mut *tx)
        .await?;

        // Add owner as admin member
        sqlx::query(
            r#"
            INSERT INTO group_memberships (user_id, group_id, role)
            VALUES ($1, $2, 'admin')
            "#,
        )
        .bind(owner_id)
        .bind(group.id)
        .execute(&mut *tx)
        .await?;

        // Create a default "General" voice channel
        sqlx::query(
            r#"
            INSERT INTO channels (group_id, name, description, channel_type)
            VALUES ($1, 'General', 'Default voice channel', 'voice')
            "#,
        )
        .bind(group.id)
        .execute(&mut *tx)
        .await?;

        tx.commit().await?;

        Ok(group)
    }

    /// List all groups the user is a member of.
    pub async fn list_for_user(pool: &PgPool, user_id: Uuid) -> Result<Vec<Group>, AppError> {
        let groups = sqlx::query_as::<_, Group>(
            r#"
            SELECT g.* FROM groups g
            INNER JOIN group_memberships gm ON g.id = gm.group_id
            WHERE gm.user_id = $1
            ORDER BY g.name
            "#,
        )
        .bind(user_id)
        .fetch_all(pool)
        .await?;

        Ok(groups)
    }

    /// List public groups (for discovery).
    pub async fn list_public(pool: &PgPool, limit: i64) -> Result<Vec<Group>, AppError> {
        let groups = sqlx::query_as::<_, Group>(
            r#"
            SELECT * FROM groups
            WHERE is_public = true
            ORDER BY created_at DESC
            LIMIT $1
            "#,
        )
        .bind(limit)
        .fetch_all(pool)
        .await?;

        Ok(groups)
    }

    /// Find a group by ID.
    pub async fn find_by_id(pool: &PgPool, id: Uuid) -> Result<Option<Group>, AppError> {
        let group = sqlx::query_as::<_, Group>("SELECT * FROM groups WHERE id = $1")
            .bind(id)
            .fetch_optional(pool)
            .await?;

        Ok(group)
    }

    /// Find a group by invite code.
    pub async fn find_by_invite_code(pool: &PgPool, code: &str) -> Result<Option<Group>, AppError> {
        let group = sqlx::query_as::<_, Group>(
            "SELECT * FROM groups WHERE invite_code = $1",
        )
        .bind(code)
        .fetch_optional(pool)
        .await?;

        Ok(group)
    }

    /// Join a group.
    pub async fn join(
        pool: &PgPool,
        user_id: Uuid,
        group_id: Uuid,
    ) -> Result<GroupMembership, AppError> {
        let membership = sqlx::query_as::<_, GroupMembership>(
            r#"
            INSERT INTO group_memberships (user_id, group_id, role)
            VALUES ($1, $2, 'member')
            ON CONFLICT (user_id, group_id) DO NOTHING
            RETURNING *
            "#,
        )
        .bind(user_id)
        .bind(group_id)
        .fetch_optional(pool)
        .await?
        .ok_or_else(|| AppError::Conflict("Already a member".to_string()))?;

        Ok(membership)
    }

    /// Leave a group.
    pub async fn leave(pool: &PgPool, user_id: Uuid, group_id: Uuid) -> Result<(), AppError> {
        let result = sqlx::query(
            "DELETE FROM group_memberships WHERE user_id = $1 AND group_id = $2",
        )
        .bind(user_id)
        .bind(group_id)
        .execute(pool)
        .await?;

        if result.rows_affected() == 0 {
            return Err(AppError::NotFound("Not a member".to_string()));
        }

        Ok(())
    }

    /// Get members of a group.
    pub async fn get_members(
        pool: &PgPool,
        group_id: Uuid,
    ) -> Result<Vec<GroupMembership>, AppError> {
        let members = sqlx::query_as::<_, GroupMembership>(
            "SELECT * FROM group_memberships WHERE group_id = $1 ORDER BY joined_at",
        )
        .bind(group_id)
        .fetch_all(pool)
        .await?;

        Ok(members)
    }

    /// Check if user is a member of the group.
    pub async fn is_member(pool: &PgPool, user_id: Uuid, group_id: Uuid) -> Result<bool, AppError> {
        let exists = sqlx::query_scalar::<_, bool>(
            "SELECT EXISTS(SELECT 1 FROM group_memberships WHERE user_id = $1 AND group_id = $2)",
        )
        .bind(user_id)
        .bind(group_id)
        .fetch_one(pool)
        .await?;

        Ok(exists)
    }

    /// Get the user's role in a group.
    pub async fn get_member_role(
        pool: &PgPool,
        user_id: Uuid,
        group_id: Uuid,
    ) -> Result<Option<String>, AppError> {
        let role = sqlx::query_scalar::<_, String>(
            "SELECT role FROM group_memberships WHERE user_id = $1 AND group_id = $2",
        )
        .bind(user_id)
        .bind(group_id)
        .fetch_optional(pool)
        .await?;

        Ok(role)
    }

    /// Delete a group (owner only).
    pub async fn delete(pool: &PgPool, id: Uuid) -> Result<(), AppError> {
        sqlx::query("DELETE FROM groups WHERE id = $1")
            .bind(id)
            .execute(pool)
            .await?;

        Ok(())
    }
}

/// Generate a random 8-character invite code.
fn generate_invite_code() -> String {
    use rand::Rng;
    const CHARSET: &[u8] = b"ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    let mut rng = rand::thread_rng();
    (0..8)
        .map(|_| {
            let idx = rng.gen_range(0..CHARSET.len());
            CHARSET[idx] as char
        })
        .collect()
}
