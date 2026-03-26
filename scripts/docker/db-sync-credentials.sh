#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$ROOT_DIR"

if [ ! -f .env ]; then
  echo "Missing .env. Copy .env.example to .env first." >&2
  exit 1
fi

set -a
# shellcheck disable=SC1091
. ./.env
set +a

: "${POSTGRES_DB:?Missing POSTGRES_DB in .env}"
: "${POSTGRES_USER:?Missing POSTGRES_USER in .env}"
: "${POSTGRES_PASSWORD:?Missing POSTGRES_PASSWORD in .env}"

ESCAPED_DB_PASSWORD="${POSTGRES_PASSWORD//\'/\'\'}"

docker compose up -d db >/dev/null

DB_CONTAINER_ID="$(docker compose ps -q db 2>/dev/null || true)"
if [ -z "$DB_CONTAINER_ID" ]; then
  echo "db service is not running. Start it with: docker compose up -d db" >&2
  exit 1
fi

CONTAINER_NAME="$(docker inspect --format '{{.Name}}' "$DB_CONTAINER_ID" | sed 's#^/##')"
PROJECT_NAME="$(docker inspect --format '{{index .Config.Labels "com.docker.compose.project"}}' "$DB_CONTAINER_ID")"
NETWORK_NAME="${PROJECT_NAME}_default"

echo "Syncing Postgres role password for $POSTGRES_USER in database $POSTGRES_DB"

docker exec \
  -e TARGET_DB_NAME="$POSTGRES_DB" \
  -e TARGET_DB_USER="$POSTGRES_USER" \
  -e TARGET_DB_PASSWORD_SQL="$ESCAPED_DB_PASSWORD" \
  "$CONTAINER_NAME" \
  sh -lc 'psql -v ON_ERROR_STOP=1 -U "$TARGET_DB_USER" -d "$TARGET_DB_NAME" -c "ALTER ROLE \"$TARGET_DB_USER\" WITH PASSWORD '\''$TARGET_DB_PASSWORD_SQL'\'';"'

docker run --rm --network "$NETWORK_NAME" -e PGPASSWORD="$POSTGRES_PASSWORD" postgres:16-alpine \
  psql -h db -U "$POSTGRES_USER" -d "$POSTGRES_DB" -Atc 'select 1' >/dev/null

echo "Verified TCP authentication to db with the credentials from .env"
