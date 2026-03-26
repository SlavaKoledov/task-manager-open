import { buildTaskDraft } from "@/lib/task-draft";

describe("buildTaskDraft", () => {
  it("prefills the due date for the Today view", () => {
    expect(buildTaskDraft("today", "2026-03-13", "2026-03-14")).toEqual({
      title: "",
      description_blocks: [{ kind: "text", text: "" }],
      due_date: "2026-03-13",
      reminder_time: "",
      repeat_until: "",
      is_done: false,
      is_pinned: false,
      priority: "not_urgent_unimportant",
      repeat: "none",
      list_id: "",
    });
  });

  it("prefills the due date for the Tomorrow view", () => {
    expect(buildTaskDraft("tomorrow", "2026-03-13", "2026-03-14").due_date).toBe("2026-03-14");
  });

  it("prefills the list for a list-scoped view", () => {
    expect(buildTaskDraft("list", "2026-03-13", "2026-03-14", 7).list_id).toBe("7");
  });

  it("allows overriding quick-create context for grouped entry points", () => {
    expect(
      buildTaskDraft("all", "2026-03-13", "2026-03-14", null, {
        due_date: "2026-03-15",
        list_id: 9,
        priority: "urgent_important",
      }),
    ).toEqual({
      title: "",
      description_blocks: [{ kind: "text", text: "" }],
      due_date: "2026-03-15",
      reminder_time: "",
      repeat_until: "",
      is_done: false,
      is_pinned: false,
      priority: "urgent_important",
      repeat: "none",
      list_id: "9",
    });
  });
});
