from datetime import date, timedelta

from fastapi import APIRouter, Depends, HTTPException, Query, Response, status
from sqlalchemy.orm import Session

from app.db.session import get_db
from app.schemas.tasks import (
    TaskMovePayload,
    TaskMoveResult,
    TopLevelTaskReorderPayload,
    SubtaskReorderPayload,
    TaskCreate,
    TaskRead,
    TaskUpdate,
)
from app.services import lists as list_service
from app.services import tasks as task_service

router = APIRouter()


@router.get("/tasks", response_model=list[TaskRead])
def get_tasks(session: Session = Depends(get_db)) -> list[TaskRead]:
    return task_service.list_all_tasks(session)


@router.get("/tasks/today", response_model=list[TaskRead])
def get_today_tasks(
    today: date | None = Query(default=None),
    session: Session = Depends(get_db),
) -> list[TaskRead]:
    target_date = today or date.today()
    return task_service.list_today_tasks(session, target_date)


@router.get("/tasks/tomorrow", response_model=list[TaskRead])
def get_tomorrow_tasks(
    tomorrow: date | None = Query(default=None),
    session: Session = Depends(get_db),
) -> list[TaskRead]:
    target_date = tomorrow or (date.today() + timedelta(days=1))
    return task_service.list_tomorrow_tasks(session, target_date)


@router.get("/tasks/inbox", response_model=list[TaskRead])
def get_inbox_tasks(session: Session = Depends(get_db)) -> list[TaskRead]:
    return task_service.list_inbox_tasks(session)


@router.get("/lists/{list_id}/tasks", response_model=list[TaskRead])
def get_tasks_for_list(list_id: int, session: Session = Depends(get_db)) -> list[TaskRead]:
    if list_service.get_list_or_none(session, list_id) is None:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="List not found.")
    return task_service.list_tasks_for_list(session, list_id)


@router.post("/tasks", response_model=TaskRead, status_code=status.HTTP_201_CREATED)
def create_task(payload: TaskCreate, session: Session = Depends(get_db)) -> TaskRead:
    return task_service.create_task(session, payload)


@router.patch("/tasks/{task_id}", response_model=TaskRead)
def update_task(task_id: int, payload: TaskUpdate, session: Session = Depends(get_db)) -> TaskRead:
    task = task_service.get_task_or_404(session, task_id)
    return task_service.update_task(task, payload, session)


@router.delete("/tasks/{task_id}", status_code=status.HTTP_204_NO_CONTENT)
def delete_task(task_id: int, session: Session = Depends(get_db)) -> Response:
    task = task_service.get_task_or_none(session, task_id, include_deleted=True)
    if task is not None:
        task_service.delete_task(task, session)
    return Response(status_code=status.HTTP_204_NO_CONTENT)


@router.post("/tasks/{task_id}/toggle", response_model=TaskRead)
def toggle_task(task_id: int, session: Session = Depends(get_db)) -> TaskRead:
    task = task_service.get_task_or_404(session, task_id)
    return task_service.toggle_task(task, session)


@router.post("/tasks/{task_id}/move", response_model=TaskMoveResult)
def move_task(task_id: int, payload: TaskMovePayload, session: Session = Depends(get_db)) -> TaskMoveResult:
    task = task_service.get_task_or_404(session, task_id)
    return task_service.move_task(task, payload, session)


@router.post("/tasks/reorder", response_model=list[TaskRead])
def reorder_top_level_tasks(
    payload: TopLevelTaskReorderPayload,
    session: Session = Depends(get_db),
) -> list[TaskRead]:
    return task_service.reorder_top_level_tasks(payload, session)


@router.post("/tasks/{task_id}/subtasks/reorder", response_model=TaskRead)
def reorder_subtasks(
    task_id: int,
    payload: SubtaskReorderPayload,
    session: Session = Depends(get_db),
) -> TaskRead:
    task = task_service.get_task_or_404(session, task_id)
    return task_service.reorder_subtasks(task, payload, session)
