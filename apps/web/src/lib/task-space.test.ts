import {
  DEFAULT_TASK_SPACE_PATH,
  getStoredTaskSpacePath,
  isTaskRoutePath,
  persistTaskSpacePath,
  sanitizeTaskSpacePath,
  TASK_SPACE_ROUTE_STORAGE_KEY,
} from "@/lib/task-space";

describe("task space helpers", () => {
  it("recognizes supported task routes", () => {
    expect(isTaskRoutePath("/")).toBe(true);
    expect(isTaskRoutePath("/today")).toBe(true);
    expect(isTaskRoutePath("/tomorrow")).toBe(true);
    expect(isTaskRoutePath("/inbox")).toBe(true);
    expect(isTaskRoutePath("/lists/42")).toBe(true);
    expect(isTaskRoutePath("/calendar")).toBe(false);
  });

  it("sanitizes unknown routes to the default task path", () => {
    expect(sanitizeTaskSpacePath("/calendar")).toBe(DEFAULT_TASK_SPACE_PATH);
    expect(sanitizeTaskSpacePath("/unknown")).toBe(DEFAULT_TASK_SPACE_PATH);
    expect(sanitizeTaskSpacePath("/lists/not-a-number")).toBe(DEFAULT_TASK_SPACE_PATH);
  });

  it("reads and writes the last task route through storage", () => {
    const storage = {
      value: "/today",
      getItem(key: string) {
        return key === TASK_SPACE_ROUTE_STORAGE_KEY ? this.value : null;
      },
      setItem(key: string, value: string) {
        if (key === TASK_SPACE_ROUTE_STORAGE_KEY) {
          this.value = value;
        }
      },
    };

    expect(getStoredTaskSpacePath(storage)).toBe("/today");
    expect(persistTaskSpacePath("/lists/9", storage)).toBe("/lists/9");
    expect(getStoredTaskSpacePath(storage)).toBe("/lists/9");
    expect(persistTaskSpacePath("/calendar", storage)).toBe(DEFAULT_TASK_SPACE_PATH);
    expect(getStoredTaskSpacePath(storage)).toBe(DEFAULT_TASK_SPACE_PATH);
  });
});
