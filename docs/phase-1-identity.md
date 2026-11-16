# Phase 1 - identity-service (port 8081, schema identity_db)
Responsibilities: tenants, users, roles (VIEWER < DEVELOPER < ADMIN), API keys, JWT (RS256), audit logs, JWKS.
Deferred on purpose: Redis cache invalidation for revoked keys comes in Phase 3, together with the ingestion-side key cache.

## Tables
tenants(id, name, slug UQ, status) | users(id, tenant_id FK, email UQ, password_hash, full_name, role, enabled)
api_keys(id, tenant_id FK, name, application, key_prefix, key_hash UQ, status, created_by, revoked_at)
audit_logs(id, tenant_id, actor_user_id, action, resource_type, resource_id, details, ip_address, created_at; indexes: tenant+time, tenant+action+time)

## API
| Method and path | Who | Notes |
|---|---|---|
| POST /v1/auth/register-tenant | public | creates tenant + first ADMIN, returns JWT |
| POST /v1/auth/login | public | generic 401 on any failure |
| GET /v1/users/me | any logged-in user | |
| GET, POST /v1/users and PATCH /v1/users/{id} | ADMIN | cannot change yourself |
| POST /v1/api-keys, DELETE /v1/api-keys/{id} | ADMIN | plain key returned once |
| GET /v1/api-keys | ADMIN, DEVELOPER | prefix only |
| GET /v1/audit-logs?action=&page=&size= | ADMIN | tenant-scoped, paged |
| GET /internal/v1/api-keys/verify (X-API-KEY + X-Internal-Token) | services | returns tenantId, apiKeyId, application |
| GET /.well-known/jwks.json | public | public key for the gateway |
Swagger UI: http://localhost:8081/swagger-ui.html

## Security decisions
Passwords BCrypt. API keys stored as SHA-256 only. Tenant id always comes from the JWT and every query is tenant-scoped (another tenant's resource is a 404). Unknown email and wrong password give the same error and similar timing. The internal endpoint needs a shared secret and is never routed by the gateway.

## Try it
    curl -s -X POST localhost:8081/v1/auth/register-tenant -H 'Content-Type: application/json' \
      -d '{"tenantName":"Acme","adminEmail":"admin@acme.test","adminPassword":"Passw0rd!Passw0rd","adminName":"Admin"}'
    TOKEN=<accessToken from the response>
    curl -s -X POST localhost:8081/v1/api-keys -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' -d '{"name":"prod","application":"checkout"}'
    curl -s localhost:8081/internal/v1/api-keys/verify -H 'X-API-KEY: lpk_...' -H 'X-Internal-Token: <value from .env>'

## Known limits
No refresh tokens (60 min access token), no login throttling here (the gateway rate-limits in Phase 2), audit IP is the direct peer address (X-Forwarded-For handling comes with the gateway).
