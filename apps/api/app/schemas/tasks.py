from __future__ import annotations

import re
from calendar import monthrange
from datetime import date, datetime
from typing import Literal

from pydantic import AliasChoices, BaseModel, ConfigDict, Field, field_validator, model_validator

from app.models.task import TaskCustomRepeatUnit, TaskPriority, TaskRepeat
from app.schemas.task_description import TaskDescriptionBlock

REMINDER_TIME_PATTERN = re.compile(r"^\d{2}:\d{2}$")


class TaskCustomRepeatConfig(BaseModel):
    model_config = ConfigDict(extra="forbid")

    interval: int = Field(default=1, ge=1)
    unit: TaskCustomRepeatUnit
    skip_weekends: bool = Field(default=False, alias="skip_weekends")
    weekdays: list[int] = Field(default_factory=list)
    month_day: int | None = Field(default=None, ge=1, le=31)
    month: int | None = Field(default=None, ge=1, le=12)
    day: int | None = Field(default=None, ge=1, le=31)

    @field_validator("weekdays")
    @classmethod
    def normalize_weekdays(cls, value: list[int]) -> list[int]:
        normalized = sorted(set(value))
        if any(day < 1 or day > 7 for day in normalized):
            raise ValueError("Weekdays must use ISO weekday numbers from 1 to 7.")
        return normalized

    @model_validator(mode="after")
    def validate_configuration(self) -> "TaskCustomRepeatConfig":
        if self.unit == TaskCustomRepeatUnit.WEEK:
            if not self.weekdays:
                raise ValueError("Weekly custom repeat requires at least one weekday.")
            return self

        if self.unit == TaskCustomRepeatUnit.MONTH:
            if self.month_day is None:
                raise ValueError("Monthly custom repeat requires month_day.")
            return self

        if self.unit == TaskCustomRepeatUnit.YEAR:
            if self.month is None or self.day is None:
                raise ValueError("Yearly custom repeat requires month and day.")
            max_day = monthrange(2024 if self.month == 2 else 2025, self.month)[1]
            if self.day > max_day:
                raise ValueError("Yearly custom repeat day is invalid for the selected month.")
            return self

        return self


class TaskSubtaskCreate(BaseModel):
    model_config = ConfigDict(extra="forbid")

    title: str = Field(min_length=1, max_length=200)
    description: str | None = None
    description_blocks: list[TaskDescriptionBlock] | None = None
    due_date: date | None = None
    reminder_time: str | None = None
    is_done: bool = False

    @field_validator("title")
    @classmethod
    def strip_title(cls, value: str) -> str:
        cleaned = value.strip()
        if not cleaned:
            raise ValueError("Task title cannot be empty.")
        return cleaned

    @field_validator("description")
    @classmethod
    def normalize_description(cls, value: str | None) -> str | None:
        if value is None:
            return None
        cleaned = value.strip()
        return cleaned or None

    @field_validator("reminder_time")
    @classmethod
    def normalize_reminder_time(cls, value: str | None) -> str | None:
        if value is None:
            return None
        cleaned = value.strip()
        if not cleaned:
            return None
        if not REMINDER_TIME_PATTERN.fullmatch(cleaned):
            raise ValueError("Reminder time must use HH:MM format.")
        hours, minutes = cleaned.split(":")
        if int(hours) > 23 or int(minutes) > 59:
            raise ValueError("Reminder time must use HH:MM format.")
        return cleaned


class TaskCreate(BaseModel):
    model_config = ConfigDict(extra="forbid")

    client_request_id: str | None = Field(default=None, min_length=1, max_length=64)
    title: str = Field(min_length=1, max_length=200)
    description: str | None = None
    description_blocks: list[TaskDescriptionBlock] | None = None
    due_date: date | None = None
    reminder_time: str | None = None
    is_done: bool = False
    is_pinned: bool = False
    priority: TaskPriority = TaskPriority.NOT_URGENT_UNIMPORTANT
    repeat: TaskRepeat = TaskRepeat.NONE
    repeat_config: TaskCustomRepeatConfig | None = None
    repeat_until: date | None = None
    parent_id: int | None = None
    list_id: int | None = None
    subtasks: list[TaskSubtaskCreate] = Field(default_factory=list)

    @field_validator("title")
    @classmethod
    def strip_title(cls, value: str) -> str:
        cleaned = value.strip()
        if not cleaned:
            raise ValueError("Task title cannot be empty.")
        return cleaned

    @field_validator("description")
    @classmethod
    def normalize_description(cls, value: str | None) -> str | None:
        if value is None:
            return None
        cleaned = value.strip()
        return cleaned or None

    @field_validator("client_request_id")
    @classmethod
    def normalize_client_request_id(cls, value: str | None) -> str | None:
        if value is None:
            return None
        cleaned = value.strip()
        return cleaned or None

    @field_validator("reminder_time")
    @classmethod
    def normalize_reminder_time(cls, value: str | None) -> str | None:
        return TaskSubtaskCreate.normalize_reminder_time(value)


