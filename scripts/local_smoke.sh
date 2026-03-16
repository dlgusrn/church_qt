#!/usr/bin/env bash
set -euo pipefail

BASE_URL="${1:-http://localhost:8080}"
ADMIN_ORIGIN="${2:-http://localhost:5173}"
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

echo "== Local Smoke Test =="
echo "BASE_URL=$BASE_URL"
echo "ADMIN_ORIGIN=$ADMIN_ORIGIN"
echo "EXPECT_ADMIN_STATUS=$EXPECT_ADMIN_STATUS"

assert_http_code "200" "$BASE_URL/health"
assert_http_code "200" "$BASE_URL/db-health"
assert_http_code "$EXPECT_ADMIN_STATUS" "$BASE_URL/admin"
assert_http_code "$EXPECT_ADMIN_STATUS" "$BASE_URL/admin.html"
assert_http_code "$EXPECT_ADMIN_STATUS" "$BASE_URL/ops"
assert_http_code "$EXPECT_ADMIN_STATUS" "$BASE_URL/ops.html"

cors_headers=$(curl -si -X OPTIONS "$BASE_URL/api/admin/years" \
  -H "Origin: $ADMIN_ORIGIN" \
  -H "Access-Control-Request-Method: GET")

echo "$cors_headers" | grep -qi "access-control-allow-origin: $ADMIN_ORIGIN" || {
  echo "[FAIL] Missing Access-Control-Allow-Origin for $ADMIN_ORIGIN"
  exit 1
}
echo "[OK] CORS preflight allows $ADMIN_ORIGIN"

echo "Local smoke test completed successfully."
