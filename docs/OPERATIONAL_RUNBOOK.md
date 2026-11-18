# Operational Runbook — Log Analytics & Alerting Engine

**Version:** 1.0  
**Platform:** Log Platform Microservices  
**Hardware:** Ubuntu laptop, 12 GB RAM, ~50 GB SSD

---

## 1. Service Inventory

| Service | Port | Purpose | Profile |
|---------|------|---------|---------|
| identity-service | 8081 | Users, tenants, API keys, JWT | `core` |
| api-gateway | 8080 | Spring Cloud Gateway, auth, rate limiting | — |
| ingestion-service | 8083 | Log ingestion, API-key auth, Redis producer | `ingestion` |
| processing-service | 8084 | Redis stream consumer, MySQL write | `processing` |
| query-service | 8085 | Log search/retrieval | — |
| alert-service | 8086 | Rule evaluation, incidents, outbox | `alerting` |
| notification-service | 8086 | Notification delivery, retry, DLQ | `notification` |
| Prometheus | 9090 | Metrics collection | `monitoring` |
| Grafana | 3000 | Dashboards | `monitoring` |

---

## 2. Starting the Platform

```bash
# 1. Copy env example and fill in secrets
cp .env.example .env
# Edit .env with your passwords

# 2. Build all services
mvn -B install -DskipTests

# 3. Start infrastructure + core services
docker compose --env-file .env up -d

# 4. Start monitoring (optional)
docker compose --env-file .env --profile monitoring up -d prometheus grafana

# 5. Verify all containers healthy
docker compose ps
```

---

## 3. Access URLs

| Service | URL | Auth |
|---------|-----|------|
| API Gateway | http://localhost:8080 | JWT (from identity-service) |
| Identity Service | http://localhost:8081 | JWT |
| Ingestion API | http://localhost:8083/api/v1/logs | X-API-KEY header |
| Query API | http://localhost:8085/api/v1/logs | JWT |
| Alert Rules | http://localhost:8086/api/v1/alert-rules | JWT |
| Incidents | http://localhost:8086/api/v1/incidents | JWT |
| Notifications | http://localhost:8086/api/v1/notifications | JWT + X-Tenant-Id |
| Prometheus | http://localhost:9090 | X-Internal-Token header |
| Grafana | http://localhost:3000 | admin / GRAFANA_ADMIN_PASSWORD |

---

## 4. Common Operational Tasks

### 4.1 Check Service Health

```bash
# All services expose /actuator/health
curl http://localhost:8081/actuator/health   # identity-service
curl http://localhost:8083/actuator/health   # ingestion-service
curl http://localhost:8084/actuator/health   # processing-service
curl http://localhost:8085/actuator/health   # query-service
curl http://localhost:8086/actuator/health   # alert-service + notification-service

# Check Docker container health
docker compose ps
```

### 4.2 View Service Logs

```bash
# All service logs
docker compose logs -f <service-name>

# Example
docker compose logs -f processing-service
docker compose logs -f notification-service
```

### 4.3 Restart a Service

```bash
docker compose restart <service-name>
```

### 4.4 Check Dead Letter Queue

```bash
# Check DLQ depth (Redis stream)
docker compose exec redis redis-cli -a <REDIS_PASSWORD> XLEN logs.dlq

# View DLQ entries
docker compose exec redis redis-cli -a <REDIS_PASSWORD> XRANGE logs.dlq - +
```

### 4.5 Replay DLQ Entries

DLQ entries are stored in Redis `logs.dlq` stream. To replay:
1. Identify the failed entry ID
2. Manually republish to the appropriate stream (`logs.raw` or `notifications.out`)
3. ACK the DLQ entry

### 4.6 Check Pending Entries List (PEL)

```bash
# Check pending messages for a consumer group
docker compose exec redis redis-cli -a <REDIS_PASSWORD> XPENDING logs.raw processing-group
```

---

## 5. Alert Response Procedures

| Alert | Severity | First Response |
|-------|----------|---------------|
| ServiceDown | Critical | Check `docker compose ps`, view service logs, restart if needed |
| HighLatency | Warning | Check JVM metrics, DB connection pool, increase resources if needed |
| HighErrorRate | Warning | Check service logs for error stack traces, verify downstream dependencies |
| JVMMemoryPressure | Warning | Check heap usage in Grafana, consider restart or increase `-Xmx` |
| DBConnectionExhaustion | Critical | Check HikariCP pool, restart service to release connections |
| RedisDown | Critical | Check Redis container, verify `redis-cli ping` |
| ProcessingBacklog | Warning | Check consumer lag, verify processing-service is running |
| NotificationFailure | Warning | Check webhook URLs, SMTP config, notification-service logs |
| AbnormalIngestion | Warning | Check ingestion-service logs, verify API keys are valid |

---

## 6. Metrics Reference (Prometheus)

| Metric | Description | Labels |
|--------|-------------|--------|
| `up` | Service availability (1=up, 0=down) | application |
| `http_server_requests_total` | HTTP request count | application, status, method, uri |
| `http_server_requests_seconds` | HTTP request duration histogram | application, status, method, uri |
| `jvm_memory_used_bytes` | JVM memory used | application, area (heap/non-heap) |
| `jvm_gc_pause_seconds` | GC pause time | application, action, cause |
| `process_cpu_usage` | CPU usage ratio | application |
| `hikaricp_connections_active` | Active DB connections | application |
| `hikaricp_connections_pending` | Pending DB connections | application |
| `logs_ingested_total` | Logs ingested counter | application |
| `logs_ingested_rejected` | Logs rejected counter | application |
| `logs_processed_total` | Logs processed counter | application |
| `logs_processed_failed` | Logs failed counter | application |
| `alert_evaluation_duration_seconds` | Alert evaluation timer | application |
| `alerts_incidents_created` | Incidents created counter | application |
| `notifications_delivered_total` | Notifications delivered counter | application, channel |
| `notifications_delivered_failed` | Notifications failed counter | application, channel |

---

## 7. Security Notes

- `/actuator/prometheus` requires `X-Internal-Token` header
- Grafana admin password from `GRAFANA_ADMIN_PASSWORD` env var
- Never expose Grafana/Prometheus to public internet in production
- JWT tokens are short-lived (15m access tokens)
- API keys are validated against identity-service

---

## 8. Contacts & Escalation

- Platform Admin: identity-service bootstrap admin
- On-call: Check incident-service for active incidents
- Escalation: Tenant Admin → Platform Admin → Dev Team
