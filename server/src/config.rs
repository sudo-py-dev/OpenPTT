use serde::Deserialize;

/// Top-level application configuration.
/// Loaded from config/default.toml, overridden by environment variables
/// prefixed with OPENPTT__ (double underscore as separator).
#[derive(Debug, Clone, Deserialize)]
pub struct AppConfig {
    pub server: ServerConfig,
    pub database: DatabaseConfig,
    pub redis: RedisConfig,
    pub auth: AuthConfig,
    pub audio: AudioConfig,
}

#[derive(Debug, Clone, Deserialize)]
pub struct ServerConfig {
    pub http_host: String,
    pub http_port: u16,
    pub udp_port: u16,
    pub tls_cert: Option<String>,
    pub tls_key: Option<String>,
}

#[derive(Debug, Clone, Deserialize)]
pub struct DatabaseConfig {
    pub url: String,
    pub max_connections: u32,
}

#[derive(Debug, Clone, Deserialize)]
pub struct RedisConfig {
    pub url: String,
}

#[derive(Debug, Clone, Deserialize)]
pub struct AuthConfig {
    pub access_token_ttl_secs: u64,
    pub refresh_token_ttl_secs: u64,
    pub jwt_secret: String,
}

#[derive(Debug, Clone, Deserialize)]
pub struct AudioConfig {
    pub opus_bitrate: u32,
    pub frame_duration_ms: u32,
    pub max_channels_per_group: u32,
    pub max_talk_duration_secs: u64,
}

impl AppConfig {
    /// Load configuration from files and environment.
    ///
    /// Priority (highest to lowest):
    /// 1. Environment variables: OPENPTT__SERVER__HTTP_PORT=9443
    /// 2. config/local.toml (git-ignored, for personal overrides)
    /// 3. config/production.toml (if RUN_ENV=production)
    /// 4. config/default.toml
    pub fn load() -> Result<Self, config::ConfigError> {
        let run_env = std::env::var("RUN_ENV").unwrap_or_else(|_| "development".to_string());

        let config = config::Config::builder()
            .add_source(config::File::with_name("config/default"))
            .add_source(
                config::File::with_name(&format!("config/{}", run_env))
                    .required(false),
            )
            .add_source(config::File::with_name("config/local").required(false))
            .add_source(
                config::Environment::with_prefix("OPENPTT")
                    .separator("__")
                    .try_parsing(true),
            )
            .build()?;

        config.try_deserialize()
    }
}
