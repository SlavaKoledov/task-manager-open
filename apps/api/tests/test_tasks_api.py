import pytest
from fastapi import HTTPException
from pydantic import ValidationError

from app.api.routes import tasks as task_routes
from app.schemas.lists import ListCreate
from app.schemas.tasks import (
    TaskMovePayload,
    TaskSubtaskCreate,
    TopLevelTaskReorderPayload,
    TopLevelTaskReorderScope,
    SubtaskReorderPayload,
    TaskCreate,
    TaskRead,
    TaskUpdate,
)
from app.services.lists import create_list
from app.services.live_events import live_event_broker


def serialize_task(task) -> dict[str, object]:
    return TaskRead.model_validate(task).model_dump(mode="json")


def serialize_tasks(tasks) -> list[dict[str, object]]:
    return [serialize_task(task) for task in tasks]


def test_legacy_task_endpoints_remain_backwards_compatible(session) -> None:
    payload = serialize_task(
        task_routes.create_task(
            TaskCreate(
                title="Legacy payload",
                description="First line\nSecond line",
                due_date=None,
                is_done=False,
                priority="not_urgent_unimportant",
                repeat="none",
                list_id=None,
            ),
            session,
        )
    )

    assert payload["title"] == "Legacy payload"
    assert payload["description"] == "First line\nSecond line"
    assert payload["description_blocks"] == [
        {"kind": "text", "text": "First line"},
        {"kind": "text", "text": "Second line"},
    ]
    assert payload["subtasks"] == []
    assert payload["is_pinned"] is False
    assert payload["parent_id"] is None


def test_task_endpoints_round_trip_custom_repeat_configuration(session) -> None:
    payload = serialize_task(
        task_routes.create_task(
            TaskCreate(
                title="Custom cadence",
                due_date="2026-03-18",
                repeat="custom",
                repeat_config={
                    "interval": 2,
                    "unit": "week",
                    "weekdays": [1, 3, 5],
                },
            ),
            session,
        )
    )

    assert payload["repeat"] == "custom"
    assert payload["repeat_config"] == {
        "interval": 2,
        "unit": "week",
        "skip_weekends": False,
        "weekdays": [1, 3, 5],
        "month_day": None,
        "month": None,
        "day": None,
    }


def test_task_endpoints_round_trip_start_and_end_time(session) -> None:
    payload = serialize_task(
        task_routes.create_task(
            TaskCreate(
                title="Timed task",
                due_date="2026-03-18",
                start_time="09:00",
                end_time="10:15",
            ),
            session,
        )
    )

    assert payload["start_time"] == "09:00"
    assert payload["end_time"] == "10:15"


def test_task_update_schema_rejects_create_only_fields() -> None:
    with pytest.raises(ValidationError) as error:
        TaskUpdate.model_validate(
            {
                "title": "Editable task",
                "description": "Original note",
                "description_blocks": [{"kind": "text", "text": "Original note"}],
                "due_date": "2026-03-13",
                "is_done": False,
                "is_pinned": False,
                "priority": "not_urgent_important",
                "repeat": "weekly",
                "list_id": None,
                "parent_id": None,
                "subtasks": [],
            }
        )

    errors = error.value.errors()
    assert any(item["loc"][-1] == "parent_id" for item in errors)
    assert any(item["loc"][-1] == "subtasks" for item in errors)


def test_update_task_endpoint_accepts_update_payload_without_create_only_fields(session) -> None:
    task = task_routes.create_task(
        TaskCreate(
            title="Editable task",
            description_blocks=[{"kind": "text", "text": "Original note"}],
            due_date="2026-03-13",
            is_done=False,
            is_pinned=False,
            priority="not_urgent_important",
            repeat="weekly",
            list_id=None,
        ),
        session,
    )

    payload = serialize_task(
        task_routes.update_task(
            task.id,
            TaskUpdate(
                title="Renamed task",
                description_blocks=[{"kind": "checkbox", "text": "Updated note", "checked": False}],
                due_date="2026-03-20",
                reminder_time="14:30",
                is_done=False,
                is_pinned=True,
                priority="urgent_important",
                repeat="weekly",
                list_id=None,
            ),
            session,
        )
    )

    assert payload["title"] == "Renamed task"
    assert payload["description"] == "- [ ] Updated note"
    assert payload["description_blocks"] == [{"kind": "checkbox", "text": "Updated note", "checked": False}]
    assert payload["due_date"] == "2026-03-20"
    assert payload["reminder_time"] == "14:30"
    assert payload["priority"] == "urgent_important"
    assert payload["is_pinned"] is True
    assert payload["repeat"] == "weekly"


