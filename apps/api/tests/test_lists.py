from datetime import date

import pytest
from fastapi import HTTPException
from pydantic import ValidationError

from app.api.routes import lists as list_routes
from app.schemas.lists import ListCreate, ListUpdate
from app.schemas.tasks import TaskCreate
from app.schemas.lists import ListReorderPayload
from app.services.lists import create_list, delete_list, list_lists, reorder_list_positions, update_list
from app.services.tasks import create_task, list_all_tasks


def test_list_crud_and_delete_unassigns_tasks(session) -> None:
    created_list = create_list(session, ListCreate(name="Work", color="#2563EB"))

    created_task = create_task(
        session,
        TaskCreate(
            title="Prepare weekly review",
            description="Summarize progress",
            due_date=date(2026, 3, 13),
            list_id=created_list.id,
        ),
    )
    assert created_task.list_id == created_list.id

    lists = list_lists(session)
    assert lists[0].name == "Work"

    renamed_list = update_list(created_list, ListUpdate(name="Deep Work", color="#1D4ED8"), session)
    assert renamed_list.name == "Deep Work"

    delete_list(renamed_list, session)

    all_tasks = list_all_tasks(session)
    assert len(all_tasks) == 1
    assert all_tasks[0].list_id is None


def test_cannot_create_blank_list_name() -> None:
    with pytest.raises(ValidationError):
        ListCreate(name="   ", color="#2563EB")


def test_list_reorder_updates_positions_dense_and_stable(session) -> None:
    first_list = create_list(session, ListCreate(name="Work", color="#2563EB"))
    second_list = create_list(session, ListCreate(name="Home", color="#F97316"))
    third_list = create_list(session, ListCreate(name="Study", color="#059669"))

    reordered_lists = reorder_list_positions(
        ListReorderPayload(list_ids=[third_list.id, first_list.id, second_list.id]),
        session,
    )

    assert [task_list.id for task_list in reordered_lists] == [third_list.id, first_list.id, second_list.id]
    assert [task_list.position for task_list in reordered_lists] == [0, 1, 2]
    assert [task_list.id for task_list in list_lists(session)] == [third_list.id, first_list.id, second_list.id]


def test_list_reorder_rejects_missing_or_invalid_ids(session) -> None:
    first_list = create_list(session, ListCreate(name="Work", color="#2563EB"))
    second_list = create_list(session, ListCreate(name="Home", color="#F97316"))

    with pytest.raises(HTTPException) as error:
        reorder_list_positions(
            ListReorderPayload(list_ids=[first_list.id, 999_999]),
            session,
        )

    assert error.value.status_code == 400
    assert error.value.detail == "List reorder payload must contain the exact current list ids."

    with pytest.raises(HTTPException) as missing_error:
        reorder_list_positions(
            ListReorderPayload(list_ids=[second_list.id]),
            session,
        )

    assert missing_error.value.status_code == 400
    assert missing_error.value.detail == "List reorder payload must contain the exact current list ids."


def test_list_reorder_route_returns_updated_order(session) -> None:
    first_list = create_list(session, ListCreate(name="Work", color="#2563EB"))
    second_list = create_list(session, ListCreate(name="Home", color="#F97316"))

    reordered_lists = list_routes.reorder_lists(
        ListReorderPayload(list_ids=[second_list.id, first_list.id]),
        session,
    )

    assert [task_list.id for task_list in reordered_lists] == [second_list.id, first_list.id]
