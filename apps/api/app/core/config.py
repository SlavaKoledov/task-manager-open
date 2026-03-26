from functools import lru_cache

from pydantic import Field
from pydantic_settings import BaseSettings, SettingsConfigDict
from sqlalchemy.engine import URL


class Settings(BaseSettings):
    app_name: str = "Task Manager API"
    database_url: str | None = None
    postgres_db: str = "task_manager"
    postgres_user: str = "task_manager"
    postgres_password: str = "local-dev-password"
    database_host: str = "localhost"
    database_port: int = 5432
    cors_origins: list[str] = Field(
        default_factory=lambda: [
            "http://localhost",
            "http://127.0.0.1",
            "http://localhost:5173",
            "http://127.0.0.1:5173",
        ]
    )

    model_config = SettingsConfigDict(
        env_file=(".env", "../../.env"),
        env_file_encoding="utf-8",
        extra="ignore",
    )

    @property
    def resolved_database_url(self) -> str:
        if self.database_url:
            return self.database_url

        return URL.create(
            "postgresql+psycopg",
            username=self.postgres_user,
            password=self.postgres_password,
            host=self.database_host,
            port=self.database_port,
            database=self.postgres_db,
        ).render_as_string(hide_password=False)


@lru_cache
def get_settings() -> Settings:
    return Settings()
