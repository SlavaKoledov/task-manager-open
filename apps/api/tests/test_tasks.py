import json
from datetime import date, datetime, timedelta, timezone

import pytest
from fastapi import HTTPException
from sqlalchemy import text

from app.models.task import Task, TaskPriority, TaskRepeat
from app.schemas.lists import ListCreate
from app.schemas.tasks import (
    TaskMovePayload,
    TaskSubtaskCreate,
    TopLevelTaskReorderPayload,
    TopLevelTaskReorderScope,
    SubtaskReorderPayload,
    TaskCreate,
    TaskUpdate,
)
from app.services.lists import create_list
from app.services.tasks import (
    _resolve_next_due_date,
    create_task,
    delete_task,
    get_task_or_404,
    list_all_tasks,
    list_inbox_tasks,
    list_tasks_for_list,
    list_today_tasks,
    list_tomorrow_tasks,
    reorder_top_level_tasks,
    reorder_subtasks,
    toggle_task,
    move_task,
    update_task,
)


def test_task_enum_columns_use_lowercase_value_contract() -> None:
    assert Task.__table__.c.priority.type.enums == [
        "urgent_important",
        "not_urgent_important",
        "urgent_unimportant",
        "not_urgent_unimportant",
    ]
    assert Task.__table__.c.repeat.type.enums == [
        "none",
        "daily",
        "weekly",
        "monthly",
        "yearly",
    ]


def test_task_filters_toggle_and_updates(session) -> None:
    created_list = create_list(session, ListCreate(name="Study", color="#059669"))
    today = date(2026, 3, 13)
    tomorrow = today + timedelta(days=1)

    overdue_task = create_task(
        session,
        TaskCreate(title="Catch up yesterday", due_date=today - timedelta(days=1), list_id=created_list.id),
    )
    task_today = create_task(
        session,
        TaskCreate(title="Read chapter 3", due_date=today, list_id=created_list.id),
    )
    inbox_task = create_task(session, TaskCreate(title="Capture random idea"))
    future_task = create_task(session, TaskCreate(title="Plan revision", due_date=tomorrow))

    today_tasks = list_today_tasks(session, today)
    assert [task.title for task in today_tasks] == ["Catch up yesterday", "Read chapter 3"]

    tomorrow_tasks = list_tomorrow_tasks(session, tomorrow)
    assert [task.title for task in tomorrow_tasks] == ["Plan revision"]

    inbox_tasks = list_inbox_tasks(session)
    assert [task.title for task in inbox_tasks] == ["Capture random idea"]

    list_tasks = list_tasks_for_list(session, created_list.id)
    assert len(list_tasks) == 2

    toggled_task = toggle_task(get_task_or_404(session, task_today.id), session)
    assert toggled_task.is_done is True

    updated_task = update_task(
        get_task_or_404(session, future_task.id),
        TaskUpdate(description="Break work into 3 steps", due_date=None, list_id=created_list.id),
        session,
    )
    assert updated_task.due_date is None
    assert updated_task.list_id == created_list.id
    assert updated_task.description_blocks == [{"kind": "text", "text": "Break work into 3 steps"}]

    delete_task(get_task_or_404(session, inbox_task.id), session)
    remaining_tasks = list_all_tasks(session)
    assert len(remaining_tasks) == 3
    deleted_row = session.execute(
        text("SELECT deleted_at FROM tasks WHERE id = :task_id"),
        {"task_id": inbox_task.id},
    ).one()
    assert deleted_row._mapping["deleted_at"] is not None


def test_toggling_non_recurring_task_does_not_create_duplicate(session) -> None:
    task = create_task(
        session,
        TaskCreate(title="One-off task", due_date=date(2026, 3, 13)),
    )

    toggled_task = toggle_task(get_task_or_404(session, task.id), session)
    all_tasks = list_all_tasks(session)

    assert toggled_task.is_done is True
    assert [item.id for item in all_tasks] == [task.id]


def test_create_task_is_idempotent_for_repeated_client_request_id(session) -> None:
    payload = TaskCreate(
        client_request_id="android-create-123",
        title="Offline create",
        description="Persist me once",
        priority=TaskPriority.NOT_URGENT_IMPORTANT,
        repeat=TaskRepeat.NONE,
        list_id=None,
    )

    first = create_task(session, payload)
    second = create_task(session, payload)

    all_tasks = list_all_tasks(session)

    assert first.id == second.id
    assert len(all_tasks) == 1
    assert all_tasks[0].title == "Offline create"


