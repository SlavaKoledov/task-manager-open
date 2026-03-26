PROJECT STACK

Architecture
- API-first monolith
- Separate frontend and backend
- Single GitHub monorepo

Backend
- Python 3.12
- FastAPI
- Pydantic v2
- SQLAlchemy 2.x
- Alembic
- PostgreSQL
- Uvicorn
- Docker
- Docker Compose
- Caddy

Web frontend
- TypeScript
- React
- Vite
- React Router
- TanStack Query
- Tailwind CSS
- shadcn/ui

Android app
- Kotlin
- Jetpack Compose
- Navigation Compose
- Retrofit
- Kotlin Coroutines
- Hilt
- DataStore
- Room only if offline cache becomes necessary
- WorkManager only if background sync or notifications become necessary

Testing
- Backend: pytest
- Frontend: Vitest
- Android: JUnit + Compose UI tests

Infra
- app
- web
- db
- caddy

API contract
- REST JSON API
- Backend is the single source of truth
- Web and Android work only through the API
