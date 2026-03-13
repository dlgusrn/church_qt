#!/usr/bin/env bash
set -euo pipefail

BASE_URL="${1:-}"

if [[ -z "$BASE_URL" ]]; then
  echo "Usage: $0 <BASE_URL>"
  echo "Example: $0 http://localhost:8080"
  exit 1
fi

function assert_redirect() {
  local from="$1"
  local expected_to="$2"
  local headers
  local location
  local code

  headers="$(curl -sSI "$from" | tr -d '\r')"
  code="$(echo "$headers" | awk 'NR==1 {print $2}')"
  location="$(echo "$headers" | awk -F': ' 'tolower($1)=="location" {print $2}' | tail -n 1)"

  if [[ "$code" != "302" ]]; then
    echo "[FAIL] $from expected redirect(302) actual=$code"
    exit 1
  fi
  if [[ "$location" != "$expected_to" ]]; then
    echo "[FAIL] $from expected location=$expected_to actual=$location"
    exit 1
  fi
  echo "[OK] $from -> $location"
}

function assert_http_code() {
  local expected="$1"
  local url="$2"
  local code
  code="$(curl -s -o /dev/null -w "%{http_code}" "$url")"
  if [[ "$code" != "$expected" ]]; then
    echo "[FAIL] $url expected=$expected actual=$code"
    exit 1
  fi
  echo "[OK] $url -> $code"
}

echo "== App Route Verify =="
echo "BASE_URL=$BASE_URL"

assert_redirect "$BASE_URL/student" "$BASE_URL/app/student"
assert_redirect "$BASE_URL/teacher" "$BASE_URL/app/teacher/login"
assert_http_code "200" "$BASE_URL/app/student"
assert_http_code "200" "$BASE_URL/app/teacher/login"

echo "App route verify completed successfully."
