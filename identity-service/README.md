# identity-service (Phase 1)

Tenants, users, roles, API keys, RS256 JWT + JWKS, audit logs.

## Setup
1. Add `<module>identity-service</module>` to the parent pom; align `<parent>` coordinates in this pom.
2. Create the DB (if Phase 0 compose doesn't already): `CREATE DATABASE IF NOT EXISTS identity_db;`
3. Run: `./mvnw -pl identity-service spring-boot:run`  (port 8081)
4. Swagger: http://localhost:8081/swagger-ui.html
5. Tests: `./mvnw -pl identity-service test` (unit) / `verify` or run `IdentityFlowIT` (needs Docker)

Optional stable signing key (otherwise an ephemeral key is generated at startup):
```
openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 -out private.pem
openssl rsa -in private.pem -pubout -out public.pem
export JWT_PRIVATE_KEY_PATH=$PWD/private.pem JWT_PUBLIC_KEY_PATH=$PWD/public.pem
```

## Try it
```
# bootstrap platform admin (dev defaults in application.yml)
TOKEN=$(curl -s localhost:8081/api/v1/auth/login -H 'Content-Type: application/json' \
  -d '{"tenantSlug":"platform","email":"admin@platform.local","password":"ChangeMe123!"}' | jq -r .accessToken)

# create tenant + first tenant admin
curl -s localhost:8081/api/v1/tenants -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '{"name":"Acme","slug":"acme","adminEmail":"boss@acme.io","adminPassword":"S3cretPassw0rd","adminFullName":"Acme Boss"}'

# JWKS
curl -s localhost:8081/.well-known/jwks.json

# internal API-key validation (what ingestion-service will call in Phase 3)
curl -s localhost:8081/internal/api-keys/validate -H 'X-Internal-Token: dev-internal-token' \
  -H 'Content-Type: application/json' -d '{"apiKey":"lap_..."}'
```

## Endpoints
| Method | Path | Who |
|---|---|---|
| POST | /api/v1/auth/login | public |
| GET | /.well-known/jwks.json | public |
| POST/GET | /api/v1/tenants | PLATFORM_ADMIN |
| GET | /api/v1/tenants/current | any authenticated |
| POST/GET | /api/v1/users | TENANT_ADMIN |
| GET | /api/v1/users/me | any authenticated |
| POST | /api/v1/api-keys | TENANT_ADMIN, DEVELOPER |
| GET | /api/v1/api-keys | TENANT_ADMIN, DEVELOPER, VIEWER |
| DELETE | /api/v1/api-keys/{id} | TENANT_ADMIN |
| GET | /api/v1/audit-logs | TENANT_ADMIN |
| POST | /internal/api-keys/validate | services (X-Internal-Token) |

## Phase 1 checklist
- [x] Flyway V1 schema (tenants, users, user_roles, api_keys, audit_logs)
- [x] BCrypt passwords, generic login errors, dummy-hash timing equalisation
- [x] RS256 JWT with `kid`, claims: sub, tenant_id, email, roles
- [x] JWKS endpoint (public key only)
- [x] Tenant id always taken from JWT, never from request
- [x] API keys: random, shown once, SHA-256 stored, revocable, expirable
- [x] Audit log (REQUIRES_NEW so failed logins persist)
- [x] Swagger, unit tests, Testcontainers integration test
- [ ] Deferred: refresh tokens, password change/reset, user disable endpoint, account lockout (gateway rate limiting arrives in Phase 2), key rotation / multiple JWKS keys, revocation event for ingestion cache (Phase 3)
