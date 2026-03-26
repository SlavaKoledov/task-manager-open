#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$ROOT_DIR"

EXPECTED_PROJECT_NAME="task_manager"
EXPECTED_VOLUME_NAME="task_manager_db_data"

echo "Expected Compose project: $EXPECTED_PROJECT_NAME"
echo "Expected Postgres volume: $EXPECTED_VOLUME_NAME"

DB_CONTAINER_ID="$(docker compose ps -q db 2>/dev/null || true)"
if [ -z "$DB_CONTAINER_ID" ]; then
  echo "db service is not running."
  echo "Start it with: docker compose up -d db"
  exit 0
fi

CONTAINER_NAME="$(docker inspect --format '{{.Name}}' "$DB_CONTAINER_ID" | sed 's#^/##')"
PROJECT_NAME="$(docker inspect --format '{{index .Config.Labels "com.docker.compose.project"}}' "$DB_CONTAINER_ID")"
ATTACHED_VOLUME="$(docker inspect --format '{{range .Mounts}}{{if eq .Destination "/var/lib/postgresql/data"}}{{.Name}}{{end}}{{end}}' "$DB_CONTAINER_ID")"
DB_NAME="$(docker exec "$CONTAINER_NAME" sh -lc 'printf "%s" "$POSTGRES_DB"')"
DB_USER="$(docker exec "$CONTAINER_NAME" sh -lc 'printf "%s" "$POSTGRES_USER"')"
COUNTS="$(
  docker exec "$CONTAINER_NAME" sh -lc \
    "psql -U \"\$POSTGRES_USER\" -d \"\$POSTGRES_DB\" -Atc \"select (select count(*) from lists), (select count(*) from tasks), (select count(*) from tasks where parent_id is not null), (select count(*) from tasks where repeat <> 'none'), (select count(*) from tasks where deleted_at is not null);\""
)"

IFS='|' read -r LIST_COUNT TASK_COUNT SUBTASK_COUNT RECURRING_COUNT DELETED_COUNT <<<"$COUNTS"

echo "Running db container: $CONTAINER_NAME"
echo "Running project label: $PROJECT_NAME"
echo "Attached Postgres volume: $ATTACHED_VOLUME"
echo "Database identity: $DB_NAME as $DB_USER"
echo "Row counts: lists=$LIST_COUNT tasks=$TASK_COUNT subtasks=$SUBTASK_COUNT recurring=$RECURRING_COUNT deleted=$DELETED_COUNT"

if [ "$PROJECT_NAME" != "$EXPECTED_PROJECT_NAME" ] || [ "$ATTACHED_VOLUME" != "$EXPECTED_VOLUME_NAME" ]; then
  echo "WARNING: running stack does not match the expected Compose project or Postgres volume." >&2
  exit 1
fi