def test_delete_task_uses_soft_delete_and_is_idempotent(session) -> None:
    task = create_task(session, TaskCreate(title="Soft delete me"))

    delete_task(get_task_or_404(session, task.id), session)
    delete_task(session.get(Task, task.id), session)

    active_tasks = list_all_tasks(session)
    deleted_row = session.execute(
        text("SELECT deleted_at FROM tasks WHERE id = :task_id"),
        {"task_id": task.id},
    ).one()

    assert active_tasks == []
    assert deleted_row._mapping["deleted_at"] is not None
    assert session.execute(text("SELECT COUNT(*) FROM tasks WHERE id = :task_id"), {"task_id": task.id}).scalar_one() == 1


def test_soft_deleted_tasks_do_not_leak_into_active_queries(session) -> None:
    today = date(2026, 3, 13)
    hidden_task = create_task(session, TaskCreate(title="Hidden task", due_date=today))
    visible_task = create_task(session, TaskCreate(title="Visible task", due_date=today))
    inbox_task = create_task(session, TaskCreate(title="Inbox task"))

    delete_task(get_task_or_404(session, hidden_task.id), session)
    delete_task(get_task_or_404(session, inbox_task.id), session)

    assert [task.id for task in list_all_tasks(session)] == [visible_task.id]
    assert [task.id for task in list_today_tasks(session, today)] == [visible_task.id]
    assert list_inbox_tasks(session) == []


def test_deleting_parent_task_soft_deletes_active_subtasks(session) -> None:
    parent = create_task(session, TaskCreate(title="Parent"))
    subtask = create_task(session, TaskCreate(title="Child", parent_id=parent.id))

    delete_task(get_task_or_404(session, parent.id), session)

    assert list_all_tasks(session) == []
    deleted_rows = session.execute(
        text("SELECT id, deleted_at FROM tasks WHERE id IN (:parent_id, :subtask_id)"),
        {"parent_id": parent.id, "subtask_id": subtask.id},
    ).all()
    assert {row._mapping["id"] for row in deleted_rows} == {parent.id, subtask.id}
    assert all(row._mapping["deleted_at"] is not None for row in deleted_rows)


def test_toggling_recurring_task_marks_current_done_and_spawns_next_active_copy(session) -> None:
    created_list = create_list(session, ListCreate(name="Home", color="#22C55E"))
    recurring_task = create_task(
        session,
        TaskCreate(
            title="Water plants",
            description_blocks=[
                {"kind": "text", "text": "Kitchen and balcony"},
                {"kind": "checkbox", "text": "Rotate pots", "checked": False},
            ],
            due_date=date(2026, 3, 13),
            reminder_time="08:30",
            is_pinned=True,
            priority=TaskPriority.URGENT_IMPORTANT,
            repeat=TaskRepeat.DAILY,
            list_id=created_list.id,
        ),
    )
    create_task(
        session,
        TaskCreate(
            title="Use balcony can",
            description_blocks=[
                {"kind": "text", "text": "Keep it filled"},
                {"kind": "checkbox", "text": "Rinse first", "checked": True},
            ],
            due_date=date(2026, 3, 13),
            reminder_time="07:15",
            priority=TaskPriority.URGENT_UNIMPORTANT,
            repeat=TaskRepeat.WEEKLY,
            parent_id=recurring_task.id,
        ),
    )

    toggled_task = toggle_task(get_task_or_404(session, recurring_task.id), session)
    all_tasks = list_all_tasks(session)
    spawned_task = next(task for task in all_tasks if task.id != recurring_task.id)
    historical_task = get_task_or_404(session, recurring_task.id)

    assert toggled_task.id == recurring_task.id
    assert toggled_task.is_done is True
    assert spawned_task.is_done is False
    assert spawned_task.due_date == date(2026, 3, 14)
    assert spawned_task.title == recurring_task.title
    assert spawned_task.description == recurring_task.description
    assert spawned_task.description_blocks == recurring_task.description_blocks
    assert spawned_task.priority == recurring_task.priority
    assert spawned_task.repeat == recurring_task.repeat
    assert spawned_task.reminder_time == "08:30"
    assert spawned_task.is_pinned is True
    assert spawned_task.list_id == recurring_task.list_id
    assert [subtask.title for subtask in historical_task.subtasks] == ["Use balcony can"]
    assert [subtask.title for subtask in spawned_task.subtasks] == ["Use balcony can"]
    assert spawned_task.subtasks[0].description == "Keep it filled\n- [x] Rinse first"
    assert spawned_task.subtasks[0].description_blocks == [
        {"kind": "text", "text": "Keep it filled"},
        {"kind": "checkbox", "text": "Rinse first", "checked": True},
    ]
    assert spawned_task.subtasks[0].priority == TaskPriority.URGENT_UNIMPORTANT
    assert spawned_task.subtasks[0].repeat == TaskRepeat.WEEKLY
    assert spawned_task.subtasks[0].due_date == date(2026, 3, 13)
    assert spawned_task.subtasks[0].reminder_time == "07:15"
    assert spawned_task.subtasks[0].is_done is False
    assert spawned_task.subtasks[0].is_pinned is False
    assert spawned_task.subtasks[0].parent_id == spawned_task.id
    assert spawned_task.subtasks[0].position == 0
    assert historical_task.subtasks[0].is_done is False


