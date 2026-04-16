// @vitest-environment jsdom

import { fireEvent, render, screen } from "@testing-library/react";

import { CalendarPageView } from "@/pages/calendar-page";
import type { TaskItem } from "@/lib/types";

function makeTask(overrides: Partial<TaskItem> = {}): TaskItem {
  return {
    id: 1,
    title: "Release review",
    description: null,
    description_blocks: [{ kind: "text", text: "" }],
    due_date: "2026-03-18",
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
});
