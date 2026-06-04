use reqwest::Client;
use serde_json::json;

// Helper to get base URL for testing
fn base_url() -> String {
    "http://127.0.0.1:8443".to_string()
}

#[tokio::test]
async fn test_sql_injection_register() {
    let client = Client::new();
    
    let res = client
        .post(format!("{}/api/v1/auth/register", base_url()))
        .json(&json!({
            "username": "admin' OR '1'='1",
            "password": "password123",
            "display_name": "Hacker"
        }))
        .send()
        .await
        .expect("Failed to send request");
        
    // Should NOT allow SQL injection. If it creates the user, the username will just literally be "admin' OR '1'='1",
    // but typically we want to see it successfully created without SQL errors, OR rejected by validation.
    // The username "admin' OR '1'='1" has special chars, our API currently allows it, but it shouldn't cause a 500 error.
    assert_ne!(res.status().as_u16(), 500, "SQL Injection caused a 500 Internal Server Error");
}

#[tokio::test]
async fn test_missing_auth_token_access() {
    let client = Client::new();
    
    // Attempt to access a protected route without a token
    let res = client
        .get(format!("{}/api/v1/auth/me", base_url()))
        .send()
        .await
        .expect("Failed to send request");
        
    // Should return 401 Unauthorized
    assert_eq!(res.status().as_u16(), 401, "API allowed access without auth token");
}

#[tokio::test]
async fn test_invalid_auth_token_access() {
    let client = Client::new();
    
    // Attempt to access a protected route with a fake token
    let res = client
        .get(format!("{}/api/v1/auth/me", base_url()))
        .header("Authorization", "Bearer fake.jwt.token")
        .send()
        .await
        .expect("Failed to send request");
        
    // Should return 401 Unauthorized
    assert_eq!(res.status().as_u16(), 401, "API allowed access with invalid token");
}

#[tokio::test]
async fn test_create_group_unauthorized() {
    let client = Client::new();
    
    // Attempt to create a group without a token
    let res = client
        .post(format!("{}/api/v1/groups/", base_url()))
        .json(&json!({
            "name": "Hack Group",
            "description": "Should fail"
        }))
        .send()
        .await
        .expect("Failed to send request");
        
    assert_eq!(res.status().as_u16(), 401, "Allowed group creation without token");
}

#[tokio::test]
async fn test_xss_prevention_in_display_name() {
    let client = Client::new();
    let username = format!("user_{}", uuid::Uuid::new_v4().to_string().replace("-", "")[..10].to_string());
    
    let xss_payload = "<script>alert(1)</script>";
    
    let res = client
        .post(format!("{}/api/v1/auth/register", base_url()))
        .json(&json!({
            "username": username,
            "password": "password123",
            "display_name": xss_payload
        }))
        .send()
        .await
        .expect("Failed to send request");
        
    assert!(res.status().is_success(), "Failed to register user");
    
    let body: serde_json::Value = res.json().await.unwrap();
    let token = body["access_token"].as_str().unwrap();
    
    // Fetch profile and check if XSS payload is safely stored/retrieved 
    // In a strict API we might want to reject HTML tags. For now we just verify it doesn't break JSON.
    let me_res = client
        .get(format!("{}/api/v1/auth/me", base_url()))
        .header("Authorization", format!("Bearer {}", token))
        .send()
        .await
        .expect("Failed to fetch me");
        
    let me_body: serde_json::Value = me_res.json().await.unwrap();
    assert_eq!(me_body["display_name"].as_str().unwrap(), xss_payload);
}
