use axum::{
    http::Method,
    routing::{delete, get, patch, post},
    Json, Router,
};
use serde::Serialize;
use tower_http::cors::{Any, CorsLayer};
use tower_http::trace::TraceLayer;
use tower_http::request_id::{MakeRequestUuid, SetRequestIdLayer};
use tower::limit::ConcurrencyLimitLayer;
use axum::http::HeaderValue;

use crate::api::{auth_routes, channel_routes, group_routes, user_routes, metrics_routes};
use crate::ws::handler::ws_handler;
use crate::AppState;

#[derive(Serialize)]
pub struct HealthResponse {
    pub status: String,
    pub version: String,
    pub http_port: u16,
    pub udp_port: u16,
}

/// Build the complete Axum router with all routes.
pub fn build_router(state: AppState) -> Router {
    let cors_allowed_origins = std::env::var("CORS_ALLOWED_ORIGINS").unwrap_or_else(|_| "*".to_string());
    
    let cors = if cors_allowed_origins == "*" {
        CorsLayer::new()
            .allow_origin(Any)
            .allow_methods([Method::GET, Method::POST, Method::PATCH, Method::DELETE])
            .allow_headers(Any)
    } else {
        let origins = cors_allowed_origins
            .split(',')
            .filter_map(|s| s.trim().parse::<HeaderValue>().ok())
            .collect::<Vec<_>>();
        CorsLayer::new()
            .allow_origin(origins)
            .allow_methods([Method::GET, Method::POST, Method::PATCH, Method::DELETE])
            .allow_headers(Any)
    };

    // Setup basic Concurrency Limiting to prevent DoS (e.g. max 1000 concurrent requests)
    let rate_limit_layer = ConcurrencyLimitLayer::new(1000);

    // Auth routes (no auth required)
    let auth_routes = Router::new()
        .route("/register", post(auth_routes::register))
        .route("/login", post(auth_routes::login))
        .route("/refresh", post(auth_routes::refresh))
        .route("/logout", post(auth_routes::logout))
        .route("/me", get(auth_routes::me))
        .route("/fcm-token", post(auth_routes::update_fcm_token));

    // User routes (auth required)
    let user_routes = Router::new()
        .route("/search", get(user_routes::search_users))
        .route("/{id}", get(user_routes::get_user))
        .route("/{id}", patch(user_routes::update_user));

    // Group routes (auth required)
    let group_routes = Router::new()
        .route("/", post(group_routes::create_group))
        .route("/", get(group_routes::list_groups))
        .route("/{id}", get(group_routes::get_group))
        .route("/{id}", delete(group_routes::delete_group))
        .route("/{id}/join", post(group_routes::join_group))
        .route("/{id}/leave", post(group_routes::leave_group))
        .route("/{id}/members", get(group_routes::get_members))
        .route("/{gid}/channels", post(channel_routes::create_channel))
        .route("/{gid}/channels", get(channel_routes::list_channels))
        .route(
            "/{gid}/channels/{id}",
            delete(channel_routes::delete_channel),
        );

    // Health endpoint
    let http_port = state.config.server.http_port;
    let udp_port = state.config.server.udp_port;

    Router::new()
        .route("/health", get(move || async move {
            Json(HealthResponse {
                status: "ok".to_string(),
                version: env!("CARGO_PKG_VERSION").to_string(),
                http_port,
                udp_port,
            })
        }))
        .route("/metrics", get(metrics_routes::metrics_handler))
        .route("/ws", get(ws_handler))
        .nest("/api/v1/auth", auth_routes)
        .nest("/api/v1/users", user_routes)
        .nest("/api/v1/groups", group_routes)
        .layer(rate_limit_layer)
        .layer(cors)
        .layer(TraceLayer::new_for_http())
        .layer(SetRequestIdLayer::x_request_id(MakeRequestUuid))
        .with_state(state)
}
