#!/usr/bin/env bash
# verify.sh - checks Phase 1 (identity-service :8081) and Phase 2 (api-gateway :8080)
# Both services + MySQL + Redis must be running. Each run creates 2 throw-away tenants.
#   PLATFORM_PW=...   bootstrap admin password (default Root123)
#   INTERNAL_TOKEN=.. identity internal token  (default dev-internal-token)

ID=http://localhost:8081
GW=http://localhost:8080
PW=${PLATFORM_PW:-Root123}
INTERNAL=${INTERNAL_TOKEN:-dev-internal-token}
S1="acme$(date +%s)"
S2="gwco$(date +%s)"
ADMIN_PW='S3cretPassw0rd'
pass=0; fail=0

check() { if [ "$2" = "$3" ]; then echo "PASS  $1"; pass=$((pass+1)); else echo "FAIL  $1   (expected: $2, got: $3)"; fail=$((fail+1)); fi; }
http()  { curl -s -o /dev/null -w "%{http_code}" "$@"; }
auth()  { echo "Authorization: Bearer $1"; }
bool()  { [ "$1" = "true" ] && echo true || echo false; }
login() { curl -s "$1/api/v1/auth/login" -H 'Content-Type: application/json' \
            -d "{\"tenantSlug\":\"$2\",\"email\":\"$3\",\"password\":\"$4\"}" | jq -r '.accessToken // empty'; }
nonempty() { [ -n "$1" ] && echo true || echo false; }
JSON='Content-Type: application/json'

echo "=============== PHASE 1: identity-service (8081, direct) ==============="
check "health" 200 "$(http $ID/actuator/health)"
JWKS=$(curl -s $ID/.well-known/jwks.json)
check "jwks has kid" true "$(echo "$JWKS" | jq '.keys[0].kid != null')"
check "jwks has NO private key (d)" true "$(echo "$JWKS" | jq '.keys[0].d == null')"

PT=$(login $ID platform admin@platform.local "$PW")
check "platform admin login returns a token" true "$(nonempty "$PT")"
CLAIMS=$(echo "$PT" | jq -R 'split(".")[1] | gsub("-";"+") | gsub("_";"/") | @base64d | fromjson')
check "token issuer"        "http://localhost:8081" "$(echo "$CLAIMS" | jq -r .iss)"
check "token role"          PLATFORM_ADMIN          "$(echo "$CLAIMS" | jq -r '.roles[0]')"
check "token has tenant_id" true                    "$(echo "$CLAIMS" | jq '.tenant_id != null')"

check "wrong password -> 401" 401 "$(http $ID/api/v1/auth/login -H "$JSON" -d '{"tenantSlug":"platform","email":"admin@platform.local","password":"nope-nope-nope"}')"
check "no token -> 401"       401 "$(http $ID/api/v1/tenants)"
check "garbage token -> 401"  401 "$(http $ID/api/v1/tenants -H 'Authorization: Bearer abc.def.ghi')"
check "platform admin GET /tenants -> 200"        200 "$(http $ID/api/v1/tenants -H "$(auth $PT)")"
check "platform admin GET /users/me -> 200"       200 "$(http $ID/api/v1/users/me -H "$(auth $PT)")"
check "platform admin GET /users -> 403 (role)"   403 "$(http $ID/api/v1/users -H "$(auth $PT)")"

BODY1="{\"name\":\"Acme\",\"slug\":\"$S1\",\"adminEmail\":\"boss@acme.io\",\"adminPassword\":\"$ADMIN_PW\",\"adminFullName\":\"Boss\"}"
check "create tenant -> 201"        201 "$(http $ID/api/v1/tenants -H "$(auth $PT)" -H "$JSON" -d "$BODY1")"
check "duplicate tenant slug -> 409" 409 "$(http $ID/api/v1/tenants -H "$(auth $PT)" -H "$JSON" -d "$BODY1")"
check "invalid slug -> 400"         400 "$(http $ID/api/v1/tenants -H "$(auth $PT)" -H "$JSON" -d '{"name":"X","slug":"A","adminEmail":"a@b.io","adminPassword":"S3cretPassw0rd","adminFullName":"X"}')"

