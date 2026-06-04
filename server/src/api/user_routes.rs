use axum::{
    extract::{Json, Path, Query, State},
};
use serde::Deserialize;
use uuid::Uuid;

use crate::auth::middleware::AuthUser;
use crate::db::UserRepo;
use crate::models::UserPublic;
use crate::AppError;
use crate::AppState;

#[derive(Debug, Deserialize)]
pub struct SearchQuery {
    pub q: String,
    pub limit: Option<i64>,
}

#[derive(Debug, Deserialize)]
pub struct UpdateUserRequest {
    pub display_name: Option<String>,
    pub avatar_url: Option<String>,
}

/// GET /api/v1/users/:id
pub async fn get_user(
    State(state): State<AppState>,
    _auth: AuthUser,
    Path(id): Path<Uuid>,
) -> Result<Json<UserPublic>, AppError> {
    let user = UserRepo::find_by_id(&state.db, id)
        .await?
        .ok_or_else(|| AppError::NotFound("User not found".to_string()))?;

    Ok(Json(user.into()))
}

/// PATCH /api/v1/users/:id
pub async fn update_user(
    State(state): State<AppState>,
    auth: AuthUser,
    Path(id): Path<Uuid>,
    Json(req): Json<UpdateUserRequest>,
) -> Result<Json<UserPublic>, AppError> {
    // Only allow self-update or admin
    if auth.claims.sub != id && auth.claims.role != "admin" {
        return Err(AppError::Forbidden("Cannot update other users".to_string()));
    }

    let user = UserRepo::update(
        &state.db,
        id,
        req.display_name.as_deref(),
        req.avatar_url.as_deref(),
    )
    .await?;

    Ok(Json(user.into()))
}

/// GET /api/v1/users/search?q=&limit=
pub async fn search_users(
    State(state): State<AppState>,
    _auth: AuthUser,
    Query(query): Query<SearchQuery>,
) -> Result<Json<Vec<UserPublic>>, AppError> {
    let limit = query.limit.unwrap_or(20).min(100);
    let users = UserRepo::search(&state.db, &query.q, limit).await?;

    Ok(Json(users.into_iter().map(|u| u.into()).collect()))
}
