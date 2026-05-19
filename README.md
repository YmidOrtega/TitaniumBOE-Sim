# 💹 TitaniumBOE-Sim

> **A comprehensive simulator for the Cboe Titanium U.S. Options Binary Order Entry (BOE) protocol with matching engine, trading bots, REST API, web dashboard, and interactive CLI**

[![Java](https://img.shields.io/badge/Java-21-orange?logo=openjdk)](https://openjdk.org/)
[![Maven](https://img.shields.io/badge/Maven-3.9+-blue?logo=apache-maven)](https://maven.apache.org/)
[![Astro](https://img.shields.io/badge/Astro-5-blueviolet?logo=astro)](https://astro.build/)
[![License](https://img.shields.io/badge/License-MIT-green.svg)](LICENSE)
[![CI/CD](https://github.com/YmidOrtega/TitaniumBOE-Sim/workflows/CI%2FCD%20Pipeline/badge.svg)](https://github.com/YmidOrtega/TitaniumBOE-Sim/actions)
[![Code Quality](https://github.com/YmidOrtega/TitaniumBOE-Sim/workflows/Code%20Quality%20%26%20Coverage/badge.svg)](https://github.com/YmidOrtega/TitaniumBOE-Sim/actions)
[![RocksDB](https://img.shields.io/badge/RocksDB-9.11-purple)](https://rocksdb.org/)

---

## 📖 Overview

**TitaniumBOE-Sim** is an enterprise-grade simulator of the **Cboe Titanium BOE protocol**, built in Java 21. It provides a complete trading ecosystem with a real-time matching engine, intelligent trading bots, RESTful API, WebSocket streaming, an Astro + Tailwind web dashboard, and an interactive CLI — all packaged as a single self-contained JAR.

### Perfect For
- 📚 Learning binary financial protocols and market microstructure
- 🧪 Testing trading strategies without live market connections
- 🎓 Educational demonstrations of trading infrastructure
- 💼 Portfolio showcase of financial systems engineering

> ⚠️ **Educational and development purposes only**. Does not connect to real Cboe systems.

---

## ✨ Key Features

- 🔌 **Complete BOE Protocol** — All 11 message types with binary serialization
- 🎯 **Matching Engine** — Real-time order matching with price-time priority
- 🤖 **Trading Bots** — Market Maker, Trend Follower, Random Trader
- 🌐 **REST API & WebSocket** — Full market data and trading APIs
- 📊 **Web Dashboard** — Real-time Astro + Tailwind UI, served directly from the JAR
- 💻 **Interactive CLI** — Beautiful terminal interface for trading
- 🗄️ **RocksDB Persistence** — All data persisted and recoverable
- 🔐 **Production-grade Security** — BCrypt hashing, rate limiting, validation
- ⚡ **Low-latency Engine** — StampedLock, async write-behind queue, hot-path optimizations

---

## 🚀 Quick Start

### Prerequisites
- Java 21+
- Maven 3.9+
- Node.js 20+ *(only needed for frontend dev; the Maven build downloads it automatically)*

### Build & Run

```bash
# Clone
git clone https://github.com/YmidOrtega/TitaniumBOE-Sim.git
cd TitaniumBOE-Sim

# Build: compiles Astro frontend + packages everything into one fat JAR
mvn clean package -DskipTests

# Start server
DEMO_MODE=true java -jar target/boe-simulator-*.jar
```

Once running:

| Interface | URL |
|---|---|
| **Web Dashboard** | http://localhost:9091 |
| **REST API** | http://localhost:9091/api |
| **WebSocket feed** | ws://localhost:9091/ws/feed |
| **Interactive API docs** | http://localhost:9091/api/docs |
| **BOE Protocol** | localhost:8081 |

**Demo Credentials:**
- Username: `TRD1` / Password: `Pass1234!`
- Username: `TRD2` / Password: `Pass5678!`

### Interactive CLI (optional, separate terminal)

```bash
mvn exec:java -Dexec.mainClass="com.boe.simulator.client.interactive.InteractiveCLI"
```

---

## 🖥️ Web Dashboard

The Astro + Tailwind dashboard is embedded in the JAR and served automatically — no separate web server needed. Open **http://localhost:9091** after starting the server to see:

- Live trade feed via WebSocket
- Order book depth per symbol
- System statistics (active orders, total matches, uptime)
- REST API playground via Scalar

---

## 🎨 Interactive CLI Example

```bash
● guest> connect localhost 8081
Username: TRD1
Password: ********
✓ Connected and authenticated successfully

● TRD1> order buy AAPL 100 150.50
✓ Order submitted
[18:30:45] ⚡ Filled AAPL: 100 @ 150.50

● TRD1> positions
╔══════════════════════════════════════════╗
║ Symbol  ║ Quantity ║  Avg Px  ║  P/L     ║
║ AAPL    ║      100 ║   150.50 ║     0.00 ║
╚══════════════════════════════════════════╝

● TRD1> book AAPL
Best Bid: 150.00 | Best Ask: 150.50 | Spread: 0.50
```

---

## 🌐 REST API

```bash
# Health check
curl http://localhost:9091/api/health

# Get market data
curl http://localhost:9091/api/symbols/AAPL

# Authenticated endpoints
curl -u TRD1:Pass1234! http://localhost:9091/api/positions
curl -u TRD1:Pass1234! http://localhost:9091/api/trades/my
```

---

## 🏗️ Architecture

```
Browser / REST client
        │
        ▼
┌─────────────────────────────────────────────┐
│           REST API Server (Port 9091)       │
│  ┌───────────────┐   ┌────────────────────┐ │
│  │  Astro+TW UI  │   │  REST / WebSocket  │ │
│  │ (classpath/)  │   │     endpoints      │ │
│  └───────────────┘   └────────────────────┘ │
└─────────────────────┬───────────────────────┘
                      │
        ┌─────────────┼─────────────┐
        │             │             │
  ┌─────▼────┐  ┌─────▼────┐  ┌────▼─────┐
  │ Matching │  │ Trading  │  │ RocksDB  │
  │  Engine  │  │   Bots   │  │   DB     │
  │StampedLk │  │MM/TF/Rnd │  │  async   │
  └──────────┘  └──────────┘  └──────────┘
        ▲
        │  BOE binary protocol (Port 8081)
  ┌─────┴─────┐
  │Interactive│
  │    CLI    │
  └───────────┘
```

---

## 🐳 Docker

```bash
# Build and start
docker compose up --build

# Stop
docker compose down
```

The Docker build compiles the Astro frontend and packages it into the JAR automatically. No pre-build step required.

Environment variables (via `.env` or `docker compose`):

| Variable | Default | Description |
|---|---|---|
| `DEMO_MODE` | `true` | Pre-load demo users and market data |
| `BOE_PORT` | `8081` | BOE binary protocol port |
| `API_PORT` | `9091` | REST API + dashboard port |
| `LOG_LEVEL` | `INFO` | Logging verbosity |
| `ADMIN_USERNAME` | — | Admin user (non-demo mode) |
| `ADMIN_PASSWORD` | — | Admin password |

---

## ☁️ Deploying to the Cloud

The entire app — dashboard, API, and matching engine — runs from a single Docker container.

### Railway (recommended — simplest)

1. Push the repo to GitHub
2. New project at [railway.app](https://railway.app) → **Deploy from GitHub repo**
3. Railway auto-detects the `Dockerfile`
4. Set env vars: `DEMO_MODE=true`, `API_PORT=9091`
5. Expose port **9091** — dashboard and API are live

### Render

1. New Web Service → connect repo → select **Docker**
2. Set `DEMO_MODE=true` and port `9091`
3. Free tier spins down on inactivity; use a paid plan for always-on

### Fly.io

```bash
fly launch   # detects Dockerfile, generates fly.toml
fly deploy
```

### Any VPS (DigitalOcean, Hetzner, etc.)

```bash
git clone https://github.com/YmidOrtega/TitaniumBOE-Sim.git
cd TitaniumBOE-Sim
docker compose up -d --build
```

---

## 📁 Project Structure

```
TitaniumBOE-Sim/
├── frontend/                   # Astro + Tailwind dashboard
│   ├── src/pages/              # Dashboard pages
│   └── dist/                   # Built output (embedded in JAR at build time)
├── src/main/java/com/boe/simulator/
│   ├── api/                    # REST API, WebSocket, static file serving
│   │   └── config/             # Scalar/Swagger/OpenAPI handlers
│   ├── bot/                    # Trading bots (MM, Trend, Random)
│   ├── client/                 # BOE Client SDK + Interactive CLI
│   ├── server/                 # BOE Server core
│   │   ├── matching/           # Matching engine (StampedLock)
│   │   ├── auth/               # Authentication (BCrypt)
│   │   └── order/              # Order management
│   ├── protocol/               # BOE message definitions & serialization
│   └── util/                   # Utilities
├── docs/                       # Additional documentation
├── data/                       # RocksDB storage (runtime)
├── Dockerfile
├── docker-compose.yml
└── pom.xml
```

---

## 🧪 Testing

```bash
# Run all tests
mvn test

# Run with coverage
mvn clean test jacoco:report

# Run specific test
mvn test -Dtest=MatchingEngineTest

# Latency benchmark
mvn exec:java@benchmark
```

---

## 🔧 Technologies

| Layer | Technology |
|---|---|
| Runtime | Java 21, Virtual Threads |
| Build | Maven 3.9, frontend-maven-plugin |
| REST API | Javalin 6.7 |
| Frontend | Astro 5, Tailwind CSS |
| Persistence | RocksDB 9.11 |
| Serialization | Jackson |
| Security | JBCrypt, rate limiting |
| Testing | JUnit 5, Mockito, Awaitility |

---

## 📊 Features Checklist

- [x] Complete BOE protocol (11 messages)
- [x] Real-time matching engine (StampedLock, optimistic reads)
- [x] Async write-behind persistence queue
- [x] Trading bot simulation (MM, Trend, Random)
- [x] REST API & WebSocket
- [x] Astro + Tailwind web dashboard (embedded in JAR)
- [x] Interactive CLI client
- [x] Position tracking & P&L
- [x] Persistent storage (RocksDB)
- [x] Security & validation (BCrypt, rate limiting)
- [x] Docker + cloud deployment ready

---

## 📚 Documentation

### **[📖 Full Documentation on DeepWiki →](https://deepwiki.com/YmidOrtega/TitaniumBOE-Sim/1-overview)**

### Interactive API Docs

| Endpoint | Description |
|---|---|
| `http://localhost:9091/api/docs` | Scalar UI (recommended) |
| `http://localhost:9091/api/swagger` | Swagger UI |
| `http://localhost:9091/openapi` | OpenAPI spec (JSON) |

---

## 📄 License

MIT License — see [LICENSE](LICENSE) for details.

---

## 🙏 Acknowledgments

- **Cboe Global Markets** for the BOE protocol specification
- **RocksDB Team** for the embedded database
- **Astro** and **Tailwind CSS** communities

---

<div align="center">

**Built with ☕ Java and 📈 Financial Engineering**

**by [Ymid Ortega](https://github.com/YmidOrtega)**

[![GitHub](https://img.shields.io/badge/GitHub-YmidOrtega-181717?logo=github)](https://github.com/YmidOrtega)
[![LinkedIn](https://img.shields.io/badge/LinkedIn-Connect-0077B5?logo=linkedin)](https://linkedin.com/in/ymidortega)

*If you found this project useful, consider giving it a ⭐!*

</div>
