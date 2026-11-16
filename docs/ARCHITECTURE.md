# Architecture (frozen decisions - change only deliberately)
## Services and database ownership
| Service | Port | Owns | Talks to |
|---|---|---|---|
| api-gateway | 8080 | - | all (REST), Redis (rate limit) |
| identity-service | 8081 | identity_db | Redis (key cache invalidation) |
| ingestion-service | 8082 | - (stateless) | identity (key lookup, cached), Redis stream logs.raw |
| processing-service | 8083 | logs_db (sole writer) | logs.raw -> logs.processed, logs.dlq |
| query-service | 8084 | read-only on logs_db | - |
| alert-service | 8085 | alert_db | logs.processed -> notifications.out |
| notification-service | 8086 | notification_db | notifications.out |
No Eureka (Docker DNS), no Kafka (Redis Streams), no PostgreSQL (MySQL 8.4).
## Conventions
- Tenant id: BIGINT internally. Set by gateway/API key, never read from request bodies. Every table has tenant_id, every index starts with it.
- Event ids: UUID strings; consumers are idempotent on (tenant_id, event_id).
- Auth: JWT RS256 for dashboard users (identity signs, gateway verifies with public key); API key (hashed, SHA-256) for log clients.
- Error body: common-web ApiError. Stream names/headers/events: common-core.
## Data flow
client -> gateway -> ingestion -> logs.raw -> processing -> logs_db + logs.processed -> alert-service -> incident + notifications.out -> notification-service -> channels
## Failure handling
Redis down: ingestion 503. Redis full: writes fail, ingestion 503 (noeviction). Consumer crash: pending entries reclaimed (XAUTOCLAIM). 3 failures: logs.dlq. Notification failure: exponential backoff, delivery_attempts rows.
