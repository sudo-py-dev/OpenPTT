pub mod config;
pub mod error;
pub mod models;
pub mod db;
pub mod auth;
pub mod api;
pub mod ws;
pub mod audio;
pub mod fcm;

pub use config::AppConfig;
pub use error::AppError;

use sqlx::PgPool;
use redis::aio::ConnectionManager as RedisConn;
use std::sync::Arc;
use metrics_exporter_prometheus::PrometheusHandle;

/// Shared application state passed to all handlers via Axum's State extractor.
#[derive(Clone)]
pub struct AppState {
    pub db: PgPool,
    pub redis: RedisConn,
    pub config: Arc<AppConfig>,
    pub prometheus_handle: PrometheusHandle,
}
