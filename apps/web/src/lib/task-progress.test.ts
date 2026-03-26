import { getSubtaskProgressSummary } from "@/lib/task-progress";

describe("subtask progress helpers", () => {
  it("returns null percent when there are no subtasks", () => {
    expect(getSubtaskProgressSummary([])).toEqual({
      total: 0,
      done: 0,
      percent: null,
    });
  });

  it("rounds to the nearest whole percent", () => {
    expect(
      getSubtaskProgressSummary([
        { is_done: true },
        { is_done: true },
        { is_done: false },
      ]),
    ).toEqual({
      total: 3,
      done: 2,
      percent: 67,
    });
  });
});
