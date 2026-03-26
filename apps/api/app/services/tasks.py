from __future__ import annotations

from calendar import monthrange
from copy import deepcopy
from datetime import date, timedelta

from fastapi import HTTPException, status
from sqlalchemy import case, func, select
from sqlalchemy.orm import Session, selectinload

from app.models.list import TaskList
from app.models.task import Task, TaskPriority, TaskRepeat, utcnow
from app.schemas.task_description import normalize_description_blocks, parse_legacy_description, serialize_description_blocks
from app.schemas.tasks import (
    TaskMovePayload,
    TaskMoveResult,
    TaskSubtaskCreate,
    TopLevelTaskReorderPayload,
    TopLevelTaskReorderScope,
    SubtaskReorderPayload,
    TaskCreate,
    TaskUpdate,
)
from app.services.live_events import publish_task_event


def _priority_ordering():
    return case(
        (Task.priority == TaskPriority.URGENT_IMPORTANT, 0),
        (Task.priority == TaskPriority.NOT_URGENT_IMPORTANT, 1),
        (Task.priority == TaskPriority.URGENT_UNIMPORTANT, 2),
        (Task.priority == TaskPriority.NOT_URGENT_UNIMPORTANT, 3),
        else_=4,
    )


def _task_ordering():
    return (
        Task.is_pinned.desc(),
        Task.is_done.asc(),
        _priority_ordering().asc(),
        Task.position.asc(),
        Task.created_at.desc(),
        Task.id.desc(),
    )


def _active_task_condition():
    return Task.deleted_at.is_(None)


def _top_level_statement():
    return (
        select(Task)
        .where(Task.parent_id.is_(None), _active_task_condition())
        .options(selectinload(Task.subtasks))
        .execution_options(populate_existing=True)
        .order_by(*_task_ordering())
    )


def _subtask_statement(parent_id: int):
    return (
        select(Task)
        .where(Task.parent_id == parent_id, _active_task_condition())
        .order_by(Task.position.asc(), Task.created_at.asc())
    )


def _resolve_next_position(session: Session, parent_id: int | None) -> int:
    statement = select(func.coalesce(func.max(Task.position) + 1, 0)).where(_active_task_condition())
    if parent_id is None:
        statement = statement.where(Task.parent_id.is_(None))
    else:
        statement = statement.where(Task.parent_id == parent_id)

    return int(session.scalar(statement) or 0)


def _get_parent_or_400(session: Session, parent_id: int) -> Task:
    parent = session.scalar(
        select(Task)
        .where(Task.id == parent_id, _active_task_condition())
        .options(selectinload(Task.subtasks))
    )
    if parent is None:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Parent task not found.")
    if parent.parent_id is not None:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail="Only one level of subtasks is supported.",
        )
    return parent


def _normalize_description_fields(
    description: str | None,
    description_blocks: object,
    prefer_blocks: bool,
) -> tuple[str | None, list[dict[str, object]]]:
    if prefer_blocks:
        normalized_blocks = normalize_description_blocks(description_blocks)  # type: ignore[arg-type]
    else:
        normalized_blocks = parse_legacy_description(description)

    normalized_description = serialize_description_blocks(normalized_blocks)
    return normalized_description, normalized_blocks


def _ensure_repeat_has_due_date(repeat: TaskRepeat, due_date: date | None) -> None:
    if repeat != TaskRepeat.NONE and due_date is None:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail="Recurring tasks require a due date.",
        )


def _ensure_repeat_configuration(
    repeat: TaskRepeat,
    due_date: date | None,
    repeat_until: date | None,
) -> None:
    _ensure_repeat_has_due_date(repeat, due_date)

    if repeat_until is None:
        return

    if repeat == TaskRepeat.NONE:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail="Repeat end date requires a repeat schedule.",
        )

    if due_date is None:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail="Repeat end date requires a due date.",
        )

    if repeat_until < due_date:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail="Repeat end date cannot be earlier than the due date.",
        )


def _ensure_reminder_configuration(
    due_date: date | None,
    reminder_time: str | None,
) -> None:
    if reminder_time is None:
        return

    if due_date is None:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail="Reminder time requires a due date.",
        )


