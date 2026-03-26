// @vitest-environment jsdom

import { fireEvent, render, screen, waitFor, within } from "@testing-library/react";

import { TaskDialog } from "@/components/task-dialog";
import { createEmptyDescription } from "@/lib/task-description";
import type { ListItem, TaskCreatePayload, TaskDraft, TaskItem, TaskSubtask } from "@/lib/types";

function makeDraft(overrides: Partial<TaskDraft> = {}): TaskDraft {
  return {
    title: "Plan launch",
    description_blocks: createEmptyDescription(),
    due_date: "",
    reminder_time: "",
    repeat_until: "",
    is_done: false,
    is_pinned: false,
    priority: "not_urgent_unimportant",
    repeat: "none",
    list_id: "",
    ...overrides,
  };
}

function makeTask(overrides: Partial<TaskItem> = {}): TaskItem {
  return {
    id: 101,
    title: "Plan launch",
    description: null,
    description_blocks: createEmptyDescription(),
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
    created_at: "2026-03-22T10:00:00Z",
    updated_at: "2026-03-22T10:00:00Z",
    subtasks: [],
    ...overrides,
  };
}

function makeSubtask(overrides: Partial<TaskSubtask> = {}): TaskSubtask {
  return {
    id: 201,
    title: "Write release notes",
    description: null,
    description_blocks: createEmptyDescription(),
    due_date: null,
    reminder_time: null,
    repeat_until: null,
    is_done: false,
    is_pinned: false,
    priority: "not_urgent_unimportant",
    repeat: "none",
    parent_id: 101,
    position: 0,
    list_id: null,
    created_at: "2026-03-22T10:00:00Z",
    updated_at: "2026-03-22T10:00:00Z",
    ...overrides,
  };
}

function renderTaskDialog({
  task = null,
  defaultDraft = makeDraft(),
  onCreateTask = async () => undefined,
  onCreateSubtask = async (_task: TaskItem, title: string) => makeSubtask({ title }),
}: {
  task?: TaskItem | null;
  defaultDraft?: TaskDraft;
  onCreateTask?: (payload: TaskCreatePayload) => Promise<void>;
  onCreateSubtask?: (task: TaskItem, title: string) => Promise<TaskSubtask>;
}) {
  const lists: ListItem[] = [];

  render(
    <TaskDialog
      open
      task={task}
      defaultDraft={defaultDraft}
      lists={lists}
      subtasksCollapsed={false}
      onOpenChange={() => undefined}
      onCreateTask={onCreateTask}
      onUpdateTask={async (nextTask) => nextTask}
      onCreateSubtask={onCreateSubtask}
      onUpdateSubtask={async (subtask) => subtask}
      onToggleSubtask={async (subtask) => ({ ...subtask, is_done: !subtask.is_done })}
      onDeleteSubtask={async () => undefined}
      onReorderSubtasks={async (nextTask) => nextTask}
      onToggleSubtasks={() => undefined}
    />,
  );
}

describe("TaskDialog", () => {
  it("uses a single description overlay instead of checklist and read-full controls", async () => {
    const onCreateTask = vi.fn(async () => undefined);
    renderTaskDialog({ onCreateTask });

    expect(screen.queryByRole("button", { name: "Checklist" })).toBeNull();
    expect(screen.queryByRole("button", { name: "Read full" })).toBeNull();

    fireEvent.click(screen.getByRole("button", { name: "Open description editor" }));

    const descriptionDialog = screen.getByRole("dialog", { name: "Description" });
    const descriptionTextarea = within(descriptionDialog).getByRole("textbox");
    fireEvent.change(descriptionTextarea, { target: { value: "Line one\n- [ ] Carry over checklist text" } });

    expect(screen.getByRole("button", { name: "Open description editor" }).textContent).toContain("Carry over checklist text");

    fireEvent.click(within(descriptionDialog).getByRole("button", { name: "Close dialog" }));
    fireEvent.click(screen.getByRole("button", { name: "Create task" }));

    await waitFor(() => expect(onCreateTask).toHaveBeenCalledTimes(1));
    expect(onCreateTask).toHaveBeenCalledWith(
      expect.objectContaining({
        description: "Line one\n- [ ] Carry over checklist text",
        description_blocks: [
          { kind: "text", text: "Line one" },
          { kind: "checkbox", text: "Carry over checklist text", checked: false },
        ],
      }),
    );
  });

  it("commits the pending create-mode subtask draft into the create payload", async () => {
    const onCreateTask = vi.fn(async () => undefined);
    renderTaskDialog({ onCreateTask });

    fireEvent.click(screen.getByRole("button", { name: "Add subtask" }));
    fireEvent.change(screen.getByPlaceholderText("New subtask"), { target: { value: "Draft release notes" } });
    fireEvent.click(screen.getByRole("button", { name: "Create task" }));

    await waitFor(() => expect(onCreateTask).toHaveBeenCalledTimes(1));
    expect(onCreateTask).toHaveBeenCalledWith(
      expect.objectContaining({
        subtasks: [
          expect.objectContaining({
            title: "Draft release notes",
            is_done: false,
          }),
        ],
      }),
    );
  });

  it("shows a newly created subtask in edit mode after the create callback resolves", async () => {
    const task = makeTask();
    const onCreateSubtask = vi.fn(async (_task: TaskItem, title: string) =>
      makeSubtask({ id: 301, title, parent_id: task.id }),
    );

    renderTaskDialog({ task, onCreateSubtask });

    fireEvent.click(screen.getByRole("button", { name: "Add subtask" }));
    const input = screen.getByPlaceholderText("New subtask");
    fireEvent.change(input, { target: { value: "Ship release notes" } });
    fireEvent.keyDown(input, { key: "Enter" });

    await waitFor(() => expect(onCreateSubtask).toHaveBeenCalledTimes(1));
    expect(screen.getByDisplayValue("Ship release notes")).not.toBeNull();
  });
});
