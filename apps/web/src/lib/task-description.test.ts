import {
  descriptionBlocksToText,
  descriptionTextToBlocks,
  ensureDescriptionBlocks,
  hasMeaningfulDescription,
} from "@/lib/task-description";

describe("task description helpers", () => {
  it("converts checkbox and text blocks into a legacy string", () => {
    expect(
      descriptionBlocksToText([
        { kind: "text", text: "Heading" },
        { kind: "checkbox", text: "Pack bag", checked: true },
      ]),
    ).toBe("Heading\n- [x] Pack bag");
  });

  it("parses legacy task text into structured blocks", () => {
    expect(descriptionTextToBlocks("- [ ] Book flight\nNotes")).toEqual([
      { kind: "checkbox", text: "Book flight", checked: false },
      { kind: "text", text: "Notes" },
    ]);
  });

  it("falls back to description text when the api response has no blocks", () => {
    expect(ensureDescriptionBlocks(undefined, "- [x] Done")).toEqual([
      { kind: "checkbox", text: "Done", checked: true },
    ]);
  });

  it("preserves empty descriptions as a single editable text block", () => {
    expect(descriptionTextToBlocks("")).toEqual([{ kind: "text", text: "" }]);
  });

  it("detects whether a description has meaningful content", () => {
    expect(hasMeaningfulDescription([{ kind: "text", text: "  " }])).toBe(false);
    expect(hasMeaningfulDescription([{ kind: "checkbox", text: "Review draft", checked: false }])).toBe(true);
  });
});
