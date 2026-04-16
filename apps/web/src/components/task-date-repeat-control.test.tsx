// @vitest-environment jsdom

import { fireEvent, render, screen, within } from "@testing-library/react";
import { useState } from "react";

import { TaskDateRepeatControl } from "@/components/task-date-repeat-control";
import type { TaskCustomRepeatConfig, TaskRepeat } from "@/lib/types";

function TaskDateRepeatControlHarness({
  initialValue = "",
  initialStartTime = "",
  initialEndTime = "",
  initialRepeat = "none",
  initialRepeatConfig = null,
  initialRepeatUntil = "",
}: {
  initialValue?: string;
  initialStartTime?: string;
  initialEndTime?: string;
  initialRepeat?: TaskRepeat;
  initialRepeatConfig?: TaskCustomRepeatConfig | null;
  initialRepeatUntil?: string;
}) {
  const [value, setValue] = useState(initialValue);
  const [startTime, setStartTime] = useState(initialStartTime);
  const [endTime, setEndTime] = useState(initialEndTime);
  const [reminderTime, setReminderTime] = useState("");
  const [repeat, setRepeat] = useState<TaskRepeat>(initialRepeat);
  const [repeatConfig, setRepeatConfig] = useState<TaskCustomRepeatConfig | null>(initialRepeatConfig);
  const [repeatUntil, setRepeatUntil] = useState(initialRepeatUntil);

  return (
    <TaskDateRepeatControl
      value={value}
      startTime={startTime}
      endTime={endTime}
      reminderTime={reminderTime}
      repeat={repeat}
      repeatConfig={repeatConfig}
      repeatUntil={repeatUntil}
      onDateChange={setValue}
      onStartTimeChange={setStartTime}
      onEndTimeChange={setEndTime}
      onReminderTimeChange={setReminderTime}
      onRepeatChange={setRepeat}
      onRepeatConfigChange={setRepeatConfig}
      onRepeatUntilChange={setRepeatUntil}
    />
  );
}

