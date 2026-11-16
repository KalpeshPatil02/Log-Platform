#!/usr/bin/env bash
# Creates keys/private.pem (PKCS#8) and keys/public.pem (X.509) for RS256. keys/ is git-ignored.
set -euo pipefail
cd "$(dirname "$0")/.."
mkdir -p keys
[ -f keys/private.pem ] && { echo "keys/private.pem already exists, not overwriting"; exit 0; }
openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 -out keys/private.pem
openssl rsa -pubout -in keys/private.pem -out keys/public.pem
chmod 600 keys/private.pem
echo "Created keys/private.pem and keys/public.pem"
