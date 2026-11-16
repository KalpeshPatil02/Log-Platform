# Query Service — Phase 5

Read-only REST API over `logs_db.processed_logs`.

Endpoints:
- `GET /api/v1/logs`
- `GET /api/v1/logs/{eventId}`
- `GET /actuator/health`

Filters: `severity`, `serviceName`, `environment`, `from`, `to`, `keyword`, `page`, `size`.

Tenant identity is taken from `X-Tenant-Id`, which must be injected by the trusted API Gateway after authentication. Do not expose this service directly to untrusted clients.

IMPORTANT: create a dedicated MySQL user with SELECT-only permissions; do not reuse processing-service credentials:

```sql
CREATE USER 'query_svc'@'%' IDENTIFIED BY 'SET_A_LOCAL_SECRET';
GRANT SELECT ON logs_db.processed_logs TO 'query_svc'@'%';
FLUSH PRIVILEGES;
```

This initial version uses LIKE keyword search. MySQL FULLTEXT indexing/MATCH AGAINST is a subsequent optimization after confirming the actual schema and query needs.

Build from repository root: `mvn -pl query-service -am package`