def _resolve_next_due_date(repeat: TaskRepeat, due_date: date) -> date:
    if repeat == TaskRepeat.DAILY:
        return due_date + timedelta(days=1)

    if repeat == TaskRepeat.WEEKLY:
        return due_date + timedelta(days=7)

    if repeat == TaskRepeat.MONTHLY:
        next_month_index = due_date.month
        next_month_year = due_date.year + (next_month_index // 12)
        next_month = (next_month_index % 12) + 1
        last_day_of_month = monthrange(next_month_year, next_month)[1]
        return date(next_month_year, next_month, min(due_date.day, last_day_of_month))

    if repeat == TaskRepeat.YEARLY:
        next_year = due_date.year + 1
        last_day_of_month = monthrange(next_year, due_date.month)[1]
        return date(next_year, due_date.month, min(due_date.day, last_day_of_month))

    return due_date


def _classify_due_date_group(due_date: date | None, reference_date: date) -> str:
    if due_date is None:
        return "no_date"

    if due_date < reference_date:
        return "overdue"

    if due_date == reference_date:
        return "today"

    tomorrow_date = reference_date + timedelta(days=1)
    if due_date == tomorrow_date:
        return "tomorrow"

    next_seven_days_end = reference_date + timedelta(days=7)
    if due_date > tomorrow_date and due_date <= next_seven_days_end:
        return "next_7_days"

    return "later"


def _resolve_due_date_for_all_group(group_id: str, reference_date: date) -> date | None:
    if group_id == "overdue":
        return reference_date - timedelta(days=1)
    if group_id == "today":
        return reference_date
    if group_id == "tomorrow":
        return reference_date + timedelta(days=1)
    if group_id == "next_7_days":
        return reference_date + timedelta(days=2)
    if group_id == "later":
        return reference_date + timedelta(days=8)
    return None


def _apply_top_level_reorder_scope(statement, scope: TopLevelTaskReorderScope):
    statement = statement.where(Task.parent_id.is_(None), Task.is_done.is_(False), _active_task_condition())

    if scope.section_id == "pinned":
        statement = statement.where(Task.is_pinned.is_(True))
    else:
        statement = statement.where(Task.is_pinned.is_(False), Task.priority == TaskPriority(scope.section_id))

    if scope.view == "list":
        return statement.where(Task.list_id == scope.list_id)

    if scope.view == "inbox":
        return statement.where(Task.due_date.is_(None))

    if scope.view == "today":
        return statement.where(Task.due_date == scope.target_date)

    if scope.view == "tomorrow":
        return statement.where(Task.due_date == scope.target_date)

    if scope.group_id == "overdue":
        return statement.where(Task.due_date.is_not(None), Task.due_date < scope.reference_date)

    if scope.group_id == "today":
        return statement.where(Task.due_date == scope.reference_date)

    if scope.group_id == "tomorrow":
        return statement.where(Task.due_date == scope.reference_date + timedelta(days=1))

    if scope.group_id == "next_7_days":
        return statement.where(
            Task.due_date.is_not(None),
            Task.due_date > scope.reference_date + timedelta(days=1),
            Task.due_date <= scope.reference_date + timedelta(days=7),
        )

    if scope.group_id == "later":
        return statement.where(Task.due_date > scope.reference_date + timedelta(days=7))

    return statement.where(Task.due_date.is_(None))


def _task_matches_top_level_reorder_scope(task: Task, scope: TopLevelTaskReorderScope) -> bool:
    if task.parent_id is not None or task.is_done or task.deleted_at is not None:
        return False

    if scope.section_id == "pinned":
        if not task.is_pinned:
            return False
    elif task.is_pinned or task.priority != TaskPriority(scope.section_id):
        return False

    if scope.view == "list":
        return task.list_id == scope.list_id

    if scope.view == "inbox":
        return task.due_date is None

    if scope.view in {"today", "tomorrow"}:
        return task.due_date == scope.target_date

    return _classify_due_date_group(task.due_date, scope.reference_date) == scope.group_id


def _apply_destination_scope_to_task(task: Task, scope: TopLevelTaskReorderScope, session: Session) -> None:
    task.parent_id = None

    if scope.section_id == "pinned":
        task.is_pinned = True
    else:
        task.is_pinned = False
        task.priority = TaskPriority(scope.section_id)

    if scope.view == "list":
        ensure_list_exists(session, scope.list_id)
        task.list_id = scope.list_id
        return

    if scope.view == "inbox":
        task.due_date = None
        return

    if scope.view in {"today", "tomorrow"}:
        task.due_date = scope.target_date
        return

    task.due_date = _resolve_due_date_for_all_group(scope.group_id, scope.reference_date)


def _get_subtask_read_or_404(session: Session, task_id: int) -> Task:
    task = session.scalar(
        select(Task)
        .where(Task.id == task_id, _active_task_condition())
        .execution_options(populate_existing=True)
    )
    if task is None:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Task not found.")
    return task


def _load_tasks_by_ids(session: Session, task_ids: list[int]) -> dict[int, Task]:
    tasks = list(
        session.scalars(
            select(Task)
            .where(Task.id.in_(task_ids), _active_task_condition())
            .options(selectinload(Task.subtasks))
            .execution_options(populate_existing=True)
        )
    )
    return {task.id: task for task in tasks}


def _load_scope_tasks(scope: TopLevelTaskReorderScope, session: Session) -> list[Task]:
    return list(
        session.scalars(
            _apply_top_level_reorder_scope(select(Task), scope)
            .options(selectinload(Task.subtasks))
            .execution_options(populate_existing=True)
            .order_by(*_task_ordering())
        )
    )


def _prepare_create_values(session: Session, payload: TaskCreate, parent_task: Task | None = None) -> dict[str, object]:
    values = payload.model_dump()
    parent_id = values.pop("parent_id")
    values.pop("subtasks", None)
    has_blocks = payload.description_blocks is not None

    if parent_id is not None:
        parent_task = parent_task or _get_parent_or_400(session, parent_id)
        values["list_id"] = parent_task.list_id
        values["is_pinned"] = False
    else:
        ensure_list_exists(session, values.get("list_id"))

    description, description_blocks = _normalize_description_fields(
        values.get("description"),
        payload.description_blocks,
        prefer_blocks=has_blocks,
    )
    values["description"] = description
    values["description_blocks"] = description_blocks
    values["parent_id"] = parent_id
    values["position"] = _resolve_next_position(session, parent_id)
    _ensure_repeat_configuration(values["repeat"], values.get("due_date"), values.get("repeat_until"))
    _ensure_reminder_configuration(values.get("due_date"), values.get("reminder_time"))

    return values


def _build_nested_subtask_values(parent_task: Task, payload: TaskSubtaskCreate, position: int) -> dict[str, object]:
    has_blocks = payload.description_blocks is not None
    values = payload.model_dump()
    description, description_blocks = _normalize_description_fields(
        values.get("description"),
        payload.description_blocks,
        prefer_blocks=has_blocks,
    )
    _ensure_reminder_configuration(values.get("due_date"), values.get("reminder_time"))

    return {
        "title": values["title"],
        "description": description,
        "description_blocks": description_blocks,
        "due_date": values.get("due_date"),
        "reminder_time": values.get("reminder_time"),
        "is_done": values["is_done"],
        "is_pinned": False,
        "priority": parent_task.priority,
        "repeat": TaskRepeat.NONE,
        "repeat_until": None,
        "parent_id": parent_task.id,
        "position": position,
        "list_id": parent_task.list_id,
    }


def _create_subtasks_for_new_task(parent_task: Task, subtasks: list[TaskSubtaskCreate], session: Session) -> None:
    for index, subtask_payload in enumerate(subtasks):
        session.add(Task(**_build_nested_subtask_values(parent_task, subtask_payload, index)))


def _prepare_update_values(task: Task, payload: TaskUpdate, session: Session) -> dict[str, object]:
    updates = payload.model_dump(exclude_unset=True)
    description_field_updated = "description" in updates
    description_blocks_field_updated = "description_blocks" in payload.model_fields_set

    if "list_id" in updates:
        if task.parent_id is None:
            ensure_list_exists(session, updates["list_id"])
        else:
            updates["list_id"] = task.parent.list_id if task.parent else task.list_id

    if "is_pinned" in updates and task.parent_id is not None:
        updates["is_pinned"] = False

    if description_blocks_field_updated or description_field_updated:
        prefer_blocks = description_blocks_field_updated
        description, description_blocks = _normalize_description_fields(
            updates.get("description", task.description),
            updates.get("description_blocks", task.description_blocks),
            prefer_blocks=prefer_blocks,
        )
        updates["description"] = description
        updates["description_blocks"] = description_blocks

    next_repeat = updates.get("repeat", task.repeat)
    next_due_date = updates["due_date"] if "due_date" in updates else task.due_date
    next_reminder_time = updates["reminder_time"] if "reminder_time" in updates else task.reminder_time
    next_repeat_until = updates["repeat_until"] if "repeat_until" in updates else task.repeat_until

    if next_due_date is None:
        updates["reminder_time"] = None
        next_reminder_time = None

    if next_repeat == TaskRepeat.NONE:
        updates["repeat_until"] = None
        next_repeat_until = None

    _ensure_repeat_configuration(next_repeat, next_due_date, next_repeat_until)
    _ensure_reminder_configuration(next_due_date, next_reminder_time)

    return updates


def _sync_children_list_id(task: Task, session: Session) -> None:
    if task.parent_id is not None:
        return

    for subtask in task.active_subtasks:
        subtask.list_id = task.list_id
        session.add(subtask)


def _reindex_subtasks(parent_id: int, session: Session) -> None:
    sibling_tasks = list(session.scalars(_subtask_statement(parent_id)))
    for index, sibling in enumerate(sibling_tasks):
        sibling.position = index
        session.add(sibling)


def _clone_subtasks_for_spawned_task(source_task: Task, spawned_task: Task, session: Session) -> None:
    source_subtasks = list(session.scalars(_subtask_statement(source_task.id)))

    for index, source_subtask in enumerate(source_subtasks):
        cloned_subtask = Task(
            title=source_subtask.title,
            description=source_subtask.description,
            description_blocks=deepcopy(source_subtask.description_blocks),
            due_date=source_subtask.due_date,
            reminder_time=source_subtask.reminder_time,
            is_done=False,
            is_pinned=False,
            priority=source_subtask.priority,
            repeat=source_subtask.repeat,
            repeat_until=source_subtask.repeat_until,
            parent_id=spawned_task.id,
            position=index,
            list_id=source_subtask.list_id,
        )
        session.add(cloned_subtask)


def list_all_tasks(session: Session) -> list[Task]:
    return list(session.scalars(_top_level_statement()))


def list_tasks_for_date(session: Session, target_date: date) -> list[Task]:
    statement = _top_level_statement().where(Task.due_date == target_date)
    return list(session.scalars(statement))


def list_today_tasks(session: Session, target_date: date) -> list[Task]:
    statement = _top_level_statement().where(Task.due_date.is_not(None), Task.due_date <= target_date)
    return list(session.scalars(statement))


def list_tomorrow_tasks(session: Session, target_date: date) -> list[Task]:
    return list_tasks_for_date(session, target_date)


def list_inbox_tasks(session: Session) -> list[Task]:
    statement = _top_level_statement().where(Task.due_date.is_(None))
    return list(session.scalars(statement))


def list_tasks_for_list(session: Session, list_id: int) -> list[Task]:
    statement = _top_level_statement().where(Task.list_id == list_id)
    return list(session.scalars(statement))


def get_task_or_404(session: Session, task_id: int) -> Task:
    task = session.scalar(
        select(Task)
        .where(Task.id == task_id, _active_task_condition())
        .options(selectinload(Task.subtasks))
        .execution_options(populate_existing=True)
    )
    if task is None:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Task not found.")
    return task


def get_task_or_none(session: Session, task_id: int, *, include_deleted: bool = False) -> Task | None:
    statement = (
        select(Task)
        .where(Task.id == task_id)
        .options(selectinload(Task.subtasks))
        .execution_options(populate_existing=True)
    )
    if not include_deleted:
        statement = statement.where(_active_task_condition())
    return session.scalar(statement)


def ensure_list_exists(session: Session, list_id: int | None) -> None:
    if list_id is None:
        return
    if session.get(TaskList, list_id) is None:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="List not found.")


