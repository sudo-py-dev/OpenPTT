use std::sync::Arc;
use tokio::net::TcpListener;
use tracing_subscriber::{layer::SubscriberExt, util::SubscriberInitExt};

use openptt_server::{
    api::router::build_router,
    audio::udp_relay::UdpRelay,
    config::AppConfig,
    db::pool::create_pool,
    fcm::init_fcm,
    AppState,
};

#[tokio::main]
async fn main() -> anyhow::Result<()> {
    // Initialize tracing (structured logging)
    tracing_subscriber::registry()
        .with(
            tracing_subscriber::EnvFilter::try_from_default_env()
                .unwrap_or_else(|_| "openptt_server=info,tower_http=info".into()),
        )
        .with(tracing_subscriber::fmt::layer())
        .init();

    tracing::info!("🎙️  OpenPTT Server starting...");

    // Load configuration
    let config = AppConfig::load()?;
    tracing::info!(
        "Configuration loaded (HTTP={}:{}, UDP={})",
        config.server.http_host,
        config.server.http_port,
        config.server.udp_port
    );

    // Connect to PostgreSQL
    let db = create_pool(&config.database).await?;

    // Run database migrations automatically
    tracing::info!("Running database migrations...");
    sqlx::migrate!("./migrations").run(&db).await?;
    tracing::info!("Database migrations complete");

    // Connect to Redis
    tracing::info!("Connecting to Redis at {}...", config.redis.url);
    let redis_client = redis::Client::open(config.redis.url.as_str())?;
    let redis = redis::aio::ConnectionManager::new(redis_client).await?;
    tracing::info!("Connected to Redis");

    // Initialize FCM
    if let Err(e) = init_fcm("config/firebase-adminsdk.json").await {
        tracing::warn!("FCM not initialized: {}", e);
    } else {
        tracing::info!("FCM initialized successfully");
    }

    // Initialize Prometheus metrics recorder
    let builder = metrics_exporter_prometheus::PrometheusBuilder::new();
    let prometheus_handle = builder.install_recorder().expect("failed to install Prometheus recorder");
    tracing::info!("Prometheus metrics recorder installed");

    let config = Arc::new(config);

    // Build application state
    let state = AppState {
        db,
        redis,
        config: config.clone(),
        prometheus_handle,
    };

    // Start UDP audio relay
    let udp_port = config.server.udp_port;
    tokio::spawn(async move {
        match UdpRelay::start(udp_port).await {
            Ok(relay) => relay.run().await,
            Err(e) => tracing::error!("Failed to start UDP relay: {}", e),
        }
    });

    // Build HTTP router
    let app = build_router(state);

    // Start HTTP server
    let addr = format!("{}:{}", config.server.http_host, config.server.http_port);
    let listener = TcpListener::bind(&addr).await?;
    tracing::info!("🚀 HTTP server listening on {}", addr);

    axum::serve(listener, app)
        .with_graceful_shutdown(shutdown_signal())
        .await?;

    // Close the database connection gracefully
    tracing::info!("Closing database connection...");
    state.db.close().await;

    tracing::info!("Server shut down gracefully");

    Ok(())
}

async fn shutdown_signal() {
    let ctrl_c = async {
        tokio::signal::ctrl_c()
            .await
            .expect("failed to install Ctrl+C handler");
    };

    #[cfg(unix)]
    let terminate = async {
        tokio::signal::unix::signal(tokio::signal::unix::SignalKind::terminate())
            .expect("failed to install signal handler")
            .recv()
            .await;
    };

    #[cfg(not(unix))]
    let terminate = std::future::pending::<()>();

    tokio::select! {
        _ = ctrl_c => {},
        _ = terminate => {},
    }

    tracing::info!("Shutdown signal received, starting graceful shutdown");
}
