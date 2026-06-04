use axum::http::StatusCode;
use axum::response::{IntoResponse, Response};
use serde::Serialize;

/// Application-wide error type used across all layers.
#[derive(Debug, thiserror::Error)]
pub enum AppError {
    #[error("Authentication failed: {0}")]
    Unauthorized(String),

    #[error("Forbidden: {0}")]
    Forbidden(String),

    #[error("Not found: {0}")]
    NotFound(String),

    #[error("Conflict: {0}")]
    Conflict(String),

    #[error("Validation error: {0}")]
    Validation(String),

    #[error("Rate limited")]
    RateLimited,

    #[error("Internal error: {0}")]
    Internal(String),

    #[error(transparent)]
    Database(#[from] sqlx::Error),

    #[error(transparent)]
    Redis(#[from] redis::RedisError),

    #[error(transparent)]
    Jwt(#[from] jsonwebtoken::errors::Error),
}

/// JSON error response body.
#[derive(Serialize)]
struct ErrorResponse {
    error: String,
    #[serde(skip_serializing_if = "Option::is_none")]
    details: Option<String>,
}

impl IntoResponse for AppError {
    fn into_response(self) -> Response {
        let (status, error_msg, details) = match &self {
            AppError::Unauthorized(msg) => (StatusCode::UNAUTHORIZED, "unauthorized", Some(msg.clone())),
            AppError::Forbidden(msg) => (StatusCode::FORBIDDEN, "forbidden", Some(msg.clone())),
            AppError::NotFound(msg) => (StatusCode::NOT_FOUND, "not_found", Some(msg.clone())),
            AppError::Conflict(msg) => (StatusCode::CONFLICT, "conflict", Some(msg.clone())),
            AppError::Validation(msg) => (StatusCode::BAD_REQUEST, "validation_error", Some(msg.clone())),
            AppError::RateLimited => (StatusCode::TOO_MANY_REQUESTS, "rate_limited", None),
            AppError::Internal(msg) => {
                tracing::error!("Internal error: {}", msg);
                (StatusCode::INTERNAL_SERVER_ERROR, "internal_error", None)
            }
            AppError::Database(e) => {
                tracing::error!("Database error: {}", e);
                // Check for unique constraint violations
                if let sqlx::Error::Database(db_err) = e {
                    if db_err.code().as_deref() == Some("23505") {
                        return (
                            StatusCode::CONFLICT,
                            axum::Json(ErrorResponse {
                                error: "conflict".to_string(),
                                details: Some("Resource already exists".to_string()),
                            }),
                        )
                            .into_response();
                    }
                }
                (StatusCode::INTERNAL_SERVER_ERROR, "internal_error", None)
            }
            AppError::Redis(e) => {
                tracing::error!("Redis error: {}", e);
                (StatusCode::INTERNAL_SERVER_ERROR, "internal_error", None)
            }
            AppError::Jwt(_) => (StatusCode::UNAUTHORIZED, "invalid_token", None),
        };

        (
            status,
            axum::Json(ErrorResponse {
                error: error_msg.to_string(),
                details,
            }),
        )
            .into_response()
    }
}
