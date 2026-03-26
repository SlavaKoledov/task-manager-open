import { QueryClient } from "@tanstack/react-query";

import { applyTaskMoveResultInCaches } from "@/lib/task-cache";
import type { TaskItem, TaskMoveResult, TaskSubtask } from "@/lib/types";

function makeSubtask(overrides: Partial<TaskSubtask> = {}): TaskSubtask {
  return {
    id: 200,
    title: "Nested task",
    description: null,
    description_blocks: [{ kind: "text", text: "" }],
    due_date: null,
    reminder_time: null,
    repeat_until: null,
    is_done: false,
    is_pinned: false,
    priority: "not_urgent_unimportant",
    repeat: "none",
    parent_id: 100,
    position: 0,
    list_id: 4,
    created_at: "2026-03-15T12:00:00Z",
    updated_at: "2026-03-15T12:00:00Z",
    ...overrides,
  };
}

function makeTask(overrides: Partial<TaskItem> = {}): TaskItem {
  return {
    id: 100,
    title: "Parent",
    description: null,
    description_blocks: [{ kind: "text", text: "" }],
    due_date: "2026-03-15",
    reminder_time: null,
    repeat_until: null,
    is_done: false,
    is_pinned: false,
    priority: "not_urgent_unimportant",
    repeat: "none",
    parent_id: null,
    position: 0,
    list_id: 4,
    created_at: "2026-03-15T12:00:00Z",
    updated_at: "2026-03-15T12:00:00Z",
    subtasks: [],
    ...overrides,
  };
}

describe("task cache move sync", () => {
  it("removes moved top-level tasks and updates affected parents", () => {
    const queryClient = new QueryClient();
    const movedTask = makeTask({ id: 300, title: "Dragged task", due_date: "2026-03-15", list_id: 8 });
    const destinationParent = makeTask({
      id: 400,
      title: "Target parent",
      due_date: "2026-03-15",
      list_id: 4,
      subtasks: [],
    });

    queryClient.setQueryData<TaskItem[]>(["tasks", "all"], [movedTask, destinationParent]);
    queryClient.setQueryData<TaskItem[]>(["tasks", "today", "2026-03-15"], [movedTask, destinationParent]);

    const result: TaskMoveResult = {
      task: makeSubtask({
        id: 300,
        title: "Dragged task",
        parent_id: 400,
        list_id: 4,
        position: 0,
      }),
      affected_tasks: [
        makeTask({
          id: 400,
          title: "Target parent",
          due_date: "2026-03-15",
          list_id: 4,
          subtasks: [
            makeSubtask({
              id: 300,
              title: "Dragged task",
              parent_id: 400,
              list_id: 4,
              position: 0,
            }),
          ],
        }),
      ],
      removed_top_level_task_ids: [300],
    };

    applyTaskMoveResultInCaches(queryClient, result);

    expect(queryClient.getQueryData<TaskItem[]>(["tasks", "all"])).toEqual([
      expect.objectContaining({
        id: 400,
        subtasks: [expect.objectContaining({ id: 300, parent_id: 400 })],
      }),
    ]);
    expect(queryClient.getQueryData<TaskItem[]>(["tasks", "today", "2026-03-15"])).toEqual([
      expect.objectContaining({
        id: 400,
        subtasks: [expect.objectContaining({ id: 300, parent_id: 400 })],
      }),
    ]);
  });
});