def create_task(session: Session, payload: TaskCreate) -> Task:
    if payload.client_request_id is not None:
        existing_task = session.scalar(
            select(Task)
            .where(Task.client_request_id == payload.client_request_id)
            .options(selectinload(Task.subtasks))
            .execution_options(populate_existing=True)
        )
        if existing_task is not None:
            return existing_task

    parent_task = _get_parent_or_400(session, payload.parent_id) if payload.parent_id is not None else None
    if parent_task is not None and payload.subtasks:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail="Subtasks cannot have nested subtasks.",
        )

    task = Task(**_prepare_create_values(session, payload, parent_task=parent_task))
    session.add(task)
    session.flush()

    if payload.subtasks:
        _create_subtasks_for_new_task(task, payload.subtasks, session)

    session.commit()
    if parent_task is not None:
        session.expire(parent_task, ["subtasks"])
    created_task = get_task_or_404(session, task.id)
    publish_task_event(task.id, parent_task.id if parent_task is not None else 0)
    return created_task


def update_task(task: Task, payload: TaskUpdate, session: Session) -> Task:
    updates = _prepare_update_values(task, payload, session)

    for field, value in updates.items():
        setattr(task, field, value)

    session.add(task)

    if "list_id" in updates:
        _sync_children_list_id(task, session)

    session.commit()
    session.refresh(task)
    updated_task = get_task_or_404(session, task.id)
    publish_task_event(task.id, task.parent_id or 0)
    return updated_task