TT=$(login $ID $S1 boss@acme.io "$ADMIN_PW")
check "tenant admin login returns a token" true "$(nonempty "$TT")"
check "tenant admin GET /users -> 200"     200 "$(http $ID/api/v1/users -H "$(auth $TT)")"
check "tenant admin GET /tenants/current -> 200" 200 "$(http $ID/api/v1/tenants/current -H "$(auth $TT)")"
check "tenant admin POST /tenants -> 403"  403 "$(http $ID/api/v1/tenants -H "$(auth $TT)" -H "$JSON" -d '{"name":"E","slug":"evilco","adminEmail":"e@e.io","adminPassword":"S3cretPassw0rd","adminFullName":"E"}')"
check "tenant admin sees only its own tenant's users" 1 "$(curl -s $ID/api/v1/users -H "$(auth $TT)" | jq '[.content[].tenantId] | unique | length')"

DEVB="{\"email\":\"dev@acme.io\",\"password\":\"$ADMIN_PW\",\"fullName\":\"Dev\",\"roles\":[\"DEVELOPER\"]}"
check "create DEVELOPER user -> 201"       201 "$(http $ID/api/v1/users -H "$(auth $TT)" -H "$JSON" -d "$DEVB")"
check "duplicate user email -> 409"        409 "$(http $ID/api/v1/users -H "$(auth $TT)" -H "$JSON" -d "$DEVB")"
check "grant PLATFORM_ADMIN via API -> 403" 403 "$(http $ID/api/v1/users -H "$(auth $TT)" -H "$JSON" -d "{\"email\":\"x@acme.io\",\"password\":\"$ADMIN_PW\",\"fullName\":\"X\",\"roles\":[\"PLATFORM_ADMIN\"]}")"
DT=$(login $ID $S1 dev@acme.io "$ADMIN_PW")
check "developer can log in"               true "$(nonempty "$DT")"
check "developer GET /users -> 403"        403 "$(http $ID/api/v1/users -H "$(auth $DT)")"

KJ=$(curl -s $ID/api/v1/api-keys -H "$(auth $TT)" -H "$JSON" -d '{"name":"billing"}')
APIKEY=$(echo "$KJ" | jq -r .apiKey); KID=$(echo "$KJ" | jq -r .id)
check "api key has lap_ prefix"            true "$(bool "$([[ $APIKEY == lap_* ]] && echo true)")"
LIST=$(curl -s $ID/api/v1/api-keys -H "$(auth $TT)")
check "key list never shows plaintext key" false "$(echo "$LIST" | jq --arg k "$APIKEY" 'tostring | contains($k)')"
check "key list never shows the hash"      false "$(echo "$LIST" | jq '.[0] | has("keyHash")')"
V() { curl -s $ID/internal/api-keys/validate -H "X-Internal-Token: $INTERNAL" -H "$JSON" -d "{\"apiKey\":\"$1\"}"; }
check "internal validate WITHOUT token -> 401" 401 "$(http $ID/internal/api-keys/validate -H "$JSON" -d "{\"apiKey\":\"$APIKEY\"}")"
check "validate active key -> valid"       true "$(V "$APIKEY" | jq .valid)"
check "validate garbage key -> invalid"    false "$(V lap_garbage_key | jq .valid)"
check "revoke key -> 204"                  204 "$(http -X DELETE $ID/api/v1/api-keys/$KID -H "$(auth $TT)")"
check "validate revoked key -> REVOKED"    REVOKED "$(V "$APIKEY" | jq -r .reason)"
check "audit log recorded API_KEY_CREATED" true "$(curl -s $ID/api/v1/audit-logs -H "$(auth $TT)" | jq '[.content[].action] | index("API_KEY_CREATED") != null')"

