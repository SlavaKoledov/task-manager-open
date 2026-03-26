#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$ROOT_DIR"

OUTPUT_DIR="${1:-./backups}"
mkdir -p "$OUTPUT_DIR"

DB_CONTAINER_ID="$(docker compose ps -q db 2>/dev/null || true)"
if [ -z "$DB_CONTAINER_ID" ]; then
  echo "db service is not running. Start it with: docker compose up -d db" >&2
  exit 1
fi

CONTAINER_NAME="$(docker inspect --format '{{.Name}}' "$DB_CONTAINER_ID" | sed 's#^/##')"
DB_NAME="$(docker exec "$CONTAINER_NAME" sh -lc 'printf "%s" "$POSTGRES_DB"')"
TIMESTAMP="$(date -u +%Y%m%dT%H%M%SZ)"
OUTPUT_PATH="${OUTPUT_DIR%/}/${DB_NAME}_${TIMESTAMP}.dump"

docker exec "$CONTAINER_NAME" sh -lc 'pg_dump -U "$POSTGRES_USER" -d "$POSTGRES_DB" -Fc' > "$OUTPUT_PATH"

echo "Postgres backup written to $OUTPUT_PATH"
