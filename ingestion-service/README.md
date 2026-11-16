# ingestion-service (Phase 3)

Port 8083. Accepts logs with an API key, validates, appends to Redis Stream `logs.raw`, answers **202**.

## Flow
1. Gateway: `POST /api/v1/logs` and `/api/v1/logs/batch` are public at the gateway (no JWT) and rate-limited per IP.
2. `ApiKeyAuthInterceptor` reads `X-API-Key` BEFORE the body is parsed.
3. `ApiKeyResolver`: Redis cache `apikey:v1:<sha256>` -> else identity `/internal/api-keys/validate`.
   valid = 5 min, invalid = 30 s (negative cache). Identity down => 503 (fail closed, never cached).
4. Bean-validation on the payload (batch max 500, all-or-nothing).
5. Backpressure: stream length >= `ingestion.max-stream-length` => 503 + `Retry-After`.
6. Idempotency: optional client `eventId`; `SET dedup:v1:<tenant>:<eventId> NX EX 24h` decides new vs duplicate (duplicates are reported, not re-queued). A failed XADD releases its dedup key so a retry is never lost.
7. `XADD logs.raw` with: eventId, tenantId, apiKeyId, receivedAt, timestamp, service, environment, severity, message, traceId?, correlationId?, attributes? (JSON). Field names live in common-core `LogFields`.
8. Revocation: identity deletes the cache entry right after the revoke transaction commits (TTL is the backstop).

## Run
```
mvn -pl ingestion-service spring-boot:run     # needs Redis + identity-service (8081)
```

## End-to-end demo (through the gateway, port 8080)
```
# 1) platform admin -> tenant (once) -> tenant admin token
PTOKEN=$(curl -s localhost:8080/api/v1/auth/login -H 'Content-Type: application/json' \
  -d '{"tenantSlug":"platform","email":"admin@platform.local","password":"<bootstrap pw>"}' | jq -r .accessToken)
curl -s localhost:8080/api/v1/tenants -H "Authorization: Bearer $PTOKEN" -H 'Content-Type: application/json' \
  -d '{"name":"Acme","slug":"acme","adminEmail":"boss@acme.io","adminPassword":"S3cretPassw0rd","adminFullName":"Acme Boss"}'
ATOKEN=$(curl -s localhost:8080/api/v1/auth/login -H 'Content-Type: application/json' \
  -d '{"tenantSlug":"acme","email":"boss@acme.io","password":"S3cretPassw0rd"}' | jq -r .accessToken)

# 2) create an API key (plaintext shown ONCE)
KEYJSON=$(curl -s localhost:8080/api/v1/api-keys -H "Authorization: Bearer $ATOKEN" -H 'Content-Type: application/json' -d '{"name":"billing-service"}')
APIKEY=$(echo $KEYJSON | jq -r .apiKey); KEYID=$(echo $KEYJSON | jq -r .id)

# 3) send logs (no JWT needed)
curl -i localhost:8080/api/v1/logs -H "X-API-Key: $APIKEY" -H 'Content-Type: application/json' \
  -d '{"service":"billing","environment":"prod","severity":"ERROR","message":"payment failed","traceId":"t-1","attributes":{"orderId":42}}'
curl -i localhost:8080/api/v1/logs/batch -H "X-API-Key: $APIKEY" -H 'Content-Type: application/json' \
  -d '{"events":[{"service":"a","environment":"prod","severity":"INFO","message":"m1"},{"service":"b","environment":"prod","severity":"WARN","message":"m2"}]}'

# 4) look inside Redis
docker exec -it <redis-container> redis-cli XLEN logs.raw
docker exec -it <redis-container> redis-cli XRANGE logs.raw - + COUNT 1
docker exec -it <redis-container> redis-cli --scan --pattern 'apikey:v1:*'

# 5) revoke -> next call must be 401 immediately (cache evicted)
curl -i -X DELETE localhost:8080/api/v1/api-keys/$KEYID -H "Authorization: Bearer $ATOKEN"
curl -i localhost:8080/api/v1/logs -H "X-API-Key: $APIKEY" -H 'Content-Type: application/json' \
  -d '{"service":"billing","environment":"prod","severity":"ERROR","message":"after revoke"}'
```

## Phase 3 checklist
- [x] Single + batch endpoints, 202
- [x] API key auth before body parsing, tenant only from key
- [x] Redis cache (positive + negative) in front of identity, fail closed
- [x] Revocation cache invalidation (identity side)
- [x] Validation (sizes, batch <= 500, timestamp not > 24h ahead, attributes <= 8 KB)
- [x] Backpressure (503 + Retry-After)
- [x] Idempotency by (tenant, eventId); correlation id in logs, headers and error bodies; 5 MB body cap (declared + streaming)
- [x] Unit + Testcontainers tests
- [ ] Known limits: logs.raw backlog metric needs Phase 4 to XDEL after XACK; no per-tenant quota yet (gateway limits per IP only); batch uses one XADD per event (pipeline later); delivery is at-least-once (server-assigned ids cannot be de-duplicated)
