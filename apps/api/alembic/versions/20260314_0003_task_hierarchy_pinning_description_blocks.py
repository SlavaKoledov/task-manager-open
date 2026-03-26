"""Add pinning, subtasks, ordering, and structured description blocks."""

from __future__ import annotations

import json
import re
from typing import Sequence, Union

from alembic import op
import sqlalchemy as sa

# revision identifiers, used by Alembic.
revision: str = "20260314_0003"
down_revision: Union[str, None] = "20260314_0002"
branch_labels: Union[str, Sequence[str], None] = None
depends_on: Union[str, Sequence[str], None] = None


CHECKBOX_LINE_PATTERN = re.compile(r"^\s*[-*]\s+\[( |x|X)\]\s*(.*)$")


def build_description_blocks(description: str | None) -> list[dict[str, object]]:
    if not description:
        return []

    blocks: list[dict[str, object]] = []
    for line in description.replace("\r\n", "\n").replace("\r", "\n").split("\n"):
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


def upgrade() -> None:
    op.add_column("tasks", sa.Column("description_blocks", sa.JSON(), nullable=False, server_default=sa.text("'[]'")))
    op.add_column("tasks", sa.Column("is_pinned", sa.Boolean(), nullable=False, server_default=sa.text("false")))
    op.add_column("tasks", sa.Column("parent_id", sa.Integer(), nullable=True))
    op.add_column("tasks", sa.Column("position", sa.Integer(), nullable=False, server_default=sa.text("0")))
    op.create_index(op.f("ix_tasks_parent_id"), "tasks", ["parent_id"], unique=False)
    op.create_foreign_key(
        "fk_tasks_parent_id_tasks",
        "tasks",
        "tasks",
        ["parent_id"],
        ["id"],
        ondelete="CASCADE",
    )

    connection = op.get_bind()
    rows = connection.execute(sa.text("SELECT id, description FROM tasks")).mappings().all()

    for row in rows:
        connection.execute(
            sa.text("UPDATE tasks SET description_blocks = :description_blocks WHERE id = :task_id"),
            {
                "task_id": row["id"],
                "description_blocks": json.dumps(build_description_blocks(row["description"])),
            },
        )


def downgrade() -> None:
    op.drop_constraint("fk_tasks_parent_id_tasks", "tasks", type_="foreignkey")
    op.drop_index(op.f("ix_tasks_parent_id"), table_name="tasks")
    op.drop_column("tasks", "position")
    op.drop_column("tasks", "parent_id")
    op.drop_column("tasks", "is_pinned")
    op.drop_column("tasks", "description_blocks")
