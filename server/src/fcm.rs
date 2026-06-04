use reqwest::Client;
use serde_json::json;
use std::sync::Arc;
use tokio::sync::OnceCell;
use yup_oauth2::{read_service_account_key, ServiceAccountAuthenticator, authenticator::Authenticator};
use hyper::client::HttpConnector;
use hyper_rustls::HttpsConnector;

use crate::AppError;

pub struct FcmClient {
    auth: Authenticator<HttpsConnector<HttpConnector>>,
    project_id: String,
    http: Client,
}

static FCM_CLIENT: OnceCell<Arc<FcmClient>> = OnceCell::const_new();

pub async fn init_fcm(json_path: &str) -> Result<(), AppError> {
    let sa_key = read_service_account_key(json_path)
        .await
        .map_err(|e| AppError::Internal(format!("Failed to read FCM key: {}", e)))?;
        
    let project_id = sa_key.project_id.clone().unwrap_or_default();

    let auth = ServiceAccountAuthenticator::builder(sa_key)
        .build()
        .await
        .map_err(|e| AppError::Internal(format!("Failed to build FCM auth: {}", e)))?;

    let client = FcmClient {
        auth,
        project_id,
        http: Client::new(),
    };

    let _ = FCM_CLIENT.set(Arc::new(client));
    Ok(())
}

pub async fn send_fcm_message(
    token: &str,
    channel_id: &str,
    speaker_name: &str,
) -> Result<(), AppError> {
    let client = match FCM_CLIENT.get() {
        Some(c) => c,
        None => return Ok(()), // FCM not configured
    };

    let token_obj = client.auth.token(&["https://www.googleapis.com/auth/firebase.messaging"])
        .await
        .map_err(|e| AppError::Internal(format!("FCM token error: {}", e)))?;

    let url = format!(
        "https://fcm.googleapis.com/v1/projects/{}/messages:send",
        client.project_id
    );

    let payload = json!({
        "message": {
            "token": token,
            "data": {
                "type": "ptt_start",
                "channel_id": channel_id,
                "speaker_name": speaker_name,
            },
            "android": {
                "priority": "high"
            }
        }
    });

    client.http.post(&url)
        .bearer_auth(token_obj.token().unwrap_or(""))
        .json(&payload)
        .send()
        .await
        .map_err(|e| AppError::Internal(format!("FCM request error: {}", e)))?;

    Ok(())
}
