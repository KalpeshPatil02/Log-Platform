#!/usr/bin/env bash
# Verifies the local machine is ready for this project.
FAIL=0
ok(){ printf '  [ok] %s\n' "$1"; }; bad(){ printf '  [!!] %s\n' "$1"; FAIL=1; }
java -version 2>&1 | grep -q 'version "21' && ok "Java 21" || bad "Java 21 not found (sudo apt install openjdk-21-jdk)"
mvn -v >/dev/null 2>&1 && ok "$(mvn -v 2>/dev/null | head -1)" || bad "Maven missing (sudo apt install maven)"
git --version >/dev/null 2>&1 && ok "$(git --version)" || bad "git missing"
docker info >/dev/null 2>&1 && ok "Docker daemon reachable" || bad "Docker not running, or your user is not in the docker group (log out and in after usermod)"
docker compose version >/dev/null 2>&1 && ok "$(docker compose version)" || bad "docker compose v2 missing (sudo apt install docker-compose-v2)"
RAM=$(awk '/MemTotal/ {printf "%d", $2/1024}' /proc/meminfo); SWAP=$(awk '/SwapTotal/ {printf "%d", $2/1024}' /proc/meminfo)
echo "  RAM: ${RAM} MB, swap: ${SWAP} MB"; [ "$SWAP" -lt 2000 ] && echo "  [!!] Less than 2 GB swap. Add swap (see README)."
[ -f .env ] && ok ".env present" || bad ".env missing (cp .env.example .env and change the passwords)"
exit $FAIL
