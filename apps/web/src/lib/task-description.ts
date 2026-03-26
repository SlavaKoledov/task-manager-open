import type { DescriptionBlock } from "@/lib/types";

export function createEmptyDescription(): DescriptionBlock[] {
  return [{ kind: "text", text: "" }];
}

export function normalizeDescriptionBlocks(blocks: DescriptionBlock[]): DescriptionBlock[] {
  if (blocks.length === 0) {
    return createEmptyDescription();
  }

  return blocks.flatMap((block) => {
    const normalizedLines = block.text.replace(/\r\n/g, "\n").replace(/\r/g, "\n").split("\n");

    return normalizedLines.map((line) =>
      block.kind === "checkbox"
        ? { kind: "checkbox" as const, text: line, checked: block.checked }
        : { kind: "text" as const, text: line },
    );
  });
}

export function stripDescriptionBlocks(blocks: DescriptionBlock[]): DescriptionBlock[] {
  const normalized = normalizeDescriptionBlocks(blocks);
  const trimmedEndIndex = [...normalized]
    .reverse()
    .findIndex((block) => block.text.trim().length > 0 || (block.kind === "checkbox" && block.checked));

  if (trimmedEndIndex === -1) {
    return [];
  }

  return normalized.slice(0, normalized.length - trimmedEndIndex);
}

export function descriptionBlocksToText(blocks: DescriptionBlock[]): string | null {
  const normalized = stripDescriptionBlocks(blocks);
  if (normalized.length === 0) {
    return null;
  }

  const serialized = normalized
    .map((block) =>
      block.kind === "checkbox" ? `- [${block.checked ? "x" : " "}] ${block.text}` : block.text,
    )
    .join("\n");

  return serialized.trim().length > 0 ? serialized : null;
}

export function descriptionTextToBlocks(description: string | null): DescriptionBlock[] {
  if (!description) {
    return createEmptyDescription();
  }

  const blocks = description.split(/\r?\n/).map((line) => {
    const checkboxMatch = line.match(/^\s*[-*]\s+\[( |x|X)\]\s*(.*)$/);
    if (checkboxMatch) {
      return {
        kind: "checkbox" as const,
        text: checkboxMatch[2],
        checked: checkboxMatch[1].toLowerCase() === "x",
      };
    }

    return { kind: "text" as const, text: line };
  });

  return blocks.length > 0 ? blocks : createEmptyDescription();
}

export function ensureDescriptionBlocks(blocks: DescriptionBlock[] | null | undefined, fallback?: string | null) {
  if (blocks && blocks.length > 0) {
    return normalizeDescriptionBlocks(blocks);
  }

  return descriptionTextToBlocks(fallback ?? null);
}

export function hasMeaningfulDescription(blocks: DescriptionBlock[] | null | undefined, fallback?: string | null): boolean {
  const normalizedBlocks = ensureDescriptionBlocks(blocks, fallback);
  return stripDescriptionBlocks(normalizedBlocks).some(
    (block) => block.text.trim().length > 0 || (block.kind === "checkbox" && block.checked),
  );
}
