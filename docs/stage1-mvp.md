# Stage 1 MVP

This repository implements the first stage of a local-first personal task tracker.

## Scope

- FastAPI backend with PostgreSQL persistence
- React + Vite frontend that consumes the REST API
- Docker Compose startup with Caddy as the browser entry point
- Alembic migration for the initial `lists` and `tasks` schema
- Basic backend and frontend tests

## View semantics

- `All`: every task
- `Today`: tasks where `due_date` equals the browser's local date string
- `Inbox`: tasks where `due_date` is `NULL`
- `List`: tasks where `list_id` matches the selected list

## Current API surface

- `GET /lists`
- `POST /lists`
- `PATCH /lists/{id}`
- `DELETE /lists/{id}`
- `GET /tasks`
- `GET /tasks/today?today=YYYY-MM-DD`
- `GET /tasks/inbox`
- `GET /lists/{id}/tasks`
- `POST /tasks`
- `PATCH /tasks/{id}`
- `DELETE /tasks/{id}`
- `POST /tasks/{id}/toggle`
