use axum::{
    extract::{Json, State},
    http::StatusCode,
    response::IntoResponse,
};
use chrono::{Duration, Utc};
use serde::{Deserialize, Serialize};
use validator::Validate;

use crate::auth::jwt::{
    generate_refresh_token, hash_refresh_token, issue_access_token,
};
use crate::auth::middleware::AuthUser;
use crate::auth::password::{hash_password, verify_password};
use crate::db::UserRepo;
use crate::models::UserPublic;
use crate::AppError;
use crate::AppState;

// ---------- Request / Response types ----------

#[derive(Debug, Deserialize, Validate)]
pub struct RegisterRequest {
    #[validate(length(min = 3, max = 50, message = "Username must be 3-50 characters"))]
    pub username: String,
    #[validate(length(min = 8, max = 128, message = "Password must be 8-128 characters"))]
    pub password: String,
    #[validate(length(min = 1, max = 100, message = "Display name must be 1-100 characters"))]
    pub display_name: String,
}

#[derive(Debug, Deserialize)]
pub struct LoginRequest {
    pub username: String,
    pub password: String,
}

#[derive(Debug, Deserialize)]
pub struct RefreshRequest {
    pub refresh_token: String,
}

#[derive(Debug, Deserialize)]
pub struct FcmTokenRequest {
    pub token: String,
}

#[derive(Debug, Serialize)]
pub struct AuthResponse {
    pub access_token: String,
    pub refresh_token: String,
    pub expires_in: u64,
    pub user: UserPublic,
}

// ---------- Handlers ----------

/// POST /api/v1/auth/register
pub async fn register(
    State(state): State<AppState>,
    Json(req): Json<RegisterRequest>,
) -> Result<impl IntoResponse, AppError> {
    // Validate input
    req.validate()
        .map_err(|e| AppError::Validation(e.to_string()))?;

    // Check if username already exists
    if UserRepo::find_by_username(&state.db, &req.username)
        .await?
        .is_some()
    {
        return Err(AppError::Conflict("Username already taken".to_string()));
    }

    // Hash password (CPU-intensive — run in blocking thread)
    let password = req.password.clone();
    let password_hash =
        tokio::task::spawn_blocking(move || hash_password(&password))
            .await
            .map_err(|e| AppError::Internal(format!("Task join error: {}", e)))??;

    // Create user
    let user = UserRepo::create(&state.db, &req.username, &req.display_name, &password_hash).await?;

    // Issue tokens
    let access_token = issue_access_token(user.id, &user.username, &user.role, &state.config.auth)?;
    let refresh_token = generate_refresh_token();
    let refresh_hash = hash_refresh_token(&refresh_token);

    // Store refresh token in database
    store_refresh_token(&state, user.id, &refresh_hash).await?;

    Ok((
        StatusCode::CREATED,
        Json(AuthResponse {
            access_token,
            refresh_token,
            expires_in: state.config.auth.access_token_ttl_secs,
            user: user.into(),
        }),
    ))
}

/// POST /api/v1/auth/login
pub async fn login(
    State(state): State<AppState>,
    Json(req): Json<LoginRequest>,
) -> Result<Json<AuthResponse>, AppError> {
    // Find user
    let user = UserRepo::find_by_username(&state.db, &req.username)
        .await?
        .ok_or_else(|| AppError::Unauthorized("Invalid credentials".to_string()))?;

    // Verify password (CPU-intensive — run in blocking thread)
    let password = req.password.clone();
    let hash = user.password_hash.clone();
    let valid = tokio::task::spawn_blocking(move || verify_password(&password, &hash))
        .await
        .map_err(|e| AppError::Internal(format!("Task join error: {}", e)))??;

    if !valid {
        return Err(AppError::Unauthorized("Invalid credentials".to_string()));
    }

    // Issue tokens
    let access_token = issue_access_token(user.id, &user.username, &user.role, &state.config.auth)?;
    let refresh_token = generate_refresh_token();
    let refresh_hash = hash_refresh_token(&refresh_token);

    // Store refresh token
    store_refresh_token(&state, user.id, &refresh_hash).await?;

    Ok(Json(AuthResponse {
        access_token,
        refresh_token,
        expires_in: state.config.auth.access_token_ttl_secs,
        user: user.into(),
    }))
}

