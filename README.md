# 📊 Log Analytics & Alerting Engine

> A production-ready, observable, and reliable microservices platform for log ingestion, processing, alerting, and notification delivery. Built with Spring Boot 3, Java 21, MySQL, and Redis Streams.

[![Java 21](https://img.shields.io/badge/Java-21-orange?style=for-the-badge&logo=openjdk)](https://openjdk.org/)
[![Spring Boot 3.3](https://img.shields.io/badge/Spring%20Boot-3.3.4-green?style=for-the-badge&logo=spring-boot)](https://spring.io/projects/spring-boot)
[![MySQL 8.4](https://img.shields.io/badge/MySQL-8.4-blue?style=for-the-badge&logo=mysql)](https://mysql.com/)
[![Redis 7.2](https://img.shields.io/badge/Redis-7.2-red?style=for-the-badge&logo=redis)](https://redis.io/)
[![Maven](https://img.shields.io/badge/Maven-3.9+-yellow?style=for-the-badge&logo=apache-maven)](https://maven.apache.org/)
[![Docker Compose](https://img.shields.io/badge/Docker%20Compose-v2-blue?style=for-the-badge&logo=docker)](https://docs.docker.com/compose/)
[![Prometheus](https://img.shields.io/badge/Prometheus-v2.53-E6522C?style=for-the-badge&logo=prometheus)](https://prometheus.io/)
[![Grafana](https://img.shields.io/badge/Grafana-v11-F46A00?style=for-the-badge&logo=grafana)](https://grafana.com/)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg?style=for-the-badge)](https://opensource.org/licenses/MIT)

---

## 🎯 Overview

The **Log Analytics & Alerting Engine** is a distributed microservices platform that ingests log events from multiple tenants, processes them in real-time, evaluates alert rules, and delivers notifications via email, Slack, and webhook. It is designed for observability, reliability, and production-readiness — suitable for a strong Java/Spring Boot backend developer portfolio.

### 🔄 Data Flow

```
Client → API Gateway → Ingestion Service → Redis Stream (logs.raw)
                                              ↓
                                    Processing Service → MySQL (processed_logs)
                                              ↓
                                    Alert Service → Redis Stream (logs.processed)
                                              ↓
                                    Alert Rules → Incidents → Notification Outbox
                                              ↓
                                    Notification Service → Email / Slack / Webhook
                                              ↓
                                    Query Service ← MySQL ← Client queries logs
```

---

## 🏗️ Architecture

### Microservices (9 Modules)

| Module | Port | Responsibility |
|--------|------|----------------|
| **identity-service** | 8081 | Users, tenants, API keys, JWT issuance, JWKS, audit logging |
| **api-gateway** | 8080 | Spring Cloud Gateway, JWT validation, rate limiting, header injection (trust boundary) |
| **ingestion-service** | 8083 | API-key auth, validation, dedup, backpressure, Redis stream producer |
| **processing-service** | 8084 | Redis stream consumer, normalization, MySQL write, outbox pattern |
| **query-service** | 8085 | Log search/retrieval from MySQL with tenant isolation |
| **alert-service** | 8086 | Rule evaluation, sliding window counters, incident management, outbox publisher |
| **notification-service** | 8086 | Notification delivery (email/slack/webhook), retry, DLQ, PEL recovery |
| **common-core** | — | Shared contracts (events, stream names, headers, roles) |
| **common-web** | — | Servlet exception handling, correlation ID filter, security utilities |

### Infrastructure

- **MySQL 8.4** — Primary database (one schema per service)
- **Redis 7.2** — Streams for async communication, PEL (Pending Entries List) for crash recovery
- **Prometheus** — Metrics collection (scrapes `/actuator/prometheus` from all services)
- **Grafana** — Dashboards for platform health, JVM, API, DB, pipeline, alerts, notifications

---

## ✨ Key Features

### 🔐 Security
- **JWT authentication** with RS256 signatures and JWKS endpoint
- **API key authentication** for log ingestion
- **RBAC** — VIEWER, DEVELOPER, TENANT_ADMIN, PLATFORM_ADMIN roles
- **Tenant isolation** — gateway strips client headers, injects trusted `X-Tenant-Id` from verified JWT
- **SSRF protection** — blocks localhost, private IPs (10.x, 172.16-31.x, 192.168.x), cloud metadata (169.254.x.x)
- **Prometheus endpoint security** — requires `X-Internal-Token` header
- **Rate limiting** — per-IP and per-user via Redis rate limiter on gateway
- **Secrets via environment variables** — no hardcoded credentials

### 📊 Observability (Phase 8)
- **Spring Boot Actuator** on all services (`/actuator/health`, `/actuator/prometheus`)
- **Micrometer metrics** — JVM memory, GC, CPU, HTTP requests, DB connection pool, Redis health
- **Custom business metrics** — log ingestion rate, processing throughput, alert evaluation duration, notification delivery success/failure
- **Prometheus + Grafana** — 10-panel Platform Health dashboard
- **Correlation ID** — end-to-end request tracing via MDC and response headers
- **Structured exception handling** — consistent `ApiError` responses with correlation IDs

### 🔁 Reliability
- **Redis Streams + Consumer Groups** — at-least-once delivery
- **PEL Recovery** — `PendingRecoveryJob` with idle timeout + XCLAIM for crash recovery
- **Dead Letter Queue** — failed events dead-lettered after max delivery attempts
- **Idempotency** — event ID dedup at ingestion, `insertIfAbsent` at processing
- **Retry with exponential backoff** — failed notifications retry with configurable backoff
- **Circuit protection** — backpressure on ingestion when Redis stream exceeds high-water mark
- **Graceful shutdown** — `server.shutdown=graceful` with 30s lifecycle timeout
- **Docker health checks** — all containers expose `/actuator/health`

---

## 📦 Module Structure

```
log-platform/
├── common-core/              # Pure Java contracts (no Spring)
├── common-web/               # Servlet exception handling, correlation ID
├── identity-service/         # Users, tenants, API keys, JWT, JWKS
├── api-gateway/              # Spring Cloud Gateway, auth, rate limiting
├── ingestion-service/        # API-key auth, Redis producer
├── processing-service/       # Redis consumer, MySQL writer
├── query-service/            # Log search/retrieval
├── alert-service/            # Rule evaluation, incidents
├── notification-service/     # Delivery, retry, DLQ
├── monitoring/               # Prometheus + Grafana config
├── docs/                     # Operational runbook, recovery, deployment guides
├── docker-compose.yml        # Multi-service orchestration
├── pom.xml                   # Multi-module Maven build
└── README.md
```

---

## 🚀 Getting Started

### Prerequisites

- **Java 21** (Eclipse Temurin recommended)
- **Maven 3.9+**
- **Docker** & **Docker Compose v2**
- ~12 GB RAM recommended

### 1. Clone the Repository

```bash
git clone https://github.com/KalpeshPatil02/Log-Platform.git
cd Log-Platform
```

### 2. Configure Environment

```bash
cp .env.example .env
# Edit .env with your passwords:
# MYSQL_ROOT_PASSWORD, IDENTITY_DB_PASSWORD, REDIS_PASSWORD, etc.
```

JWT keys (place in `./secrets/`):
```bash
mkdir -p secrets
# Place private.pem and public.pem (RSA 2048 key pair)
```

### 3. Build All Services

```bash
mvn -B install -DskipTests
```

### 4. Run with Docker Compose

**Core services only:**
```bash
docker compose --env-file .env up -d
```

**With monitoring (Prometheus + Grafana):**
```bash
docker compose --env-file .env --profile monitoring up -d
```

**Specific profiles:**
```bash
docker compose --env-file .env --profile core --profile alerting --profile notification up -d
```

### 5. Verify Deployment

```bash
# Check all containers
docker compose ps

# Check service health
curl http://localhost:8081/actuator/health   # identity-service
curl http://localhost:8083/actuator/health   # ingestion-service
curl http://localhost:8084/actuator/health   # processing-service
curl http://localhost:8085/actuator/health   # query-service
curl http://localhost:8086/actuator/health   # alert + notification service
```

### 6. Access URLs

| Service | URL | Auth |
|---------|-----|------|
| API Gateway | http://localhost:8080 | JWT |
| Identity Service | http://localhost:8081 | JWT |
| Ingestion API | http://localhost:8083/api/v1/logs | X-API-KEY |
| Query API | http://localhost:8085/api/v1/logs | JWT |
| Alert Rules | http://localhost:8086/api/v1/alert-rules | JWT |
| Incidents | http://localhost:8086/api/v1/incidents | JWT |
| Notifications | http://localhost:8086/api/v1/notifications | JWT + X-Tenant-Id |
| Prometheus | http://localhost:9090 | X-Internal-Token |
| Grafana | http://localhost:3000 | admin / GRAFANA_ADMIN_PASSWORD |

---

## 🧪 Running Tests

```bash
# All module unit tests
mvn -B test

# With Testcontainers integration tests (requires Docker)
mvn -B verify

# Specific module
mvn -B test -pl notification-service
```

### Test Coverage

| Module | Tests | Status |
|--------|-------|--------|
| common-web | 4 | ✅ |
| identity-service | 12 | ✅ |
| ingestion-service | 7 | ✅ |
| processing-service | 2 | ✅ |
| alert-service | 9 | ✅ |
| notification-service | 20 | ✅ |
| **Total** | **54** | **0 failures** |

Integration tests use **Testcontainers** with real MySQL 8.4 and Redis 7.2.

---

## 📈 Monitoring & Observability

### Prometheus Metrics

Scraped from each service at `/actuator/prometheus`:

| Category | Metrics |
|----------|---------|
| JVM | `jvm.memory.used`, `jvm.memory.committed`, `jvm.gc.pause`, `process.cpu.usage` |
| HTTP | `http.server.requests` (count, latency, by status) |
| Database | `hikaricp.connections.active`, `hikaricp.connections.pending` |
| Business | `logs.ingested.total`, `logs.processed.total`, `alerts.incidents.created`, `notifications.delivered.total` |

### Grafana Dashboards

Access at http://localhost:3000 (admin / `GRAFANA_ADMIN_PASSWORD`):

- **Platform Health** — service availability, error rates, response times, JVM, DB, pipeline, notifications

### Operational Alerts (Defined)

- ServiceDown, HighLatency, HighErrorRate, JVMMemoryPressure, DBConnectionExhaustion, RedisDown, ProcessingBacklog, NotificationFailure, AbnormalIngestion

---

## 🛡️ Security Review

| Area | Implementation |
|------|---------------|
| JWT Validation | RS256, JWKS, issuer validation |
| RBAC | RoleGuard — READ/WRITE role sets per service |
| Tenant Isolation | Gateway strips client headers, injects from JWT; controllers verify tenantId |
| API Key Auth | Ingestion service validates against identity-service |
| SSRF Protection | Blocks localhost, private IPs, cloud metadata (169.254.x.x) |
| Secrets | All passwords/keys via environment variables |
| Actuator | Limited to `health,info,prometheus`; prometheus requires internal token |

---

## 📁 Environment Variables

| Variable | Description | Required |
|----------|-------------|----------|
| `MYSQL_ROOT_PASSWORD` | MySQL root password | ✅ |
| `IDENTITY_DB_PASSWORD` | Identity service DB password | ✅ |
| `PROCESSING_DB_PASSWORD` | Processing service DB password | ✅ |
| `QUERY_DB_PASSWORD` | Query service DB password | ✅ |
| `ALERT_DB_PASSWORD` | Alert service DB password | ✅ |
| `NOTIFICATION_DB_PASSWORD` | Notification service DB password | ✅ |
| `REDIS_PASSWORD` | Redis password | ✅ |
| `IDENTITY_INTERNAL_TOKEN` | Internal service communication token | ✅ |
| `GRAFANA_ADMIN_PASSWORD` | Grafana admin password | ✅ |
| `SMTP_USERNAME` | SMTP username (email notifications) | ❌ |
| `SMTP_PASSWORD` | SMTP password | ❌ |
| `SLACK_WEBHOOK_URL` | Slack webhook URL | ❌ |
| `WEBHOOK_BASE_URL` | Default webhook destination | ❌ |

---

## 📚 Documentation

| Document | Path |
|----------|------|
| Phase 8 Design Document | `PHASE8_DESIGN_DOCUMENT.md` |
| Operational Runbook | `docs/OPERATIONAL_RUNBOOK.md` |
| Failure Recovery Guide | `docs/FAILURE_RECOVERY_GUIDE.md` |
| Deployment Guide | `docs/DEPLOYMENT_GUIDE.md` |
| Testing Report | `docs/TESTING_REPORT.md` |
| Known Limitations | `docs/KNOWN_LIMITATIONS.md` |

---

## 🛑 Known Limitations

- No distributed tracing (Kafka/Eureka not used — by design for lightweight architecture)
- No centralized log storage (logs remain local per container)
- Alertmanager container not yet deployed (alert rules defined, ready to add)
- 1 Grafana dashboard provisioned (6 more planned, same pattern)
- Performance baselines are proposed targets, not yet measured under load

---

## 🤝 Contributing

1. Fork the repository
2. Create a feature branch (`git checkout -b feature/your-feature`)
3. Commit your changes (`git commit -m 'feat: add some feature'`)
4. Push to the branch (`git push origin feature/your-feature`)
5. Open a Pull Request

---

## 📄 License

This project is licensed under the MIT License. See the [LICENSE](LICENSE) file for details.

---

## 👨‍💻 Author

**Kalpesh Patil** — Java Backend Developer (Spring Boot, Microservices, REST APIs)

- GitHub: [@KalpeshPatil02](https://github.com/KalpeshPatil02)
- Location: India

---

## ⭐ Star This Repo

If you found this project helpful, please give it a ⭐ to show your support!

---

**Built for production readiness, observability, and reliability — suitable for Java/Spring Boot backend developer portfolios and technical interviews.**