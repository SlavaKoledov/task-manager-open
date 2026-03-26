// @vitest-environment jsdom

import { fireEvent, render, screen, within } from "@testing-library/react";

import { TaskDateRepeatControl } from "@/components/task-date-repeat-control";

describe("TaskDateRepeatControl", () => {
  it("opens scheduling controls inside a large dialog overlay", () => {
    render(
      <TaskDateRepeatControl
        value=""
        reminderTime=""
        repeat="none"
        repeatUntil=""
        onDateChange={() => undefined}
        onReminderTimeChange={() => undefined}
        onRepeatChange={() => undefined}
        onRepeatUntilChange={() => undefined}
      />,
    );

    fireEvent.click(screen.getByRole("button", { name: /No date/i }));

    expect(screen.getByRole("dialog", { name: "Schedule" })).not.toBeNull();
    expect(screen.getByText("Choose the due date, reminder, and repeat settings in one place.")).not.toBeNull();
    expect(screen.getByRole("button", { name: "Today" })).not.toBeNull();
    expect(screen.getByText("Reminder")).not.toBeNull();
    expect(screen.getByText("Repeat")).not.toBeNull();
  });

  it("renders monday-first weekday headers that stay aligned with date selection", () => {
    const onDateChange = vi.fn();

    render(
      <TaskDateRepeatControl
        value="2026-03-16"
        reminderTime=""
        repeat="none"
        repeatUntil=""
        onDateChange={onDateChange}
        onReminderTimeChange={() => undefined}
        onRepeatChange={() => undefined}
        onRepeatUntilChange={() => undefined}
      />,
    );

    fireEvent.click(screen.getByRole("button", { name: /Mar 16/i }));

    const weekdayHeaderGrid = screen.getAllByText(/^Mon$/)[0]?.parentElement;
    expect(weekdayHeaderGrid).not.toBeNull();
    expect(Array.from(weekdayHeaderGrid?.children ?? []).map((node) => node.textContent?.trim())).toEqual([
      "Mon",
      "Tue",
      "Wed",
      "Thu",
      "Fri",
      "Sat",
      "Sun",
    ]);

    const dayGrid = weekdayHeaderGrid?.nextElementSibling;
    expect(dayGrid).not.toBeNull();
    expect(within(dayGrid as HTMLElement).getAllByRole("button").slice(0, 7).map((button) => button.textContent?.trim())).toEqual([
      "23",
      "24",
      "25",
      "26",
      "27",
      "28",
      "1",
    ]);

    fireEvent.click(screen.getByRole("button", { name: "17" }));

    expect(onDateChange).toHaveBeenCalledWith("2026-03-17");
  });
});