def delete_task(task: Task, session: Session) -> None:
    if task.deleted_at is not None:
        return

    parent_id = task.parent_id
    task_id = task.id
    deleted_at = utcnow()
    affected_task_ids = [task_id]

    tasks_to_soft_delete = [task]
    if task.parent_id is None:
        tasks_to_soft_delete.extend(
            session.scalars(
                select(Task).where(Task.parent_id == task.id, _active_task_condition())
            )
        )

    for target in tasks_to_soft_delete:
        target.deleted_at = deleted_at
        target.updated_at = deleted_at
        session.add(target)
        if target.id != task_id:
            affected_task_ids.append(target.id)

    session.flush()

    if parent_id is not None:
        _reindex_subtasks(parent_id, session)

    session.commit()
    if parent_id is not None:
        parent_task = session.get(Task, parent_id)
        if parent_task is not None:
            session.expire(parent_task, ["subtasks"])
    session.expire(task)
    publish_task_event(*affected_task_ids, parent_id or 0)


def _spawn_next_recurring_task(task: Task, session: Session) -> Task | None:
    if task.due_date is None:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail="Recurring tasks require a due date.",
        )

    next_due_date = _resolve_next_due_date(task.repeat, task.due_date)
    if task.repeat_until is not None and next_due_date > task.repeat_until:
        return None

    spawned_task = Task(
        title=task.title,
        description=task.description,
        description_blocks=deepcopy(task.description_blocks),
        due_date=next_due_date,
        reminder_time=task.reminder_time,
        is_done=False,
        is_pinned=False if task.parent_id is not None else task.is_pinned,
        priority=task.priority,
        repeat=task.repeat,
        repeat_until=task.repeat_until,
        parent_id=task.parent_id,
        position=_resolve_next_position(session, task.parent_id),
        list_id=task.list_id,
    )
    session.add(spawned_task)
    session.flush()

    if task.parent_id is None:
        _clone_subtasks_for_spawned_task(task, spawned_task, session)

    return spawned_task


