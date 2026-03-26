"""Initial schema for lists and tasks."""

from typing import Sequence, Union

from alembic import op
import sqlalchemy as sa

# revision identifiers, used by Alembic.
revision: str = "20260313_0001"
down_revision: Union[str, None] = None
branch_labels: Union[str, Sequence[str], None] = None
depends_on: Union[str, Sequence[str], None] = None


def upgrade() -> None:
    op.create_table(
        "lists",
        sa.Column("id", sa.Integer(), primary_key=True),
        sa.Column("name", sa.String(length=120), nullable=False),
        sa.Column("color", sa.String(length=7), nullable=False),
        sa.Column("position", sa.Integer(), nullable=False),
        sa.Column("created_at", sa.DateTime(timezone=True), nullable=False, server_default=sa.text("CURRENT_TIMESTAMP")),
        sa.Column("updated_at", sa.DateTime(timezone=True), nullable=False, server_default=sa.text("CURRENT_TIMESTAMP")),
        sa.UniqueConstraint("name"),
    )
    op.create_index(op.f("ix_lists_id"), "lists", ["id"], unique=False)

    op.create_table(
        "tasks",
        sa.Column("id", sa.Integer(), primary_key=True),
        sa.Column("title", sa.String(length=200), nullable=False),
        sa.Column("description", sa.Text(), nullable=True),
        sa.Column("due_date", sa.Date(), nullable=True),
        sa.Column("is_done", sa.Boolean(), nullable=False, server_default=sa.text("false")),
        sa.Column("list_id", sa.Integer(), nullable=True),
        sa.Column("created_at", sa.DateTime(timezone=True), nullable=False, server_default=sa.text("CURRENT_TIMESTAMP")),
        sa.Column("updated_at", sa.DateTime(timezone=True), nullable=False, server_default=sa.text("CURRENT_TIMESTAMP")),
        sa.ForeignKeyConstraint(["list_id"], ["lists.id"], ondelete="SET NULL"),
    )
    op.create_index(op.f("ix_tasks_due_date"), "tasks", ["due_date"], unique=False)
    op.create_index(op.f("ix_tasks_id"), "tasks", ["id"], unique=False)
    op.create_index(op.f("ix_tasks_list_id"), "tasks", ["list_id"], unique=False)


def downgrade() -> None:
    op.drop_index(op.f("ix_tasks_list_id"), table_name="tasks")
    op.drop_index(op.f("ix_tasks_id"), table_name="tasks")
    op.drop_index(op.f("ix_tasks_due_date"), table_name="tasks")
    op.drop_table("tasks")
    op.drop_index(op.f("ix_lists_id"), table_name="lists")
    op.drop_table("lists")

