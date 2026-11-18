# Failure Recovery Guide

**Version:** 1.0  
**Platform:** Log Analytics & Alerting Engine

---

## 1. MySQL Failure

### Symptom
- Services fail to start or throw `Connection refused` / `CommunicationsException`
- `/actuator/health` shows `DOWN` for database

### Recovery Steps
1. Check MySQL container: `docker compose ps mysql`
2. Check MySQL logs: `docker compose logs mysql`
3. Restart MySQL: `docker compose restart mysql`
4. Wait for health check: `docker compose ps mysql` should show `healthy`
5. Restart affected services: `docker compose restart <service>`

### Data Safety
- MySQL data is persisted in `mysql-data` volume
- No data loss on restart

---

## 2. Redis Failure

### Symptom
- Ingestion-service returns 503 (backpressure)
- Processing/alert/notification services stop consuming streams
- `/actuator/health` shows `DOWN` for Redis

### Recovery Steps
1. Check Redis container: `docker compose ps redis`
2. Check Redis logs: `docker compose logs redis`
3. Restart Redis: `docker compose restart redis`
4. Wait for health check: `docker compose ps redis` should show `healthy`
5. Restart affected services: `docker compose restart <service>`

### Data Safety
- Redis data is persisted in `redis-data` volume (appendonly yes)
- UnACKed stream messages remain in PEL and will be recovered by `PendingRecoveryJob`

---

## 3. Service Crash / Restart

### Symptom
- Container shows `Exit Code` non-zero
- `/actuator/health` unreachable

### Recovery Steps
1. Check container status: `docker compose ps <service>`
2. Check logs: `docker compose logs <service>`
3. Restart: `docker compose restart <service>`
4. Verify health: `curl http://localhost:<port>/actuator/health`

### PEL Recovery
- After crash, unACKed messages stay in Redis Stream PEL
- `PendingRecoveryJob` runs every 15s and claims idle messages
- Messages are reprocessed automatically after `pendingIdleMs` (60s default)

---

## 4. Dead Letter Queue Recovery

### Symptom
- Events appear in `logs.dlq` Redis stream
- Processing failures are not being retried

### Recovery Steps
1. Check DLQ depth: `XLEN logs.dlq`
2. View DLQ entries: `XRANGE logs.dlq - +`
3. Manually republish valid entries:
   ```bash
   # Copy entry from DLQ back to raw stream
   docker compose exec redis redis-cli -a <PASSWORD> XADD logs.raw * field1 value1 field2 value2
   ```
4. ACK and remove processed DLQ entries:
   ```bash
   docker compose exec redis redis-cli -a <PASSWORD> XDEL logs.dlq <entry-id>
   ```

### Notification DLQ
- Failed notifications move to `FAILED` status, then `DEAD` after max attempts
- Retry via API: `POST /api/v1/notifications/{id}/retry` with `X-Tenant-Id` and `X-User-Roles: DEVELOPER`

---

## 5. Processing Backlog

### Symptom
- `logs.raw` Redis stream length growing
- `logs.processed` stream length growing
- Latency increasing

### Recovery Steps
1. Check consumer lag: `XPENDING logs.raw processing-group`
2. Verify processing-service is running: `docker compose ps processing-service`
3. Check processing logs for errors: `docker compose logs processing-service`
4. Restart processing-service if stuck: `docker compose restart processing-service`
5. PendingRecoveryJob will claim idle messages automatically

---

## 6. Notification Delivery Failure

### Symptom
- Notifications stuck in `FAILED` or `DEAD` status
- Webhook URLs returning errors

### Recovery Steps
1. Check failed notifications: `GET /api/v1/notifications?status=FAILED`
2. Check webhook URL validity and connectivity
3. Check SMTP/Slack configuration in NotificationProperties
4. Retry individual notification: `POST /api/v1/notifications/{id}/retry`
5. For DEAD notifications, investigate root cause then manually retry via API

---

## 7. JVM Out of Memory

### Symptom
- Service crashes with `OutOfMemoryError`
- Container killed by OOM killer

### Recovery Steps
1. Check JVM memory in Grafana dashboard
2. Restart service: `docker compose restart <service>`
3. If recurring, increase `JAVA_TOOL_OPTIONS` `-Xmx` value in docker-compose
4. Check for memory leaks in service logs

---

## 8. Disk Space Exhaustion

### Symptom
- Docker volumes full
- MySQL/Redis cannot write

### Recovery Steps
1. Check disk: `df -h`
2. Check Docker volume sizes: `docker system df`
3. Clean old Prometheus data: retention is 7 days (configured)
4. Clean old Docker images: `docker image prune`
5. Restart affected services

---

## 9. Contact Escalation

| Level | Role | Contact |
|-------|------|---------|
| L1 | Tenant Admin | Via identity-service |
| L2 | Platform Admin | Via identity-service bootstrap admin |
| L3 | Dev Team | Service logs + metrics from Grafana |
