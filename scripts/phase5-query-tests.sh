#!/usr/bin/env bash

BASE="http://localhost:8085/api/v1/logs"
TENANT="tenant-b"
EVENT="p4-1793718874-tenant-b"

run_test() {
  echo
  echo "======================================"
  echo "TEST: $1"
  echo "======================================"

  curl -sS -w "\nHTTP STATUS: %{http_code}\n" \
    -H "X-Tenant-Id: $TENANT" \
    "$2"
}

run_test "1. Get all logs" \
  "$BASE?page=0&size=20"

run_test "2. Single log lookup" \
  "$BASE/$EVENT"

run_test "3. Severity filter" \
  "$BASE?severity=INFO&page=0&size=20"

run_test "4. Service name filter" \
  "$BASE?serviceName=payment-service&page=0&size=20"

run_test "5. Environment filter" \
  "$BASE?environment=development&page=0&size=20"

run_test "6. Keyword search" \
  "$BASE?keyword=reliability&page=0&size=20"

run_test "7. Pagination - second page" \
  "$BASE?page=1&size=1"

run_test "8. Invalid page size" \
  "$BASE?page=0&size=500"

run_test "9. Invalid date range" \
  "$BASE?from=2026-11-04T00:00:00Z&to=2026-11-01T00:00:00Z"
