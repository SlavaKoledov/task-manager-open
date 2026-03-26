export type ReorderInsertDirection = "before" | "after";
export type TaskDropDirection = ReorderInsertDirection | "inside" | "append";

export type ReorderDropIndicator = {
  targetId: number;
  direction: ReorderInsertDirection;
};

export type TaskDropIndicator = {
  targetId: number;
  direction: TaskDropDirection;
};

type RectLike = {
  top: number;
  height: number;
};

export function resolveReorderInsertDirection(clientY: number, rect: RectLike): ReorderInsertDirection {
  return clientY < rect.top + rect.height / 2 ? "before" : "after";
}

export function moveOrderedIds(
  ids: readonly number[],
  draggedId: number,
  targetId: number,
  direction: ReorderInsertDirection,
): number[] {
  if (draggedId === targetId) {
    return [...ids];
  }

  const draggedIndex = ids.indexOf(draggedId);
  const targetIndex = ids.indexOf(targetId);

  if (draggedIndex === -1 || targetIndex === -1) {
    return [...ids];
  }

  const remainingIds = ids.filter((id) => id !== draggedId);
  const remainingTargetIndex = remainingIds.indexOf(targetId);

  if (remainingTargetIndex === -1) {
    return [...ids];
  }

  const insertIndex = direction === "before" ? remainingTargetIndex : remainingTargetIndex + 1;
  const nextIds = [...remainingIds];
  nextIds.splice(insertIndex, 0, draggedId);
  return nextIds;
}

export function insertOrderedId(ids: readonly number[], insertedId: number, placement: "start" | "end"): number[] {
  return placement === "start" ? [insertedId, ...ids] : [...ids, insertedId];
}

export function insertOrderedIdRelative(
  ids: readonly number[],
  insertedId: number,
  targetId: number,
  direction: ReorderInsertDirection,
): number[] {
  const nextIds = ids.filter((id) => id !== insertedId);
  const targetIndex = nextIds.indexOf(targetId);

  if (targetIndex === -1) {
    return [...ids];
  }

  nextIds.splice(direction === "before" ? targetIndex : targetIndex + 1, 0, insertedId);
  return nextIds;
}

export function areDropIndicatorsEqual(
  left: ReorderDropIndicator | null,
  right: ReorderDropIndicator | null,
): boolean {
  if (left === right) {
    return true;
  }

  if (left === null || right === null) {
    return false;
  }

  return left.targetId === right.targetId && left.direction === right.direction;
}

export function areTaskDropIndicatorsEqual(left: TaskDropIndicator | null, right: TaskDropIndicator | null): boolean {
  if (left === right) {
    return true;
  }

  if (left === null || right === null) {
    return false;
  }

  return left.targetId === right.targetId && left.direction === right.direction;
}

export function resolveTaskDropDirection(
  clientY: number,
  rect: RectLike,
  allowInside: boolean,
): TaskDropDirection {
  const relativeY = clientY - rect.top;
  const topThreshold = rect.height * 0.25;
  const bottomThreshold = rect.height * 0.75;

  if (relativeY <= topThreshold) {
    return "before";
  }

  if (relativeY >= bottomThreshold) {
    return "after";
  }

  return allowInside ? "inside" : "after";
}