def test_toggling_monthly_recurring_task_uses_safe_end_of_month_due_date(session) -> None:
    recurring_task = create_task(
        session,
        TaskCreate(
            title="Month-end review",
            due_date=date(2024, 1, 31),
            repeat=TaskRepeat.MONTHLY,
        ),
    )

    toggle_task(get_task_or_404(session, recurring_task.id), session)
    all_tasks = list_all_tasks(session)
    spawned_task = next(task for task in all_tasks if task.id != recurring_task.id)

    assert spawned_task.due_date == date(2024, 2, 29)


def test_resolve_next_due_date_supports_yearly_and_clamps_invalid_days() -> None:
    assert _resolve_next_due_date(TaskRepeat.YEARLY, date(2025, 6, 30)) == date(2026, 6, 30)
    assert _resolve_next_due_date(TaskRepeat.YEARLY, date(2024, 2, 29)) == date(2025, 2, 28)


def test_move_top_level_task_under_other_top_level_task(session) -> None:
    target_parent = create_task(
        session,
        TaskCreate(
            title="Plan launch",
            list_id=create_list(session, ListCreate(name="Launch", color="#2563EB")).id,
        ),
    )
    dragged_task = create_task(
        session,
        TaskCreate(
            title="Prepare assets",
            list_id=None,
            priority=TaskPriority.URGENT_IMPORTANT,
        ),
    )

    result = move_task(
        get_task_or_404(session, dragged_task.id),
        TaskMovePayload(
            destination_parent_id=target_parent.id,
            ordered_ids=[dragged_task.id],
        ),
        session,
    )

    assert result.task.parent_id == target_parent.id
    assert result.task.list_id == target_parent.list_id
    assert result.removed_top_level_task_ids == [dragged_task.id]
    refreshed_parent = get_task_or_404(session, target_parent.id)
    assert [subtask.id for subtask in refreshed_parent.subtasks] == [dragged_task.id]


def test_move_rejects_nesting_task_that_already_has_subtasks(session) -> None:
    target_parent = create_task(session, TaskCreate(title="Target"))
    source_parent = create_task(session, TaskCreate(title="Source parent"))
    dragged_task = create_task(session, TaskCreate(title="Dragged parent"))
    create_task(session, TaskCreate(title="Nested child", parent_id=dragged_task.id))

    with pytest.raises(HTTPException) as error:
        move_task(
            get_task_or_404(session, dragged_task.id),
            TaskMovePayload(
                destination_parent_id=target_parent.id,
                ordered_ids=[dragged_task.id],
            ),
            session,
        )

    assert error.value.status_code == 400
    assert error.value.detail == "Tasks with subtasks cannot be nested under another task."


def test_move_rejects_self_parenting(session) -> None:
    task = create_task(session, TaskCreate(title="Self move"))

    with pytest.raises(HTTPException) as error:
        move_task(
            get_task_or_404(session, task.id),
            TaskMovePayload(destination_parent_id=task.id, ordered_ids=[task.id]),
            session,
        )

    assert error.value.status_code == 400
    assert error.value.detail == "A task cannot become a subtask of itself."


def test_move_subtask_to_top_level_scope_updates_parent_and_scope_fields(session) -> None:
    parent = create_task(session, TaskCreate(title="Parent"))
    subtask = create_task(
        session,
        TaskCreate(
            title="Detached",
            parent_id=parent.id,
            priority=TaskPriority.URGENT_UNIMPORTANT,
        ),
    )

    result = move_task(
        get_task_or_404(session, subtask.id),
        TaskMovePayload(
            destination_scope=TopLevelTaskReorderScope(
                view="today",
                target_date=date(2026, 3, 15),
                section_id="urgent_important",
            ),
            ordered_ids=[subtask.id],
        ),
        session,
    )

    assert result.task.parent_id is None
    assert result.task.is_pinned is False
    assert result.task.priority == TaskPriority.URGENT_IMPORTANT
    assert result.task.due_date == date(2026, 3, 15)
    refreshed_parent = get_task_or_404(session, parent.id)
    assert refreshed_parent.subtasks == []


