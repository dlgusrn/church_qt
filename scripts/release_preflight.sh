#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

if [[ -z "${DB_URL:-}" ]]; then
  if [[ -n "${DB_HOST:-}" && -n "${DB_NAME:-}" ]]; then
    export DB_URL="jdbc:mysql://${DB_HOST}:3306/${DB_NAME}?serverTimezone=Asia/Seoul&characterEncoding=UTF-8"
    echo "[INFO] DB_URL is not set; derived from DB_HOST/DB_NAME"
  fi
fi

if [[ -z "${DB_USERNAME:-}" && -n "${DB_USER:-}" ]]; then
  export DB_USERNAME="$DB_USER"
  echo "[INFO] DB_USERNAME is not set; using DB_USER"
fi

REQUIRED_VARS=(
  "DB_URL"
  "DB_USERNAME"
  "DB_PASSWORD"
  "APP_JWT_SECRET"
)

echo "== Release Preflight =="
echo "Project: $ROOT_DIR"

missing=()
for var_name in "${REQUIRED_VARS[@]}"; do
  value="${!var_name:-}"
  if [[ -z "$value" ]]; then
    missing+=("$var_name")
  fi
done

if (( ${#missing[@]} > 0 )); then
  echo "[FAIL] Missing required env vars: ${missing[*]}"
  echo "       Required: DB_URL (or DB_HOST+DB_NAME), DB_USERNAME (or DB_USER), DB_PASSWORD, APP_JWT_SECRET"
  exit 1
fi

echo "[OK] Required env vars are set"

if [[ "${APP_ADMIN_CONSOLE_ENABLED:-false}" != "false" ]]; then
  echo "[FAIL] APP_ADMIN_CONSOLE_ENABLED must be false in staging/production"
  exit 1
fi
echo "[OK] APP_ADMIN_CONSOLE_ENABLED=false"

origins="${APP_ADMIN_FRONTEND_ALLOWED_ORIGINS:-}"
if [[ -z "$origins" ]]; then
  echo "[FAIL] APP_ADMIN_FRONTEND_ALLOWED_ORIGINS must be configured"
  exit 1
fi
echo "[OK] APP_ADMIN_FRONTEND_ALLOWED_ORIGINS is configured"

jwt_len=${#APP_JWT_SECRET}
if (( jwt_len < 32 )); then
  echo "[FAIL] APP_JWT_SECRET is too short (length=$jwt_len, required>=32)"
  exit 1
fi
echo "[OK] APP_JWT_SECRET length >= 32"

echo "Running build..."
./gradlew build --no-daemon
echo "[OK] Build succeeded"

echo "Preflight completed successfully."
