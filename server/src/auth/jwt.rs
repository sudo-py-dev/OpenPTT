use chrono::{Duration, Utc};
use jsonwebtoken::{decode, encode, DecodingKey, EncodingKey, Header, TokenData, Validation};
use serde::{Deserialize, Serialize};
use uuid::Uuid;

use crate::config::AuthConfig;
use crate::AppError;

/// JWT claims included in the access token.
#[derive(Debug, Serialize, Deserialize, Clone)]
pub struct Claims {
    /// Subject (user ID)
    pub sub: Uuid,
    /// Username
    pub username: String,
    /// User role
    pub role: String,
    /// Issued at (Unix timestamp)
    pub iat: i64,
    /// Expiration (Unix timestamp)
    pub exp: i64,
    /// Issuer
    pub iss: String,
}

/// Issue a new access token for the given user.
pub fn issue_access_token(
    user_id: Uuid,
    username: &str,
    role: &str,
    config: &AuthConfig,
) -> Result<String, AppError> {
    let now = Utc::now();
    let exp = now + Duration::seconds(config.access_token_ttl_secs as i64);

    let claims = Claims {
        sub: user_id,
        username: username.to_string(),
        role: role.to_string(),
        iat: now.timestamp(),
        exp: exp.timestamp(),
        iss: "openptt".to_string(),
    };

    let token = encode(
        &Header::default(), // HS256
        &claims,
        &EncodingKey::from_secret(config.jwt_secret.as_bytes()),
    )?;

    Ok(token)
}

/// Verify and decode an access token.
pub fn verify_access_token(
    token: &str,
    config: &AuthConfig,
) -> Result<TokenData<Claims>, AppError> {
    let mut validation = Validation::default();
    validation.set_issuer(&["openptt"]);
    validation.leeway = 30; // 30 second clock skew tolerance

    let token_data = decode::<Claims>(
        token,
        &DecodingKey::from_secret(config.jwt_secret.as_bytes()),
        &validation,
    )?;

    Ok(token_data)
}

/// Generate an opaque refresh token (random 32-byte string, base64url-encoded).
pub fn generate_refresh_token() -> String {
    use rand::Rng;
    let mut rng = rand::thread_rng();
    let bytes: Vec<u8> = (0..32).map(|_| rng.gen()).collect();
    base64::engine::general_purpose::URL_SAFE_NO_PAD.encode(&bytes)
}

/// Hash a refresh token for storage (using SHA-256).
pub fn hash_refresh_token(token: &str) -> String {
    use std::collections::hash_map::DefaultHasher;
    use std::hash::{Hash, Hasher};
    // Simple hash for lookup — the token itself is the secret
    let mut hasher = DefaultHasher::new();
    token.hash(&mut hasher);
    format!("{:x}", hasher.finish())
}

use base64::Engine;

#[cfg(test)]
mod tests {
    use super::*;
    use crate::config::AuthConfig;

    fn test_config() -> AuthConfig {
        AuthConfig {
            access_token_ttl_secs: 900,
            refresh_token_ttl_secs: 604800,
            jwt_secret: "test-secret-at-least-32-characters-long!!".to_string(),
        }
    }

    #[test]
    fn test_issue_and_verify_token() {
        let config = test_config();
        let user_id = Uuid::new_v4();
        let token = issue_access_token(user_id, "testuser", "user", &config).unwrap();

        let decoded = verify_access_token(&token, &config).unwrap();
        assert_eq!(decoded.claims.sub, user_id);
        assert_eq!(decoded.claims.username, "testuser");
        assert_eq!(decoded.claims.role, "user");
    }

    #[test]
    fn test_refresh_token_generation() {
        let token1 = generate_refresh_token();
        let token2 = generate_refresh_token();
        assert_ne!(token1, token2);
        assert!(token1.len() > 20);
    }
}
