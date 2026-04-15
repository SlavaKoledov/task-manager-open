import {
  buildTaskOccurrencesInRange,
  buildRecurringPreviewDates,
  compareTaskOccurrences,
  getCalendarMonthRange,
  getCalendarWeekDays,
  getCalendarWeekRange,
  buildVisibleRecurringPreviewDateSet,
  groupTaskOccurrencesByDate,
  getCalendarDays,
} from "@/lib/task-calendar";
import type { TaskItem } from "@/lib/types";

function makeTask(overrides: Partial<TaskItem> = {}): TaskItem {
  return {
    id: 1,
    title: "Task",
    description: null,
    description_blocks: [{ kind: "text", text: "" }],
    due_date: "2026-03-10",
    reminder_time: null,
    repeat_config: null,
    repeat_until: null,
    is_done: false,
    is_pinned: false,
    priority: "not_urgent_unimportant",
    repeat: "none",
    parent_id: null,
    position: 0,
    list_id: null,
    created_at: "2026-03-10T08:00:00Z",
    updated_at: "2026-03-10T08:00:00Z",
    subtasks: [],
    ...overrides,
  };
}

describe("task calendar helpers", () => {
  it("builds a 6-week calendar grid around the visible month", () => {
    const days = getCalendarDays(new Date(2026, 2, 14));

    expect(days).toHaveLength(42);
    expect(days[0]?.dateString).toBe("2026-02-23");
    expect(days[41]?.dateString).toBe("2026-04-05");
  });

  it("builds month and week ranges with monday-based weeks", () => {
    const monthRange = getCalendarMonthRange(new Date(2026, 2, 14));
    const weekRange = getCalendarWeekRange(new Date(2026, 2, 18));

    expect(monthRange.startDateString).toBe("2026-02-23");
    expect(monthRange.endDateString).toBe("2026-04-05");
    expect(weekRange.startDateString).toBe("2026-03-16");
    expect(weekRange.endDateString).toBe("2026-03-22");
    expect(getCalendarWeekDays(new Date(2026, 2, 18)).map((day) => day.dateString)).toEqual([
      "2026-03-16",
      "2026-03-17",
      "2026-03-18",
      "2026-03-19",
      "2026-03-20",
      "2026-03-21",
      "2026-03-22",
    ]);
  });

  it("builds daily preview dates for the current task", () => {
    expect(buildRecurringPreviewDates("2026-03-10", "daily", undefined, undefined, 3)).toEqual([
      "2026-03-11",
      "2026-03-12",
      "2026-03-13",
    ]);
  });

  it("builds weekly preview dates for the current task", () => {
    expect(buildRecurringPreviewDates("2026-03-10", "weekly", undefined, undefined, 3)).toEqual([
      "2026-03-17",
      "2026-03-24",
      "2026-03-31",
    ]);
  });

  it("uses safe end-of-month logic for monthly preview dates", () => {
    expect(buildRecurringPreviewDates("2024-01-31", "monthly", undefined, undefined, 3)).toEqual([
      "2024-02-29",
      "2024-03-29",
      "2024-04-29",
    ]);
  });

  it("uses safe leap-year logic for yearly preview dates", () => {
    expect(buildRecurringPreviewDates("2024-02-29", "yearly", undefined, undefined, 4)).toEqual([
      "2025-02-28",
      "2026-02-28",
      "2027-02-28",
      "2028-02-28",
    ]);
  });

  it("builds preview dates for custom repeat schedules", () => {
    expect(
      buildRecurringPreviewDates(
        "2026-03-13",
        "custom",
        {
          interval: 1,
          unit: "day",
          skip_weekends: true,
          weekdays: [],
          month_day: null,
          month: null,
          day: null,
        },
        undefined,
        3,
      ),
    ).toEqual(["2026-03-16", "2026-03-17", "2026-03-18"]);

    expect(
      buildRecurringPreviewDates(
        "2026-03-16",
        "custom",
        {
          interval: 2,
          unit: "week",
          skip_weekends: false,
          weekdays: [1, 3, 5],
          month_day: null,
          month: null,
          day: null,
        },
        undefined,
        4,
      ),
    ).toEqual(["2026-03-18", "2026-03-20", "2026-03-30", "2026-04-01"]);
  });

  it("returns no preview dates when repeat is disabled or due date is missing", () => {
    expect(buildRecurringPreviewDates("2026-03-10", "none", undefined, undefined, 3)).toEqual([]);
    expect(buildRecurringPreviewDates("", "daily", undefined, undefined, 3)).toEqual([]);
  });

  it("stops previewing recurring dates after repeat_until", () => {
    expect(buildRecurringPreviewDates("2026-03-10", "weekly", undefined, "2026-03-24", 6)).toEqual([
      "2026-03-17",
      "2026-03-24",
    ]);
  });

  it("filters preview dates to the currently visible month grid", () => {
    const previewDates = [
      "2026-03-11",
      "2026-03-18",
      "2026-04-15",
    ];
    const calendarDays = getCalendarDays(new Date(2026, 2, 14));

    expect([...buildVisibleRecurringPreviewDateSet(previewDates, calendarDays)].sort()).toEqual([
      "2026-03-11",
      "2026-03-18",
    ]);
  });

  it("builds task occurrences in a visible range including the base due date", () => {
    const range = getCalendarWeekRange(new Date(2026, 2, 10));
    const occurrences = buildTaskOccurrencesInRange(
      [
        makeTask({ id: 1, title: "One-off", due_date: "2026-03-10" }),
        makeTask({ id: 2, title: "Daily repeat", due_date: "2026-03-09", repeat: "daily", repeat_until: "2026-03-12" }),
      ],
      range,
    );

    expect(
      occurrences.map((occurrence) => ({
        id: occurrence.task.id,
        date: occurrence.dateString,
        recurring: occurrence.isRecurring,
      })),
    ).toEqual([
      { id: 2, date: "2026-03-09", recurring: false },
      { id: 2, date: "2026-03-10", recurring: true },
      { id: 1, date: "2026-03-10", recurring: false },
      { id: 2, date: "2026-03-11", recurring: true },
      { id: 2, date: "2026-03-12", recurring: true },
    ]);
  });

  it("omits recurring occurrences after repeat_until", () => {
    const range = getCalendarMonthRange(new Date(2026, 2, 1));
    const occurrences = buildTaskOccurrencesInRange(
      [
        makeTask({
          id: 3,
          title: "Weekly review",
          due_date: "2026-03-05",
          repeat: "weekly",
          repeat_until: "2026-03-19",
        }),
      ],
      range,
    );

    expect(occurrences.map((occurrence) => occurrence.dateString)).toEqual([
      "2026-03-05",
      "2026-03-12",
      "2026-03-19",
    ]);
  });

  it("uses safe monthly custom occurrences inside visible ranges", () => {
    const range = getCalendarMonthRange(new Date(2026, 2, 1));
    const occurrences = buildTaskOccurrencesInRange(
      [
        makeTask({
          id: 4,
          title: "Month-end custom",
          due_date: "2026-01-31",
          repeat: "custom",
          repeat_config: {
            interval: 1,
            unit: "month",
            skip_weekends: false,
            weekdays: [],
            month_day: 31,
            month: null,
            day: null,
          },
          repeat_until: "2026-03-31",
        }),
      ],
      range,
    );

    expect(occurrences.map((occurrence) => occurrence.dateString)).toEqual([
      "2026-02-28",
      "2026-03-31",
    ]);
  });

  it("groups and sorts occurrences by reminder time then title", () => {
    const occurrencesByDate = groupTaskOccurrencesByDate([
      {
        task: makeTask({ id: 2, title: "No time", due_date: "2026-03-10" }),
        date: new Date(2026, 2, 10),
        dateString: "2026-03-10",
        isRecurring: false,
      },
      {
        task: makeTask({ id: 3, title: "Afternoon", due_date: "2026-03-10", reminder_time: "14:00" }),
        date: new Date(2026, 2, 10),
        dateString: "2026-03-10",
        isRecurring: false,
      },
      {
        task: makeTask({ id: 1, title: "Morning", due_date: "2026-03-10", reminder_time: "09:00" }),
        date: new Date(2026, 2, 10),
        dateString: "2026-03-10",
        isRecurring: false,
      },
    ]);

    const sortedTitles = [...(occurrencesByDate.get("2026-03-10") ?? [])].sort(compareTaskOccurrences).map((occurrence) => occurrence.task.title);

    expect(sortedTitles).toEqual(["Morning", "Afternoon", "No time"]);
  });
});
