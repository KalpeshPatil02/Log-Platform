# api-gateway (Phase 2)

Single entry point (port 8080). Reactive Spring Cloud Gateway - **no spring-boot-starter-web**.

## Request pipeline
1. `CorrelationIdWebFilter` (before security): accept a sane `X-Correlation-Id` or generate one; echoed on every response, including 401s.
2. Spring Security resource server: RS256 JWT verified against identity-service JWKS, issuer checked.
3. `TenantContextGlobalFilter`: strips client-sent `X-Tenant-Id`, `X-User-Id`, `X-User-Roles`, `X-Internal-Token`; sets the first three from the verified JWT claims.
4. `RequestRateLimiter` (Redis token bucket): login = 2/s burst 10 per IP; identity API = 20/s burst 40 per user.
5. Route to the service. `/internal/**` has no route, so it is unreachable from outside.

## Run
1. Add `<module>api-gateway</module>` to the parent pom; make sure `spring-cloud.version` matches your Boot version.
2. Start MySQL + Redis (compose), then identity-service (8081), then the gateway.
3. `mvn -pl api-gateway spring-boot:run`

```
TOKEN=$(curl -s localhost:8080/api/v1/auth/login -H 'Content-Type: application/json' \
  -d '{"tenantSlug":"platform","email":"admin@platform.local","password":"<bootstrap password>"}' | jq -r .accessToken)

curl -i localhost:8080/api/v1/tenants                              # 401 JSON + X-Correlation-Id
curl -i localhost:8080/api/v1/tenants -H "Authorization: Bearer $TOKEN"   # 200 via gateway
curl -i -X POST localhost:8080/internal/api-keys/validate -H "Authorization: Bearer $TOKEN"   # 404
for i in $(seq 1 30); do curl -s -o /dev/null -w "%{http_code} " -X POST localhost:8080/api/v1/auth/login \
  -H 'Content-Type: application/json' -d '{}'; done                # ...400 400 429 429
```
Rate-limit responses carry `X-RateLimit-Remaining` / `X-RateLimit-Replenish-Rate` / `X-RateLimit-Burst-Capacity`.

## Phase 2 checklist
- [x] Routes: login (public), identity API (JWT)
- [x] JWT verification via JWKS (cached, refetch on unknown kid)
- [x] Redis rate limiting (two policies)
- [x] Correlation ID generate/propagate/echo
- [x] Trusted tenant/user/roles headers, client spoofing stripped
- [x] Uniform JSON 401/403
- [x] Unit tests (2 filters) + Testcontainers IT (401, /internal 404, 429)
- [ ] Notes: RedisRateLimiter fails OPEN if Redis is down (design decision for later); client IP needs trusted X-Forwarded-For behind a load balancer (deployment phase); gateway only authenticates - role checks stay in each service (defence in depth)