def test_update_task_endpoint_validates_and_normalizes_task_times(session) -> None:
    task = task_routes.create_task(
        TaskCreate(
            title="Editable timing",
            due_date="2026-03-13",
            start_time="08:30",
        ),
        session,
    )

    payload = serialize_task(
        task_routes.update_task(
            task.id,
            TaskUpdate(
                start_time="09:00",
                end_time="11:30",
            ),
            session,
        )
    )

    assert payload["start_time"] == "09:00"
    assert payload["end_time"] == "11:30"

    with pytest.raises(HTTPException) as error:
        task_routes.update_task(
            task.id,
            TaskUpdate(end_time="08:00"),
            session,
        )

    assert error.value.status_code == 400
    assert error.value.detail == "End time must be later than the start time."

    untimed_task = task_routes.create_task(
        TaskCreate(
            title="Untimed task",
            due_date="2026-03-13",
        ),
        session,
    )

    with pytest.raises(HTTPException) as missing_start_error:
        task_routes.update_task(
            untimed_task.id,
            TaskUpdate(end_time="08:00"),
            session,
        )

    assert missing_start_error.value.status_code == 400
    assert missing_start_error.value.detail == "End time requires a start time."

    cleared_payload = serialize_task(
        task_routes.update_task(
            task.id,
            TaskUpdate(due_date=None),
            session,
        )
    )

    assert cleared_payload["due_date"] is None
    assert cleared_payload["start_time"] is None
    assert cleared_payload["end_time"] is None


def test_create_task_endpoint_can_create_task_with_nested_subtasks(session) -> None:
    created_list = create_list(session, ListCreate(name="Launch", color="#2563EB"))

    payload = serialize_task(
        task_routes.create_task(
            TaskCreate(
                title="Release prep",
                description_blocks=[{"kind": "text", "text": "Ship it safely"}],
                priority="urgent_important",
                repeat="daily",
                due_date="2026-03-13",
                list_id=created_list.id,
                subtasks=[
                    TaskSubtaskCreate(title="Write changelog"),
                    TaskSubtaskCreate(
                        title="Regression pass",
                        description_blocks=[{"kind": "checkbox", "text": "Smoke test", "checked": True}],
                        is_done=True,
                    ),
                ],
            ),
            session,
        )
    )

    assert payload["title"] == "Release prep"
    assert [subtask["title"] for subtask in payload["subtasks"]] == ["Write changelog", "Regression pass"]
    assert [subtask["position"] for subtask in payload["subtasks"]] == [0, 1]
    assert [subtask["parent_id"] for subtask in payload["subtasks"]] == [payload["id"], payload["id"]]
    assert [subtask["list_id"] for subtask in payload["subtasks"]] == [created_list.id, created_list.id]
    assert [subtask["priority"] for subtask in payload["subtasks"]] == ["urgent_important", "urgent_important"]
    assert [subtask["repeat"] for subtask in payload["subtasks"]] == ["none", "none"]
    assert payload["subtasks"][1]["description"] == "- [x] Smoke test"
    assert payload["subtasks"][1]["is_done"] is True


def test_create_task_endpoint_reuses_existing_task_for_duplicate_client_request_id(session) -> None:
    first = serialize_task(
        task_routes.create_task(
            TaskCreate(
                client_request_id="android-duplicate-create",
                title="One network-safe create",
                description_blocks=[{"kind": "text", "text": "Only once"}],
                priority="not_urgent_important",
                repeat="none",
                list_id=None,
            ),
            session,
        )
    )

    duplicate = serialize_task(
        task_routes.create_task(
            TaskCreate(
                client_request_id="android-duplicate-create",
                title="One network-safe create",
                description_blocks=[{"kind": "text", "text": "Only once"}],
                priority="not_urgent_important",
                repeat="none",
                list_id=None,
            ),
            session,
        )
    )

    all_tasks = serialize_tasks(task_routes.get_tasks(session))

    assert duplicate["id"] == first["id"]
    assert len(all_tasks) == 1


def test_create_task_endpoint_rejects_nested_subtasks_below_one_level(session) -> None:
    parent = task_routes.create_task(
        TaskCreate(
            title="Parent",
            is_done=False,
            priority="not_urgent_unimportant",
            repeat="none",
            list_id=None,
        ),
        session,
    )

    with pytest.raises(HTTPException) as error:
        task_routes.create_task(
            TaskCreate(
                title="Child",
                parent_id=parent.id,
                subtasks=[TaskSubtaskCreate(title="Too deep")],
            ),
            session,
        )

    assert error.value.status_code == 400
    assert error.value.detail == "Subtasks cannot have nested subtasks."