def test_toggling_yearly_recurring_task_spawns_next_year_copy(session) -> None:
    recurring_task = create_task(
        session,
        TaskCreate(
            title="Renew passport",
            due_date=date(2024, 2, 29),
            repeat=TaskRepeat.YEARLY,
            is_pinned=True,
        ),
    )

    toggled_task = toggle_task(get_task_or_404(session, recurring_task.id), session)
    all_tasks = list_all_tasks(session)
    spawned_task = next(task for task in all_tasks if task.id != recurring_task.id)

    assert toggled_task.is_done is True
    assert spawned_task.is_done is False
    assert spawned_task.repeat == TaskRepeat.YEARLY
    assert spawned_task.due_date == date(2025, 2, 28)
    assert spawned_task.is_pinned is True


def test_toggling_recurring_task_stops_spawning_after_repeat_until(session) -> None:
    recurring_task = create_task(
        session,
        TaskCreate(
            title="Weekly review",
            due_date=date(2026, 3, 13),
            repeat=TaskRepeat.WEEKLY,
            repeat_until=date(2026, 3, 20),
        ),
    )

    first_toggle = toggle_task(get_task_or_404(session, recurring_task.id), session)
    after_first_toggle = list_all_tasks(session)
    spawned_task = next(task for task in after_first_toggle if task.id != recurring_task.id)

    assert first_toggle.is_done is True
    assert spawned_task.due_date == date(2026, 3, 20)
    assert spawned_task.repeat_until == date(2026, 3, 20)

    second_toggle = toggle_task(get_task_or_404(session, spawned_task.id), session)
    after_second_toggle = list_all_tasks(session)

    assert second_toggle.is_done is True
    assert len(after_second_toggle) == 2
    assert all(task.is_done for task in after_second_toggle)


def test_deleting_recurring_task_marks_it_deleted_without_returning_to_active_lists(session) -> None:
    recurring_task = create_task(
        session,
        TaskCreate(
            title="Delete recurring",
            due_date=date(2026, 3, 13),
            repeat=TaskRepeat.WEEKLY,
            repeat_until=date(2026, 3, 27),
        ),
    )

    delete_task(get_task_or_404(session, recurring_task.id), session)

    assert list_all_tasks(session) == []
    deleted_row = session.execute(
        text("SELECT deleted_at FROM tasks WHERE id = :task_id"),
        {"task_id": recurring_task.id},
    ).one()
    assert deleted_row._mapping["deleted_at"] is not None


def test_task_priority_and_repeat_defaults_and_updates(session) -> None:
    task = create_task(session, TaskCreate(title="Default enums"))

    assert task.priority == TaskPriority.NOT_URGENT_UNIMPORTANT
    assert task.repeat == TaskRepeat.NONE

    persisted_defaults = session.execute(
        text("SELECT priority, repeat FROM tasks WHERE id = :task_id"),
        {"task_id": task.id},
    ).one()
    assert persisted_defaults._mapping["priority"] == "not_urgent_unimportant"
    assert persisted_defaults._mapping["repeat"] == "none"

    updated_task = update_task(
        get_task_or_404(session, task.id),
        TaskUpdate(
            due_date=date(2026, 3, 13),
            reminder_time="09:45",
            priority=TaskPriority.URGENT_IMPORTANT,
            repeat=TaskRepeat.WEEKLY,
        ),
        session,
    )

    assert updated_task.priority == TaskPriority.URGENT_IMPORTANT
    assert updated_task.repeat == TaskRepeat.WEEKLY
    assert updated_task.due_date == date(2026, 3, 13)
    assert updated_task.reminder_time == "09:45"

    persisted_updates = session.execute(
        text("SELECT priority, repeat, reminder_time FROM tasks WHERE id = :task_id"),
        {"task_id": task.id},
    ).one()
    assert persisted_updates._mapping["priority"] == "urgent_important"
    assert persisted_updates._mapping["repeat"] == "weekly"
    assert persisted_updates._mapping["reminder_time"] == "09:45"


def test_updating_task_to_yearly_repeat_persists_new_value(session) -> None:
    task = create_task(session, TaskCreate(title="Annual renewal"))

    updated_task = update_task(
        get_task_or_404(session, task.id),
        TaskUpdate(
            due_date=date(2024, 2, 29),
            repeat=TaskRepeat.YEARLY,
        ),
        session,
    )

    assert updated_task.repeat == TaskRepeat.YEARLY
    assert updated_task.due_date == date(2024, 2, 29)

    persisted_repeat = session.execute(
        text("SELECT repeat FROM tasks WHERE id = :task_id"),
        {"task_id": task.id},
    ).scalar_one()
    assert persisted_repeat == "yearly"


