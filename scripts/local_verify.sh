#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

BASE_URL="${1:-http://localhost:8081}"
ADMIN_ORIGIN="${2:-http://localhost:5173}"
EXPECT_ADMIN_STATUS="${EXPECT_ADMIN_STATUS:-404}"
START_APP="${START_APP:-true}"
WAIT_SECONDS="${WAIT_SECONDS:-60}"
APP_LOG_PATH="${APP_LOG_PATH:-/tmp/church-qt-local-verify.log}"

APP_PID=""
STARTED_BY_SCRIPT="false"

function cleanup() {
  if [[ "$STARTED_BY_SCRIPT" == "true" ]] && [[ -n "$APP_PID" ]] && kill -0 "$APP_PID" >/dev/null 2>&1; then
    kill "$APP_PID" || true
    wait "$APP_PID" || true
  fi
}
trap cleanup EXIT

function wait_for_health() {
  local url="$1/health"
  local max_wait="$2"
  local start_ts
  start_ts="$(date +%s)"

  while true; do
    local code
    code="$(curl -s -o /dev/null -w "%{http_code}" "$url" || true)"
    if [[ "$code" == "200" ]]; then
      echo "[OK] Health is up: $url"
      return 0
    fi

    local now
    now="$(date +%s)"
    if (( now - start_ts >= max_wait )); then
      echo "[FAIL] Timed out waiting for health endpoint: $url"
      if [[ -f "$APP_LOG_PATH" ]]; then
        echo "[INFO] Last app logs ($APP_LOG_PATH):"
        tail -n 80 "$APP_LOG_PATH" || true
      else
        echo "[INFO] App log file not found: $APP_LOG_PATH"
      fi
      return 1
    fi
    sleep 1
  done
}

echo "== Local Verify =="
echo "BASE_URL=$BASE_URL"
echo "ADMIN_ORIGIN=$ADMIN_ORIGIN"
echo "EXPECT_ADMIN_STATUS=$EXPECT_ADMIN_STATUS"
echo "START_APP=$START_APP"
echo "APP_LOG_PATH=$APP_LOG_PATH"

if [[ "$START_APP" == "true" ]]; then
  health_code="$(curl -s -o /dev/null -w "%{http_code}" "$BASE_URL/health" || true)"
  if [[ "$health_code" == "200" ]]; then
    echo "[INFO] Health already up; skipping app start: $BASE_URL/health"
  else
    echo "Starting app with ./gradlew bootRun ..."
    ./gradlew bootRun --no-daemon > "$APP_LOG_PATH" 2>&1 &
    APP_PID=$!
    STARTED_BY_SCRIPT="true"
    echo "APP_PID=$APP_PID"
    wait_for_health "$BASE_URL" "$WAIT_SECONDS"
  fi
else
  echo "Skipping app start (START_APP=false)"
fi

EXPECT_ADMIN_STATUS="$EXPECT_ADMIN_STATUS" ./scripts/local_smoke.sh "$BASE_URL" "$ADMIN_ORIGIN"
echo "Local verify completed successfully."