echo
echo "=============== PHASE 2: api-gateway (8080) ==============="
check "gateway health" 200 "$(http $GW/actuator/health)"
check "no token -> 401"          401 "$(http $GW/api/v1/tenants)"
check "401 body code"            UNAUTHORIZED "$(curl -s $GW/api/v1/tenants | jq -r .code)"
curl -s -D /tmp/v_h -o /tmp/v_b $GW/api/v1/tenants
HDR=$(tr -d '\r' </tmp/v_h | grep -i '^x-correlation-id:' | awk '{print $2}')
check "401 carries X-Correlation-Id header" true "$(nonempty "$HDR")"
check "401 body correlationId == header"    "$HDR" "$(jq -r .correlationId /tmp/v_b)"
check "garbage token -> 401"     401 "$(http $GW/api/v1/tenants -H 'Authorization: Bearer abc.def.ghi')"

GT=$(login $GW platform admin@platform.local "$PW")
check "login through gateway returns a token" true "$(nonempty "$GT")"
check "gateway: GET /tenants -> 200"          200 "$(http $GW/api/v1/tenants -H "$(auth $GT)")"
check "gateway: GET /users/me -> 200"         200 "$(http $GW/api/v1/users/me -H "$(auth $GT)")"
check "gateway: GET /users -> 403 (role, from identity)" 403 "$(http $GW/api/v1/users -H "$(auth $GT)")"
check "gateway: /internal NOT routed (valid token) -> 404" 404 "$(http -X POST $GW/internal/api-keys/validate -H "$(auth $GT)" -H "$JSON" -d '{"apiKey":"x"}')"
check "gateway: /internal without token -> 401"           401 "$(http -X POST $GW/internal/api-keys/validate -H "$JSON" -d '{"apiKey":"x"}')"
check "gateway: unknown path with token -> 404"           404 "$(http $GW/api/v1/nope -H "$(auth $GT)")"

ECHO=$(curl -s -D - -o /dev/null -H 'X-Correlation-Id: my-trace-12345' $GW/api/v1/tenants | tr -d '\r' | grep -i '^x-correlation-id:' | awk '{print $2}')
check "well-formed client correlation id is echoed" my-trace-12345 "$ECHO"
BAD=$(curl -s -D - -o /dev/null -H 'X-Correlation-Id: <bad>' $GW/api/v1/tenants | tr -d '\r' | grep -i '^x-correlation-id:' | awk '{print $2}')
check "suspicious correlation id is replaced by a UUID" true "$(bool "$([[ $BAD =~ ^[0-9a-f-]{36}$ ]] && echo true)")"

BODY2="{\"name\":\"GwCo\",\"slug\":\"$S2\",\"adminEmail\":\"boss@acme.io\",\"adminPassword\":\"$ADMIN_PW\",\"adminFullName\":\"Boss\"}"
check "gateway: create tenant -> 201 (409 would mean double-forward bug)" 201 "$(http $GW/api/v1/tenants -H "$(auth $GT)" -H "$JSON" -d "$BODY2")"
T2=$(login $GW $S2 boss@acme.io "$ADMIN_PW")
check "gateway: tenant admin GET /users -> 200"   200 "$(http $GW/api/v1/users -H "$(auth $T2)")"
check "gateway: tenant admin POST /tenants -> 403" 403 "$(http $GW/api/v1/tenants -H "$(auth $T2)" -H "$JSON" -d '{"name":"E","slug":"evilco2","adminEmail":"e@e.io","adminPassword":"S3cretPassw0rd","adminFullName":"E"}')"
check "gateway: TENANT_CREATED audited exactly once" 1 "$(curl -s $GW/api/v1/audit-logs -H "$(auth $T2)" | jq '[.content[] | select(.action=="TENANT_CREATED")] | length')"

# keep LAST: it exhausts the per-IP login bucket for a few seconds
got429=false
for i in $(seq 1 40); do
  c=$(http -X POST $GW/api/v1/auth/login -H "$JSON" -d '{}')
  if [ "$c" = "429" ]; then got429=true; break; fi
done
check "login rate limit -> 429 within 40 rapid calls" true "$got429"

echo
echo "RESULT: $pass passed, $fail failed"
[ "$fail" -eq 0 ]
