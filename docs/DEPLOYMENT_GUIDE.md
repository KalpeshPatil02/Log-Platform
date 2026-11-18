# Deployment Guide

**Version:** 1.0  
**Platform:** Log Analytics & Alerting Engine  
**Target:** Single-node Docker Compose (dev laptop, 12 GB RAM)

---

## 1. Prerequisites

| Requirement | Minimum | Recommended |
|-------------|---------|-------------|
| OS | Ubuntu 22.04+ | Ubuntu 22.04+ |
| RAM | 8 GB | 12 GB |
| Disk | 20 GB free | 50 GB free |
| Docker | 24.0+ | 24.0+ |
| Docker Compose | v2 | v2 |
| Maven | 3.9+ | 3.9+ |
| Java | 21 | 21 |

---

## 2. Build All Services

```bash
cd /home/kalpesh/Videos/log-platform

# Build all modules and run tests
mvn -B install -DskipTests

# Or build with tests
mvn -B verify
```

---

## 3. Environment Configuration

Copy `.env.example` to `.env` and configure:

```bash
cp .env.example .env
```

Required variables:
| Variable | Description | Example |
|----------|-------------|---------|
| MYSQL_ROOT_PASSWORD | MySQL root password | `Root@123` |
| IDENTITY_DB_PASSWORD | Identity service DB password | `identity-pass` |
| PROCESSING_DB_PASSWORD | Processing service DB password | `processing-pass` |
| QUERY_DB_PASSWORD | Query service DB password | `query-pass` |
| ALERT_DB_PASSWORD | Alert service DB password | `alert-pass` |
| NOTIFICATION_DB_PASSWORD | Notification service DB password | `notification-pass` |
| REDIS_PASSWORD | Redis password | `redis-pass` |
| IDENTITY_INTERNAL_TOKEN | Internal service token | `dev-internal-token` |
| IDENTITY_JWT_PRIVATE_KEY_PATH | Path to JWT private key | `/keys/private.pem` |
| IDENTITY_JWT_PUBLIC_KEY_PATH | Path to JWT public key | `/keys/public.pem` |
| GRAFANA_ADMIN_PASSWORD | Grafana admin password | `admin-pass` |
| SMTP_USERNAME | SMTP username (optional) | `user@gmail.com` |
| SMTP_PASSWORD | SMTP password (optional) | `app-password` |
| SLACK_WEBHOOK_URL | Slack webhook URL (optional) | `https://hooks.slack.com/...` |
| WEBHOOK_BASE_URL | Default webhook base URL | `https://example.com/webhook` |

Secrets are mounted from `./secrets` directory for JWT keys:
```bash
mkdir -p secrets
# Place private.pem and public.pem in secrets/
```

---

## 4. Start Platform

### Core Services Only

```bash
docker compose --env-file .env up -d
```

This starts: mysql, redis, identity-service, api-gateway, ingestion-service, processing-service, query-service, alert-service, notification-service

### With Monitoring

```bash
docker compose --env-file .env --profile monitoring up -d
```

This additionally starts: prometheus, grafana

### Specific Profiles Only

```bash
# Core only
docker compose --env-file .env --profile core up -d

# With alerting
docker compose --env-file .env --profile core --profile alerting up -d

# With notification
docker compose --env-file .env --profile core --profile notification up -d
```

---

## 5. Verify Deployment

```bash
# Check all containers
docker compose ps

# Check service health
curl http://localhost:8081/actuator/health
curl http://localhost:8083/actuator/health
curl http://localhost:8084/actuator/health
curl http://localhost:8085/actuator/health
curl http://localhost:8086/actuator/health

# Check Prometheus (if monitoring profile)
curl -H "X-Internal-Token: dev-internal-token" http://localhost:9090/-/healthy

# Check Grafana
curl http://localhost:3000/api/health
```

---

## 6. Stop Platform

```bash
# Stop all services
docker compose --env-file .env down

# Stop and remove volumes (WARNING: deletes all data)
docker compose --env-file .env down -v
```

---

## 7. Resource Limits

| Container | Memory Limit | Purpose |
|-----------|-------------|---------|
| mysql | 640 MB | Primary database |
| redis | 192 MB | Stream processing |
| identity-service | 384 MB | Auth service |
| api-gateway | 384 MB | Gateway |
| ingestion-service | 384 MB | Log ingestion |
| processing-service | 384 MB | Log processing |
| query-service | 384 MB | Log query |
| alert-service | 384 MB | Alert evaluation |
| notification-service | 384 MB | Notification delivery |
| prometheus | 256 MB | Metrics (monitoring profile) |
| grafana | 256 MB | Dashboards (monitoring profile) |

**Total estimated RAM:** ~3.5 GB (core), ~4.5 GB (with monitoring)

---

## 8. Upgrading

```bash
# 1. Pull new code
git pull

# 2. Rebuild
mvn -B install -DskipTests

# 3. Recreate containers
docker compose --env-file .env up -d --build

# 4. Verify
docker compose ps
```

---

## 9. Backup

```bash
# MySQL data
docker run --rm -v logplatform_mysql-data:/data -v $(pwd):/backup ubuntu tar czf /backup/mysql-backup.tar.gz -C /data .

# Redis data
docker run --rm -v logplatform_redis-data:/data -v $(pwd):/backup ubuntu tar czf /backup/redis-backup.tar.gz -C /data .

# Prometheus data
docker run --rm -v logplatform_prometheus-data:/data -v $(pwd):/backup ubuntu tar czf /backup/prometheus-backup.tar.gz -C /data .
```

---

## 10. Troubleshooting Deployment

| Issue | Solution |
|-------|----------|
| Container fails to start | Check `docker compose logs <service>` |
| MySQL connection refused | Wait for MySQL health check, then restart service |
| Redis connection refused | Wait for Redis health check, then restart service |
| JWT validation fails | Verify JWT keys in `./secrets/` directory |
| Prometheus cannot scrape | Verify `X-Internal-Token` matches `INTERNAL_TOKEN` |
| Grafana cannot connect to Prometheus | Verify Prometheus is reachable at `http://prometheus:9090` |
