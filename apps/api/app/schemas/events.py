from __future__ import annotations

from datetime import datetime
from typing import Literal

from pydantic import BaseModel, ConfigDict, Field


class LiveEventRead(BaseModel):
    model_config = ConfigDict(extra="forbid")

    version: int
    entity_type: Literal["task", "list"]
    entity_ids: list[int] = Field(default_factory=list)
    changed_at: datetime
