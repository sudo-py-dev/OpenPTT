use sqlx::postgres::PgPoolOptions;
use sqlx::PgPool;

use crate::config::DatabaseConfig;

/// Create a PostgreSQL connection pool and run migrations.
pub async fn create_pool(config: &DatabaseConfig) -> Result<PgPool, sqlx::Error> {
    let pool = PgPoolOptions::new()
        .max_connections(config.max_connections)
        .acquire_timeout(std::time::Duration::from_secs(5))
        .connect(&config.url)
        .await?;

    tracing::info!("Connected to PostgreSQL (max_connections={})", config.max_connections);

    // Run migrations
    sqlx::migrate!("./migrations")
        .run(&pool)
        .await?;

    tracing::info!("Database migrations applied successfully");

    Ok(pool)
}