def toggle_task(task: Task, session: Session) -> Task:
    should_spawn_next_task = not task.is_done and task.repeat != TaskRepeat.NONE
    task.is_done = not task.is_done
    session.add(task)
    spawned_task: Task | None = None

    if should_spawn_next_task:
        spawned_task = _spawn_next_recurring_task(task, session)

    session.commit()
    session.refresh(task)

    if spawned_task is not None:
        session.refresh(spawned_task)
    toggled_task = get_task_or_404(session, task.id)
    publish_task_event(task.id, spawned_task.id if spawned_task is not None else 0, task.parent_id or 0)
    return toggled_task


def reorder_subtasks(parent_task: Task, payload: SubtaskReorderPayload, session: Session) -> Task:
    if parent_task.parent_id is not None:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail="Only top-level tasks can reorder subtasks.",
        )

    current_subtasks = list(parent_task.active_subtasks)
    current_ids = [subtask.id for subtask in current_subtasks]

    if sorted(payload.subtask_ids) != sorted(current_ids):
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail="Subtask reorder payload must contain the exact current subtask ids.",
        )

    positions = {subtask_id: index for index, subtask_id in enumerate(payload.subtask_ids)}

    for subtask in current_subtasks:
        subtask.position = positions[subtask.id]
        session.add(subtask)

    session.commit()
    session.refresh(parent_task)
    updated_parent = get_task_or_404(session, parent_task.id)
    publish_task_event(parent_task.id, *payload.subtask_ids)
    return updated_parent


