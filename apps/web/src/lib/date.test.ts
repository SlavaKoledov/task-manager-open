import { formatDueDateLabel, getLocalDateString, getMillisecondsUntilNextLocalMidnight, getTomorrowDateString } from "@/lib/date";

describe("date helpers", () => {
  it("builds a local yyyy-mm-dd string", () => {
    const value = getLocalDateString(new Date(2026, 2, 13));
    expect(value).toBe("2026-03-13");
  });

  it("formats today and other dates", () => {
    expect(formatDueDateLabel("2026-03-13", "2026-03-13")).toBe("Today");
    expect(formatDueDateLabel("2026-03-14", "2026-03-13", "2026-03-14")).toBe("Tomorrow");
    expect(formatDueDateLabel("2026-03-15", "2026-03-13", "2026-03-14")).toBe("Mar 15");
  });

  it("builds tomorrow from a local date", () => {
    expect(getTomorrowDateString(new Date(2026, 2, 13))).toBe("2026-03-14");
  });

  it("computes time remaining until the next local midnight", () => {
    const value = getMillisecondsUntilNextLocalMidnight(new Date(2026, 2, 13, 23, 59, 0, 0));
    expect(value).toBe(60_000);
  });
});
