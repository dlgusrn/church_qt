#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

PORT="${1:-8080}"
ADMIN_ORIGIN="${2:-http://localhost:5173}"

if [[ -z "${APP_JWT_SECRET:-}" ]]; then
  export APP_JWT_SECRET="$(openssl rand -base64 48 | tr -d '\n')"
  echo "[INFO] APP_JWT_SECRET was empty; generated a temporary value for local run"
fi

export APP_ADMIN_CONSOLE_ENABLED=false
export APP_ADMIN_FRONTEND_ALLOWED_ORIGINS="$ADMIN_ORIGIN"
export SERVER_PORT="$PORT"

echo "== Run Local (Prod-like) =="
echo "SERVER_PORT=$SERVER_PORT"
echo "APP_ADMIN_CONSOLE_ENABLED=$APP_ADMIN_CONSOLE_ENABLED"
echo "APP_ADMIN_FRONTEND_ALLOWED_ORIGINS=$APP_ADMIN_FRONTEND_ALLOWED_ORIGINS"
echo
echo "Press Ctrl+C to stop."

./gradlew bootRun --no-daemon