def test_repeat_until_requires_repeat_due_date_and_valid_range(session) -> None:
    with pytest.raises(HTTPException) as no_repeat_error:
        create_task(
            session,
            TaskCreate(
                title="Broken recurring task",
                due_date=date(2026, 3, 13),
                repeat=TaskRepeat.NONE,
                repeat_until=date(2026, 3, 20),
            ),
        )

    assert no_repeat_error.value.status_code == 400
    assert no_repeat_error.value.detail == "Repeat end date requires a repeat schedule."

    task = create_task(
        session,
        TaskCreate(
            title="Valid recurring task",
            due_date=date(2026, 3, 13),
            repeat=TaskRepeat.WEEKLY,
            repeat_until=date(2026, 3, 27),
        ),
    )

    with pytest.raises(HTTPException) as range_error:
        update_task(
            get_task_or_404(session, task.id),
            TaskUpdate(repeat_until=date(2026, 3, 6)),
            session,
        )

    assert range_error.value.status_code == 400
    assert range_error.value.detail == "Repeat end date cannot be earlier than the due date."

    updated_task = update_task(
        get_task_or_404(session, task.id),
        TaskUpdate(repeat=TaskRepeat.NONE),
        session,
    )

    assert updated_task.repeat == TaskRepeat.NONE
    assert updated_task.repeat_until is None


def test_reminder_time_requires_due_date_and_clears_when_date_is_removed(session) -> None:
    with pytest.raises(HTTPException) as create_error:
        create_task(
            session,
            TaskCreate(
                title="Broken reminder",
                reminder_time="09:00",
            ),
        )

    assert create_error.value.status_code == 400
    assert create_error.value.detail == "Reminder time requires a due date."

    task = create_task(
        session,
        TaskCreate(
            title="Valid reminder",
            due_date=date(2026, 3, 13),
            reminder_time="09:00",
        ),
    )

    updated_task = update_task(
        get_task_or_404(session, task.id),
        TaskUpdate(due_date=None),
        session,
    )

    assert updated_task.due_date is None
    assert updated_task.reminder_time is None


def test_orm_reads_existing_lowercase_enum_values(session) -> None:
    timestamp = datetime.now(timezone.utc)

    session.execute(
        text(
            """
            INSERT INTO tasks (
                title,
                description,
                due_date,
                is_done,
                priority,
                repeat,
                list_id,
                created_at,
                updated_at
            ) VALUES (
                :title,
                :description,
                :due_date,
                :is_done,
                :priority,
                :repeat,
                :list_id,
                :created_at,
                :updated_at
            )
            """
        ),
        {
            "title": "Raw lowercase row",
            "description": None,
            "due_date": None,
            "is_done": False,
            "priority": "not_urgent_unimportant",
            "repeat": "yearly",
            "list_id": None,
            "created_at": timestamp,
            "updated_at": timestamp,
        },
    )
    session.commit()
    session.expire_all()

    loaded_task = next(task for task in list_all_tasks(session) if task.title == "Raw lowercase row")

    assert loaded_task.priority == TaskPriority.NOT_URGENT_UNIMPORTANT
    assert loaded_task.repeat == TaskRepeat.YEARLY


def test_task_create_with_priority_repeat_and_pinning_persists_values(session) -> None:
    created_task = create_task(
        session,
        TaskCreate(
            title="Persist lowercase values",
            priority=TaskPriority.URGENT_IMPORTANT,
            repeat=TaskRepeat.NONE,
            is_pinned=True,
        ),
    )

    assert created_task.priority == TaskPriority.URGENT_IMPORTANT
    assert created_task.repeat == TaskRepeat.NONE
    assert created_task.is_pinned is True

    persisted_row = session.execute(
        text("SELECT priority, repeat, is_pinned FROM tasks WHERE id = :task_id"),
        {"task_id": created_task.id},
    ).one()

    assert persisted_row._mapping["priority"] == "urgent_important"
    assert persisted_row._mapping["repeat"] == "none"
    assert bool(persisted_row._mapping["is_pinned"]) is True


