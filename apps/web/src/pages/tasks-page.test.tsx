// @vitest-environment jsdom

import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { MemoryRouter, Route, Routes } from "react-router-dom";

import { TasksPage } from "@/pages/tasks-page";
import { createEmptyDescription } from "@/lib/task-description";
import {
  createTask,
  deleteTask,
  getInboxTasks,
  getListTasks,
  getTasks,
  getTodayTasks,
  getTomorrowTasks,
  moveTask,
  reorderSubtasks,
  reorderTopLevelTasks,
  toggleTask,
  updateTask,
} from "@/lib/api";
import type { TaskCreatePayload, TaskItem, TaskMoveResult, TaskUpdatePayload } from "@/lib/types";

vi.mock("@/lib/api", () => ({
  createTask: vi.fn(),
  deleteTask: vi.fn(),
  getInboxTasks: vi.fn(),
  getListTasks: vi.fn(),
  getTasks: vi.fn(),
  getTodayTasks: vi.fn(),
  getTomorrowTasks: vi.fn(),
  moveTask: vi.fn(),
  reorderSubtasks: vi.fn(),
  reorderTopLevelTasks: vi.fn(),
  toggleTask: vi.fn(),
  updateTask: vi.fn(),
}));

function makeTask(overrides: Partial<TaskItem> = {}): TaskItem {
  return {
    id: 1,
    title: "Recurring review",
    description: null,
    description_blocks: createEmptyDescription(),
    due_date: "2026-03-22",
    start_time: null,
    end_time: null,
    reminder_time: null,
    repeat_config: null,
    repeat_until: null,
    is_done: false,
    is_pinned: false,
    priority: "not_urgent_unimportant",
    repeat: "weekly",
    parent_id: null,
    position: 0,
    list_id: null,
    created_at: "2026-03-22T09:00:00Z",
    updated_at: "2026-03-22T09:00:00Z",
    subtasks: [],
    ...overrides,
  };
}

const createTaskMock = vi.mocked(createTask);
const deleteTaskMock = vi.mocked(deleteTask);
const getInboxTasksMock = vi.mocked(getInboxTasks);
const getListTasksMock = vi.mocked(getListTasks);
const getTasksMock = vi.mocked(getTasks);
const getTodayTasksMock = vi.mocked(getTodayTasks);
const getTomorrowTasksMock = vi.mocked(getTomorrowTasks);
const moveTaskMock = vi.mocked(moveTask);
const reorderSubtasksMock = vi.mocked(reorderSubtasks);
const reorderTopLevelTasksMock = vi.mocked(reorderTopLevelTasks);
const toggleTaskMock = vi.mocked(toggleTask);
const updateTaskMock = vi.mocked(updateTask);

function createTestQueryClient() {
  return new QueryClient({
    defaultOptions: {
      queries: {
        retry: false,
      },
    },
  });
}

beforeEach(() => {
  createTaskMock.mockReset();
  deleteTaskMock.mockReset();
  getInboxTasksMock.mockReset();
  getListTasksMock.mockReset();
  getTasksMock.mockReset();
  getTodayTasksMock.mockReset();
  getTomorrowTasksMock.mockReset();
  moveTaskMock.mockReset();
  reorderSubtasksMock.mockReset();
  reorderTopLevelTasksMock.mockReset();
  toggleTaskMock.mockReset();
  updateTaskMock.mockReset();

  createTaskMock.mockImplementation(async (payload: TaskCreatePayload) => makeTask({ id: 999, ...payload }));
  deleteTaskMock.mockResolvedValue(undefined);
  getInboxTasksMock.mockResolvedValue([]);
  getListTasksMock.mockResolvedValue([]);
  getTodayTasksMock.mockResolvedValue([]);
  getTomorrowTasksMock.mockResolvedValue([]);
  moveTaskMock.mockResolvedValue({ task: makeTask(), affected_tasks: [], removed_top_level_task_ids: [] } as TaskMoveResult);
  reorderSubtasksMock.mockResolvedValue(makeTask({ id: 999 }));
  reorderTopLevelTasksMock.mockResolvedValue([]);
  updateTaskMock.mockImplementation(async (id: number, payload: TaskUpdatePayload) => makeTask({ id, ...payload }));
});

describe("TasksPage", () => {
  it("keeps using toggle semantics for recurring completion from the task list", async () => {
    let taskStore: TaskItem[] = [makeTask()];

    getTasksMock.mockImplementation(async () => taskStore.map((task) => ({ ...task, subtasks: [...task.subtasks] })));
    toggleTaskMock.mockImplementation(async (id: number) => {
      const currentTask = taskStore.find((task) => task.id === id) ?? makeTask({ id });
      const toggledTask = { ...currentTask, is_done: true };
      const spawnedTask = makeTask({
        id: 2,
        due_date: "2026-03-29",
        is_done: false,
        created_at: "2026-03-22T09:05:00Z",
        updated_at: "2026-03-22T09:05:00Z",
      });
      taskStore = [toggledTask, spawnedTask];
      return toggledTask;
    });

    const queryClient = createTestQueryClient();

    render(
      <QueryClientProvider client={queryClient}>
        <MemoryRouter initialEntries={["/tasks"]}>
          <Routes>
            <Route
              path="*"
              element={
                <TasksPage
                  mode="all"
                  lists={[]}
                  listsLoading={false}
                  showCompleted={false}
                  newTaskPlacement="end"
                  todayString="2026-03-01"
                  tomorrowString="2026-03-02"
                />
              }
            />
          </Routes>
        </MemoryRouter>
      </QueryClientProvider>,
    );

    await waitFor(() => expect(screen.getByText("Recurring review")).not.toBeNull());

    fireEvent.click(screen.getByRole("checkbox", { name: "Toggle task" }));

    await waitFor(() => expect(toggleTaskMock).toHaveBeenCalledWith(1));
    await waitFor(() => expect(getTasksMock).toHaveBeenCalledTimes(2));
    await waitFor(() => expect(screen.getByText("Mar 29")).not.toBeNull());
  });
});
