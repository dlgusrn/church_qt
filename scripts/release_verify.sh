#!/usr/bin/env bash
set -euo pipefail

BASE_URL="${1:-}"
ADMIN_ORIGIN="${2:-}"

if [[ -z "$BASE_URL" || -z "$ADMIN_ORIGIN" ]]; then
  echo "Usage: $0 <BASE_URL> <ADMIN_ORIGIN>"
  echo "Example: $0 https://api-staging.example.com https://admin-staging.example.com"
  exit 1
fi

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

echo "== Release Verify =="
echo "BASE_URL=$BASE_URL"
echo "ADMIN_ORIGIN=$ADMIN_ORIGIN"

echo "[1/2] Running release preflight..."
./scripts/release_preflight.sh

echo "[2/2] Running staging smoke..."
./scripts/staging_smoke.sh "$BASE_URL" "$ADMIN_ORIGIN"

echo "Release verify completed successfully."
