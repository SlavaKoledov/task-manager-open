import {
  buildTopLevelTaskDropScope,
  buildTopLevelTaskReorderScopeForTask,
  buildTaskSectionCollapseKey,
  buildSidebarTaskCounts,
  classifyTaskDueDateGroup,
  filterVisibleTasks,
  getTopLevelTaskIdsForReorderScope,
  getVisibleAllTaskGroups,
  getVisibleTodayTaskGroups,
  getVisibleTaskCollection,
  getTaskSectionId,
  groupTasksByPriority,
  groupTasksForAllView,
  groupTasksWithDone,
  isTaskOverdue,
  taskMatchesTopLevelReorderScope,
} from "@/lib/task-groups";
import type { TaskItem } from "@/lib/types";

function makeTask(id: number, dueDate: string | null, overrides: Partial<TaskItem> = {}): TaskItem {
  return {
    id,
    title: `Task ${id}`,
    description: null,
    description_blocks: [{ kind: "text", text: "" }],
    due_date: dueDate,
    reminder_time: null,
    repeat_until: null,
    is_done: false,
    is_pinned: false,
    priority: "not_urgent_unimportant",
    repeat: "none",
    parent_id: null,
    position: id - 1,
    list_id: null,
    created_at: "2026-03-13T10:00:00Z",
    updated_at: "2026-03-13T10:00:00Z",
    subtasks: [],
    ...overrides,
  };
}

