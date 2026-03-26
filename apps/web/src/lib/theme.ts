import type { Theme } from "@/lib/types";

export const THEME_STORAGE_KEY = "task-manager.theme";

export function getStoredTheme(): Theme {
  if (typeof window === "undefined") {
    return "light";
  }

  const storedTheme = window.localStorage.getItem(THEME_STORAGE_KEY);
  return storedTheme === "dark" ? "dark" : "light";
}

export function applyTheme(theme: Theme) {
  if (typeof document === "undefined") {
    return;
  }

  document.documentElement.dataset.theme = theme;
}

export function persistTheme(theme: Theme) {
  if (typeof window === "undefined") {
    return;
  }

  window.localStorage.setItem(THEME_STORAGE_KEY, theme);
}

export function toggleThemeValue(theme: Theme): Theme {
  return theme === "light" ? "dark" : "light";
}
