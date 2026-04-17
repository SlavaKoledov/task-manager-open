// @vitest-environment jsdom

import { useQuery, QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { fireEvent, render, screen, waitFor } from "@testing-library/react";

import { CalendarPage, CalendarPageView } from "@/pages/calendar-page";
import { createEmptyDescription } from "@/lib/task-description";
import { createTask, deleteTask, getTasks, reorderSubtasks, reorderTopLevelTasks, toggleTask, updateTask } from "@/lib/api";
import type { TaskItem, TaskUpdatePayload } from "@/lib/types";

vi.mock("@/lib/api", () => ({
  createTask: vi.fn(),
  deleteTask: vi.fn(),
  getTasks: vi.fn(),
  reorderSubtasks: vi.fn(),
  reorderTopLevelTasks: vi.fn(),
  toggleTask: vi.fn(),
  updateTask: vi.fn(),
}));

function makeTask(overrides: Partial<TaskItem> = {}): TaskItem {
  return {
    id: 1,
    title: "Release review",
    description: null,
    description_blocks: createEmptyDescription(),
    due_date: "2026-03-18",
    start_time: null,
    end_time: null,
    reminder_time: "09:30",
    repeat_config: null,
    repeat_until: null,
    is_done: false,
    is_pinned: false,
    priority: "urgent_important",
    repeat: "weekly",
    parent_id: null,
    position: 0,
    list_id: 5,
    created_at: "2026-03-18T08:00:00Z",
    updated_at: "2026-03-18T08:00:00Z",
    subtasks: [],
    ...overrides,
  };
}

const getTasksMock = vi.mocked(getTasks);
const updateTaskMock = vi.mocked(updateTask);
const toggleTaskMock = vi.mocked(toggleTask);
const createTaskMock = vi.mocked(createTask);
const deleteTaskMock = vi.mocked(deleteTask);
const reorderSubtasksMock = vi.mocked(reorderSubtasks);
const reorderTopLevelTasksMock = vi.mocked(reorderTopLevelTasks);

function createTestQueryClient() {
  return new QueryClient({
    defaultOptions: {
      queries: {
        retry: false,
      },
    },
  });
}

function CalendarPageHarness({
  lists = [],
  showCompleted = false,
  todayString = "2026-03-05",
  tomorrowString = "2026-03-06",
}: {
  lists?: { id: number; name: string; color: string; position: number; created_at: string; updated_at: string }[];
  showCompleted?: boolean;
  todayString?: string;
  tomorrowString?: string;
}) {
  const tasksQuery = useQuery({
    queryKey: ["tasks", "all"],
    queryFn: () => getTasks(),
    retry: false,
  });

  return (
    <CalendarPage
      tasks={tasksQuery.data ?? []}
      tasksLoading={tasksQuery.isLoading}
      tasksError={Boolean(tasksQuery.error)}
      onRetry={() => void tasksQuery.refetch()}
      lists={lists}
      showCompleted={showCompleted}
      newTaskPlacement="end"
      todayString={todayString}
      tomorrowString={tomorrowString}
    />
  );
}

beforeEach(() => {
  getTasksMock.mockReset();
  updateTaskMock.mockReset();
  toggleTaskMock.mockReset();
  createTaskMock.mockReset();
  deleteTaskMock.mockReset();
  reorderSubtasksMock.mockReset();
  reorderTopLevelTasksMock.mockReset();

  createTaskMock.mockResolvedValue(makeTask({ id: 999 }));
  deleteTaskMock.mockResolvedValue(undefined);
  reorderSubtasksMock.mockResolvedValue(makeTask({ id: 999 }));
  reorderTopLevelTasksMock.mockResolvedValue([]);
});

describe("CalendarPageView", () => {
  it("switches between month and week layouts", () => {
    render(
      <CalendarPageView
        tasks={[makeTask()]}
        lists={[
          {
            id: 5,
            name: "Work",
            color: "#2563eb",
            position: 0,
            created_at: "2026-03-18T08:00:00Z",
            updated_at: "2026-03-18T08:00:00Z",
          },
        ]}
        showCompleted
        todayString="2026-03-18"
        onOpenTask={() => undefined}
        onCreateTask={() => undefined}
      />,
    );

    expect(screen.getByRole("button", { name: "Month" }).getAttribute("aria-pressed")).toBe("true");
    expect(screen.getAllByRole("gridcell")).toHaveLength(42);
    expect(screen.getByTestId("calendar-month-split-handle")).not.toBeNull();

    fireEvent.click(screen.getByRole("button", { name: "Week" }));

    expect(screen.getByRole("button", { name: "Week" }).getAttribute("aria-pressed")).toBe("true");
    expect(screen.queryAllByRole("gridcell")).toHaveLength(0);
    expect(screen.getByText("Mar 16 - 22, 2026")).not.toBeNull();
    expect(screen.getByTestId("calendar-week-split-handle")).not.toBeNull();
  });

  it("shows task time badges in the selected day panel and week view", () => {
    render(
      <CalendarPageView
        tasks={[
          makeTask({
            start_time: "09:00",
            end_time: "10:30",
          }),
        ]}
        lists={[
          {
            id: 5,
            name: "Work",
            color: "#2563eb",
            position: 0,
            created_at: "2026-03-18T08:00:00Z",
            updated_at: "2026-03-18T08:00:00Z",
          },
        ]}
        showCompleted
        todayString="2026-03-18"
        onOpenTask={() => undefined}
        onCreateTask={() => undefined}
      />,
    );

    expect(screen.getAllByText("09:00–10:30").length).toBeGreaterThan(0);

    fireEvent.click(screen.getByRole("button", { name: "Week" }));

    expect(screen.getAllByText("09:00–10:30").length).toBeGreaterThan(0);
  });

  it("uses toggle semantics for recurring completion from the calendar dialog", async () => {
    let taskStore: TaskItem[] = [
      makeTask({
        id: 21,
        title: "Recurring planning",
        due_date: "2026-03-05",
        repeat: "weekly",
      }),
    ];

    getTasksMock.mockImplementation(async () => taskStore.map((task) => ({ ...task, subtasks: [...task.subtasks] })));
    updateTaskMock.mockImplementation(async (id: number, payload: TaskUpdatePayload) => {
      const currentTask = taskStore.find((task) => task.id === id) ?? makeTask({ id });
      const updatedTask = { ...currentTask, ...payload };
      taskStore = taskStore.map((task) => (task.id === id ? updatedTask : task));
      return updatedTask;
    });
    toggleTaskMock.mockImplementation(async (id: number) => {
      const currentTask = taskStore.find((task) => task.id === id) ?? makeTask({ id });
      const toggledTask = { ...currentTask, is_done: !currentTask.is_done };
      const spawnedTask = {
        ...currentTask,
        id: 22,
        due_date: "2026-03-12",
        is_done: false,
        created_at: "2026-03-05T11:00:00Z",
        updated_at: "2026-03-05T11:00:00Z",
      };
      taskStore = [toggledTask, spawnedTask];
      return toggledTask;
    });

    const queryClient = createTestQueryClient();

    render(
      <QueryClientProvider client={queryClient}>
        <CalendarPageHarness showCompleted={false} />
      </QueryClientProvider>,
    );

    await waitFor(() => expect(screen.getAllByText("Recurring planning").length).toBeGreaterThan(0));

    fireEvent.click(screen.getAllByRole("button", { name: /Recurring planning/i })[0]!);
    fireEvent.change(screen.getByDisplayValue("Recurring planning"), { target: { value: "Recurring planning updated" } });
    fireEvent.click(screen.getByRole("checkbox", { name: "Toggle task status" }));
    fireEvent.click(screen.getByRole("button", { name: "Save changes" }));

    await waitFor(() => expect(toggleTaskMock).toHaveBeenCalledWith(21));
    await waitFor(() => expect(getTasksMock).toHaveBeenCalledTimes(2));
    expect(updateTaskMock).toHaveBeenCalledTimes(1);
    expect(updateTaskMock.mock.calls[0]?.[1]).not.toHaveProperty("is_done");
    expect(screen.queryByRole("dialog", { name: "Edit task" })).toBeNull();
  });
});
