#!/usr/bin/env bash

set -u

ROOT="$(cd "$(dirname "$0")" && pwd)"
cd "$ROOT"

PASS=0
FAIL=0
WARN=0

pass() {
    echo -e "\033[32m[PASS]\033[0m $1"
    ((PASS+=1))
}

fail() {
    echo -e "\033[31m[FAIL]\033[0m $1"
    ((FAIL+=1))
}

warn() {
    echo -e "\033[33m[WARN]\033[0m $1"
    ((WARN+=1))
}

section() {
    echo
    echo "========================================"
    echo "$1"
    echo "========================================"
}

check_port() {
    local name="$1"
    local port="$2"

    if timeout 2 bash -c "echo > /dev/tcp/127.0.0.1/$port" 2>/dev/null; then
        pass "$name is listening on port $port"
    else
        fail "$name is NOT listening on port $port"
    fi
}

section "1. PROJECT STRUCTURE"

if [[ -f pom.xml ]]; then
    pass "Root pom.xml found"
else
    fail "Root pom.xml missing"
fi

if [[ -d ingestion-service ]]; then
    pass "ingestion-service directory found"
else
    fail "ingestion-service directory missing"
    exit 1
fi

section "2. JAVA AND MAVEN"

if command -v java >/dev/null 2>&1; then
    java -version 2>&1 | head -n 1
    pass "Java installed"
else
    fail "Java not installed"
fi

if command -v mvn >/dev/null 2>&1; then
    mvn -version | head -n 2
    pass "Maven installed"
else
    fail "Maven not installed"
    exit 1
fi

section "3. INGESTION SERVICE TESTS"

echo "Running Maven tests. This may take a few minutes..."

if mvn -B -pl ingestion-service -am clean test; then
    pass "Maven build and tests passed"
else
    fail "Maven build or tests failed"
fi

section "4. DOCKER COMPOSE"

if ! command -v docker >/dev/null 2>&1; then
    fail "Docker command not found"
elif ! docker info >/dev/null 2>&1; then
    fail "Docker daemon is not running or accessible"
else
    pass "Docker daemon is running"

    if docker compose ps; then
        pass "Docker Compose status retrieved"
    else
        fail "Docker Compose status failed"
    fi

    if docker compose ps --status running --services 2>/dev/null | grep -qx redis; then
        pass "Redis Compose service is running"

        if docker compose exec -T redis redis-cli ping 2>/dev/null | grep -q PONG; then
            pass "Redis responds to PING"
        elif [[ -f .env ]] && grep -q '^REDIS_PASSWORD=' .env; then
            REDIS_PASSWORD="$(sed -n 's/^REDIS_PASSWORD=//p' .env | head -n 1 | tr -d '\r' | sed 's/^"//;s/"$//;s/^'\''//;s/'\''$//')"

            if docker compose exec -T redis redis-cli -a "$REDIS_PASSWORD" --no-auth-warning ping 2>/dev/null | grep -q PONG; then
                pass "Redis responds to authenticated PING"
            else
                fail "Redis is running but PING failed"
            fi
        else
            fail "Redis PING failed; check Redis authentication and configuration"
        fi
    else
        fail "Redis Compose service is not running"
    fi
fi

section "5. SERVICE PORTS"

check_port "Identity service" 8081
check_port "Ingestion service" 8083

section "6. INGESTION STARTUP LOG"

if [[ -f ingestion-service/target/diagnostic-startup.log ]]; then
    if grep -q "Started IngestionServiceApplication" ingestion-service/target/diagnostic-startup.log; then
        pass "Ingestion startup log contains Started message"
    else
        fail "Ingestion startup log does not contain Started message"
        echo "Last error lines:"
        grep -E "ERROR|Caused by:|APPLICATION FAILED" \
            ingestion-service/target/diagnostic-startup.log | tail -n 15 || true
    fi
else
    warn "No diagnostic startup log found; startup log check skipped"
fi

section "FINAL REPORT"

echo "PASS: $PASS"
echo "FAIL: $FAIL"
echo "WARN: $WARN"

if (( FAIL > 0 )); then
    echo "RESULT: ISSUES FOUND - inspect the FAIL entries above."
    exit 1
else
    echo "RESULT: ALL EXECUTED CHECKS PASSED."
    exit 0
fi