/// POST /api/v1/auth/refresh
pub async fn refresh(
    State(state): State<AppState>,
    Json(req): Json<RefreshRequest>,
) -> Result<Json<AuthResponse>, AppError> {
    let old_hash = hash_refresh_token(&req.refresh_token);

    // Find and validate the refresh token
    let row = sqlx::query_as::<_, (uuid::Uuid, uuid::Uuid)>(
        r#"
        SELECT id, user_id FROM refresh_tokens
        WHERE token_hash = $1 AND revoked = false AND expires_at > now()
        "#,
    )
    .bind(&old_hash)
    .fetch_optional(&state.db)
    .await?
    .ok_or_else(|| AppError::Unauthorized("Invalid or expired refresh token".to_string()))?;

    let (token_id, user_id) = row;

    // Revoke old refresh token (rotation)
    sqlx::query("UPDATE refresh_tokens SET revoked = true WHERE id = $1")
        .bind(token_id)
        .execute(&state.db)
        .await?;

    // Get user
    let user = UserRepo::find_by_id(&state.db, user_id)
        .await?
        .ok_or_else(|| AppError::Unauthorized("User not found".to_string()))?;

    // Issue new tokens
    let access_token = issue_access_token(user.id, &user.username, &user.role, &state.config.auth)?;
    let new_refresh_token = generate_refresh_token();
    let new_refresh_hash = hash_refresh_token(&new_refresh_token);

    // Store new refresh token
    store_refresh_token(&state, user.id, &new_refresh_hash).await?;

    Ok(Json(AuthResponse {
        access_token,
        refresh_token: new_refresh_token,
        expires_in: state.config.auth.access_token_ttl_secs,
        user: user.into(),
    }))
}

/// POST /api/v1/auth/logout
pub async fn logout(
    State(state): State<AppState>,
    auth: AuthUser,
    Json(req): Json<RefreshRequest>,
) -> Result<StatusCode, AppError> {
    let hash = hash_refresh_token(&req.refresh_token);

    // Revoke the refresh token
    sqlx::query(
        "UPDATE refresh_tokens SET revoked = true WHERE token_hash = $1 AND user_id = $2",
    )
    .bind(&hash)
    .bind(auth.claims.sub)
    .execute(&state.db)
    .await?;

    Ok(StatusCode::OK)
}

/// GET /api/v1/auth/me
pub async fn me(
    State(state): State<AppState>,
    auth: AuthUser,
) -> Result<Json<UserPublic>, AppError> {
    let user = UserRepo::find_by_id(&state.db, auth.claims.sub)
        .await?
        .ok_or_else(|| AppError::NotFound("User not found".to_string()))?;

    Ok(Json(user.into()))
}

/// POST /api/v1/auth/fcm-token
pub async fn update_fcm_token(
    State(state): State<AppState>,
    auth: AuthUser,
    Json(req): Json<FcmTokenRequest>,
) -> Result<StatusCode, AppError> {
    sqlx::query("UPDATE users SET fcm_token = $1 WHERE id = $2")
        .bind(&req.token)
        .bind(auth.claims.sub)
        .execute(&state.db)
        .await?;

    Ok(StatusCode::OK)
}

// ---------- Helpers ----------

async fn store_refresh_token(
    state: &AppState,
    user_id: uuid::Uuid,
    token_hash: &str,
) -> Result<(), AppError> {
    let expires_at =
        Utc::now() + Duration::seconds(state.config.auth.refresh_token_ttl_secs as i64);

    sqlx::query(
        r#"
        INSERT INTO refresh_tokens (user_id, token_hash, expires_at)
        VALUES ($1, $2, $3)
        "#,
    )
    .bind(user_id)
    .bind(token_hash)
    .bind(expires_at)
    .execute(&state.db)
    .await?;

    Ok(())
}
