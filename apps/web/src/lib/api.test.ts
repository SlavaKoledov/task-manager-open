import { parseLiveEvent } from "@/lib/api";

describe("api live event parsing", () => {
  it("accepts supported task and list events", () => {
    const taskEvent = parseLiveEvent(
      new MessageEvent("message", {
        data: JSON.stringify({
          version: 1,
          entity_type: "task",
          entity_ids: [1, 2],
          changed_at: "2026-03-15T12:00:00Z",
        }),
      }),
    );

    const listEvent = parseLiveEvent(
      new MessageEvent("message", {
        data: JSON.stringify({
          version: 2,
          entity_type: "list",
          entity_ids: [5],
          changed_at: "2026-03-15T12:05:00Z",
        }),
      }),
    );

    expect(taskEvent?.entity_type).toBe("task");
    expect(listEvent?.entity_type).toBe("list");
  });

  it("rejects malformed or unsupported events", () => {
    const invalidJson = parseLiveEvent(new MessageEvent("message", { data: "not-json" }));
    const unsupported = parseLiveEvent(
      new MessageEvent("message", {
        data: JSON.stringify({
          version: 3,
          entity_type: "unknown",
          entity_ids: [9],
          changed_at: "2026-03-15T12:10:00Z",
        }),
      }),
    );

    expect(invalidJson).toBeNull();
    expect(unsupported).toBeNull();
  });
});
