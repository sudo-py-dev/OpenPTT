use axum::{
    extract::{Json, Path, State},
    http::StatusCode,
    response::IntoResponse,
};
use serde::Deserialize;
use uuid::Uuid;
use validator::Validate;

use crate::auth::middleware::AuthUser;
use crate::db::{ChannelRepo, GroupRepo};
use crate::models::Channel;
use crate::AppError;
use crate::AppState;

#[derive(Debug, Deserialize, Validate)]
pub struct CreateChannelRequest {
    #[validate(length(min = 1, max = 100, message = "Name must be 1-100 characters"))]
    pub name: String,
    pub description: Option<String>,
    pub channel_type: Option<String>,
    pub max_users: Option<i32>,
}

/// POST /api/v1/groups/:gid/channels
pub async fn create_channel(
    State(state): State<AppState>,
    auth: AuthUser,
    Path(gid): Path<Uuid>,
    Json(req): Json<CreateChannelRequest>,
) -> Result<impl IntoResponse, AppError> {
    req.validate()
        .map_err(|e| AppError::Validation(e.to_string()))?;

    // Check user is admin/moderator of the group
    let role = GroupRepo::get_member_role(&state.db, auth.claims.sub, gid)
        .await?
        .ok_or_else(|| AppError::Forbidden("Not a member".to_string()))?;

    if role != "admin" && role != "moderator" {
        return Err(AppError::Forbidden(
            "Only admins and moderators can create channels".to_string(),
        ));
    }

    let channel = ChannelRepo::create(
        &state.db,
        gid,
        &req.name,
        req.description.as_deref(),
        req.channel_type.as_deref().unwrap_or("voice"),
        req.max_users.unwrap_or(50),
    )
    .await?;

    Ok((StatusCode::CREATED, Json(channel)))
}

/// GET /api/v1/groups/:gid/channels
pub async fn list_channels(
    State(state): State<AppState>,
    auth: AuthUser,
    Path(gid): Path<Uuid>,
) -> Result<Json<Vec<Channel>>, AppError> {
    // Check membership
    let is_member = GroupRepo::is_member(&state.db, auth.claims.sub, gid).await?;
    if !is_member {
        return Err(AppError::Forbidden("Not a member".to_string()));
    }

    let channels = ChannelRepo::list_by_group(&state.db, gid).await?;
    Ok(Json(channels))
}

/// DELETE /api/v1/groups/:gid/channels/:id
pub async fn delete_channel(
    State(state): State<AppState>,
    auth: AuthUser,
    Path((gid, id)): Path<(Uuid, Uuid)>,
) -> Result<StatusCode, AppError> {
    // Check user is admin of the group
    let role = GroupRepo::get_member_role(&state.db, auth.claims.sub, gid)
        .await?
        .ok_or_else(|| AppError::Forbidden("Not a member".to_string()))?;

    if role != "admin" {
        return Err(AppError::Forbidden("Only admins can delete channels".to_string()));
    }

    ChannelRepo::delete(&state.db, id).await?;
    Ok(StatusCode::NO_CONTENT)
}
