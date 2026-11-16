#!/usr/bin/env bash
set -Eeuo pipefail

PASS=0
FAIL=0
REDIS_STOPPED=0

TENANT_EVENT="tenant-isolation-$(date +%s)-$$"
TENANT_A="tenant-a-${TENANT_EVENT}"
TENANT_B="tenant-b-${TENANT_EVENT}"
RETRY_KEY="retry-verification-$(date +%s)-$$"

sql() {
  docker compose exec -T mysql sh -c \
    'mysql -N -B -uroot -p"$MYSQL_ROOT_PASSWORD" logs_db' <<< "$1"
}

result() {
  if [[ "$2" == "PASS" ]]; then
    echo "PASS: $1"
    PASS=$((PASS + 1))
  else
    echo "FAIL: $1"
    FAIL=$((FAIL + 1))
  fi
}

cleanup() {
  if [[ "$REDIS_STOPPED" -eq 1 ]]; then
    echo "Restoring Redis..."
    docker compose start redis >/dev/null 2>&1 || true
  fi
}
trap cleanup EXIT

echo "===== PHASE 4 FINAL VERIFICATION ====="

# 1. Tenant isolation: same event ID, different tenants
echo
echo "[1/2] Testing tenant isolation..."

for tenant in "$TENANT_A" "$TENANT_B"; do
  docker compose exec -T redis sh -c \
    "REDISCLI_AUTH=\"\$REDIS_PASSWORD\" redis-cli XADD logs.raw '*' \
    tenantId '$tenant' eventId '$TENANT_EVENT' \
    apiKeyId test-api-key \
    receivedAt 2026-11-03T15:40:00Z \
    timestamp 2026-11-03T15:40:00Z \
    service tenant-isolation-test environment development \
    severity INFO message tenant-isolation-check \
    traceId test-trace correlationId test-correlation" >/dev/null
done

TENANT_OK=0
for i in $(seq 1 25); do
  COUNT=$(sql "SELECT COUNT(*) FROM processed_logs
    WHERE event_id='$TENANT_EVENT'
    AND tenant_id IN ('$TENANT_A','$TENANT_B');" | tr -d '[:space:]')
  if [[ "$COUNT" == "2" ]]; then
    TENANT_OK=1
    break
  fi
  sleep 1
done

if [[ "$TENANT_OK" -eq 1 ]]; then
  result "Same event ID stored separately for two tenants" PASS
  sql "SELECT tenant_id,event_id FROM processed_logs
       WHERE event_id='$TENANT_EVENT'
       ORDER BY tenant_id;"
else
  result "Tenant isolation (expected 2 separate rows, got ${COUNT:-0})" FAIL
fi

# 2. Outbox retry and backoff
echo
echo "[2/2] Testing outbox retry/backoff..."

sql "INSERT INTO outbox_events
(event_key,payload,status,attempt_count,next_attempt_at,created_at)
VALUES ('$RETRY_KEY',
JSON_OBJECT('test','outbox-retry-verification'),
'PENDING',0,NOW(6),NOW(6));" >/dev/null

docker compose stop redis >/dev/null
REDIS_STOPPED=1

ATTEMPT=""
for i in $(seq 1 15); do
  ATTEMPT=$(sql "SELECT attempt_count FROM outbox_events
                 WHERE event_key='$RETRY_KEY';" | tr -d '[:space:]')
  if [[ "${ATTEMPT:-0}" -ge 1 ]]; then
    break
  fi
  sleep 1
done

BACKOFF_OK=0
if [[ "${ATTEMPT:-0}" -ge 1 ]]; then
  FUTURE=$(sql "SELECT IF(next_attempt_at > NOW(6),1,0)
                FROM outbox_events WHERE event_key='$RETRY_KEY';" |
           tr -d '[:space:]')
  if [[ "$FUTURE" == "1" ]]; then
    BACKOFF_OK=1
  fi
fi

if [[ "$BACKOFF_OK" -eq 1 ]]; then
  result "Redis failure increments attempt_count and schedules future retry" PASS
else
  result "Retry/backoff scheduling (attempt=${ATTEMPT:-0})" FAIL
fi

docker compose start redis >/dev/null
REDIS_STOPPED=0

PUBLISHED_OK=0
for i in $(seq 1 30); do
  STATUS=$(sql "SELECT status FROM outbox_events
                WHERE event_key='$RETRY_KEY';" | tr -d '[:space:]')
  if [[ "$STATUS" == "PUBLISHED" ]]; then
    PUBLISHED_OK=1
    break
  fi
  sleep 1
done

if [[ "$PUBLISHED_OK" -eq 1 ]]; then
  result "Outbox event successfully published after Redis recovery" PASS
else
  result "Outbox event published after Redis recovery (status=${STATUS:-missing})" FAIL
fi

sql "SELECT event_key,status,attempt_count,next_attempt_at,last_error
     FROM outbox_events WHERE event_key='$RETRY_KEY';"

echo
echo "===== FINAL REPORT ====="
echo "PASS: $PASS"
echo "FAIL: $FAIL"

if [[ "$FAIL" -eq 0 ]]; then
  echo "Both verification areas passed."
else
  echo "Some checks need investigation. Do not close Phase 4 yet."
fi
