#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

BASE_URL="${1:-http://localhost:8080}"
ADMIN_ORIGIN="${2:-http://localhost:5173}"
START_APP="${START_APP:-false}"

if [[ -z "${APP_JWT_SECRET:-}" ]]; then
  export APP_JWT_SECRET="$(openssl rand -base64 48 | tr -d '\n')"
  echo "[INFO] APP_JWT_SECRET was empty; generated a temporary value"
fi

if [[ -z "${DB_URL:-}" && -n "${DB_HOST:-}" && -n "${DB_NAME:-}" ]]; then
  export DB_URL="jdbc:mysql://${DB_HOST}:3306/${DB_NAME}?serverTimezone=Asia/Seoul&characterEncoding=UTF-8"
  echo "[INFO] DB_URL derived from DB_HOST/DB_NAME"
fi

if [[ -z "${DB_USERNAME:-}" && -n "${DB_USER:-}" ]]; then
  export DB_USERNAME="$DB_USER"
  echo "[INFO] DB_USERNAME set from DB_USER"
fi

export APP_ADMIN_CONSOLE_ENABLED=false
export APP_ADMIN_FRONTEND_ALLOWED_ORIGINS="$ADMIN_ORIGIN"

echo "== Local Release Rehearsal =="
echo "BASE_URL=$BASE_URL"
echo "ADMIN_ORIGIN=$ADMIN_ORIGIN"
echo "START_APP=$START_APP"
echo

./scripts/release_preflight.sh
EXPECT_ADMIN_STATUS=404 START_APP="$START_APP" ./scripts/local_verify.sh "$BASE_URL" "$ADMIN_ORIGIN"

if ./scripts/verify_app_routes.sh "$BASE_URL"; then
  :
elif [[ "$START_APP" == "true" ]]; then
  # local_verify may stop the app it started; bring up a temporary instance and retry.
  APP_PID=""
  cleanup() {
    if [[ -n "$APP_PID" ]] && kill -0 "$APP_PID" >/dev/null 2>&1; then
      kill "$APP_PID" || true
      wait "$APP_PID" || true
    fi
  }
  trap cleanup EXIT

  server_port="$(echo "$BASE_URL" | sed -E 's#^https?://[^:/]+:([0-9]+).*$#\1#')"
  if [[ ! "$server_port" =~ ^[0-9]+$ ]]; then
    server_port="8080"
  fi

  echo "[INFO] Retrying route verify with temporary app (SERVER_PORT=$server_port)"
  SERVER_PORT="$server_port" ./gradlew bootRun --no-daemon >/tmp/church-qt-local-rehearsal-route.log 2>&1 &
  APP_PID=$!

  for _ in $(seq 1 60); do
    code="$(curl -s -o /dev/null -w "%{http_code}" "$BASE_URL/health" || true)"
    if [[ "$code" == "200" ]]; then
      break
    fi
    sleep 1
  done
  ./scripts/verify_app_routes.sh "$BASE_URL"
  cleanup
  trap - EXIT
else
  echo "[WARN] Skipping app route verify because app is not running (START_APP=false)."
fi

echo "Local release rehearsal completed successfully."
