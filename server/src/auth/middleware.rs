use axum::extract::FromRequestParts;
use axum::http::request::Parts;

use crate::auth::jwt::{verify_access_token, Claims};
use crate::AppError;
use crate::AppState;

/// Axum extractor that validates the JWT Bearer token and provides
/// the authenticated user's claims to handlers.
///
/// Usage in handlers:
/// ```ignore
/// async fn my_handler(auth: AuthUser) -> ... {
///     let user_id = auth.claims.sub;
/// }
/// ```
#[derive(Debug, Clone)]
pub struct AuthUser {
    pub claims: Claims,
}

impl FromRequestParts<AppState> for AuthUser {
    type Rejection = AppError;

    async fn from_request_parts(
        parts: &mut Parts,
        state: &AppState,
    ) -> Result<Self, Self::Rejection> {
        // Extract the Authorization header
        let auth_header = parts
            .headers
            .get("authorization")
            .and_then(|v| v.to_str().ok())
            .ok_or_else(|| AppError::Unauthorized("Missing authorization header".to_string()))?;

        // Expect "Bearer <token>"
        let token = auth_header
            .strip_prefix("Bearer ")
            .ok_or_else(|| AppError::Unauthorized("Invalid authorization format".to_string()))?;

        // Verify the JWT
        let token_data = verify_access_token(token, &state.config.auth)?;

        Ok(AuthUser {
            claims: token_data.claims,
        })
    }
}
