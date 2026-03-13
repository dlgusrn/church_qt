#!/usr/bin/env bash
set -euo pipefail

BASE_URL="${1:-}"
ADMIN_ORIGIN="${2:-}"

if [[ -z "$BASE_URL" || -z "$ADMIN_ORIGIN" ]]; then
  echo "Usage: $0 <BASE_URL> <ADMIN_ORIGIN>"
  echo "Example: $0 https://api.example.com https://admin.example.com"
  exit 1
fi

if [[ "$BASE_URL" != https://* ]]; then
  echo "[FAIL] BASE_URL must start with https://"
  exit 1
fi

if [[ "$ADMIN_ORIGIN" != https://* ]]; then
  echo "[FAIL] ADMIN_ORIGIN must start with https://"
  exit 1
fi

if [[ "$BASE_URL" == *"your-domain.com"* || "$ADMIN_ORIGIN" == *"your-domain.com"* ]]; then
  echo "[FAIL] Placeholder domain detected. Replace with real production domains."
  exit 1
fi

if [[ "$BASE_URL" == *"localhost"* || "$ADMIN_ORIGIN" == *"localhost"* ]]; then
  echo "[FAIL] localhost is not allowed in production go-live check."
  exit 1
fi

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

echo "== Production Go-Live Check =="
echo "BASE_URL=$BASE_URL"
echo "ADMIN_ORIGIN=$ADMIN_ORIGIN"
echo

echo "[1/2] release_preflight"
./scripts/release_preflight.sh

echo "[2/2] staging_smoke"
./scripts/staging_smoke.sh "$BASE_URL" "$ADMIN_ORIGIN"

echo "Production go-live check completed successfully."
