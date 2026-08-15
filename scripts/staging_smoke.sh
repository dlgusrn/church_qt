#!/usr/bin/env bash
set -euo pipefail

BASE_URL="${1:-}"
ADMIN_ORIGIN="${2:-http://admin.example.com}"
APP_ORIGIN="${3:-$ADMIN_ORIGIN}"

if [[ -z "$BASE_URL" ]]; then
  echo "Usage: $0 <BASE_URL> [ADMIN_ORIGIN] [APP_ORIGIN]"
  echo "Example: $0 https://api-staging.example.com https://admin-staging.example.com https://app-staging.example.com"
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

echo "== Staging Smoke Test =="
echo "BASE_URL=$BASE_URL"
echo "ADMIN_ORIGIN=$ADMIN_ORIGIN"
echo "APP_ORIGIN=$APP_ORIGIN"

assert_http_code "200" "$BASE_URL/health"
assert_http_code "200" "$BASE_URL/db-health"
assert_json_response_contains "$BASE_URL/health" "\"status\":\"ok\"" "/health"
assert_json_response_contains "$BASE_URL/health" "\"service\":\"church-qt\"" "/health"
assert_json_response_contains "$BASE_URL/db-health" "\"db\"" "/db-health"

# Admin static console should be hidden in production/staging.
assert_http_code "404" "$BASE_URL/admin"
assert_http_code "404" "$BASE_URL/admin.html"
assert_http_code "404" "$BASE_URL/ops"
assert_http_code "404" "$BASE_URL/ops.html"

# CORS preflight checks for split frontend and browser Origin headers.
assert_cors_preflight "/api/admin/years" "$ADMIN_ORIGIN" "GET"
assert_cors_preflight "/api/teacher/login" "$ADMIN_ORIGIN" "POST"
assert_cors_preflight "/api/teacher/login" "$APP_ORIGIN" "POST"
assert_cors_preflight "/api/teacher/meeting-notes" "$APP_ORIGIN" "POST"

echo "Smoke test completed successfully."
