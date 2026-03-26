import {
  NEW_TASK_PLACEMENT_STORAGE_KEY,
  parseStoredNewTaskPlacement,
  parseStoredShowCompleted,
  serializeNewTaskPlacement,
  serializeShowCompleted,
  SHOW_COMPLETED_STORAGE_KEY,
} from "@/lib/task-view-preferences";

describe("task view preferences", () => {
  it("uses the shared show-completed storage key", () => {
    expect(SHOW_COMPLETED_STORAGE_KEY).toBe("task-manager.show-completed");
  });

  it("defaults to showing completed tasks unless storage explicitly disables them", () => {
    expect(parseStoredShowCompleted(null)).toBe(true);
    expect(parseStoredShowCompleted("true")).toBe(true);
    expect(parseStoredShowCompleted("false")).toBe(false);
    expect(serializeShowCompleted(false)).toBe("false");
  });

  it("stores new task placement as a persisted start-or-end preference", () => {
    expect(NEW_TASK_PLACEMENT_STORAGE_KEY).toBe("task-manager.new-task-placement");
    expect(parseStoredNewTaskPlacement(null)).toBe("end");
    expect(parseStoredNewTaskPlacement("start")).toBe("start");
    expect(parseStoredNewTaskPlacement("something-else")).toBe("end");
    expect(serializeNewTaskPlacement("start")).toBe("start");
    expect(serializeNewTaskPlacement("end")).toBe("end");
  });
});
