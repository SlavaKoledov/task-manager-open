export const DEFAULT_TASK_SPACE_PATH = "/";
export const TASK_SPACE_ROUTE_STORAGE_KEY = "task-manager.last-task-route";

const TASK_ROUTE_PATTERNS = [/^\/$/, /^\/today$/, /^\/tomorrow$/, /^\/inbox$/, /^\/lists\/\d+$/];

export function isTaskRoutePath(pathname: string): boolean {
  return TASK_ROUTE_PATTERNS.some((pattern) => pattern.test(pathname));
}

export function sanitizeTaskSpacePath(pathname: string | null | undefined): string {
  if (!pathname) {
    return DEFAULT_TASK_SPACE_PATH;
  }

  return isTaskRoutePath(pathname) ? pathname : DEFAULT_TASK_SPACE_PATH;
}

export function getStoredTaskSpacePath(storage?: Pick<Storage, "getItem"> | null): string {
  if (!storage) {
    return DEFAULT_TASK_SPACE_PATH;
  }

  return sanitizeTaskSpacePath(storage.getItem(TASK_SPACE_ROUTE_STORAGE_KEY));
}

export function persistTaskSpacePath(pathname: string, storage?: Pick<Storage, "setItem"> | null): string {
  const nextPathname = sanitizeTaskSpacePath(pathname);
  storage?.setItem(TASK_SPACE_ROUTE_STORAGE_KEY, nextPathname);
  return nextPathname;
}