def test_subtask_api_flow_and_reorder_endpoint(session) -> None:
    created_list = create_list(session, ListCreate(name="Inbox+", color="#7C3AED"))

    parent = task_routes.create_task(
        TaskCreate(
            title="Parent",
            description_blocks=[{"kind": "text", "text": "Parent note"}],
            is_done=False,
            is_pinned=True,
            priority="urgent_important",
            repeat="none",
            list_id=created_list.id,
        ),
        session,
    )
    first_subtask = task_routes.create_task(
        TaskCreate(
            title="Subtask A",
            parent_id=parent.id,
            is_done=False,
            is_pinned=True,
            priority="not_urgent_unimportant",
            repeat="none",
            list_id=None,
        ),
        session,
    )
    second_subtask = task_routes.create_task(
        TaskCreate(
            title="Subtask B",
            parent_id=parent.id,
            is_done=False,
            priority="not_urgent_important",
            repeat="none",
            list_id=None,
        ),
        session,
    )

    all_tasks = serialize_tasks(task_routes.get_tasks(session))
    assert [task["title"] for task in all_tasks] == ["Parent"]
    assert [task["title"] for task in all_tasks[0]["subtasks"]] == ["Subtask A", "Subtask B"]
    assert all_tasks[0]["subtasks"][0]["is_pinned"] is False
    assert all_tasks[0]["subtasks"][0]["list_id"] == created_list.id

    updated_subtask = serialize_task(
        task_routes.update_task(
            first_subtask.id,
            TaskUpdate(
                title="Renamed subtask",
                description_blocks=[{"kind": "checkbox", "text": "Nested item", "checked": True}],
            ),
            session,
        )
    )
    assert updated_subtask["description"] == "- [x] Nested item"

    toggled_subtask = serialize_task(task_routes.toggle_task(second_subtask.id, session))
    assert toggled_subtask["is_done"] is True

    reordered_parent = serialize_task(
        task_routes.reorder_subtasks(
            parent.id,
            SubtaskReorderPayload(subtask_ids=[second_subtask.id, first_subtask.id]),
            session,
        )
    )
    assert [subtask["id"] for subtask in reordered_parent["subtasks"]] == [second_subtask.id, first_subtask.id]

    delete_response = task_routes.delete_task(first_subtask.id, session)
    assert delete_response.status_code == 204

    refreshed_tasks = serialize_tasks(task_routes.get_tasks(session))
    assert [subtask["title"] for subtask in refreshed_tasks[0]["subtasks"]] == ["Subtask B"]


def test_http_create_task_with_parent_id_returns_subtasks_field_and_keeps_child_visible(client) -> None:
    parent_response = client.post(
        "/tasks",
        json={
            "title": "Parent",
            "priority": "urgent_important",
            "repeat": "none",
            "subtasks": [],
        },
    )
    assert parent_response.status_code == 201
    parent_payload = parent_response.json()
    assert "subtasks" in parent_payload
    assert "active_subtasks" not in parent_payload

    child_response = client.post(
        "/tasks",
        json={
            "title": "Child",
            "priority": "not_urgent_unimportant",
            "repeat": "none",
            "parent_id": parent_payload["id"],
            "subtasks": [],
        },
    )
    assert child_response.status_code == 201
    child_payload = child_response.json()
    assert child_payload["parent_id"] == parent_payload["id"]
    assert "subtasks" in child_payload
    assert "active_subtasks" not in child_payload

    tasks_response = client.get("/tasks")
    assert tasks_response.status_code == 200
    tasks_payload = tasks_response.json()

    assert [task["title"] for task in tasks_payload] == ["Parent"]
    assert "active_subtasks" not in tasks_payload[0]
    assert [subtask["title"] for subtask in tasks_payload[0]["subtasks"]] == ["Child"]


def test_http_create_task_with_soft_deleted_parent_returns_404(client) -> None:
    parent_response = client.post(
        "/tasks",
        json={
            "title": "Parent",
            "priority": "not_urgent_unimportant",
            "repeat": "none",
            "subtasks": [],
        },
    )
    assert parent_response.status_code == 201

    delete_response = client.delete(f"/tasks/{parent_response.json()['id']}")
    assert delete_response.status_code == 204

    child_response = client.post(
        "/tasks",
        json={
            "title": "Child",
            "priority": "not_urgent_unimportant",
            "repeat": "none",
            "parent_id": parent_response.json()["id"],
            "subtasks": [],
        },
    )

    assert child_response.status_code == 404
    assert child_response.json()["detail"] == "Parent task not found."


