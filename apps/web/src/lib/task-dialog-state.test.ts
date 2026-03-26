import {
  buildTaskCreatePayloadFromDraft,
  buildTaskUpdatePayloadFromDraft,
  validateTaskDraft,
} from "@/lib/task-dialog-state";
import type { TaskDraft, TaskSubtask } from "@/lib/types";

function makeDraft(overrides: Partial<TaskDraft> = {}): TaskDraft {
  return {
    title: "Plan sprint",
    description_blocks: [
      { kind: "text", text: "Ship the milestone" },
      { kind: "text", text: "" },
    ],
    due_date: "2026-03-13",
    reminder_time: "08:30",
    repeat_until: "",
    is_done: false,
    is_pinned: true,
    priority: "urgent_important",
    repeat: "weekly",
    list_id: "42",
    ...overrides,
  };
}

describe("task dialog state helpers", () => {
  it("requires a title", () => {
    expect(validateTaskDraft(makeDraft({ title: "   " }))).toBe("Task title is required.");
  });

  it("requires a due date before repeat is enabled", () => {
    expect(validateTaskDraft(makeDraft({ due_date: "", repeat: "daily" }))).toBe(
      "Choose a due date before setting a repeat schedule.",
    );
  });

  it("builds a normalized create payload from dialog draft state", () => {
    const draftSubtasks: TaskSubtask[] = [
      {
        id: -1,
        title: "  Draft release note  ",
        description: null,
        description_blocks: [{ kind: "text", text: "Summarize fixes" }],
        due_date: null,
        reminder_time: null,
        repeat_until: null,
        is_done: false,
        is_pinned: false,
        priority: "urgent_important",
        repeat: "none",
        parent_id: null,
        position: 0,
        list_id: 42,
        created_at: "",
        updated_at: "",
      },
    ];

    expect(
      buildTaskCreatePayloadFromDraft(makeDraft({ title: "  Plan sprint  ", repeat_until: "2026-04-10" }), undefined, draftSubtasks),
    ).toEqual({
      title: "Plan sprint",
      description: "Ship the milestone",
      description_blocks: [{ kind: "text", text: "Ship the milestone" }],
      due_date: "2026-03-13",
      reminder_time: "08:30",
      repeat_until: "2026-04-10",
      is_done: false,
      is_pinned: true,
      priority: "urgent_important",
      repeat: "weekly",
      parent_id: null,
      list_id: 42,
      subtasks: [
        {
          title: "Draft release note",
          description: "Summarize fixes",
          description_blocks: [{ kind: "text", text: "Summarize fixes" }],
          due_date: null,
          reminder_time: null,
          is_done: false,
        },
      ],
    });
  });

  it("preserves checklist-only descriptions without injecting an empty text block", () => {
    expect(
      buildTaskCreatePayloadFromDraft(
        makeDraft({
          description_blocks: [{ kind: "checkbox", text: "Review draft", checked: false }],
        }),
      ),
    ).toMatchObject({
      description: "- [ ] Review draft",
      description_blocks: [{ kind: "checkbox", text: "Review draft", checked: false }],
    });
  });

  it("drops blank create-mode subtasks from the payload", () => {
    const draftSubtasks: TaskSubtask[] = [
      {
        id: -1,
        title: "   ",
        description: null,
        description_blocks: [],
        due_date: null,
        reminder_time: null,
        repeat_until: null,
        is_done: false,
        is_pinned: false,
        priority: "urgent_important",
        repeat: "none",
        parent_id: null,
        position: 0,
        list_id: 42,
        created_at: "",
        updated_at: "",
      },
      {
        id: -2,
        title: "Keep this",
        description: null,
        description_blocks: [],
        due_date: null,
        reminder_time: null,
        repeat_until: null,
        is_done: false,
        is_pinned: false,
        priority: "urgent_important",
        repeat: "none",
        parent_id: null,
        position: 1,
        list_id: 42,
        created_at: "",
        updated_at: "",
      },
    ];

    expect(buildTaskCreatePayloadFromDraft(makeDraft(), undefined, draftSubtasks).subtasks).toEqual([
      {
        title: "Keep this",
        description: null,
        description_blocks: [],
        due_date: null,
        reminder_time: null,
        is_done: false,
      },
    ]);
  });

  it("requires a repeat schedule before allowing repeat_until", () => {
    expect(validateTaskDraft(makeDraft({ repeat: "none", repeat_until: "2026-03-20" }))).toBe(
      "Choose a repeat schedule before setting a repeat end date.",
    );
  });

  it("requires a due date before allowing repeat_until", () => {
    expect(validateTaskDraft(makeDraft({ due_date: "", repeat_until: "2026-03-20" }))).toBe(
      "Choose a due date before setting a repeat end date.",
    );
  });

  it("rejects repeat_until values earlier than the due date", () => {
    expect(validateTaskDraft(makeDraft({ repeat_until: "2026-03-12" }))).toBe(
      "Repeat end date cannot be earlier than the due date.",
    );
  });

  it("builds an update payload without create-only fields", () => {
    expect(buildTaskUpdatePayloadFromDraft(makeDraft({ repeat_until: "2026-04-10" }))).toEqual({
      title: "Plan sprint",
      description: "Ship the milestone",
      description_blocks: [{ kind: "text", text: "Ship the milestone" }],
      due_date: "2026-03-13",
      reminder_time: "08:30",
      repeat_until: "2026-04-10",
      is_done: false,
      is_pinned: true,
      priority: "urgent_important",
      repeat: "weekly",
      list_id: 42,
    });
  });

  it("clears repeat_until when repeat is disabled", () => {
    expect(buildTaskUpdatePayloadFromDraft(makeDraft({ repeat: "none" }))).toMatchObject({
      repeat: "none",
      repeat_until: null,
    });
  });

  it("requires a due date before allowing a reminder", () => {
    expect(validateTaskDraft(makeDraft({ due_date: "", repeat: "none", reminder_time: "09:00" }))).toBe(
      "Choose a due date before setting a reminder.",
    );
  });
});
