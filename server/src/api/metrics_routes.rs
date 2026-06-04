use axum::{extract::State, response::IntoResponse};
use crate::AppState;

/// Handler to export Prometheus metrics
pub async fn metrics_handler(State(state): State<AppState>) -> impl IntoResponse {
    state.prometheus_handle.render()
}