def reorder_top_level_tasks(payload: TopLevelTaskReorderPayload, session: Session) -> list[Task]:
    tasks = list(
        session.scalars(
            select(Task)
            .where(Task.id.in_(payload.task_ids), _active_task_condition())
            .options(selectinload(Task.subtasks))
            .execution_options(populate_existing=True)
        )
    )

    if len(tasks) != len(payload.task_ids):
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail="Task reorder payload contains invalid ids.",
        )

    if any(task.parent_id is not None for task in tasks):
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail="Only top-level tasks can be reordered.",
        )

    if any(task.is_done for task in tasks):
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail="Completed tasks cannot be manually reordered.",
        )

    for task in tasks:
        if not _task_matches_top_level_reorder_scope(task, payload.scope):
            raise HTTPException(
                status_code=status.HTTP_400_BAD_REQUEST,
                detail="Tasks must belong to the requested reorder scope.",
            )

    scope_tasks = list(
        session.scalars(
            _apply_top_level_reorder_scope(select(Task), payload.scope)
            .options(selectinload(Task.subtasks))
            .execution_options(populate_existing=True)
            .order_by(*_task_ordering())
        )
    )
    scope_ids = [task.id for task in scope_tasks]

    if sorted(payload.task_ids) != sorted(scope_ids):
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail="Task reorder payload must contain the exact current section task ids.",
        )

    positions = {task_id: index for index, task_id in enumerate(payload.task_ids)}
    for task in scope_tasks:
        task.position = positions[task.id]
        session.add(task)

    session.commit()
    reordered_tasks = list(
        session.scalars(
            select(Task)
            .where(Task.id.in_(payload.task_ids), _active_task_condition())
            .options(selectinload(Task.subtasks))
            .execution_options(populate_existing=True)
            .order_by(Task.position.asc(), Task.created_at.desc(), Task.id.desc())
        )
    )
    publish_task_event(*payload.task_ids)
    return reordered_tasks


