#!/usr/bin/env sh
set -eu

cd /app/apps/api

attempts=0
echo "Running migrations against ${DATABASE_HOST:-localhost}:${DATABASE_PORT:-5432}/${POSTGRES_DB:-task_manager} as ${POSTGRES_USER:-task_manager}"
until alembic upgrade head; do
  attempts=$((attempts + 1))
  if [ "$attempts" -ge 20 ]; then
    echo "Database migrations failed after multiple retries."
    echo "If this is an existing Docker volume with older credentials, run scripts/docker/db-sync-credentials.sh from the repo root."
    exit 1
  fi
  echo "Waiting for database..."
  sleep 2
done

exec uvicorn app.main:app --host 0.0.0.0 --port 8000
