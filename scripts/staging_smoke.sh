#!/usr/bin/env bash
set -euo pipefail

BASE_URL="${1:-}"
ADMIN_ORIGIN="${2:-http://admin.example.com}"

if [[ -z "$BASE_URL" ]]; then
  echo "Usage: $0 <BASE_URL> [ADMIN_ORIGIN]"
  echo "Example: $0 https://api-staging.example.com https://admin-staging.example.com"
  exit 1
fi

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

function assert_json_response_contains() {
  local url="$1"
  local expected_snippet="$2"
  local label="$3"
  local headers
  local body
  local content_type

  headers="$(curl -sSI "$url" | tr -d '\r')"
  body="$(curl -sS "$url")"
  content_type="$(echo "$headers" | awk -F': ' 'tolower($1)=="content-type" {print tolower($2)}' | tail -n 1)"

  if [[ "$content_type" != application/json* ]]; then
    echo "[FAIL] $label is not JSON (content-type=$content_type)"
    echo "       Check BASE_URL. It may point to a parked page/reverse-proxy default site."
    exit 1
  fi

  echo "$body" | grep -q "$expected_snippet" || {
    echo "[FAIL] $label JSON does not include expected snippet: $expected_snippet"
    echo "       body=$body"
    exit 1
  }
  echo "[OK] $label JSON shape looks valid"
}

echo "== Staging Smoke Test =="
echo "BASE_URL=$BASE_URL"
echo "ADMIN_ORIGIN=$ADMIN_ORIGIN"

assert_http_code "200" "$BASE_URL/health"
assert_http_code "200" "$BASE_URL/db-health"
assert_json_response_contains "$BASE_URL/health" "\"status\":\"ok\"" "/health"
assert_json_response_contains "$BASE_URL/health" "\"service\":\"church-qt\"" "/health"
assert_json_response_contains "$BASE_URL/db-health" "\"db\"" "/db-health"

# Admin static console should be hidden in production/staging.
assert_http_code "404" "$BASE_URL/admin"
assert_http_code "404" "$BASE_URL/admin.html"

# CORS preflight check for split frontend.
cors_headers=$(curl -si -X OPTIONS "$BASE_URL/api/admin/years" \
  -H "Origin: $ADMIN_ORIGIN" \
  -H "Access-Control-Request-Method: GET")

echo "$cors_headers" | grep -qi "access-control-allow-origin: $ADMIN_ORIGIN" || {
  echo "[FAIL] Missing Access-Control-Allow-Origin for $ADMIN_ORIGIN"
  exit 1
}
echo "[OK] CORS preflight allows $ADMIN_ORIGIN"

echo "Smoke test completed successfully."