describe("TaskDateRepeatControl", () => {
  it("opens scheduling controls inside a large dialog overlay", () => {
    render(<TaskDateRepeatControlHarness />);

    fireEvent.click(screen.getByRole("button", { name: /No date/i }));

    expect(screen.getByRole("dialog", { name: "Schedule" })).not.toBeNull();
    expect(screen.getByText("Choose the due date, task time, reminder, and repeat settings in one place.")).not.toBeNull();
    expect(screen.getByRole("button", { name: "Today" })).not.toBeNull();
    expect(screen.getByText("Time")).not.toBeNull();
    expect(screen.getByText("Reminder")).not.toBeNull();
    expect(screen.getByText("Repeat")).not.toBeNull();
  });

  it("renders monday-first weekday headers that stay aligned with date selection", () => {
    render(<TaskDateRepeatControlHarness initialValue="2026-03-16" />);

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

    expect(screen.getByText("Tuesday, March 17")).not.toBeNull();
  });

  it("configures custom weekly repeat with weekday selection and restores it when reopened", async () => {
    render(<TaskDateRepeatControlHarness initialValue="2026-03-16" />);

    fireEvent.click(screen.getByRole("button", { name: /Mar 16/i }));
    fireEvent.click(screen.getByRole("button", { name: /^Custom/i }));
    fireEvent.change(await screen.findByRole("combobox"), { target: { value: "week" } });

    const mondayButton = screen.getByRole("button", { name: "Monday" });
    const wednesdayButton = screen.getByRole("button", { name: "Wednesday" });

    expect(mondayButton.getAttribute("aria-pressed")).toBe("true");
    fireEvent.click(wednesdayButton);
    fireEvent.mouseDown(document.body);

    expect(screen.getAllByText("Every 1 week on M, W").length).toBeGreaterThan(0);

    fireEvent.click(screen.getByRole("button", { name: /^Custom/i }));
    expect((await screen.findByRole("combobox") as HTMLSelectElement).value).toBe("week");
    expect(screen.getByRole("button", { name: "Monday" }).getAttribute("aria-pressed")).toBe("true");
    expect(screen.getByRole("button", { name: "Wednesday" }).getAttribute("aria-pressed")).toBe("true");
  });

  it("supports daily and monthly custom controls including skip weekends and month day selection", async () => {
    render(<TaskDateRepeatControlHarness initialValue="2026-03-16" />);

    fireEvent.click(screen.getByRole("button", { name: /Mar 16/i }));
    fireEvent.click(screen.getByRole("button", { name: /^Custom/i }));

    const skipWeekendsCheckbox = await screen.findByRole("checkbox", { name: "Skip weekends" });
    fireEvent.click(skipWeekendsCheckbox);
    fireEvent.mouseDown(document.body);
    expect(screen.getAllByText("Every 1 day, skip weekends").length).toBeGreaterThan(0);

    fireEvent.click(screen.getByRole("button", { name: /^Custom/i }));
    fireEvent.change(await screen.findByRole("combobox"), { target: { value: "month" } });
    fireEvent.click(screen.getAllByRole("button", { name: "31" }).at(-1) as HTMLElement);
    expect((screen.getByRole("checkbox", { name: "Skip weekends" }) as HTMLInputElement).checked).toBe(true);
    fireEvent.mouseDown(document.body);

    expect(screen.getAllByText("Every 1 month on 31, skip weekends").length).toBeGreaterThan(0);
  });

  it("lets the custom Every field go blank during editing and restores empty saves to 1", async () => {
    render(<TaskDateRepeatControlHarness initialValue="2026-03-16" />);

    fireEvent.click(screen.getByRole("button", { name: /Mar 16/i }));
    fireEvent.click(screen.getByRole("button", { name: /^Custom/i }));

    const intervalInput = await screen.findByRole("spinbutton");
    fireEvent.change(intervalInput, { target: { value: "" } });

    expect((screen.getByRole("spinbutton") as HTMLInputElement).value).toBe("");

    fireEvent.mouseDown(document.body);
    expect(screen.getAllByText("Every 1 day").length).toBeGreaterThan(0);

    fireEvent.click(screen.getByRole("button", { name: /^Custom/i }));
    expect((await screen.findByRole("spinbutton") as HTMLInputElement).value).toBe("1");
  });

  it("supports start and end time selection with inline validation", () => {
    render(<TaskDateRepeatControlHarness initialValue="2026-03-16" />);

    fireEvent.click(screen.getByRole("button", { name: /Mar 16/i }));
    fireEvent.change(screen.getByLabelText("Start time"), { target: { value: "09:00" } });
    fireEvent.change(screen.getByLabelText("End time"), { target: { value: "08:30" } });

    expect(screen.getByText("End time must be later than the start time.")).not.toBeNull();

    fireEvent.change(screen.getByLabelText("End time"), { target: { value: "10:15" } });
    expect(screen.queryByText("End time must be later than the start time.")).toBeNull();
  });

  it("restores saved custom yearly repeat state and allows month navigation plus date selection", async () => {
    render(
      <TaskDateRepeatControlHarness
        initialValue="2026-03-16"
        initialRepeat="custom"
        initialRepeatConfig={{
          interval: 3,
          unit: "year",
          skip_weekends: false,
          weekdays: [],
          month_day: null,
          month: 2,
          day: 28,
        }}
      />,
    );

    expect(screen.getAllByText("Every 3 years on Feb 28").length).toBeGreaterThan(0);

    fireEvent.click(screen.getByRole("button", { name: /Mar 16/i }));
    fireEvent.click(screen.getByRole("button", { name: /^Custom/i }));

    expect((await screen.findByRole("spinbutton")).getAttribute("value")).toBe("3");
    expect((screen.getByRole("combobox") as HTMLSelectElement).value).toBe("year");
    expect(screen.getAllByRole("button", { name: "28" }).at(-1)?.getAttribute("aria-pressed")).toBe("true");

    fireEvent.click(screen.getByRole("button", { name: "Next custom repeat month" }));
    fireEvent.click(screen.getAllByRole("button", { name: "20" }).at(-1) as HTMLElement);
    fireEvent.mouseDown(document.body);

    expect(screen.getAllByText("Every 3 years on Mar 20").length).toBeGreaterThan(0);
  });
});
