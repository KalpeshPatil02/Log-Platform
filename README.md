# Log Analytics & Alerting Platform
Status: Phase 0 (foundation) and Phase 1 (identity-service) complete. See docs/IMPLEMENTATION_CHECKLIST.md and docs/ARCHITECTURE.md.

## Prerequisites (Ubuntu 24.04)
    sudo apt update
    sudo apt install -y openjdk-21-jdk maven git curl jq docker.io docker-compose-v2 mysql-client-core-8.0 redis-tools
    sudo usermod -aG docker $USER      # then log out and log in again
Use `docker.io` from apt, not the snap Docker (Testcontainers needs the normal socket). Maven 3.8.7 from apt is fine.

## Quick start
    cp .env.example .env               # change all passwords
    ./scripts/check-env.sh
    mvn -B verify                      # builds common-core, common-web and runs tests
    docker compose up -d
    docker compose ps                  # mysql and redis should be "healthy"
    mysql -h127.0.0.1 -uidentity_svc -p identity_db -e 'select 1'
    redis-cli -a "$(grep REDIS_PASSWORD .env | cut -d= -f2)" ping

## Phase 1: run identity-service
    echo 'IDENTITY_INTERNAL_TOKEN=change-me-internal-token' >> .env   # only if your .env was created before Phase 1
    ./scripts/generate-jwt-keys.sh
    docker compose up -d                                            # mysql + redis
    ./scripts/run-identity.sh                                       # runs on the host, ~250 MB
    # or in Docker: mvn -B package && docker compose --profile core up -d --build
See docs/phase-1-identity.md for the API, security decisions and curl examples.
Tests: `mvn -B verify` (IdentityFlowTest uses Testcontainers, so Docker must be running).