describe("task grouping helpers", () => {
  it("classifies due dates into All view sections", () => {
    expect(classifyTaskDueDateGroup("2026-03-12", "2026-03-13")).toBe("overdue");
    expect(classifyTaskDueDateGroup("2026-03-13", "2026-03-13")).toBe("today");
    expect(classifyTaskDueDateGroup("2026-03-14", "2026-03-13")).toBe("tomorrow");
    expect(classifyTaskDueDateGroup("2026-03-20", "2026-03-13")).toBe("next_7_days");
    expect(classifyTaskDueDateGroup("2026-03-21", "2026-03-13")).toBe("later");
    expect(classifyTaskDueDateGroup(null, "2026-03-13")).toBe("no_date");
  });

  it("creates a dedicated pinned section above priority groups", () => {
    const sections = groupTasksByPriority([
      makeTask(1, null, { is_pinned: true }),
      makeTask(2, null, { priority: "urgent_important" }),
      makeTask(3, null, { priority: "urgent_unimportant" }),
    ]);

    expect(sections.map((section) => section.id)).toEqual(["pinned", "urgent_important", "urgent_unimportant"]);
    expect(sections[0].tasks.map((task) => task.id)).toEqual([1]);
  });

  it("derives the visible reorder scope from the current task and view", () => {
    const task = makeTask(1, "2026-03-13", { priority: "urgent_important" });

    expect(
      buildTopLevelTaskReorderScopeForTask({
        task,
        mode: "today",
        todayString: "2026-03-13",
        targetDateString: "2026-03-13",
      }),
    ).toEqual({
      view: "today",
      target_date: "2026-03-13",
      section_id: "urgent_important",
    });
  });

  it("derives drop scopes for section-level moves across views", () => {
    expect(
      buildTopLevelTaskDropScope({
        mode: "all",
        groupId: "later",
        sectionId: "urgent_unimportant",
        todayString: "2026-03-13",
        tomorrowString: "2026-03-14",
      }),
    ).toEqual({
      view: "all",
      group_id: "later",
      reference_date: "2026-03-13",
      section_id: "urgent_unimportant",
    });
    expect(
      buildTopLevelTaskDropScope({
        mode: "list",
        listId: 7,
        sectionId: "pinned",
        todayString: "2026-03-13",
        tomorrowString: "2026-03-14",
      }),
    ).toEqual({
      view: "list",
      list_id: 7,
      section_id: "pinned",
    });
  });

  it("matches only active top-level tasks inside the exact reorder scope", () => {
    const scope = {
      view: "all" as const,
      group_id: "later" as const,
      reference_date: "2026-03-13",
      section_id: "urgent_unimportant" as const,
    };

    expect(taskMatchesTopLevelReorderScope(makeTask(1, "2026-03-22", { priority: "urgent_unimportant" }), scope)).toBe(
      true,
    );
    expect(taskMatchesTopLevelReorderScope(makeTask(2, "2026-03-22", { is_done: true, priority: "urgent_unimportant" }), scope)).toBe(
      false,
    );
    expect(taskMatchesTopLevelReorderScope(makeTask(3, "2026-03-22", { is_pinned: true }), scope)).toBe(false);
  });

  it("collects only ids from the visible reorder scope", () => {
    const scope = {
      view: "list" as const,
      list_id: 7,
      section_id: "pinned" as const,
    };

    expect(
      getTopLevelTaskIdsForReorderScope(
        [
          makeTask(1, null, { is_pinned: true, list_id: 7 }),
          makeTask(2, null, { is_pinned: true, list_id: 8 }),
          makeTask(3, null, { priority: "urgent_important", list_id: 7 }),
        ],
        scope,
      ),
    ).toEqual([1]);
  });

  it("creates stable collapsed-state keys per group and section", () => {
    expect(buildTaskSectionCollapseKey("today", "pinned")).toBe("today:pinned");
    expect(buildTaskSectionCollapseKey("list:42", "urgent_important")).toBe("list:42:urgent_important");
  });

  it("moves completed tasks into a separate done collection", () => {
    const grouped = groupTasksWithDone([
      makeTask(1, null, { is_done: true, is_pinned: true }),
      makeTask(2, null, { priority: "urgent_important" }),
      makeTask(3, null, { priority: "urgent_unimportant" }),
    ]);

    expect(grouped.sections.map((section) => section.id)).toEqual(["urgent_important", "urgent_unimportant"]);
    expect(grouped.doneTasks.map((task) => task.id)).toEqual([1]);
    expect(grouped.activeCount).toBe(2);
    expect(grouped.doneCount).toBe(1);
  });

  it("preserves task order while splitting into All view columns and sections", () => {
    const grouped = groupTasksForAllView(
      [
        makeTask(1, "2026-03-12", { is_pinned: true }),
        makeTask(2, "2026-03-14", { priority: "urgent_important", is_done: true }),
        makeTask(3, "2026-03-22", { priority: "urgent_unimportant" }),
        makeTask(4, null),
      ],
      "2026-03-13",
    );

    expect(grouped.find((group) => group.id === "overdue")?.sections[0]?.id).toBe("pinned");
    expect(grouped.find((group) => group.id === "overdue")?.tasks.map((task) => task.id)).toEqual([1]);
    expect(grouped.find((group) => group.id === "tomorrow")?.doneTasks.map((task) => task.id)).toEqual([2]);
    expect(grouped.find((group) => group.id === "later")?.tasks.map((task) => task.id)).toEqual([3]);
    expect(grouped.find((group) => group.id === "no_date")?.tasks.map((task) => task.id)).toEqual([4]);
  });

  it("keeps completed overdue tasks out of active overdue counts while retaining them in done", () => {
    const grouped = groupTasksForAllView(
      [
        makeTask(1, "2026-03-12"),
        makeTask(2, "2026-03-12", { is_done: true }),
      ],
      "2026-03-13",
    );

    const overdueGroup = grouped.find((group) => group.id === "overdue");

    expect(overdueGroup?.activeCount).toBe(1);
    expect(overdueGroup?.doneCount).toBe(1);
    expect(overdueGroup?.doneTasks.map((task) => task.id)).toEqual([2]);
  });

  it("filters completed tasks out of non-all collections when hidden", () => {
    const grouped = getVisibleTaskCollection(
      [
        makeTask(1, null, { priority: "urgent_important" }),
        makeTask(2, null, { is_done: true, priority: "urgent_important" }),
      ],
      false,
    );

    expect(filterVisibleTasks([makeTask(1, null), makeTask(2, null, { is_done: true })], false).map((task) => task.id)).toEqual([
      1,
    ]);
    expect(grouped.sections.map((section) => section.id)).toEqual(["urgent_important"]);
    expect(grouped.sections[0]?.tasks.map((task) => task.id)).toEqual([1]);
    expect(grouped.doneTasks).toEqual([]);
    expect(grouped.totalCount).toBe(1);
  });

  it("hides all-view groups that only contain completed tasks when completed items are hidden", () => {
    const grouped = getVisibleAllTaskGroups(
      [
        makeTask(1, "2026-03-12", { is_done: true }),
        makeTask(2, "2026-03-13"),
      ],
      "2026-03-13",
      false,
    );

    expect(grouped.map((group) => group.id)).toEqual(["today"]);
    expect(grouped[0]?.tasks.map((task) => task.id)).toEqual([2]);
  });

  it("splits the Today view into overdue and today sections", () => {
    const grouped = getVisibleTodayTaskGroups(
      [
        makeTask(1, "2026-03-12"),
        makeTask(2, "2026-03-13"),
      ],
      "2026-03-13",
      true,
    );

    expect(grouped.map((group) => group.id)).toEqual(["overdue", "today"]);
  });

  it("marks only active past-due tasks as overdue for task chrome", () => {
    expect(isTaskOverdue(makeTask(1, "2026-03-12"), "2026-03-13")).toBe(true);
    expect(isTaskOverdue(makeTask(2, "2026-03-12", { is_done: true }), "2026-03-13")).toBe(false);
  });

  it("uses pinned as a dedicated section id before falling back to task priority", () => {
    expect(getTaskSectionId(makeTask(1, null, { is_pinned: true }))).toBe("pinned");
    expect(getTaskSectionId(makeTask(2, null, { priority: "urgent_important" }))).toBe("urgent_important");
  });

  it("builds sidebar counters from incomplete tasks only", () => {
    const counts = buildSidebarTaskCounts(
      [
        makeTask(1, "2026-03-12"),
        makeTask(2, "2026-03-13"),
        makeTask(3, "2026-03-13", { is_done: true }),
        makeTask(4, "2026-03-14", { list_id: 42 }),
        makeTask(5, null, { list_id: 42 }),
      ],
      [42, 99],
      "2026-03-13",
      "2026-03-14",
    );

    expect(counts).toEqual({
      all: 4,
      today: 2,
      tomorrow: 1,
      inbox: 1,
      listTaskCounts: {
        42: 2,
        99: 0,
      },
    });
  });
});