def test_tasks_are_sorted_by_pinning_completion_then_priority(session) -> None:
    create_task(
        session,
        TaskCreate(title="Done but critical", is_done=True, priority=TaskPriority.URGENT_IMPORTANT),
    )
    create_task(
        session,
        TaskCreate(title="Backlog", priority=TaskPriority.NOT_URGENT_UNIMPORTANT),
    )
    create_task(
        session,
        TaskCreate(title="Pinned note", is_pinned=True, priority=TaskPriority.NOT_URGENT_UNIMPORTANT),
    )
    create_task(
        session,
        TaskCreate(title="Important now", priority=TaskPriority.URGENT_IMPORTANT),
    )
    create_task(
        session,
        TaskCreate(title="Important later", priority=TaskPriority.NOT_URGENT_IMPORTANT),
    )

    ordered = list_all_tasks(session)

    assert [task.title for task in ordered] == [
        "Pinned note",
        "Important now",
        "Important later",
        "Backlog",
        "Done but critical",
    ]


def test_description_blocks_are_serialized_and_backfilled_from_legacy_description(session) -> None:
    created_task = create_task(
        session,
        TaskCreate(
            title="Structured note",
            description_blocks=[
                {"kind": "text", "text": "Heading"},
                {"kind": "checkbox", "text": "Pack bag", "checked": True},
                {"kind": "checkbox", "text": "Water plants", "checked": False},
            ],
        ),
    )

    assert created_task.description == "Heading\n- [x] Pack bag\n- [ ] Water plants"
    assert created_task.description_blocks == [
        {"kind": "text", "text": "Heading"},
        {"kind": "checkbox", "text": "Pack bag", "checked": True},
        {"kind": "checkbox", "text": "Water plants", "checked": False},
    ]

    persisted_row = session.execute(
        text("SELECT description_blocks FROM tasks WHERE id = :task_id"),
        {"task_id": created_task.id},
    ).one()
    serialized_blocks = persisted_row._mapping["description_blocks"]

    if isinstance(serialized_blocks, str):
        serialized_blocks = json.loads(serialized_blocks)

    assert serialized_blocks == created_task.description_blocks

    legacy_task = create_task(
        session,
        TaskCreate(
            title="Legacy note",
            description="- [x] Send invoice\nPlain line",
        ),
    )

    assert legacy_task.description_blocks == [
        {"kind": "checkbox", "text": "Send invoice", "checked": True},
        {"kind": "text", "text": "Plain line"},
    ]


def test_top_level_task_can_be_created_atomically_with_subtasks(session) -> None:
    work_list = create_list(session, ListCreate(name="Planning", color="#2563EB"))

    created_task = create_task(
        session,
        TaskCreate(
            title="Ship release",
            list_id=work_list.id,
            priority=TaskPriority.URGENT_IMPORTANT,
            repeat=TaskRepeat.WEEKLY,
            due_date=date(2026, 3, 13),
            subtasks=[
                TaskSubtaskCreate(title="Draft release notes"),
                TaskSubtaskCreate(
                    title="QA smoke test",
                    description_blocks=[{"kind": "checkbox", "text": "Run regression pack", "checked": False}],
                    is_done=True,
                ),
            ],
        ),
    )

    assert [subtask.title for subtask in created_task.subtasks] == ["Draft release notes", "QA smoke test"]
    assert [subtask.position for subtask in created_task.subtasks] == [0, 1]
    assert [subtask.parent_id for subtask in created_task.subtasks] == [created_task.id, created_task.id]
    assert [subtask.list_id for subtask in created_task.subtasks] == [work_list.id, work_list.id]
    assert [subtask.priority for subtask in created_task.subtasks] == [
        TaskPriority.URGENT_IMPORTANT,
        TaskPriority.URGENT_IMPORTANT,
    ]
    assert [subtask.repeat for subtask in created_task.subtasks] == [TaskRepeat.NONE, TaskRepeat.NONE]
    assert created_task.subtasks[1].description == "- [ ] Run regression pack"
    assert created_task.subtasks[1].is_done is True


