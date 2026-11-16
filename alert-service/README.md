# Alert Service — Phase 6

This module is added against the actual uploaded `log-platform` source tree (Spring Boot 3.3.4, Java 21, MySQL 8.4, Redis 7.2). It consumes the existing `com.logplatform.common.ProcessedLogEvent` contract from `common-core` and the `logs.processed` stream. It writes only to `alert_db`; it does not modify `logs_db`.

## Implemented
- Tenant-scoped alert-rule CRUD (severity/service/environment matchers, threshold and time window).
- Redis sorted-set sliding-window counter (Lua script keeps prune/add/count atomic).
- Threshold evaluation and deterministic rule/event-dimension fingerprint.
- Incident creation, duplicate active-incident occurrence updates, acknowledge/assign/resolve endpoints.
- MySQL notification outbox and retrying publisher to `notifications.out`.
- Redis consumer group creation, event ACK after DB transaction, malformed/permanent-event DLQ.
- Flyway schema, Dockerfile, Compose profile, actuator health endpoints.

## Important engineering notes / limitations
1. This package matches the uploaded code's `ProcessedLogEvent` record exactly: `eventId`, numeric `tenantId`, `timestamp`, `service`, `environment`, `severity`, `message`, `traceId`, `fingerprint`.
2. Gateway must route `/api/v1/alert-rules/**` and `/api/v1/incidents/**` to `http://alert-service:8086` (or `http://localhost:8086` when running on host). Add those routes to `api-gateway/src/main/resources/application.yml`. The existing Gateway strips spoofed tenant headers and re-adds verified JWT claims. The Alert Service currently treats `X-Tenant-Id` as trusted internal context; do not expose port 8086 publicly in production. For direct host testing, this trust boundary is not secure by itself.
3. `notification-service` is Phase 7 and is not in this package. This service publishes versioned JSON payloads on `notifications.out` with fields `eventKey`, `tenantId`, and `payload`.
4. Consumer ACK occurs after the transactional evaluator returns. The implementation is an initial Phase 6 baseline, not a claim of fully verified production-grade pending-message reclaim/concurrency recovery. The consumer group persists pending entries; a dedicated XPENDING/XAUTOCLAIM recovery worker and robust concurrent active-fingerprint locking should be added before declaring Phase 6 production-complete.
5. Sliding-window counter currently uses a unique member per delivery. To avoid duplicate-delivery inflation in a strict production system, persist/claim event idempotency per tenant/rule or use a deterministic sorted-set member and update it atomically. Do not treat this baseline as exactly-once processing.

## Build and run
From the project root:

```bash
mvn -pl alert-service -am clean test
mvn -pl alert-service -am package -DskipTests
```

Start MySQL and Redis first (`docker compose up -d mysql redis`). Ensure `.env` has `ALERT_DB_PASSWORD` and `REDIS_PASSWORD`, and the MySQL init scripts create `alert_db` plus `alert_svc` with the grants shown in the integration SQL. Then:

```bash
docker compose --profile alerting up -d --build alert-service
# or run on host after dependencies are available:
ALERT_DB_URL='jdbc:mysql://127.0.0.1:3307/alert_db?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC' ALERT_DB_USERNAME=alert_svc ALERT_DB_PASSWORD='your-local-value' REDIS_HOST=127.0.0.1 REDIS_PASSWORD='your-local-value' mvn -pl alert-service spring-boot:run
```

Health: `GET http://localhost:8086/actuator/health`.

## API
All tenant-scoped endpoints require trusted `X-Tenant-Id` context from the Gateway.

- `POST /api/v1/alert-rules`
- `GET /api/v1/alert-rules`
- `PUT /api/v1/alert-rules/{id}`
- `DELETE /api/v1/alert-rules/{id}` (soft-disable)
- `GET /api/v1/incidents?status=OPEN`
- `GET /api/v1/incidents/{id}`
- `POST /api/v1/incidents/{id}/acknowledge`
- `POST /api/v1/incidents/{id}/assign` body `{"value":"on-call"}`
- `POST /api/v1/incidents/{id}/resolve`

Example rule:

```json
{"name":"Payment API errors","description":"5 errors in 60 seconds","severity":"ERROR","serviceName":"payment-api","environment":"production","thresholdCount":5,"windowSeconds":60,"cooldownSeconds":300,"enabled":true}
```
