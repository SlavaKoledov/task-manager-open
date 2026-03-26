from datetime import datetime

from pydantic import BaseModel, ConfigDict, Field, field_validator


class ListBase(BaseModel):
    name: str = Field(min_length=1, max_length=120)
    color: str = Field(pattern=r"^#[0-9A-Fa-f]{6}$")

    @field_validator("name")
    @classmethod
    def strip_name(cls, value: str) -> str:
        cleaned = value.strip()
        if not cleaned:
            raise ValueError("List name cannot be empty.")
        return cleaned


class ListCreate(ListBase):
    pass


class ListUpdate(BaseModel):
    name: str | None = Field(default=None, min_length=1, max_length=120)
    color: str | None = Field(default=None, pattern=r"^#[0-9A-Fa-f]{6}$")

    @field_validator("name")
    @classmethod
    def strip_name(cls, value: str | None) -> str | None:
        if value is None:
            return None
        cleaned = value.strip()
        if not cleaned:
            raise ValueError("List name cannot be empty.")
        return cleaned


class ListRead(BaseModel):
    model_config = ConfigDict(from_attributes=True)

    id: int
    name: str
    color: str
    position: int
    created_at: datetime
    updated_at: datetime


class ListReorderPayload(BaseModel):
    model_config = ConfigDict(extra="forbid")

    list_ids: list[int] = Field(default_factory=list, min_length=1)

    @field_validator("list_ids")
    @classmethod
    def ensure_unique_list_ids(cls, value: list[int]) -> list[int]:
        if len(value) != len(set(value)):
            raise ValueError("List ids must be unique.")
        return value