def test_subtasks_create_update_toggle_delete_and_reorder(session) -> None:
    work_list = create_list(session, ListCreate(name="Work", color="#2563EB"))
    parent = create_task(
        session,
        TaskCreate(title="Launch checklist", list_id=work_list.id, is_pinned=True),
    )
    first_subtask = create_task(
        session,
        TaskCreate(title="Write announcement", parent_id=parent.id, is_pinned=True, list_id=None),
    )
    second_subtask = create_task(
        session,
        TaskCreate(title="QA release", parent_id=parent.id),
    )

    assert first_subtask.parent_id == parent.id
    assert first_subtask.list_id == work_list.id
    assert first_subtask.is_pinned is False
    assert second_subtask.position == 1

    top_level_tasks = list_all_tasks(session)
    assert [task.title for task in top_level_tasks] == ["Launch checklist"]
    assert [subtask.title for subtask in top_level_tasks[0].subtasks] == ["Write announcement", "QA release"]

    updated_subtask = update_task(
        get_task_or_404(session, first_subtask.id),
        TaskUpdate(title="Write public announcement", is_pinned=True),
        session,
    )
    assert updated_subtask.title == "Write public announcement"
    assert updated_subtask.is_pinned is False

    toggled_subtask = toggle_task(get_task_or_404(session, second_subtask.id), session)
    assert toggled_subtask.is_done is True

    reordered_parent = reorder_subtasks(
        get_task_or_404(session, parent.id),
        SubtaskReorderPayload(subtask_ids=[second_subtask.id, first_subtask.id]),
        session,
    )
    assert [subtask.id for subtask in reordered_parent.subtasks] == [second_subtask.id, first_subtask.id]

    delete_task(get_task_or_404(session, second_subtask.id), session)
    refreshed_parent = get_task_or_404(session, parent.id)
    assert [subtask.title for subtask in refreshed_parent.subtasks] == ["Write public announcement"]
    assert refreshed_parent.subtasks[0].position == 0


def test_subtasks_do_not_leak_into_top_level_views(session) -> None:
    today = date(2026, 3, 13)
    parent = create_task(session, TaskCreate(title="Parent task", due_date=today))
    create_task(session, TaskCreate(title="Nested child", parent_id=parent.id, due_date=today))

    assert [task.title for task in list_all_tasks(session)] == ["Parent task"]
    assert [task.title for task in list_today_tasks(session, today)] == ["Parent task"]
    assert list_inbox_tasks(session) == []


def test_list_today_tasks_includes_overdue_but_not_future_top_level_tasks(session) -> None:
    today = date(2026, 3, 13)
    create_task(session, TaskCreate(title="Overdue task", due_date=today - timedelta(days=1)))
    create_task(session, TaskCreate(title="Today task", due_date=today))
    create_task(session, TaskCreate(title="Tomorrow task", due_date=today + timedelta(days=1)))

    assert [task.title for task in list_today_tasks(session, today)] == ["Overdue task", "Today task"]


def test_top_level_reorder_updates_manual_order_within_a_visible_section(session) -> None:
    work_list = create_list(session, ListCreate(name="Ops", color="#0EA5E9"))
    first_task = create_task(
        session,
        TaskCreate(title="First", list_id=work_list.id, priority=TaskPriority.NOT_URGENT_IMPORTANT),
    )
    second_task = create_task(
        session,
        TaskCreate(title="Second", list_id=work_list.id, priority=TaskPriority.NOT_URGENT_IMPORTANT),
    )
    third_task = create_task(
        session,
        TaskCreate(title="Third", list_id=work_list.id, priority=TaskPriority.NOT_URGENT_IMPORTANT),
    )

    reordered_tasks = reorder_top_level_tasks(
        TopLevelTaskReorderPayload(
            task_ids=[third_task.id, first_task.id, second_task.id],
            scope=TopLevelTaskReorderScope(
                view="list",
                list_id=work_list.id,
                section_id="not_urgent_important",
            ),
        ),
        session,
    )

    assert [task.id for task in reordered_tasks] == [third_task.id, first_task.id, second_task.id]
    assert [task.position for task in reordered_tasks] == [0, 1, 2]
    assert [task.id for task in list_tasks_for_list(session, work_list.id)] == [third_task.id, first_task.id, second_task.id]


def test_top_level_reorder_rejects_invalid_ids(session) -> None:
    work_list = create_list(session, ListCreate(name="Ops", color="#0EA5E9"))
    first_task = create_task(
        session,
        TaskCreate(title="First", list_id=work_list.id, priority=TaskPriority.NOT_URGENT_IMPORTANT),
    )
    create_task(
        session,
        TaskCreate(title="Second", list_id=work_list.id, priority=TaskPriority.NOT_URGENT_IMPORTANT),
    )

    with pytest.raises(HTTPException) as error:
        reorder_top_level_tasks(
            TopLevelTaskReorderPayload(
                task_ids=[first_task.id, 999_999],
                scope=TopLevelTaskReorderScope(
                    view="list",
                    list_id=work_list.id,
                    section_id="not_urgent_important",
                ),
            ),
            session,
        )

    assert error.value.status_code == 400
    assert error.value.detail == "Task reorder payload contains invalid ids."