def move_task(task: Task, payload: TaskMovePayload, session: Session) -> TaskMoveResult:
    if task.is_done:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail="Completed tasks cannot be moved.",
        )

    old_parent_id = task.parent_id
    removed_top_level_task_ids: list[int] = []
    affected_top_level_ids: set[int] = set()

    if payload.destination_parent_id is not None:
        destination_parent = _get_parent_or_400(session, payload.destination_parent_id)

        if destination_parent.is_done:
            raise HTTPException(
                status_code=status.HTTP_400_BAD_REQUEST,
                detail="Completed tasks cannot become subtask parents.",
            )

        if destination_parent.id == task.id:
            raise HTTPException(
                status_code=status.HTTP_400_BAD_REQUEST,
                detail="A task cannot become a subtask of itself.",
            )

        if task.parent_id is None and task.active_subtasks:
            raise HTTPException(
                status_code=status.HTTP_400_BAD_REQUEST,
                detail="Tasks with subtasks cannot be nested under another task.",
            )

        destination_subtasks = list(session.scalars(_subtask_statement(destination_parent.id)))
        destination_ids = [subtask.id for subtask in destination_subtasks]
        expected_ids = destination_ids if task.parent_id == destination_parent.id else [*destination_ids, task.id]

        if sorted(payload.ordered_ids) != sorted(expected_ids):
            raise HTTPException(
                status_code=status.HTTP_400_BAD_REQUEST,
                detail="Move payload must contain the exact destination subtask ids.",
            )

        if task.parent_id is None:
            removed_top_level_task_ids.append(task.id)

        task.parent_id = destination_parent.id
        task.list_id = destination_parent.list_id
        task.is_pinned = False
        session.add(task)
        session.flush()

        destination_tasks = _load_tasks_by_ids(session, payload.ordered_ids)
        if len(destination_tasks) != len(payload.ordered_ids):
            raise HTTPException(
                status_code=status.HTTP_400_BAD_REQUEST,
                detail="Move payload contains invalid task ids.",
            )

        for index, sibling_id in enumerate(payload.ordered_ids):
            sibling = task if sibling_id == task.id else destination_tasks[sibling_id]

            if sibling.id != task.id and sibling.parent_id != destination_parent.id:
                raise HTTPException(
                    status_code=status.HTTP_400_BAD_REQUEST,
                    detail="Move payload contains tasks outside the destination parent.",
                )

            sibling.parent_id = destination_parent.id
            sibling.list_id = destination_parent.list_id
            sibling.is_pinned = False
            sibling.position = index
            session.add(sibling)

        if old_parent_id is not None and old_parent_id != destination_parent.id:
            _reindex_subtasks(old_parent_id, session)
            affected_top_level_ids.add(old_parent_id)

        affected_top_level_ids.add(destination_parent.id)
    else:
        assert payload.destination_scope is not None
        destination_scope = payload.destination_scope
        destination_tasks = _load_scope_tasks(destination_scope, session)
        destination_ids = [candidate.id for candidate in destination_tasks]

        expected_ids = destination_ids if task.parent_id is None and _task_matches_top_level_reorder_scope(task, destination_scope) else [*destination_ids, task.id]

        if sorted(payload.ordered_ids) != sorted(expected_ids):
            raise HTTPException(
                status_code=status.HTTP_400_BAD_REQUEST,
                detail="Move payload must contain the exact destination scope ids.",
            )

        destination_task_map = _load_tasks_by_ids(session, payload.ordered_ids)
        if len(destination_task_map) != len(payload.ordered_ids):
            raise HTTPException(
                status_code=status.HTTP_400_BAD_REQUEST,
                detail="Move payload contains invalid task ids.",
            )

        _apply_destination_scope_to_task(task, destination_scope, session)
        session.add(task)
        session.flush()

        for index, sibling_id in enumerate(payload.ordered_ids):
            sibling = task if sibling_id == task.id else destination_task_map[sibling_id]

            if sibling.id != task.id and not _task_matches_top_level_reorder_scope(sibling, destination_scope):
                raise HTTPException(
                    status_code=status.HTTP_400_BAD_REQUEST,
                    detail="Move payload contains tasks outside the destination scope.",
                )

            if sibling.id == task.id:
                _apply_destination_scope_to_task(sibling, destination_scope, session)

            sibling.parent_id = None
            sibling.position = index
            session.add(sibling)

        if old_parent_id is not None:
            _reindex_subtasks(old_parent_id, session)
            affected_top_level_ids.add(old_parent_id)

        if task.parent_id is None and task.active_subtasks:
            _sync_children_list_id(task, session)

        affected_top_level_ids.update(payload.ordered_ids)

    session.commit()

    moved_task = _get_subtask_read_or_404(session, task.id)
    affected_tasks = [get_task_or_404(session, task_id) for task_id in sorted(affected_top_level_ids)]

    publish_task_event(task.id, *affected_top_level_ids, *removed_top_level_task_ids)

    return TaskMoveResult(
        task=moved_task,
        affected_tasks=affected_tasks,
        removed_top_level_task_ids=sorted(removed_top_level_task_ids),
    )