def test_delete_task_endpoint_is_idempotent_and_hides_soft_deleted_task(session) -> None:
    task = task_routes.create_task(
        TaskCreate(
            title="Delete me twice",
            priority="not_urgent_unimportant",
            repeat="none",
        ),
        session,
    )

    first_response = task_routes.delete_task(task.id, session)
    second_response = task_routes.delete_task(task.id, session)

    assert first_response.status_code == 204
    assert second_response.status_code == 204
    assert serialize_tasks(task_routes.get_tasks(session)) == []


def test_delete_recurring_task_endpoint_keeps_deleted_task_out_of_active_results(session) -> None:
    task = task_routes.create_task(
        TaskCreate(
            title="Recurring delete",
            due_date="2026-03-13",
            is_done=False,
            priority="urgent_important",
            repeat="weekly",
            repeat_until="2026-03-20",
            list_id=None,
        ),
        session,
    )

    delete_response = task_routes.delete_task(task.id, session)

    assert delete_response.status_code == 204
    assert serialize_tasks(task_routes.get_tasks(session)) == []


def test_move_task_endpoint_promotes_subtask_to_top_level_scope(session) -> None:
    parent = task_routes.create_task(
        TaskCreate(
            title="Parent",
            priority="not_urgent_unimportant",
            repeat="none",
        ),
        session,
    )
    subtask = task_routes.create_task(
        TaskCreate(
            title="Child",
            parent_id=parent.id,
            priority="not_urgent_unimportant",
            repeat="none",
        ),
        session,
    )

    payload = task_routes.move_task(
        subtask.id,
        TaskMovePayload(
            destination_scope=TopLevelTaskReorderScope(
                view="today",
                target_date="2026-03-15",
                section_id="urgent_important",
            ),
            ordered_ids=[subtask.id],
        ),
        session,
    ).model_dump(mode="json")

    assert payload["task"]["parent_id"] is None
    assert payload["task"]["priority"] == "urgent_important"
    assert payload["task"]["due_date"] == "2026-03-15"
    assert any(task["id"] == subtask.id for task in payload["affected_tasks"])


def test_sse_events_emit_after_task_mutation(client) -> None:
    subscriber_id, subscriber_queue = live_event_broker.subscribe()

    try:
        response = client.post(
            "/tasks",
            json={
                "title": "Live sync task",
                "priority": "not_urgent_unimportant",
                "repeat": "none",
                "subtasks": [],
            },
        )

        assert response.status_code == 201
        event = subscriber_queue.get(timeout=1)

        assert event.entity_type == "task"
        assert event.entity_ids == [response.json()["id"]]
    finally:
        live_event_broker.unsubscribe(subscriber_id)


def test_toggle_recurring_task_endpoint_marks_current_done_and_spawns_next_task(session) -> None:
    task = task_routes.create_task(
        TaskCreate(
            title="Daily review",
            description_blocks=[{"kind": "text", "text": "Check backlog"}],
            due_date="2026-03-13",
            is_done=False,
            is_pinned=True,
            priority="urgent_important",
            repeat="daily",
            repeat_until="2026-03-20",
            list_id=None,
        ),
        session,
    )
    task_routes.create_task(
        TaskCreate(
            title="Check roadmap",
            description_blocks=[{"kind": "checkbox", "text": "Archive stale items", "checked": True}],
            due_date="2026-03-13",
            is_done=False,
            priority="urgent_unimportant",
            repeat="weekly",
            parent_id=task.id,
            list_id=None,
        ),
        session,
    )

    toggled_task = serialize_task(task_routes.toggle_task(task.id, session))
    payload = serialize_tasks(task_routes.get_tasks(session))
    spawned_task = next(item for item in payload if item["id"] != task.id)

    assert toggled_task["id"] == task.id
    assert toggled_task["is_done"] is True
    assert len(payload) == 2
    assert spawned_task["title"] == "Daily review"
    assert spawned_task["description"] == "Check backlog"
    assert spawned_task["due_date"] == "2026-03-14"
    assert spawned_task["is_done"] is False
    assert spawned_task["is_pinned"] is True
    assert spawned_task["repeat"] == "daily"
    assert spawned_task["repeat_until"] == "2026-03-20"
    assert [subtask["title"] for subtask in spawned_task["subtasks"]] == ["Check roadmap"]
    assert spawned_task["subtasks"][0]["description_blocks"] == [
        {"kind": "checkbox", "text": "Archive stale items", "checked": True},
    ]
    assert spawned_task["subtasks"][0]["is_done"] is False
    assert spawned_task["subtasks"][0]["is_pinned"] is False
    assert spawned_task["subtasks"][0]["parent_id"] == spawned_task["id"]
    assert spawned_task["subtasks"][0]["position"] == 0


