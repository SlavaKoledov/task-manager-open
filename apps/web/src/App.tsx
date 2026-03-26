import type { CSSProperties, PointerEvent as ReactPointerEvent } from "react";
import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import { PanelLeftOpen } from "lucide-react";
import { Route, Routes, useLocation, useNavigate } from "react-router-dom";

import { ListDialog } from "@/components/list-dialog";
import { NotificationSettingsDialog } from "@/components/notification-settings-dialog";
import { Sidebar } from "@/components/sidebar";
import { ThemeToggle } from "@/components/theme-toggle";
import { Button } from "@/components/ui/button";
import { createList, deleteList, getLists, getTasks, reorderLists, updateList } from "@/lib/api";
import {
  getNotificationPermissionState,
  loadDailyNotificationPreferences,
  persistDailyNotificationPreferences,
  requestBrowserNotificationPermission,
  useBrowserNotificationScheduler,
} from "@/lib/browser-notifications";
import { clearListFromTaskCaches } from "@/lib/task-cache";
import { buildSidebarTaskCounts } from "@/lib/task-groups";
import { applyTheme, getStoredTheme, persistTheme, toggleThemeValue } from "@/lib/theme";
import { useLiveSync } from "@/lib/use-live-sync";
import { useLocalDayRollover } from "@/lib/use-local-day-rollover";
import {
  NEW_TASK_PLACEMENT_STORAGE_KEY,
  parseStoredNewTaskPlacement,
  parseStoredShowCompleted,
  serializeNewTaskPlacement,
  serializeShowCompleted,
  SHOW_COMPLETED_STORAGE_KEY,
} from "@/lib/task-view-preferences";
import { getStoredTaskSpacePath, isTaskRoutePath, persistTaskSpacePath } from "@/lib/task-space";
import type { DailyNotificationPreferences, ListItem, ListPayload, NewTaskPlacementPreference, Theme } from "@/lib/types";
import { CalendarPage } from "@/pages/calendar-page";
import { TasksPage } from "@/pages/tasks-page";

const SIDEBAR_MIN_WIDTH = 264;
const SIDEBAR_MAX_WIDTH = 420;
const SIDEBAR_DEFAULT_WIDTH = 320;
const SIDEBAR_WIDTH_STORAGE_KEY = "task-manager.sidebar.width";
const SIDEBAR_HIDDEN_STORAGE_KEY = "task-manager.sidebar.hidden";

function clampSidebarWidth(width: number): number {
  if (typeof window === "undefined") {
    return Math.min(SIDEBAR_MAX_WIDTH, Math.max(SIDEBAR_MIN_WIDTH, width));
  }

  const viewportMax = Math.max(SIDEBAR_MIN_WIDTH, Math.min(SIDEBAR_MAX_WIDTH, window.innerWidth - 360));
  return Math.min(viewportMax, Math.max(SIDEBAR_MIN_WIDTH, width));
}

function getStoredSidebarWidth(): number {
  if (typeof window === "undefined") {
    return SIDEBAR_DEFAULT_WIDTH;
  }

  const storedValue = window.localStorage.getItem(SIDEBAR_WIDTH_STORAGE_KEY);
  const parsedValue = storedValue ? Number(storedValue) : Number.NaN;

  return Number.isFinite(parsedValue) ? clampSidebarWidth(parsedValue) : SIDEBAR_DEFAULT_WIDTH;
}

function getStoredSidebarHidden(): boolean {
  if (typeof window === "undefined") {
    return false;
  }

  return window.localStorage.getItem(SIDEBAR_HIDDEN_STORAGE_KEY) === "true";
}

function getStoredShowCompleted(): boolean {
  if (typeof window === "undefined") {
    return true;
  }

  return parseStoredShowCompleted(window.localStorage.getItem(SHOW_COMPLETED_STORAGE_KEY));
}

function getStoredNewTaskPlacement(): NewTaskPlacementPreference {
  if (typeof window === "undefined") {
    return "end";
  }

  return parseStoredNewTaskPlacement(window.localStorage.getItem(NEW_TASK_PLACEMENT_STORAGE_KEY));
}

