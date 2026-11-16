# Implementation checklist
- [x] Phase 0: parent pom, common-core, common-web, compose (MySQL + Redis), CI skeleton, env check script
- [x] Phase 1: identity-service (tenants, users, RBAC, API keys, JWT RS256, audit logs)
- [ ] Phase 2: api-gateway (JWT validation, rate limiting, correlation id, tenant header injection)
- [ ] Phase 3: ingestion-service (API-key auth, batch validation, logs.raw)
- [ ] Phase 4: processing-service (parse, normalize, enrich, logs_db, logs.processed, DLQ)
- [ ] Phase 5: query-service (search, filters, analytics)
- [ ] Phase 6: alert-service (rules, windows, dedup/cooldown, incidents)
- [ ] Phase 7: notification-service (email, Slack, webhook, retry, delivery tracking)
- [ ] Phase 8: Prometheus/Grafana, sample client, e2e tests, diagrams, final README
