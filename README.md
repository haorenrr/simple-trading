# Simple Trading System (Backend)

## 📖 Introduction

**Simple Trading Backend** is a lightweight, high-performance cryptocurrency trading system prototype built on a **Spring Cloud** microservices architecture. 

Unlike traditional CRUD applications, this project focuses on the core complexity of a trading exchange: **high-concurrency order matching**, **asset isolation**, and **distributed sequence generation**. It is designed as a learning ground for distributed systems, financial logic correctness, and clean architecture.

## 🏗 Architecture

The system follows a classic microservice separation of concerns:

- **openapi**: The entry point (Gateway/Facade) for external REST APIs. It forwards requests to internal engines.
- **trading-engine**: The heart of the system.
  - **Asset Module**: Manages user funds with strict double-entry bookkeeping principles.
  - **Order Module**: Handles order lifecycle (Pending, Partial, Filled, Canceled).
  - **Match Engine**: An asynchronous, high-performance in-memory order book matching algorithm (Price/Time priority).
- **sequence-engine**: A dedicated service for generating globally unique, monotonic IDs for orders and transactions.
- **registry-server**: Service discovery (Eureka).
- **config-server**: Centralized configuration management.

> **Note**: Currently, the `trading-engine` uses concurrent in-memory data structures (ConcurrentHashMap/SkipList) to simulate database storage for maximum throughput during prototyping.

## 🛠 Tech Stack

- **Language**: Java 21
- **Framework**: Spring Boot 3.x / Spring Cloud 2025.x
- **Service Discovery**: Netflix Eureka
- **Communication**: OpenFeign (RPC)
- **Build Tool**: Maven
- **Testing**: JUnit 5, Mockito, AssertJ

## 🚀 Getting Started

### Prerequisites
- JDK 21+
- Maven 3.x

### Build the Project
```bash
# In the root directory
mvn clean install -DskipTests
```

### Running the Services
To start the system, run the services in the following order to ensure dependencies are ready:

```bash
# In the root directory
./start.sh
```
It will launch the service accordingly, includes:
1. **Registry Server** (`registry-server`)
2. **Sequence Engine** (`sequence-engine`)
3. **Trading Engine** (`trading-engine`)
4. **OpenAPI** (`openapi`)

## 🧪 Testing Strategy

Since financial systems require absolute logic correctness, we prioritize a **"Regression Safety"** testing strategy. We focus heavily on Unit Tests for core business logic rather than broad, brittle End-to-End tests.

### 🛡️ Core Defense Layers

We have established three main lines of defense in the `trading-engine`:

1.  **Asset Safety (Fund Integrity)**
    *   **Focus**: Ensure no negative balances, correct freezing/unfreezing, and atomic transfers.
    *   **Location**: `AssetServiceTest`
    *   **Technique**: Mocking dependencies to verify mathematical correctness of fund operations.

2.  **Order Flow (State Machine)**
    *   **Focus**: Validate order state transitions (e.g., specific checks before `INIT` -> `TRADING`).
    *   **Location**: `OrderServiceTest`
    *   **Technique**: Verifying interaction between Order creation and Asset freezing.

3.  **Matching Engine (Algorithm)**
    *   **Focus**: The core matching algorithm (Price Priority > Time Priority).
    *   **Location**: `MatcherServiceTest`
    *   **Technique**: Handling async execution threads to verify that Maker/Taker orders are matched correctly and trigger clearing.

### How to Run Tests
To verify the core logic integrity:

```bash
# Run tests specifically for the trading engine
mvn test -pl trading-engine -am
```

## 📂 Project Structure

```text
simple-trading-backend/
├── common/             # Shared utilities, Result wrappers, ErrorCodes
├── trading-engine/     # Core logic (Assets, Orders, Matching)
│   └── src/test/       # Core regression tests reside here
├── sequence-engine/    # Distributed ID generation
├── openapi/            # Public API Gateway
├── registry-server/    # Eureka Server
├── config-server/      # Spring Cloud Config
├── parent/             # Dependency management (BOM)
└── script/             # Scripts such as somke testing

```

## 📝 Roadmap

- [x] Basic Microservice Architecture
- [x] In-memory Matching Engine (Maker/Taker)
- [x] Core Regression Test Suite
- [ ] Persistence Layer Integration (MySQL/Redis)
- [ ] WebSocket Market Data Push
- [ ] Distributed Transaction Management (Seata/Saga)

## 📄 License

This project is licensed under the MIT License.
