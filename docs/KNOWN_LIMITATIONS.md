# Known Limitations

**Version:** 1.0  
**Platform:** Log Analytics & Alerting Engine

---

## 1. Architecture Limitations

| Limitation | Description | Workaround |
|------------|-------------|------------|
| No distributed tracing | Correlation ID exists at gateway but is not propagated to downstream service calls beyond the basic field in stream messages | Use correlation ID in logs for manual tracing |
| No distributed service discovery | Services communicate via hardcoded URLs/env vars, no Eureka/Consul | Update env vars when service locations change |
| No message broker (Kafka) | Redis Streams used for inter-service communication; limited throughput compared to Kafka | Suitable for dev/small production; monitor stream length |
| Single MySQL instance | No replication or failover | Acceptable for dev; add MySQL replica for production |
| Single Redis instance | No Redis Sentinel/Cluster | Acceptable for dev; add Redis Sentinel for HA |

---

## 2. Monitoring Limitations

| Limitation | Description | Workaround |
|------------|-------------|------------|
| No detailed Redis metrics | Spring Boot Actuator provides Redis health but not detailed metrics (memory, connections, keyspace) | Add redis-exporter for detailed Redis metrics |
| No detailed MySQL metrics | Spring Boot Actuator provides DB health but not detailed MySQL metrics (queries, slow queries, innodb stats) | Add mysqld-exporter for detailed MySQL metrics |
| No container-level metrics | cAdvisor not deployed | Add cAdvisor if container resource monitoring needed |
| Log aggregation not centralized | Logs remain local to each container | Use `docker compose logs` or add ELK/EFK stack |
| Prometheus retention limited to 7 days | To save disk space on laptop | Increase retention if more disk available |

---

## 3. Security Limitations

| Limitation | Description | Workaround |
|------------|-------------|------------|
| Prometheus endpoint protected by shared token | Single `X-Internal-Token` for all services | Rotate token periodically; consider per-service tokens |
| Grafana admin password in env var | Password stored in `.env` file | Use Docker secrets or external secret manager |
| No API key rotation | Long-lived API keys | Manual rotation via identity-service API |
| No rate limiting on notification API | Could be abused | Add rate limiter to notification controller |
| JWT in access tokens | 15-minute TTL | Acceptable for most use cases |

---

## 4. Reliability Limitations

| Limitation | Description | Workaround |
|------------|-------------|------------|
| No circuit breaker on Identity client | Cascading failures possible if identity-service is down | Add Resilience4j circuit breaker |
| No graceful stream pause on shutdown | Minor risk of message loss during shutdown | Spring Boot graceful shutdown handles most cases |
| No DLQ replay automation | Failed notifications require manual retry | Build DLQ replay job |
| Processing backlog not auto-scaled | Single processing-service consumer | Add more consumer instances with different consumer names |

---

## 5. Scalability Limitations

| Limitation | Description | Workaround |
|------------|-------------|------------|
| Single consumer per stream group | Throughput limited by single consumer | Add more instances with unique consumer names |
| No horizontal scaling of MySQL | All services share single MySQL instance | Add read replicas for query-service |
| No sharding | All tenants in same database | Acceptable for dev; add tenant sharding for large scale |

---

## 6. Hardware Constraints

| Constraint | Impact | Mitigation |
|------------|--------|------------|
| 12 GB RAM | Limited headroom for monitoring stack | Keep Prometheus/Grafana lightweight (~512 MB combined) |
| ~50 GB SSD | Limited disk for metrics retention | Prometheus retention set to 7 days |
| Single CPU | Concurrent service startup may be slow | Start services in phases using profiles |

---

## 7. Missing Features (Future Phases)

| Feature | Priority | Description |
|---------|----------|-------------|
| Circuit breaker | High | Resilience4j on IdentityClient and external calls |
| Distributed tracing | Medium | OpenTelemetry integration |
| DLQ replay automation | Medium | Scheduled job to replay DLQ entries |
| API key rotation | Medium | Automated key expiration and rotation |
| Log aggregation | Low | ELK/EFK stack for centralized logs |
| Read replicas | Low | MySQL read replicas for query-service |
| Auto-scaling | Low | Kubernetes migration for horizontal scaling |

---

## 8. Test Coverage Gaps

| Gap | Severity | Description |
|-----|----------|-------------|
| No cross-service E2E test | High | All services together not tested |
| No performance test | High | No load/throughput measurements |
| No failure injection test | Medium | DB/Redis failure mid-flow not tested |
| api-gateway no tests | Medium | Gateway filters not tested |
| query-service no tests | Medium | Query API not tested |
| No DLQ replay test | Medium | DLQ recovery not automated |
