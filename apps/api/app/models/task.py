from __future__ import annotations

from datetime import date, datetime, timezone
from enum import Enum
from typing import List, Optional

from sqlalchemy import Boolean, Date, DateTime, Enum as SqlEnum, ForeignKey, Integer, JSON, String, Text, and_, text
from sqlalchemy.orm import Mapped, foreign, mapped_column, relationship, remote

from app.db.base import Base


def utcnow() -> datetime:
    return datetime.now(timezone.utc)


def enum_values(enum_class: type[Enum]) -> list[str]:
    return [str(member.value) for member in enum_class]


class TaskPriority(str, Enum):
    URGENT_IMPORTANT = "urgent_important"
    NOT_URGENT_IMPORTANT = "not_urgent_important"
    URGENT_UNIMPORTANT = "urgent_unimportant"
    NOT_URGENT_UNIMPORTANT = "not_urgent_unimportant"


class TaskRepeat(str, Enum):
    NONE = "none"
    DAILY = "daily"
    WEEKLY = "weekly"
    MONTHLY = "monthly"
    YEARLY = "yearly"
    CUSTOM = "custom"


class TaskCustomRepeatUnit(str, Enum):
    DAY = "day"
    WEEK = "week"
    MONTH = "month"
    YEAR = "year"


class Task(Base):
    __tablename__ = "tasks"

    id: Mapped[int] = mapped_column(Integer, primary_key=True, index=True)
    client_request_id: Mapped[str | None] = mapped_column(String(64), nullable=True, unique=True, index=True)
    title: Mapped[str] = mapped_column(String(200), nullable=False)
    description: Mapped[str | None] = mapped_column(Text, nullable=True)
    description_blocks: Mapped[list[dict[str, object]]] = mapped_column(
        JSON,
        nullable=False,
        default=list,
        server_default=text("'[]'"),
    )
    due_date: Mapped[date | None] = mapped_column(Date, nullable=True, index=True)
    reminder_time: Mapped[str | None] = mapped_column(String(5), nullable=True)
    repeat_until: Mapped[date | None] = mapped_column(Date, nullable=True)
    is_done: Mapped[bool] = mapped_column(Boolean, nullable=False, default=False)
    is_pinned: Mapped[bool] = mapped_column(
        Boolean,
        nullable=False,
        default=False,
        server_default=text("false"),
    )
    priority: Mapped[TaskPriority] = mapped_column(
        SqlEnum(
            TaskPriority,
            name="task_priority",
            native_enum=False,
            create_constraint=True,
            values_callable=enum_values,
            validate_strings=True,
        ),
        nullable=False,
        default=TaskPriority.NOT_URGENT_UNIMPORTANT,
        server_default=TaskPriority.NOT_URGENT_UNIMPORTANT.value,
    )
    repeat: Mapped[TaskRepeat] = mapped_column(
        SqlEnum(
            TaskRepeat,
            name="task_repeat",
            native_enum=False,
            create_constraint=True,
            values_callable=enum_values,
            validate_strings=True,
        ),
        nullable=False,
        default=TaskRepeat.NONE,
        server_default=TaskRepeat.NONE.value,
    )
    repeat_config: Mapped[dict[str, object] | None] = mapped_column(JSON, nullable=True)
    parent_id: Mapped[int | None] = mapped_column(
        ForeignKey("tasks.id", ondelete="CASCADE"),
        nullable=True,
        index=True,
    )
    position: Mapped[int] = mapped_column(
        Integer,
        nullable=False,
        default=0,
        server_default=text("0"),
    )
    list_id: Mapped[int | None] = mapped_column(ForeignKey("lists.id", ondelete="SET NULL"), nullable=True, index=True)
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), nullable=False, default=utcnow)
    updated_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), nullable=False, default=utcnow, onupdate=utcnow)
    deleted_at: Mapped[datetime | None] = mapped_column(DateTime(timezone=True), nullable=True, index=True)

    list = relationship("TaskList", back_populates="tasks")
    parent: Mapped[Optional["Task"]] = relationship(
        "Task",
        back_populates="subtasks",
        remote_side=lambda: [Task.id],
        foreign_keys=lambda: [Task.parent_id],
    )
    subtasks: Mapped[List["Task"]] = relationship(
        "Task",
        back_populates="parent",
        cascade="all, delete-orphan",
        passive_deletes=True,
        single_parent=True,
        uselist=True,
        foreign_keys=lambda: [Task.parent_id],
        primaryjoin=lambda: and_(
            Task.id == remote(foreign(Task.parent_id)),
            remote(Task.deleted_at).is_(None),
        ),
        lazy="selectin",
        order_by=lambda: [Task.position.asc(), Task.created_at.asc()],
    )

    @property
    def active_subtasks(self) -> list["Task"]:
        return list(self.subtasks)

    @property
    def is_deleted(self) -> bool:
        return self.deleted_at is not None
