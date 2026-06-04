use axum::{
    extract::{Json, Path, State},
    http::StatusCode,
    response::IntoResponse,
};
use serde::{Deserialize, Serialize};
use uuid::Uuid;
use validator::Validate;

use crate::auth::middleware::AuthUser;
use crate::db::{ChannelRepo, GroupRepo};
use crate::models::{Channel, Group, GroupMembership};
use crate::AppError;
use crate::AppState;

// ---------- Request / Response types ----------

#[derive(Debug, Deserialize, Validate)]
pub struct CreateGroupRequest {
    #[validate(length(min = 1, max = 100, message = "Name must be 1-100 characters"))]
    pub name: String,
    pub description: Option<String>,
    pub is_public: Option<bool>,
}

#[derive(Debug, Deserialize)]
pub struct JoinGroupRequest {
    pub invite_code: Option<String>,
}

#[derive(Debug, Serialize)]
pub struct GroupDetailResponse {
    #[serde(flatten)]
    pub group: Group,
    pub channels: Vec<Channel>,
    pub member_count: i64,
}

// ---------- Handlers ----------

/// POST /api/v1/groups
pub async fn create_group(
    State(state): State<AppState>,
    auth: AuthUser,
    Json(req): Json<CreateGroupRequest>,
) -> Result<impl IntoResponse, AppError> {
    req.validate()
        .map_err(|e| AppError::Validation(e.to_string()))?;

    let group = GroupRepo::create(
        &state.db,
        &req.name,
        req.description.as_deref(),
        req.is_public.unwrap_or(true),
        auth.claims.sub,
    )
    .await?;

    Ok((StatusCode::CREATED, Json(group)))
}

/// GET /api/v1/groups
pub async fn list_groups(
    State(state): State<AppState>,
    auth: AuthUser,
) -> Result<Json<Vec<Group>>, AppError> {
    let groups = GroupRepo::list_for_user(&state.db, auth.claims.sub).await?;
    Ok(Json(groups))
}

/// GET /api/v1/groups/:id
pub async fn get_group(
    State(state): State<AppState>,
    auth: AuthUser,
    Path(id): Path<Uuid>,
) -> Result<Json<GroupDetailResponse>, AppError> {
    let group = GroupRepo::find_by_id(&state.db, id)
        .await?
        .ok_or_else(|| AppError::NotFound("Group not found".to_string()))?;

    // Check membership (public groups can be viewed by anyone)
    if !group.is_public {
        let is_member = GroupRepo::is_member(&state.db, auth.claims.sub, id).await?;
        if !is_member {
            return Err(AppError::Forbidden("Not a member of this group".to_string()));
        }
    }

    let channels = ChannelRepo::list_by_group(&state.db, id).await?;
    let members = GroupRepo::get_members(&state.db, id).await?;

    Ok(Json(GroupDetailResponse {
        group,
        channels,
        member_count: members.len() as i64,
    }))
}

/// POST /api/v1/groups/:id/join
pub async fn join_group(
    State(state): State<AppState>,
    auth: AuthUser,
    Path(id): Path<Uuid>,
    Json(req): Json<JoinGroupRequest>,
) -> Result<StatusCode, AppError> {
    let group = GroupRepo::find_by_id(&state.db, id)
        .await?
        .ok_or_else(|| AppError::NotFound("Group not found".to_string()))?;

    // Private groups require invite code
    if !group.is_public {
        match (&req.invite_code, &group.invite_code) {
            (Some(provided), Some(expected)) if provided == expected => {}
            _ => return Err(AppError::Forbidden("Invalid invite code".to_string())),
        }
    }

    GroupRepo::join(&state.db, auth.claims.sub, id).await?;
    Ok(StatusCode::OK)
}

/// POST /api/v1/groups/:id/leave
pub async fn leave_group(
    State(state): State<AppState>,
    auth: AuthUser,
    Path(id): Path<Uuid>,
) -> Result<StatusCode, AppError> {
    GroupRepo::leave(&state.db, auth.claims.sub, id).await?;
    Ok(StatusCode::OK)
}

/// GET /api/v1/groups/:id/members
pub async fn get_members(
    State(state): State<AppState>,
    auth: AuthUser,
    Path(id): Path<Uuid>,
) -> Result<Json<Vec<GroupMembership>>, AppError> {
    // Check membership
    let is_member = GroupRepo::is_member(&state.db, auth.claims.sub, id).await?;
    if !is_member {
        return Err(AppError::Forbidden("Not a member".to_string()));
    }

    let members = GroupRepo::get_members(&state.db, id).await?;
    Ok(Json(members))
}

/// DELETE /api/v1/groups/:id
pub async fn delete_group(
    State(state): State<AppState>,
    auth: AuthUser,
    Path(id): Path<Uuid>,
) -> Result<StatusCode, AppError> {
    let group = GroupRepo::find_by_id(&state.db, id)
        .await?
        .ok_or_else(|| AppError::NotFound("Group not found".to_string()))?;

    // Only owner can delete
    if group.owner_id != Some(auth.claims.sub) && auth.claims.role != "admin" {
        return Err(AppError::Forbidden("Only the owner can delete this group".to_string()));
    }

    GroupRepo::delete(&state.db, id).await?;
    Ok(StatusCode::NO_CONTENT)
}
