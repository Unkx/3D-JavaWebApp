#!/usr/bin/env bash
set -euo pipefail

DB_HOST="${DB_HOST:-db}"
DB_PORT="${DB_PORT:-5432}"
DB_USER="${DB_USER:-postgres}"
RETRIES=${DB_RETRIES:-60}
SLEEP_SEC=${DB_SLEEP:-1}

echo "Waiting for Postgres at ${DB_HOST}:${DB_PORT} (user=${DB_USER})..."
i=0
until pg_isready -h "$DB_HOST" -p "$DB_PORT" -U "$DB_USER" >/dev/null 2>&1; do
  i=$((i+1))
  if [ "$i" -ge "$RETRIES" ]; then
    echo "Timed out waiting for Postgres after $((RETRIES * SLEEP_SEC)) seconds"
    netstat -tnlp || true
    exit 1
  fi
  printf '.'
  sleep "$SLEEP_SEC"
done
echo "Postgres is up — starting application"
exec "$@"
