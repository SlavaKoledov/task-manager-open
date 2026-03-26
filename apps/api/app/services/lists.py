from fastapi import HTTPException, status
from sqlalchemy import func, select, update
from sqlalchemy.exc import IntegrityError
from sqlalchemy.orm import Session

from app.models.list import TaskList
from app.models.task import Task
from app.schemas.lists import ListCreate, ListReorderPayload, ListUpdate
from app.services.live_events import publish_list_event, publish_task_event


def list_lists(session: Session) -> list[TaskList]:
    statement = select(TaskList).order_by(TaskList.position.asc(), TaskList.created_at.asc())
    return list(session.scalars(statement))


def get_list_or_none(session: Session, list_id: int) -> TaskList | None:
    return session.get(TaskList, list_id)


def create_list(session: Session, payload: ListCreate) -> TaskList:
    next_position = session.scalar(select(func.coalesce(func.max(TaskList.position) + 1, 0)))
    task_list = TaskList(name=payload.name, color=payload.color, position=int(next_position or 0))
    session.add(task_list)
    _commit_or_raise_conflict(session)
    session.refresh(task_list)
    publish_list_event(task_list.id)
    return task_list


def update_list(task_list: TaskList, payload: ListUpdate, session: Session) -> TaskList:
    updates = payload.model_dump(exclude_unset=True)
    for field, value in updates.items():
        setattr(task_list, field, value)
    session.add(task_list)
    _commit_or_raise_conflict(session)
    session.refresh(task_list)
    publish_list_event(task_list.id)
    return task_list


def delete_list(task_list: TaskList, session: Session) -> None:
    affected_task_ids = list(session.scalars(select(Task.id).where(Task.list_id == task_list.id)))
    deleted_list_id = task_list.id
    session.execute(update(Task).where(Task.list_id == task_list.id).values(list_id=None))
    session.execute(
        update(TaskList)
        .where(TaskList.position > task_list.position)
        .values(position=TaskList.position - 1)
    )
    session.delete(task_list)
    session.commit()
    publish_list_event(deleted_list_id)
    if affected_task_ids:
        publish_task_event(*affected_task_ids)


def reorder_list_positions(payload: ListReorderPayload, session: Session) -> list[TaskList]:
    current_lists = list_lists(session)
    current_ids = [task_list.id for task_list in current_lists]

    if sorted(payload.list_ids) != sorted(current_ids):
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail="List reorder payload must contain the exact current list ids.",
        )

    positions = {list_id: index for index, list_id in enumerate(payload.list_ids)}
    for task_list in current_lists:
        task_list.position = positions[task_list.id]
        session.add(task_list)

    session.commit()
    reordered_lists = list_lists(session)
    publish_list_event(*payload.list_ids)
    return reordered_lists


def _commit_or_raise_conflict(session: Session) -> None:
    try:
        session.commit()
    except IntegrityError as error:
        session.rollback()
        raise HTTPException(
            status_code=status.HTTP_409_CONFLICT,
            detail="A list with this name already exists.",
        ) from error
