# 📻 OpenPTT

> Open-source Push-to-Talk platform — a high-performance server + native Android client for real-time voice communication.

[![License: MIT](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)
[![Rust](https://img.shields.io/badge/Server-Rust-orange.svg)](server/)
[![Android](https://img.shields.io/badge/Client-Android-green.svg)](android/)

---

## ✨ Features

- **🎤 Push-to-Talk** — Half-duplex voice with floor control (one speaker at a time)
- **🔒 Secure Auth** — JWT RS256 + Argon2id password hashing + refresh token rotation
- **👥 Groups & Channels** — Create groups, add channels, manage members with roles
- **⚡ Low Latency** — UDP audio relay with Opus codec (~50ms end-to-end)
- **🔧 Configurable Server** — Users can point the app at any OpenPTT server
- **🎨 Clean UI** — Jetpack Compose with Material 3, dark/light themes
- **📡 Background Operation** — Foreground service keeps audio alive

## 🏗️ Architecture

```
┌─────────────────┐     HTTPS/WSS     ┌──────────────────┐
│  Android Client  │◄────────────────►│   Rust Server     │
│  (Kotlin/Compose)│     UDP/Opus      │   (Axum/Tokio)    │
└─────────────────┘◄────────────────►│                    │
                                      ├──────────────────┤
                                      │  PostgreSQL       │
                                      │  Redis            │
                                      └──────────────────┘
```

## 🚀 Quick Start

### Server

```bash
# Clone
git clone https://github.com/your-org/open-ptt.git
cd open-ptt

# Start with Docker (recommended)
docker-compose up -d

# Or run locally
cd server
cp config/default.toml config/local.toml  # Edit your config
cargo run --release
```

### Android

1. Open `android/` in Android Studio
2. Build & run on device/emulator
3. Enter your server URL on first launch
4. Register an account and start talking!

## 📖 Documentation

- [API Reference](docs/api.md)
- [Deployment Guide](docs/deployment.md)
- [Contributing](CONTRIBUTING.md)

## 📄 License

MIT — see [LICENSE](LICENSE)
# OpenPTT
