import { createElement } from "react";
import { renderToStaticMarkup } from "react-dom/server";

import { TaskCard } from "@/components/task-card";
import type { TaskItem } from "@/lib/types";

function makeTask(overrides: Partial<TaskItem> = {}): TaskItem {
  return {
    id: 1,
    title: "Parent task",
    description: null,
    description_blocks: [{ kind: "text", text: "" }],
    due_date: "2026-03-14",
    reminder_time: null,
    repeat_config: null,
    repeat_until: null,
    is_done: false,
    is_pinned: false,
    priority: "urgent_unimportant",
    repeat: "none",
    parent_id: null,
    position: 0,
    list_id: null,
    created_at: "2026-03-14T08:00:00Z",
    updated_at: "2026-03-14T08:00:00Z",
    subtasks: [
      {
        id: 11,
        title: "Done subtask",
        description: null,
        description_blocks: [{ kind: "text", text: "" }],
        due_date: null,
        reminder_time: null,
        repeat_config: null,
        repeat_until: null,
        is_done: true,
        is_pinned: false,
        priority: "urgent_unimportant",
        repeat: "none",
        parent_id: 1,
        position: 0,
        list_id: null,
        created_at: "2026-03-14T08:00:00Z",
        updated_at: "2026-03-14T08:00:00Z",
      },
      {
        id: 12,
        title: "Active subtask",
        description: null,
        description_blocks: [{ kind: "text", text: "" }],
        due_date: null,
        reminder_time: null,
        repeat_config: null,
        repeat_until: null,
        is_done: false,
        is_pinned: false,
        priority: "urgent_unimportant",
        repeat: "none",
        parent_id: 1,
        position: 1,
        list_id: null,
        created_at: "2026-03-14T08:00:00Z",
        updated_at: "2026-03-14T08:00:00Z",
      },
      {
        id: 13,
        title: "Another done subtask",
        description: null,
        description_blocks: [{ kind: "text", text: "" }],
        due_date: null,
        reminder_time: null,
        repeat_config: null,
        repeat_until: null,
        is_done: true,
        is_pinned: false,
        priority: "urgent_unimportant",
        repeat: "none",
        parent_id: 1,
        position: 2,
        list_id: null,
        created_at: "2026-03-14T08:00:00Z",
        updated_at: "2026-03-14T08:00:00Z",
      },
    ],
    ...overrides,
  };
}

describe("TaskCard", () => {
  it("renders the rounded subtask progress percent next to the summary", () => {
    const html = renderToStaticMarkup(
      createElement(TaskCard, {
        task: makeTask(),
        todayString: "2026-03-13",
        tomorrowString: "2026-03-14",
        subtasksCollapsed: false,
        onToggle: async () => undefined,
        onToggleSubtask: async () => undefined,
        onEdit: () => undefined,
        onToggleSubtasks: () => undefined,
      }),
    );

    expect(html).toContain("3 subtasks");
    expect(html).toContain("67%");
  });

  it("renders a description indicator only for meaningful content", () => {
    const withDescription = renderToStaticMarkup(
      createElement(TaskCard, {
        task: makeTask({
          description_blocks: [{ kind: "text", text: "Useful notes" }],
        }),
        todayString: "2026-03-13",
        tomorrowString: "2026-03-14",
        subtasksCollapsed: false,
        onToggle: async () => undefined,
        onToggleSubtask: async () => undefined,
        onEdit: () => undefined,
        onToggleSubtasks: () => undefined,
      }),
    );
    const withoutDescription = renderToStaticMarkup(
      createElement(TaskCard, {
        task: makeTask({
          description_blocks: [{ kind: "text", text: "" }],
        }),
        todayString: "2026-03-13",
        tomorrowString: "2026-03-14",
        subtasksCollapsed: false,
        onToggle: async () => undefined,
        onToggleSubtask: async () => undefined,
        onEdit: () => undefined,
        onToggleSubtasks: () => undefined,
      }),
    );

    expect(withDescription).toContain("Has description");
    expect(withoutDescription).not.toContain("Has description");
  });

  it("renders custom repeat summaries on the card badge", () => {
    const html = renderToStaticMarkup(
      createElement(TaskCard, {
        task: makeTask({
          repeat: "custom",
          repeat_config: {
            interval: 2,
            unit: "week",
            skip_weekends: false,
            weekdays: [1, 3, 5],
            month_day: null,
            month: null,
            day: null,
          },
        }),
        todayString: "2026-03-13",
        tomorrowString: "2026-03-14",
        subtasksCollapsed: false,
        onToggle: async () => undefined,
        onToggleSubtask: async () => undefined,
        onEdit: () => undefined,
        onToggleSubtasks: () => undefined,
      }),
    );

    expect(html).toContain("Every 2 weeks on M, W, F");
  });

  it("renders a priority-colored time badge before the other metadata", () => {
    const html = renderToStaticMarkup(
      createElement(TaskCard, {
        task: makeTask({
          start_time: "09:00",
          end_time: "10:30",
        }),
        todayString: "2026-03-13",
        tomorrowString: "2026-03-14",
        subtasksCollapsed: false,
        onToggle: async () => undefined,
        onToggleSubtask: async () => undefined,
        onEdit: () => undefined,
        onToggleSubtasks: () => undefined,
      }),
    );

    expect(html).toContain("09:00–10:30");
    expect(html.indexOf("09:00–10:30")).toBeLessThan(html.indexOf("Tomorrow"));
  });
});
