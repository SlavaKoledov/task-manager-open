import { THEME_STORAGE_KEY, toggleThemeValue } from "@/lib/theme";

describe("theme helpers", () => {
  it("toggles between light and dark", () => {
    expect(toggleThemeValue("light")).toBe("dark");
    expect(toggleThemeValue("dark")).toBe("light");
  });

  it("uses the shared storage key", () => {
    expect(THEME_STORAGE_KEY).toBe("task-manager.theme");
  });
});