def test_top_level_reorder_rejects_subtask_ids(session) -> None:
    work_list = create_list(session, ListCreate(name="Ops", color="#0EA5E9"))
    parent_task = create_task(session, TaskCreate(title="Parent", list_id=work_list.id))
    subtask = create_task(session, TaskCreate(title="Nested", parent_id=parent_task.id))

    with pytest.raises(HTTPException) as error:
        reorder_top_level_tasks(
            TopLevelTaskReorderPayload(
                task_ids=[subtask.id],
                scope=TopLevelTaskReorderScope(
                    view="list",
                    list_id=work_list.id,
                    section_id="not_urgent_unimportant",
                ),
            ),
            session,
        )

    assert error.value.status_code == 400
    assert error.value.detail == "Only top-level tasks can be reordered."


def test_top_level_reorder_rejects_tasks_outside_requested_scope(session) -> None:
    work_list = create_list(session, ListCreate(name="Ops", color="#0EA5E9"))
    home_list = create_list(session, ListCreate(name="Home", color="#F97316"))
    pinned_task = create_task(session, TaskCreate(title="Pinned", list_id=work_list.id, is_pinned=True))
    medium_task = create_task(
        session,
        TaskCreate(title="Medium", list_id=work_list.id, priority=TaskPriority.NOT_URGENT_IMPORTANT),
    )
    other_list_task = create_task(
        session,
        TaskCreate(title="Elsewhere", list_id=home_list.id, priority=TaskPriority.NOT_URGENT_IMPORTANT),
    )

    with pytest.raises(HTTPException) as pinned_error:
        reorder_top_level_tasks(
            TopLevelTaskReorderPayload(
                task_ids=[pinned_task.id, medium_task.id],
                scope=TopLevelTaskReorderScope(
                    view="list",
                    list_id=work_list.id,
                    section_id="pinned",
                ),
            ),
            session,
        )

    assert pinned_error.value.status_code == 400
    assert pinned_error.value.detail == "Tasks must belong to the requested reorder scope."

    with pytest.raises(HTTPException) as list_error:
        reorder_top_level_tasks(
            TopLevelTaskReorderPayload(
                task_ids=[medium_task.id, other_list_task.id],
                scope=TopLevelTaskReorderScope(
                    view="list",
                    list_id=work_list.id,
                    section_id="not_urgent_important",
                ),
            ),
            session,
        )

    assert list_error.value.status_code == 400
    assert list_error.value.detail == "Tasks must belong to the requested reorder scope."


def test_top_level_reorder_supports_today_scope(session) -> None:
    today = date(2026, 3, 13)
    first_task = create_task(
        session,
        TaskCreate(title="First", due_date=today, priority=TaskPriority.URGENT_IMPORTANT),
    )
    second_task = create_task(
        session,
        TaskCreate(title="Second", due_date=today, priority=TaskPriority.URGENT_IMPORTANT),
    )
    create_task(
        session,
        TaskCreate(title="Outside scope", due_date=date(2026, 3, 14), priority=TaskPriority.URGENT_IMPORTANT),
    )

    reordered_tasks = reorder_top_level_tasks(
        TopLevelTaskReorderPayload(
            task_ids=[second_task.id, first_task.id],
            scope=TopLevelTaskReorderScope(
                view="today",
                target_date=today,
                section_id="urgent_important",
            ),
        ),
        session,
    )

    assert [task.id for task in reordered_tasks] == [second_task.id, first_task.id]
    assert [task.position for task in reordered_tasks] == [0, 1]


def test_top_level_reorder_supports_all_view_group_scope(session) -> None:
    reference_date = date(2026, 3, 13)
    first_task = create_task(
        session,
        TaskCreate(title="First later", due_date=date(2026, 3, 25), priority=TaskPriority.URGENT_UNIMPORTANT),
    )
    second_task = create_task(
        session,
        TaskCreate(title="Second later", due_date=date(2026, 3, 26), priority=TaskPriority.URGENT_UNIMPORTANT),
    )
    create_task(
        session,
        TaskCreate(title="No date task", due_date=None, priority=TaskPriority.URGENT_UNIMPORTANT),
    )

    reordered_tasks = reorder_top_level_tasks(
        TopLevelTaskReorderPayload(
            task_ids=[second_task.id, first_task.id],
            scope=TopLevelTaskReorderScope(
                view="all",
                group_id="later",
                reference_date=reference_date,
                section_id="urgent_unimportant",
            ),
        ),
        session,
    )

    assert [task.id for task in reordered_tasks] == [second_task.id, first_task.id]
    assert [task.position for task in reordered_tasks] == [0, 1]
