# 💹 TitaniumBOE-Sim

> **A comprehensive simulator for the Cboe Titanium U.S. Options Binary Order Entry (BOE) protocol with matching engine, trading bots, REST API, and interactive CLI**

[![Java](https://img.shields.io/badge/Java-21-orange?logo=openjdk)](https://openjdk.org/)
[![Maven](https://img.shields.io/badge/Maven-3.8+-blue?logo=apache-maven)](https://maven.apache.org/)
[![License](https://img.shields.io/badge/License-MIT-green.svg)](LICENSE)
[![CI/CD](https://github.com/YmidOrtega/TitaniumBOE-Sim/workflows/CI%2FCD%20Pipeline/badge.svg)](https://github.com/YmidOrtega/TitaniumBOE-Sim/actions)
[![Code Quality](https://github.com/YmidOrtega/TitaniumBOE-Sim/workflows/Code%20Quality%20%26%20Coverage/badge.svg)](https://github.com/YmidOrtega/TitaniumBOE-Sim/actions)
[![RocksDB](https://img.shields.io/badge/RocksDB-8.5.3-purple)](https://rocksdb.org/)

---

## 📖 Overview

**TitaniumBOE-Sim** is an enterprise-grade simulator of the **Cboe Titanium BOE protocol**, built in Java 21. It provides a complete trading ecosystem with a real-time matching engine, intelligent trading bots, RESTful API, WebSocket streaming, interactive CLI, and persistent storage.

### Perfect For
- 📚 Learning binary financial protocols and market microstructure
- 🧪 Testing trading strategies without live market connections
- 🎓 Educational demonstrations of trading infrastructure
- 💼 Portfolio showcase of financial systems engineering

> ⚠️ **Educational and development purposes only**. Does not connect to real Cboe systems.

---

## ✨ Key Features

- 🔌 **Complete BOE Protocol** - All 11 message types with binary serialization
- 🎯 **Matching Engine** - Real-time order matching with price-time priority
- 🤖 **Trading Bots** - Market Maker, Trend Follower, Random Trader
- 🌐 **REST API & WebSocket** - Full market data and trading APIs
- 💻 **Interactive CLI** - Beautiful terminal interface for trading
- 🗄️ **RocksDB Persistence** - All data persisted and recoverable
- 🔐 **Production-grade Security** - BCrypt hashing, rate limiting, validation
- 📊 **Position Tracking** - Real-time P&L calculation

---

## 🚀 Quick Start

### Prerequisites
- Java 21+
- Maven 3.8+

### Installation & Run

```bash
# Clone and build
git clone https://github.com/YourUsername/TitaniumBOE-Sim.git
cd TitaniumBOE-Sim
mvn clean package

# Start server (creates demo users and market data)
DEMO_MODE=true mvn exec:java -Dexec.mainClass="com.boe.simulator.server.CboeServer"

# In another terminal, start Interactive CLI
mvn exec:java -Dexec.mainClass="com.boe.simulator.client.interactive.InteractiveCLI"
```

**Demo Credentials:**
- Username: `TRD1` / Password: `Pass1234!`
- Username: `TRD2` / Password: `Pass5678!`

---

## 🎨 Interactive CLI Example

```bash
● guest> connect localhost 8080
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
curl http://localhost:8081/api/health

# Get market data
curl http://localhost:8081/api/symbols/AAPL

# Authenticated endpoints
curl -u TRD1:Pass1234! http://localhost:8081/api/positions
curl -u TRD1:Pass1234! http://localhost:8081/api/trades/my
```

**WebSocket:** `ws://localhost:8081/ws/feed`

---

## 🏗️ Architecture

```
┌─────────────┐     ┌──────────────┐     ┌─────────────┐
│ Interactive │────>│  BOE Server  │────>│  REST API   │
│     CLI     │     │  (Port 8080) │     │ (Port 8081) │
└─────────────┘     └──────┬───────┘     └─────────────┘
                           │
              ┌────────────┼────────────┐
              │            │            │
        ┌─────▼────┐  ┌────▼────┐  ┌────▼────┐
        │ Matching │  │ Trading │  │ RocksDB │
        │  Engine  │  │   Bots  │  │   DB    │
        └──────────┘  └─────────┘  └─────────┘
```

---

## 📁 Project Structure

```
TitaniumBOE-Sim/
├── src/main/java/com/boe/simulator/
│   ├── api/              # REST API & WebSocket
│   ├── bot/              # Trading bots (MM, Trend, Random)
│   ├── client/           # BOE Client SDK
│   │   └── interactive/  # CLI application
│   ├── server/           # BOE Server core
│   │   ├── matching/     # Matching engine
│   │   ├── auth/         # Authentication
│   │   └── order/        # Order management
│   ├── protocol/         # BOE message definitions
│   └── util/             # Utilities
├── docs/                 # Documentation
├── data/                 # RocksDB storage
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
```

---

## 🔧 Technologies

- **Java 21** - Core language
- **Maven** - Build tool
- **RocksDB 8.5.3** - Persistence
- **Javalin 6.7.0** - REST API framework
- **Jackson** - JSON serialization
- **JUnit 5 + Mockito** - Testing

---

## 📊 Features Checklist

- [x] Complete BOE protocol (11 messages)
- [x] Real-time matching engine
- [x] Trading bot simulation
- [x] REST API & WebSocket
- [x] Interactive CLI client
- [x] Position tracking & P&L
- [x] Persistent storage
- [x] Security & validation

---

## 📚 Complete Documentation

**For detailed architecture, implementation guides, API reference, and more:**

### **[📖 Full Documentation on DeepWiki →](https://deepwiki.com/YmidOrtega/TitaniumBOE-Sim/1-overview)**

The DeepWiki contains:
- 🏛️ Complete architecture diagrams
- 📘 API reference and examples
- 📗 Protocol implementation details
- 📙 Development guides
- 📕 Performance optimization tips
- 🔐 Security best practices
- 🚀 Deployment instructions

### **[📚 Interactive API Documentation (Scalar) →](docs/API_DOCUMENTATION.md)**

Access the interactive API documentation:
- **Scalar UI**: http://localhost:8081/api/docs *(recommended)*
- **Swagger UI**: http://localhost:8081/api/swagger
- **OpenAPI Spec**: http://localhost:8081/openapi

Features:
- ✨ Modern, clean interface with Scalar
- 🔧 Interactive request testing
- 📝 Code examples in multiple languages
- 🔍 Advanced search functionality
- 🌙 Dark mode support

---

## 📄 License

MIT License - see [LICENSE](LICENSE) file for details.

---

## 🙏 Acknowledgments

- **Cboe Global Markets** for the BOE protocol specification
- **RocksDB Team** for the embedded database
- **Open Source Community** for inspiration and tools

---

<div align="center">

**Built with ☕ Java and 📈 Financial Engineering**

**by [Ymid Ortega](https://github.com/YmidOrtega)**

[![GitHub](https://img.shields.io/badge/GitHub-YmidOrtega-181717?logo=github)](https://github.com/YmidOrtega)
[![LinkedIn](https://img.shields.io/badge/LinkedIn-Connect-0077B5?logo=linkedin)](https://linkedin.com/in/ymidortega)

*If you found this project useful, consider giving it a ⭐!*

**© 2024 Ymid Ortega. All Rights Reserved.**

</div>
