#!/usr/bin/env bash
set -euo pipefail

BASE_URL="${1:-http://localhost:8081}"
ADMIN_ORIGIN="${2:-http://localhost:5173}"
APP_ORIGIN="${3:-$ADMIN_ORIGIN}"
EXPECT_ADMIN_STATUS="${EXPECT_ADMIN_STATUS:-404}"

function assert_http_code() {
  local expected="$1"
  local url="$2"
  local code
  code=$(curl -s -o /dev/null -w "%{http_code}" "$url")
  if [[ "$code" != "$expected" ]]; then
    echo "[FAIL] $url expected=$expected actual=$code"
    exit 1
  fi
  echo "[OK] $url -> $code"
}

function assert_cors_preflight() {
  local path="$1"
  local origin="$2"
  local method="$3"
  local cors_headers

  cors_headers=$(curl -si -X OPTIONS "$BASE_URL$path" \
    -H "Origin: $origin" \
    -H "Access-Control-Request-Method: $method")

  echo "$cors_headers" | grep -qi "access-control-allow-origin: $origin" || {
    echo "[FAIL] Missing Access-Control-Allow-Origin for $origin on $path"
    exit 1
  }
  echo "[OK] CORS preflight allows $origin on $path"
}

echo "== Local Smoke Test =="
echo "BASE_URL=$BASE_URL"
echo "ADMIN_ORIGIN=$ADMIN_ORIGIN"
echo "APP_ORIGIN=$APP_ORIGIN"
echo "EXPECT_ADMIN_STATUS=$EXPECT_ADMIN_STATUS"

assert_http_code "200" "$BASE_URL/health"
assert_http_code "200" "$BASE_URL/db-health"
assert_http_code "$EXPECT_ADMIN_STATUS" "$BASE_URL/admin"
assert_http_code "$EXPECT_ADMIN_STATUS" "$BASE_URL/admin.html"
assert_http_code "$EXPECT_ADMIN_STATUS" "$BASE_URL/ops"
assert_http_code "$EXPECT_ADMIN_STATUS" "$BASE_URL/ops.html"

assert_cors_preflight "/api/admin/years" "$ADMIN_ORIGIN" "GET"
assert_cors_preflight "/api/teacher/login" "$ADMIN_ORIGIN" "POST"
assert_cors_preflight "/api/teacher/login" "$APP_ORIGIN" "POST"
assert_cors_preflight "/api/teacher/meeting-notes" "$APP_ORIGIN" "POST"

echo "Local smoke test completed successfully."
