#!/usr/bin/env bash
# Runs identity-service on the host (low memory) against the Docker MySQL. Needs: docker compose up -d (mysql) and .env
set -euo pipefail
cd "$(dirname "$0")/.."
set -a; source .env; set +a
export DB_PASSWORD="$IDENTITY_DB_PASSWORD"
: "${IDENTITY_INTERNAL_TOKEN:?add IDENTITY_INTERNAL_TOKEN=... to .env}"
if [ -f keys/private.pem ]; then
  export JWT_PRIVATE_KEY_PATH="$PWD/keys/private.pem" JWT_PUBLIC_KEY_PATH="$PWD/keys/public.pem"
else
  export JWT_GENERATE_IF_MISSING=true; echo "No keys/ found: using TEMPORARY dev keys (run scripts/generate-jwt-keys.sh for stable keys)"
fi
export JAVA_TOOL_OPTIONS="-Xmx256m -XX:+UseSerialGC"
mvn -q -pl common-core,common-web -DskipTests install
mvn -q -pl identity-service spring-boot:run
