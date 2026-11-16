#!/usr/bin/env bash

set -u

ROOT="$(cd "$(dirname "$0")" && pwd)"
cd "$ROOT"

REPORT_DIR="$ROOT/test-reports"
mkdir -p "$REPORT_DIR"

REPORT="$REPORT_DIR/phase-1-2-3-audit-$(date +%Y%m%d-%H%M%S).txt"

exec > >(tee "$REPORT") 2>&1

section() {
  echo
  echo "=================================================="
  echo "$1"
  echo "=================================================="
}

section "PROJECT AUDIT: PHASE 1, 2 AND 3"
echo "Date: $(date)"
echo "Project: $ROOT"

section "1. GIT STATUS"
if git rev-parse --is-inside-work-tree >/dev/null 2>&1; then
  git status --short
else
  echo "Git repository not detected."
fi

section "2. MODULE STRUCTURE"
find . -maxdepth 2 -name pom.xml \
  -not -path '*/target/*' -print | sort

section "3. ALL JAVA SOURCE FILES"
find common-core common-web identity-service api-gateway ingestion-service \
  -type f -name '*.java' 2>/dev/null | sort

section "4. ALL API ENDPOINT MAPPINGS"
grep -R -nE \
  '@(RequestMapping|GetMapping|PostMapping|PutMapping|PatchMapping|DeleteMapping)' \
  identity-service/src/main/java \
  api-gateway/src/main/java \
  ingestion-service/src/main/java 2>/dev/null || true

section "5. SECURITY CONFIGURATION"
grep -R -nE \
  'SecurityFilterChain|SecurityWebFilterChain|EnableWebSecurity|EnableReactiveMethodSecurity|PreAuthorize|PostAuthorize|JwtDecoder|JwtEncoder|JwtAuthenticationConverter|JWKS|jwk-set-uri|issuer-uri|X-API-KEY|X-Tenant-Id|RequestRateLimiter' \
  identity-service/src/main/java \
  api-gateway/src/main/java \
  ingestion-service/src/main/java 2>/dev/null || true

section "6. DATABASE ENTITIES AND REPOSITORIES"
find identity-service ingestion-service \
  -type f \( -name '*Entity.java' -o -name '*Repository.java' \
  -o -name '*Migration*.java' -o -name 'V*.sql' \) \
  2>/dev/null | sort

section "7. FLYWAY MIGRATIONS"
find identity-service ingestion-service \
  -path '*/db/migration/*' -type f 2>/dev/null | sort

section "8. REDIS AND STREAM IMPLEMENTATION"
grep -R -nE \
  'RedisTemplate|StringRedisTemplate|StreamOperations|opsForStream|XADD|XREAD|XACK|XGROUP|StreamMessageListenerContainer|LOGS_RAW|logs\.raw|dedup|idempotency' \
  common-core ingestion-service api-gateway 2>/dev/null || true

section "9. EXISTING TEST FILES"
find common-core common-web identity-service api-gateway ingestion-service \
  -path '*/src/test/*' -type f 2>/dev/null | sort

section "10. ENVIRONMENT VARIABLE NAMES (VALUES HIDDEN)"
if [ -f .env ]; then
  sed -n 's/^[[:space:]]*\([A-Za-z_][A-Za-z0-9_]*\)=.*/\1=PRESENT (value hidden)/p' .env
else
  echo ".env not found"
fi

section "11. DOCKER SERVICES"
docker compose ps 2>&1 || true

section "12. REDIS AUTHENTICATION"
if docker compose ps --services --filter status=running | grep -qx redis; then
  docker compose exec -T redis sh -c \
    'redis-cli -a "$REDIS_PASSWORD" --no-auth-warning ping' 2>&1 || true
else
  echo "Redis container is not running."
fi

section "13. SERVICE HEALTH CHECKS"
for port in 8081 8080 8083; do
  case "$port" in
    8081) name="Identity Service" ;;
    8080) name="API Gateway" ;;
    8083) name="Ingestion Service" ;;
  esac

  echo "--- $name (port $port) ---"
  curl -sS --max-time 5 \
    -w '\nHTTP_STATUS=%{http_code}\n' \
    "http://localhost:$port/actuator/health" || true
done

section "14. IDENTITY JWKS ENDPOINT"
curl -sS --max-time 5 \
  -w '\nHTTP_STATUS=%{http_code}\n' \
  http://localhost:8081/.well-known/jwks.json || true

section "15. MAVEN TESTS: ALL PHASE 1-3 MODULES"
mvn -B -pl common-core,common-web,identity-service,api-gateway,ingestion-service -am test
MAVEN_STATUS=$?

section "16. TEST REPORT SUMMARY"
find common-core common-web identity-service api-gateway ingestion-service \
  -path '*/target/surefire-reports/*.txt' -type f \
  -exec grep -H 'Tests run:' {} \; 2>/dev/null || true

section "17. MAVEN RESULT"
if [ "$MAVEN_STATUS" -eq 0 ]; then
  echo "MAVEN_TEST_RESULT=PASS"
else
  echo "MAVEN_TEST_RESULT=FAIL"
  echo "MAVEN_EXIT_CODE=$MAVEN_STATUS"
fi

section "18. AUDIT COMPLETE"
echo "Report saved at: $REPORT"
echo "Note: Source inventory and endpoint discovery are not proof of API integration coverage."
echo "Manual and automated end-to-end scenarios still need explicit test cases."

exit "$MAVEN_STATUS"
