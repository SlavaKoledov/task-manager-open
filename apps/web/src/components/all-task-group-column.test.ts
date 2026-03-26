import { createElement } from "react";
import { renderToStaticMarkup } from "react-dom/server";

import {
  ALL_TASK_GROUP_COLUMN_CLASSNAME,
  ALL_TASK_GROUP_HEADER_CLASSNAME,
  AllTaskGroupColumn,
} from "@/components/all-task-group-column";
import type { AllTaskGroup } from "@/lib/task-groups";
import type { TaskItem } from "@/lib/types";
import {
  ALL_VIEW_COLUMNS_ROW_CLASSNAME,
  ALL_VIEW_COLUMNS_SCROLLER_CLASSNAME,
} from "@/pages/tasks-page";

function makeTask(id: number, overrides: Partial<TaskItem> = {}): TaskItem {
  return {
    id,
    title: `Task ${id}`,
    description: null,
    description_blocks: [{ kind: "text", text: "" }],
    due_date: "2026-03-14",
    reminder_time: null,
    repeat_until: null,
    is_done: false,
    is_pinned: false,
    priority: "urgent_unimportant",
    repeat: "none",
    parent_id: null,
    position: id - 1,
    list_id: null,
    created_at: "2026-03-14T08:00:00Z",
    updated_at: "2026-03-14T08:00:00Z",
    subtasks: [],
    ...overrides,
  };
}

function makeGroup(): AllTaskGroup {
  const lowTask = makeTask(1);
  const noneTask = makeTask(2, { priority: "not_urgent_unimportant" });
  const doneTask = makeTask(3, { is_done: true });

  return {
    id: "tomorrow",
    title: "Tomorrow",
    emptyDescription: "Nothing queued for tomorrow.",
    tasks: [lowTask, noneTask, doneTask],
    sections: [
      { id: "urgent_unimportant", title: "Low", tasks: [lowTask] },
      { id: "not_urgent_unimportant", title: "None", tasks: [noneTask] },
    ],
    doneTasks: [doneTask],
    activeCount: 2,
    doneCount: 1,
    totalCount: 3,
  };
}

describe("AllTaskGroupColumn", () => {
  it("renders a single top-level vertical scroll owner with a sticky header", () => {
    const html = renderToStaticMarkup(
      createElement(AllTaskGroupColumn, {
        group: makeGroup(),
        listById: new Map(),
        todayString: "2026-03-13",
        tomorrowString: "2026-03-14",
        collapsedTaskSections: {},
        collapsedSubtasks: {},
        onOpenCreateTaskDialog: () => undefined,
        onTaskToggle: async () => undefined,
        onSubtaskToggle: async () => undefined,
        onTaskEdit: () => undefined,
        onToggleSubtasks: () => undefined,
        onToggleCollapsed: () => undefined,
        onCreateTaskInSection: () => undefined,
        onReorderTasks: async () => undefined,
      }),
    );

    expect(html).toContain(ALL_TASK_GROUP_COLUMN_CLASSNAME);
    expect(html).toContain(ALL_TASK_GROUP_HEADER_CLASSNAME);
    expect(html.match(/overflow-y-auto/g)?.length ?? 0).toBe(1);
    expect(html).toContain(">Tomorrow<");
    expect(html).toContain(">Low<");
    expect(html).toContain(">None<");
    expect(html).toContain(">Done<");
  });

  it("keeps the All-view shell responsible only for horizontal scrolling and definite column height", () => {
    expect(ALL_VIEW_COLUMNS_SCROLLER_CLASSNAME).toContain("overflow-x-auto");
    expect(ALL_VIEW_COLUMNS_SCROLLER_CLASSNAME).toContain("overflow-y-hidden");
    expect(ALL_VIEW_COLUMNS_ROW_CLASSNAME).toContain("h-full");
    expect(ALL_VIEW_COLUMNS_ROW_CLASSNAME).toContain("min-h-0");
    expect(ALL_VIEW_COLUMNS_ROW_CLASSNAME).not.toContain("min-h-full");
  });
});
