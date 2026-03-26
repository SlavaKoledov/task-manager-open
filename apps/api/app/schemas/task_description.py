from __future__ import annotations

import re
from typing import Annotated, Any, Literal

from pydantic import BaseModel, Field

CHECKBOX_LINE_PATTERN = re.compile(r"^\s*[-*]\s+\[( |x|X)\]\s*(.*)$")


class DescriptionTextBlock(BaseModel):
    kind: Literal["text"]
    text: str = ""


class DescriptionCheckboxBlock(BaseModel):
    kind: Literal["checkbox"]
    text: str = ""
    checked: bool = False


TaskDescriptionBlock = Annotated[
    DescriptionTextBlock | DescriptionCheckboxBlock,
    Field(discriminator="kind"),
]


def parse_legacy_description(description: str | None) -> list[dict[str, Any]]:
    if not description:
        return []

    lines = description.replace("\r\n", "\n").replace("\r", "\n").split("\n")
    blocks: list[dict[str, Any]] = []

    for line in lines:
        match = CHECKBOX_LINE_PATTERN.match(line)
        if match:
            blocks.append(
                {
                    "kind": "checkbox",
                    "text": match.group(2),
                    "checked": match.group(1).lower() == "x",
                }
            )
            continue

        blocks.append({"kind": "text", "text": line})

    return blocks


def normalize_description_blocks(
    blocks: list[TaskDescriptionBlock] | list[dict[str, Any]] | None,
) -> list[dict[str, Any]]:
    if not blocks:
        return []

    normalized: list[dict[str, Any]] = []

    for block in blocks:
        data = block.model_dump() if isinstance(block, BaseModel) else dict(block)
        kind = "checkbox" if data.get("kind") == "checkbox" else "text"
        text = str(data.get("text", "")).replace("\r\n", "\n").replace("\r", "\n")
        lines = text.split("\n")

        for line in lines:
            if kind == "checkbox":
                normalized.append(
                    {
                        "kind": "checkbox",
                        "text": line,
                        "checked": bool(data.get("checked", False)),
                    }
                )
                continue

            normalized.append({"kind": "text", "text": line})

    return normalized


def serialize_description_blocks(blocks: list[dict[str, Any]] | None) -> str | None:
    if not blocks:
        return None

    lines: list[str] = []

    for block in blocks:
        if block.get("kind") == "checkbox":
            marker = "x" if bool(block.get("checked", False)) else " "
            lines.append(f"- [{marker}] {str(block.get('text', ''))}")
            continue

        lines.append(str(block.get("text", "")))

    serialized = "\n".join(lines)
    return serialized if serialized.strip() else None