export default function App() {
  const listsQuery = useQuery({
    queryKey: ["lists"],
    queryFn: getLists,
    staleTime: 30_000,
  });
  const allTasksQuery = useQuery({
    queryKey: ["tasks", "all"],
    queryFn: getTasks,
    staleTime: 30_000,
  });
  const queryClient = useQueryClient();
  const navigate = useNavigate();
  const location = useLocation();
  const [listDialogOpen, setListDialogOpen] = useState(false);
  const [editingList, setEditingList] = useState<ListItem | null>(null);
  const [sidebarWidth, setSidebarWidth] = useState(getStoredSidebarWidth);
  const [sidebarHidden, setSidebarHidden] = useState(getStoredSidebarHidden);
  const [isResizingSidebar, setIsResizingSidebar] = useState(false);
  const [theme, setTheme] = useState<Theme>(getStoredTheme);
  const [showCompleted, setShowCompleted] = useState(getStoredShowCompleted);
  const [newTaskPlacement, setNewTaskPlacement] = useState<NewTaskPlacementPreference>(getStoredNewTaskPlacement);
  const [notificationDialogOpen, setNotificationDialogOpen] = useState(false);
  const [dailyNotificationPreferences, setDailyNotificationPreferences] =
    useState<DailyNotificationPreferences>(loadDailyNotificationPreferences);
  const [notificationPermissionState, setNotificationPermissionState] =
    useState<NotificationPermission | "unsupported">(getNotificationPermissionState);
  const [taskSpacePath, setTaskSpacePath] = useState(() =>
    typeof window === "undefined" ? "/" : getStoredTaskSpacePath(window.localStorage),
  );
  const resizeStateRef = useRef<{ startX: number; startWidth: number } | null>(null);
  const lists = listsQuery.data ?? [];
  const { todayString, tomorrowString } = useLocalDayRollover(
    useCallback(() => {
      void queryClient.invalidateQueries({ queryKey: ["tasks"] });
    }, [queryClient]),
  );

  useLiveSync(queryClient);
  useBrowserNotificationScheduler(allTasksQuery.data ?? [], todayString, dailyNotificationPreferences, notificationPermissionState);

  useEffect(() => {
    applyTheme(theme);
    persistTheme(theme);
  }, [theme]);

  useEffect(() => {
    window.localStorage.setItem(SIDEBAR_WIDTH_STORAGE_KEY, String(sidebarWidth));
  }, [sidebarWidth]);

  useEffect(() => {
    window.localStorage.setItem(SIDEBAR_HIDDEN_STORAGE_KEY, String(sidebarHidden));
  }, [sidebarHidden]);

  useEffect(() => {
    window.localStorage.setItem(SHOW_COMPLETED_STORAGE_KEY, serializeShowCompleted(showCompleted));
  }, [showCompleted]);

  useEffect(() => {
    window.localStorage.setItem(NEW_TASK_PLACEMENT_STORAGE_KEY, serializeNewTaskPlacement(newTaskPlacement));
  }, [newTaskPlacement]);

  useEffect(() => {
    persistDailyNotificationPreferences(dailyNotificationPreferences);
  }, [dailyNotificationPreferences]);

  useEffect(() => {
    const handleWindowFocus = () => setNotificationPermissionState(getNotificationPermissionState());
    window.addEventListener("focus", handleWindowFocus);
    return () => window.removeEventListener("focus", handleWindowFocus);
  }, []);

  useEffect(() => {
    if (!isTaskRoutePath(location.pathname)) {
      return;
    }

    const storage = typeof window === "undefined" ? null : window.localStorage;
    setTaskSpacePath(persistTaskSpacePath(location.pathname, storage));
  }, [location.pathname]);

  useEffect(() => {
    if (!isResizingSidebar || !resizeStateRef.current) {
      return;
    }

    const handlePointerMove = (event: PointerEvent) => {
      const nextWidth = resizeStateRef.current
        ? clampSidebarWidth(resizeStateRef.current.startWidth + event.clientX - resizeStateRef.current.startX)
        : SIDEBAR_DEFAULT_WIDTH;

      setSidebarWidth(nextWidth);
    };

    const handlePointerUp = () => {
      setIsResizingSidebar(false);
      resizeStateRef.current = null;
    };

    document.body.style.cursor = "col-resize";
    document.body.style.userSelect = "none";

    window.addEventListener("pointermove", handlePointerMove);
    window.addEventListener("pointerup", handlePointerUp);

    return () => {
      document.body.style.cursor = "";
      document.body.style.userSelect = "";
      window.removeEventListener("pointermove", handlePointerMove);
      window.removeEventListener("pointerup", handlePointerUp);
    };
  }, [isResizingSidebar]);

  const sidebarCounts = useMemo(() => {
    if (!allTasksQuery.data) {
      return undefined;
    }

    const counts = buildSidebarTaskCounts(
      allTasksQuery.data,
      lists.map((list) => list.id),
      todayString,
      tomorrowString,
    );

    return {
      ...counts,
      lists: lists.length,
    };
  }, [allTasksQuery.data, lists, todayString, tomorrowString]);

  const refreshLists = useCallback(async () => {
    await queryClient.invalidateQueries({ queryKey: ["lists"] });
  }, [queryClient]);

  const handleReorderLists = useCallback(
    async (listIds: number[]) => {
      const currentListIds = lists.map((list) => list.id);

      if (listIds.length !== currentListIds.length || listIds.every((listId, index) => listId === currentListIds[index])) {
        return;
      }

      const reorderedLists = await reorderLists(listIds);
      queryClient.setQueryData(["lists"], reorderedLists);

      if (editingList) {
        const updatedEditingList = reorderedLists.find((list) => list.id === editingList.id);
        if (updatedEditingList) {
          setEditingList(updatedEditingList);
        }
      }
    },
    [editingList, lists, queryClient],
  );

  const openCreateListDialog = useCallback(() => {
    setEditingList(null);
    setListDialogOpen(true);
  }, []);

  const openEditListDialog = useCallback((list: ListItem) => {
    setEditingList(list);
    setListDialogOpen(true);
  }, []);

  const handleListSubmit = useCallback(
    async (payload: ListPayload) => {
      if (editingList) {
        await updateList(editingList.id, payload);
      } else {
        await createList(payload);
      }

      await refreshLists();
      setEditingList(null);
    },
    [editingList, refreshLists],
  );

  const handleListDelete = useCallback(
    async (list: ListItem) => {
      await deleteList(list.id);
      clearListFromTaskCaches(queryClient, list.id);

      if (location.pathname === `/lists/${list.id}`) {
        navigate("/");
      }

      await refreshLists();
      setEditingList(null);
    },
    [location.pathname, navigate, queryClient, refreshLists],
  );

  const handleSidebarResizeStart = useCallback(
    (event: ReactPointerEvent<HTMLButtonElement>) => {
      resizeStateRef.current = {
        startX: event.clientX,
        startWidth: sidebarWidth,
      };
      setIsResizingSidebar(true);
      event.preventDefault();
    },
    [sidebarWidth],
  );

  const sidebarStyle = {
    "--sidebar-width": `${sidebarWidth}px`,
  } as CSSProperties;

  return (
    <>
      <div className="pointer-events-none fixed right-6 top-6 z-40">
        <div className="pointer-events-auto">
          <ThemeToggle
            theme={theme}
            showCompleted={showCompleted}
            newTaskPlacement={newTaskPlacement}
            onToggle={() => setTheme((current) => toggleThemeValue(current))}
            onToggleShowCompleted={() => setShowCompleted((current) => !current)}
            onChangeNewTaskPlacement={setNewTaskPlacement}
            onOpenNotifications={() => setNotificationDialogOpen(true)}
          />
        </div>
      </div>

      <div className="mx-auto flex min-h-screen max-w-[1540px] flex-col gap-4 px-4 py-6 lg:h-screen lg:max-h-screen lg:flex-row lg:items-stretch lg:overflow-hidden lg:px-6">
        {!sidebarHidden ? (
          <div className="w-full shrink-0 lg:flex lg:min-h-0 lg:flex-col lg:h-full lg:w-[var(--sidebar-width)]" style={sidebarStyle}>
            <Sidebar
              lists={lists}
              isLoading={listsQuery.isLoading}
              counts={sidebarCounts}
              currentSpace={location.pathname === "/calendar" ? "calendar" : "tasks"}
              taskSpacePath={taskSpacePath}
              onCreateList={openCreateListDialog}
              onEditList={openEditListDialog}
              onReorderLists={handleReorderLists}
              onHide={() => setSidebarHidden(true)}
              onResizeStart={handleSidebarResizeStart}
            />
          </div>
        ) : null}

        <main className="min-w-0 flex-1 lg:flex lg:min-h-0 lg:flex-col lg:overflow-hidden">
          {sidebarHidden ? (
            <div className="mb-4 shrink-0">
              <Button variant="outline" onClick={() => setSidebarHidden(false)}>
                <PanelLeftOpen className="h-4 w-4" />
                Show sidebar
              </Button>
            </div>
          ) : null}

          <div className="min-h-0 flex-1 lg:overflow-hidden">
            <Routes>
              <Route
                path="/"
                element={
                  <TasksPage
                    mode="all"
                    lists={lists}
                    listsLoading={listsQuery.isLoading}
                    showCompleted={showCompleted}
                    newTaskPlacement={newTaskPlacement}
                    todayString={todayString}
                    tomorrowString={tomorrowString}
                  />
                }
              />
              <Route
                path="/today"
                element={
                  <TasksPage
                    mode="today"
                    lists={lists}
                    listsLoading={listsQuery.isLoading}
                    showCompleted={showCompleted}
                    newTaskPlacement={newTaskPlacement}
                    todayString={todayString}
                    tomorrowString={tomorrowString}
                  />
                }
              />
              <Route
                path="/tomorrow"
                element={
                  <TasksPage
                    mode="tomorrow"
                    lists={lists}
                    listsLoading={listsQuery.isLoading}
                    showCompleted={showCompleted}
                    newTaskPlacement={newTaskPlacement}
                    todayString={todayString}
                    tomorrowString={tomorrowString}
                  />
                }
              />
              <Route
                path="/inbox"
                element={
                  <TasksPage
                    mode="inbox"
                    lists={lists}
                    listsLoading={listsQuery.isLoading}
                    showCompleted={showCompleted}
                    newTaskPlacement={newTaskPlacement}
                    todayString={todayString}
                    tomorrowString={tomorrowString}
                  />
                }
              />
              <Route
                path="/lists/:listId"
                element={
                  <TasksPage
                    mode="list"
                    lists={lists}
                    listsLoading={listsQuery.isLoading}
                    showCompleted={showCompleted}
                    newTaskPlacement={newTaskPlacement}
                    todayString={todayString}
                    tomorrowString={tomorrowString}
                  />
                }
              />
              <Route
                path="/calendar"
                element={
                  <CalendarPage
                    tasks={allTasksQuery.data ?? []}
                    tasksLoading={allTasksQuery.isLoading}
                    tasksError={allTasksQuery.isError}
                    onRetry={() => {
                      void allTasksQuery.refetch();
                    }}
                    lists={lists}
                    showCompleted={showCompleted}
                    newTaskPlacement={newTaskPlacement}
                    todayString={todayString}
                    tomorrowString={tomorrowString}
                  />
                }
              />
            </Routes>
          </div>
        </main>
      </div>

      <ListDialog
        open={listDialogOpen}
        list={editingList}
        onOpenChange={(open) => {
          setListDialogOpen(open);
          if (!open) {
            setEditingList(null);
          }
        }}
        onSubmit={handleListSubmit}
        onDelete={handleListDelete}
      />

      <NotificationSettingsDialog
        open={notificationDialogOpen}
        preferences={dailyNotificationPreferences}
        permissionState={notificationPermissionState}
        onOpenChange={setNotificationDialogOpen}
        onChangePreferences={setDailyNotificationPreferences}
        onRequestPermission={async () => {
          const nextPermission = await requestBrowserNotificationPermission();
          setNotificationPermissionState(nextPermission);
          if (nextPermission === "granted") {
            setDailyNotificationPreferences((current) => ({ ...current, enabled: true }));
          }
        }}
      />
    </>
  );
}
