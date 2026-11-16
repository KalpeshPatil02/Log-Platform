#!/usr/bin/env bash
set -Eeuo pipefail

cd "$(dirname "$0")/.."

PASS=0
FAIL=0
SKIP=0
PREFIX="p4-$(date +%s)"
MYSQL_STOPPED=0

pass() { echo "PASS: $1"; PASS=$((PASS + 1)); }
fail() { echo "FAIL: $1"; FAIL=$((FAIL + 1)); }
skip() { echo "SKIP: $1"; SKIP=$((SKIP + 1)); }

cleanup() {
  if [[ "$MYSQL_STOPPED" == "1" ]]; then
    echo "Restoring MySQL..."
    docker compose start mysql || true
    docker compose up -d mysql || true
  fi
}
trap cleanup EXIT

redis() {
  docker compose exec -T redis sh -c \
    'REDISCLI_AUTH="$REDIS_PASSWORD" redis-cli "$@"' sh "$@"
}

add_event() {
  local tenant="$1" event="$2" severity="$3" received="$4"
  redis XADD logs.raw '*' \
    tenantId "$tenant" eventId "$event" apiKeyId test-api-key \
    receivedAt "$received" timestamp 2026-11-03T11:29:00Z \
    service payment-service environment development \
    severity "$severity" message "Phase 4 reliability test" \
    traceId "$event-trace" correlationId "$event-correlation" >/dev/null
}

contains() {
  redis XRANGE "$1" - + | grep -Fq "$2"
}

echo "======================================"
echo " PHASE 4 RELIABILITY TEST"
echo " Test prefix: $PREFIX"
echo "======================================"

docker compose up -d mysql redis processing-service

echo "Waiting for services..."
sleep 8

if ! docker compose ps --status running --services | grep -qx processing-service; then
  fail "Processing service is not running"
  exit 1
fi
pass "Processing service is running"

# 1. Normal event
NORMAL="${PREFIX}-normal"
add_event test-tenant "$NORMAL" INFO 2026-11-03T11:29:00Z
sleep 3
if contains logs.processed "$NORMAL"; then
  pass "Normal event reached logs.processed"
else
  fail "Normal event was not published"
fi

# 2. Duplicate event
DUP="${PREFIX}-duplicate"
add_event test-tenant "$DUP" INFO 2026-11-03T11:29:00Z
sleep 2
add_event test-tenant "$DUP" INFO 2026-11-03T11:29:00Z
sleep 4
echo "Duplicate event submitted twice: $DUP"
echo "Verify DB uniqueness using the SQL query in the final report."

# 3. Invalid severity
BAD_SEV="${PREFIX}-bad-severity"
add_event test-tenant "$BAD_SEV" UNKNOWN 2026-11-03T11:29:00Z
sleep 3
if contains logs.dlq "$BAD_SEV"; then
  pass "Invalid severity reached DLQ"
else
  fail "Invalid severity did not reach DLQ"
fi

# 4. Invalid timestamp
BAD_TIME="${PREFIX}-bad-time"
add_event test-tenant "$BAD_TIME" ERROR not-a-timestamp
sleep 3
if contains logs.dlq "$BAD_TIME"; then
  pass "Invalid timestamp reached DLQ"
else
  fail "Invalid timestamp did not reach DLQ"
fi

# 5. Tenant separation: two distinct tenant/event pairs
TENANT_A="${PREFIX}-tenant-a"
TENANT_B="${PREFIX}-tenant-b"
add_event tenant-a "$TENANT_A" INFO 2026-11-03T11:29:00Z
add_event tenant-b "$TENANT_B" INFO 2026-11-03T11:29:00Z
sleep 4
if contains logs.processed "$TENANT_A" && contains logs.processed "$TENANT_B"; then
  pass "Both tenant events were processed"
else
  fail "One or both tenant events were not processed"
fi
echo "Tenant isolation still requires DB query verification."

# 6. MySQL outage and recovery
OUTAGE="${PREFIX}-mysql-recovery"
echo "Stopping MySQL for outage test..."
docker compose stop mysql
MYSQL_STOPPED=1

add_event test-tenant "$OUTAGE" INFO 2026-11-03T11:29:00Z
sleep 4

PENDING="$(redis XPENDING logs.raw processing-group | head -n 1 | tr -d '\r')"
if [[ "$PENDING" =~ ^[0-9]+$ ]] && (( PENDING > 0 )); then
  pass "Message became pending during MySQL outage"
else
  fail "Could not verify pending message during MySQL outage"
fi

echo "Restarting MySQL..."
docker compose start mysql
MYSQL_STOPPED=0

for i in $(seq 1 30); do
  if docker compose exec -T mysql sh -c \
    'mysqladmin ping -h 127.0.0.1 -uroot -p"$MYSQL_ROOT_PASSWORD" --silent' \
    >/dev/null 2>&1; then
    break
  fi
  sleep 2
done

sleep 20

if contains logs.processed "$OUTAGE"; then
  pass "Pending event recovered and published after MySQL restart"
else
  fail "Pending event recovery not verified after MySQL restart"
fi

# 7. Final pending count
echo
echo "Final XPENDING summary:"
redis XPENDING logs.raw processing-group || true

echo
echo "DLQ test records:"
redis XRANGE logs.dlq - + | grep -E "$BAD_SEV|$BAD_TIME" || true

echo
echo "======================================"
echo "RESULT: PASS=$PASS FAIL=$FAIL SKIP=$SKIP"
echo "======================================"

echo
echo "NOTE: DB uniqueness, tenant isolation, and outbox retry/backoff"
echo "need SQL/fault-injection verification; this script does not"
echo "claim those tests passed."
