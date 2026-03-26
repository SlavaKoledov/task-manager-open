# Task Manager

Task Manager is a monorepo with one backend and two clients:

- `apps/api`: FastAPI + SQLAlchemy + Alembic backend
- `apps/web`: React + Vite web client
- `apps/android`: Kotlin + Jetpack Compose Android client

The backend is the source of truth. Both clients talk to the same API and share the same task/list model.

## Stack

- Backend: FastAPI, SQLAlchemy 2, Alembic, PostgreSQL, pytest
- Web: React 18, TypeScript, Vite, TanStack Query, Vitest
- Android: Kotlin, Jetpack Compose, Retrofit, Room, WorkManager, Hilt
- Infra: Docker Compose, Caddy

## Repository Layout

```text
apps/
  api/       Backend API, migrations, backend tests
  web/       Web app, frontend tests, Vite build
  android/   Native Android app and unit tests
infra/
  caddy/     Reverse proxy config for docker compose
  docker/    Dockerfiles and container startup scripts
scripts/
  docker/    Local helper scripts for DB inspection, backup, and password sync
docs/
  stage1-mvp.md
```

## Example Files

Copy only the files you need for your local setup:

| Source example | Copy to | Purpose |
| --- | --- | --- |
| `.env.example` | `.env` | Local PostgreSQL + backend settings for Docker Compose and direct backend runs |
| `apps/web/.env.example` | `apps/web/.env.local` | Optional web API base URL override |
| `apps/android/local.properties.example` | `apps/android/local.properties` | Optional Android SDK path template if Android Studio does not generate it |

The committed example values are local-development defaults only. Do not reuse them for production.

## Required Environment Variables

The root `.env` file is the main local config entry point.

Required for Docker Compose and the default backend setup:

- `POSTGRES_DB`
- `POSTGRES_USER`
- `POSTGRES_PASSWORD`
- `DATABASE_HOST`
- `DATABASE_PORT`

Optional backend overrides:

- `DATABASE_URL`
- `CORS_ORIGINS` as a JSON array

Optional web override:

- `VITE_API_BASE_URL`

## Run With Docker Compose

This is the easiest way to run backend + web together.

```bash
cp .env.example .env
docker compose up --build
```

Services exposed on the host:

| Service | Role | Host access |
| --- | --- | --- |
| `db` | PostgreSQL 16 | `localhost:5432` |
| `api` | FastAPI backend | `localhost:8001` |
| `caddy` | Browser entry point and `/api` reverse proxy | `http://localhost` |

After startup:

- Web app: `http://localhost`
- Backend docs: `http://localhost:8001/docs`
- Backend health: `http://localhost:8001/health`
- Proxied API example: `http://localhost/api/tasks`

Notes:

- Caddy proxies `/api/*` to the backend and everything else to the built web app.
- FastAPI docs are not exposed through Caddy; use `:8001/docs`.
- PostgreSQL data is stored in the named Docker volume `task_manager_db_data`.

Useful commands:

```bash
docker compose up --build
docker compose down
docker compose logs -f api
docker compose logs -f web
```

Helper scripts:

```bash
scripts/docker/db-context.sh
scripts/docker/db-sync-credentials.sh
scripts/docker/db-backup.sh
```

`scripts/docker/db-backup.sh` writes dumps to `./backups` by default, and that directory is intentionally gitignored.

## Run The Backend Locally

Prerequisites:

- Python 3.12
- PostgreSQL running locally, or `docker compose up -d db`

Setup:

```bash
cp .env.example .env
python3 -m venv .venv
./.venv/bin/pip install -r apps/api/requirements.txt
cd apps/api
../../.venv/bin/alembic upgrade head
../../.venv/bin/uvicorn app.main:app --reload
```

Endpoints:

- API docs: `http://localhost:8000/docs`
- Health check: `http://localhost:8000/health`

The backend loads env values from `apps/api/.env` or the repo-root `.env`. The root `.env` is the recommended default for this repo.

## Run The Web Locally

Prerequisites:

- Node.js 18+

Setup:

```bash
cd apps/web
npm install
npm run dev
```

Optional web env override:

```bash
cp .env.example .env.local
```

Open:

- Web app: `http://localhost:5173`

Web API behavior:

- By default the app uses `/api`.
- In Vite dev mode, `/api` is proxied to `http://localhost:8000`.
- If you need a different API origin, set `VITE_API_BASE_URL` in `apps/web/.env.local`.

## Run The Android App

Prerequisites:

- Android Studio
- JDK 17
- Android SDK 34

Recommended setup:

```bash
cp .env.example .env
docker compose up --build
```

Then open `apps/android` in Android Studio and run the `app` configuration.

If Android Studio does not create `apps/android/local.properties`, copy the example and set your SDK path:

```bash
cp apps/android/local.properties.example apps/android/local.properties
```

### Android Base URL

Recommended values inside the app settings:

- Emulator against this repo's Docker stack: `http://10.0.2.2/api/`
- Physical device on the same LAN: `http://YOUR_HOST_LAN_IP/api/`

Why:

- `localhost` on Android points to the emulator or phone itself, not your laptop.
- The Android client expects an `/api/` base path.
- The Docker stack already exposes that path through Caddy on host port `80`.

### Android Build Notes

- Debug cleartext HTTP is enabled only through `apps/android/app/src/debug/AndroidManifest.xml` and `apps/android/app/src/debug/res/xml/network_security_config.xml`.
- Do not assume the same cleartext behavior for release builds.
- `apps/android/local.properties` is machine-specific and intentionally gitignored.

CLI commands:

```bash
cd apps/android
./gradlew testDebugUnitTest
./gradlew assembleDebug
```

The debug APK is written to `apps/android/app/build/outputs/apk/debug/app-debug.apk`.

## Validation Commands

Backend:

```bash
./.venv/bin/pytest
```

Web:

```bash
cd apps/web
npm test
npm run build
```

Android:

```bash
cd apps/android
./gradlew testDebugUnitTest
./gradlew assembleDebug
```

## Intentionally Ignored Local Files

The repo intentionally ignores local-only and machine-specific files, including:

- `.env` and `apps/*/.env*` local override files
- `apps/android/local.properties`
- Android build outputs, Gradle caches, APK/AAB artifacts
- `node_modules`, `dist`, coverage output, Python caches, virtualenvs
- local DB files, dumps, backups, logs, temp files
- signing material, keystores, Firebase service files, and secret property files
- local Docker Compose override files such as `compose.override.yaml`

These files are either machine-specific, generated, or may contain secrets and should not be committed to a public repository.

## Notes

- Backend: `apps/api`
- Web: `apps/web`
- Android: `apps/android`
- Android-specific guide: `apps/android/README.md`
- Infra: `compose.yaml`, `infra/docker`, `infra/caddy/Caddyfile`