def test_toggle_recurring_task_endpoint_stops_at_repeat_until(session) -> None:
    task = task_routes.create_task(
        TaskCreate(
            title="Weekly review",
            due_date="2026-03-13",
            is_done=False,
            priority="urgent_important",
            repeat="weekly",
            repeat_until="2026-03-20",
            list_id=None,
        ),
        session,
    )

    first_toggle = serialize_task(task_routes.toggle_task(task.id, session))
    after_first_toggle = serialize_tasks(task_routes.get_tasks(session))
    spawned_task = next(item for item in after_first_toggle if item["id"] != task.id)

    assert first_toggle["is_done"] is True
    assert spawned_task["due_date"] == "2026-03-20"
    assert spawned_task["repeat_until"] == "2026-03-20"

    second_toggle = serialize_task(task_routes.toggle_task(spawned_task["id"], session))
    after_second_toggle = serialize_tasks(task_routes.get_tasks(session))

    assert second_toggle["is_done"] is True
    assert len(after_second_toggle) == 2
    assert all(item["is_done"] for item in after_second_toggle)


def test_toggle_yearly_recurring_task_endpoint_uses_safe_next_due_date(session) -> None:
    task = task_routes.create_task(
        TaskCreate(
            title="Renew subscription",
            due_date="2024-02-29",
            is_done=False,
            is_pinned=True,
            priority="not_urgent_important",
            repeat="yearly",
            list_id=None,
        ),
        session,
    )

    toggled_task = serialize_task(task_routes.toggle_task(task.id, session))
    payload = serialize_tasks(task_routes.get_tasks(session))
    spawned_task = next(item for item in payload if item["id"] != task.id)

    assert toggled_task["is_done"] is True
    assert spawned_task["repeat"] == "yearly"
    assert spawned_task["due_date"] == "2025-02-28"
    assert spawned_task["is_done"] is False
    assert spawned_task["is_pinned"] is True


def test_task_api_rejects_repeat_without_due_date(session) -> None:
    with pytest.raises(HTTPException) as error:
        task_routes.create_task(
            TaskCreate(
                title="Broken recurring task",
                description=None,
                due_date=None,
                is_done=False,
                is_pinned=False,
                priority="not_urgent_unimportant",
                repeat="daily",
                list_id=None,
            ),
            session,
        )

    assert error.value.status_code == 400
    assert error.value.detail == "Recurring tasks require a due date."


def test_task_api_rejects_repeat_until_without_valid_repeat_range(session) -> None:
    with pytest.raises(HTTPException) as no_repeat_error:
        task_routes.create_task(
            TaskCreate(
                title="Broken recurring task",
                due_date="2026-03-13",
                is_done=False,
                is_pinned=False,
                priority="not_urgent_unimportant",
                repeat="none",
                repeat_until="2026-03-20",
                list_id=None,
            ),
            session,
        )

    assert no_repeat_error.value.status_code == 400
    assert no_repeat_error.value.detail == "Repeat end date requires a repeat schedule."


def test_task_api_rejects_reminder_time_without_due_date(session) -> None:
    with pytest.raises(HTTPException) as error:
        task_routes.create_task(
            TaskCreate(
                title="Broken reminder",
                reminder_time="10:15",
                repeat="none",
                priority="not_urgent_unimportant",
            ),
            session,
        )

    assert error.value.status_code == 400
    assert error.value.detail == "Reminder time requires a due date."


def test_top_level_reorder_endpoint_returns_dense_positions(session) -> None:
    created_list = create_list(session, ListCreate(name="Focus", color="#2563EB"))
    first_task = task_routes.create_task(
        TaskCreate(
            title="First",
            is_done=False,
            priority="urgent_unimportant",
            repeat="none",
            list_id=created_list.id,
        ),
        session,
    )
    second_task = task_routes.create_task(
        TaskCreate(
            title="Second",
            is_done=False,
            priority="urgent_unimportant",
            repeat="none",
            list_id=created_list.id,
        ),
        session,
    )

    reordered_tasks = serialize_tasks(
        task_routes.reorder_top_level_tasks(
            TopLevelTaskReorderPayload(
                task_ids=[second_task.id, first_task.id],
                scope=TopLevelTaskReorderScope(
                    view="list",
                    list_id=created_list.id,
                    section_id="urgent_unimportant",
                ),
            ),
            session,
        )
    )

    assert [task["id"] for task in reordered_tasks] == [second_task.id, first_task.id]
    assert [task["position"] for task in reordered_tasks] == [0, 1]
