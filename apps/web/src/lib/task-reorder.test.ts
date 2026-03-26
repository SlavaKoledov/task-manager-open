import {
  areDropIndicatorsEqual,
  areTaskDropIndicatorsEqual,
  insertOrderedId,
  insertOrderedIdRelative,
  moveOrderedIds,
  resolveReorderInsertDirection,
  resolveTaskDropDirection,
} from "@/lib/task-reorder";

describe("task reorder helpers", () => {
  it("resolves insertion direction from the hovered half", () => {
    expect(resolveReorderInsertDirection(10, { top: 0, height: 40 })).toBe("before");
    expect(resolveReorderInsertDirection(30, { top: 0, height: 40 })).toBe("after");
  });

  it("moves ids before or after the hovered target", () => {
    expect(moveOrderedIds([1, 2, 3, 4], 4, 2, "before")).toEqual([1, 4, 2, 3]);
    expect(moveOrderedIds([1, 2, 3, 4], 1, 3, "after")).toEqual([2, 3, 1, 4]);
  });

  it("inserts new ids at the configured beginning or end", () => {
    expect(insertOrderedId([2, 3], 1, "start")).toEqual([1, 2, 3]);
    expect(insertOrderedId([1, 2], 3, "end")).toEqual([1, 2, 3]);
    expect(insertOrderedIdRelative([10, 20], 5, 20, "before")).toEqual([10, 5, 20]);
  });

  it("compares drop indicators without false positives", () => {
    expect(areDropIndicatorsEqual(null, null)).toBe(true);
    expect(areDropIndicatorsEqual({ targetId: 2, direction: "before" }, { targetId: 2, direction: "before" })).toBe(
      true,
    );
    expect(areDropIndicatorsEqual({ targetId: 2, direction: "before" }, { targetId: 2, direction: "after" })).toBe(
      false,
    );
  });

  it("resolves task drop direction to inside only for the middle band", () => {
    expect(resolveTaskDropDirection(5, { top: 0, height: 40 }, true)).toBe("before");
    expect(resolveTaskDropDirection(20, { top: 0, height: 40 }, true)).toBe("inside");
    expect(resolveTaskDropDirection(35, { top: 0, height: 40 }, true)).toBe("after");
    expect(resolveTaskDropDirection(20, { top: 0, height: 40 }, false)).toBe("after");
  });

  it("compares task drop indicators including inside targets", () => {
    expect(areTaskDropIndicatorsEqual({ targetId: 7, direction: "inside" }, { targetId: 7, direction: "inside" })).toBe(
      true,
    );
    expect(areTaskDropIndicatorsEqual({ targetId: 7, direction: "inside" }, { targetId: 7, direction: "after" })).toBe(
      false,
    );
  });
});
