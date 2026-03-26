import { buildDailyNotificationLines, findNextTaskReminder } from "@/lib/browser-notifications";
import type { TaskItem, TaskSubtask } from "@/lib/types";

function makeTask(overrides: Partial<TaskItem> = {}): TaskItem {
  return {
    id: 1,
    title: "Task",
    description: null,
    description_blocks: [{ kind: "text", text: "" }],
    due_date: null,
    reminder_time: null,
    repeat_until: null,
    is_done: false,
    is_pinned: false,
    priority: "not_urgent_unimportant",
    repeat: "none",
    parent_id: null,
    position: 0,
    list_id: null,
    created_at: "2026-03-15T08:00:00Z",
    updated_at: "2026-03-15T08:00:00Z",
    subtasks: [],
    ...overrides,
  };
}

function makeSubtask(overrides: Partial<TaskSubtask> = {}): TaskSubtask {
  return {
    id: 11,
    title: "Subtask",
    description: null,
    description_blocks: [{ kind: "text", text: "" }],
    due_date: null,
    reminder_time: null,
    repeat_until: null,
    is_done: false,
    is_pinned: false,
    priority: "not_urgent_unimportant",
    repeat: "none",
    parent_id: 1,
    position: 0,
    list_id: null,
    created_at: "2026-03-15T08:00:00Z",
    updated_at: "2026-03-15T08:00:00Z",
    ...overrides,
  };
}

describe("browser notifications helpers", () => {
  it("builds daily summary lines with overdue today and timed reminders", () => {
    expect(
      buildDailyNotificationLines(
        [
          makeTask({ id: 1, title: "Overdue", due_date: "2026-03-14" }),
          makeTask({ id: 2, title: "Today", due_date: "2026-03-15" }),
          makeTask({
            id: 3,
            title: "Parent",
            due_date: "2026-03-16",
            subtasks: [
              makeSubtask({ id: 31, title: "Subtask", due_date: "2026-03-15", reminder_time: "16:00", parent_id: 3 }),
            ],
          }),
        ],
        "2026-03-15",
      ),
    ).toEqual([
      'Overdue "Overdue"',
      'Today "Today"',
      '16:00 "Subtask"',
    ]);
  });

  it("finds the next upcoming task reminder across tasks and subtasks", () => {
    const nextReminder = findNextTaskReminder(
      [
        makeTask({ id: 1, title: "Later", due_date: "2026-03-15", reminder_time: "16:00" }),
        makeTask({
          id: 2,
          title: "Parent",
          subtasks: [
            makeSubtask({ id: 21, title: "Sooner subtask", due_date: "2026-03-15", reminder_time: "12:30", parent_id: 2 }),
          ],
        }),
      ],
      new Date("2026-03-15T12:00:00"),
    );

    expect(nextReminder?.id).toBe(21);
    expect(nextReminder?.title).toBe("Sooner subtask");
  });
});