class TaskUpdate(BaseModel):
    model_config = ConfigDict(extra="forbid")

    title: str | None = Field(default=None, min_length=1, max_length=200)
    description: str | None = None
    description_blocks: list[TaskDescriptionBlock] | None = None
    due_date: date | None = None
    reminder_time: str | None = None
    is_done: bool | None = None
    is_pinned: bool | None = None
    priority: TaskPriority | None = None
    repeat: TaskRepeat | None = None
    repeat_config: TaskCustomRepeatConfig | None = None
    repeat_until: date | None = None
    list_id: int | None = None

    @field_validator("title")
    @classmethod
    def strip_title(cls, value: str | None) -> str | None:
        if value is None:
            return None
        cleaned = value.strip()
        if not cleaned:
            raise ValueError("Task title cannot be empty.")
        return cleaned

    @field_validator("description")
    @classmethod
    def normalize_description(cls, value: str | None) -> str | None:
        if value is None:
            return None
        cleaned = value.strip()
        return cleaned or None

    @field_validator("reminder_time")
    @classmethod
    def normalize_reminder_time(cls, value: str | None) -> str | None:
        return TaskSubtaskCreate.normalize_reminder_time(value)


class TaskSubtaskRead(BaseModel):
    model_config = ConfigDict(from_attributes=True)

    id: int
    title: str
    description: str | None
    description_blocks: list[TaskDescriptionBlock] = Field(default_factory=list)
    due_date: date | None
    reminder_time: str | None
    is_done: bool
    is_pinned: bool
    priority: TaskPriority
    repeat: TaskRepeat
    repeat_config: TaskCustomRepeatConfig | None = None
    repeat_until: date | None
    parent_id: int | None
    position: int
    list_id: int | None
    created_at: datetime
    updated_at: datetime


class TaskRead(TaskSubtaskRead):
    subtasks: list[TaskSubtaskRead] = Field(
        default_factory=list,
        validation_alias=AliasChoices("subtasks", "active_subtasks"),
    )


class SubtaskReorderPayload(BaseModel):
    model_config = ConfigDict(extra="forbid")

    subtask_ids: list[int] = Field(default_factory=list, min_length=1)

    @field_validator("subtask_ids")
    @classmethod
    def ensure_unique_subtask_ids(cls, value: list[int]) -> list[int]:
        if len(value) != len(set(value)):
            raise ValueError("Subtask ids must be unique.")
        return value


class TopLevelTaskReorderPayload(BaseModel):
    model_config = ConfigDict(extra="forbid")

    task_ids: list[int] = Field(default_factory=list, min_length=1)
    scope: "TopLevelTaskReorderScope"

    @field_validator("task_ids")
    @classmethod
    def ensure_unique_task_ids(cls, value: list[int]) -> list[int]:
        if len(value) != len(set(value)):
            raise ValueError("Task ids must be unique.")
        return value


class TopLevelTaskReorderScope(BaseModel):
    model_config = ConfigDict(extra="forbid")

    view: Literal["list", "inbox", "today", "tomorrow", "all"]
    section_id: Literal[
        "pinned",
        "urgent_important",
        "not_urgent_important",
        "urgent_unimportant",
        "not_urgent_unimportant",
    ]
    list_id: int | None = None
    target_date: date | None = None
    group_id: Literal["overdue", "today", "tomorrow", "next_7_days", "later", "no_date"] | None = None
    reference_date: date | None = None

    @model_validator(mode="after")
    def validate_scope(self) -> TopLevelTaskReorderScope:
        if self.view == "list":
            if self.list_id is None:
                raise ValueError("List reorder scope requires list_id.")
            if self.target_date is not None or self.group_id is not None or self.reference_date is not None:
                raise ValueError("List reorder scope only accepts list_id and section_id.")
            return self

        if self.view == "inbox":
            if self.list_id is not None or self.target_date is not None or self.group_id is not None or self.reference_date is not None:
                raise ValueError("Inbox reorder scope only accepts section_id.")
            return self

        if self.view in {"today", "tomorrow"}:
            if self.target_date is None:
                raise ValueError("Date-based reorder scope requires target_date.")
            if self.list_id is not None or self.group_id is not None or self.reference_date is not None:
                raise ValueError("Date-based reorder scope only accepts target_date and section_id.")
            return self

        if self.group_id is None or self.reference_date is None:
            raise ValueError("All-view reorder scope requires group_id and reference_date.")

        if self.list_id is not None or self.target_date is not None:
            raise ValueError("All-view reorder scope only accepts group_id, reference_date, and section_id.")

        return self


TopLevelTaskReorderPayload.model_rebuild()


class TaskMovePayload(BaseModel):
    model_config = ConfigDict(extra="forbid")

    destination_parent_id: int | None = None
    destination_scope: TopLevelTaskReorderScope | None = None
    ordered_ids: list[int] = Field(default_factory=list, min_length=1)

    @field_validator("ordered_ids")
    @classmethod
    def ensure_unique_ordered_ids(cls, value: list[int]) -> list[int]:
        if len(value) != len(set(value)):
            raise ValueError("Moved task ids must be unique.")
        return value

    @model_validator(mode="after")
    def validate_destination(self) -> "TaskMovePayload":
        has_parent_destination = self.destination_parent_id is not None
        has_scope_destination = self.destination_scope is not None

        if has_parent_destination == has_scope_destination:
            raise ValueError("Task move payload requires exactly one destination target.")

        return self


class TaskMoveResult(BaseModel):
    model_config = ConfigDict(extra="forbid")

    task: TaskSubtaskRead
    affected_tasks: list[TaskRead] = Field(default_factory=list)
    removed_top_level_task_ids: list[int] = Field(default_factory=list)
