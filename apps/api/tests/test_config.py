from app.core.config import Settings


def test_settings_build_database_url_from_postgres_parts(monkeypatch) -> None:
    monkeypatch.setenv("POSTGRES_DB", "tasks")
    monkeypatch.setenv("POSTGRES_USER", "task_user")
    monkeypatch.setenv("POSTGRES_PASSWORD", "secret-password")
    monkeypatch.setenv("DATABASE_HOST", "db")
    monkeypatch.setenv("DATABASE_PORT", "5433")
    monkeypatch.delenv("DATABASE_URL", raising=False)

    settings = Settings(_env_file=None)

    assert settings.resolved_database_url == "postgresql+psycopg://task_user:secret-password@db:5433/tasks"


def test_database_url_overrides_derived_postgres_parts(monkeypatch) -> None:
    monkeypatch.setenv("DATABASE_URL", "postgresql+psycopg://override:pw@custom-host:5434/custom-db")
    monkeypatch.setenv("POSTGRES_PASSWORD", "ignored")

    settings = Settings(_env_file=None)

    assert settings.resolved_database_url == "postgresql+psycopg://override:pw@custom-host:5434/custom-db"
