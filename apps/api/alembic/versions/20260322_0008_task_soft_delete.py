"""Add soft-delete tombstone support for tasks."""

from typing import Sequence, Union

from alembic import op
import sqlalchemy as sa

# revision identifiers, used by Alembic.
revision: str = "20260322_0008"
down_revision: Union[str, None] = "20260318_0007"
branch_labels: Union[str, Sequence[str], None] = None
depends_on: Union[str, Sequence[str], None] = None


def upgrade() -> None:
    op.add_column("tasks", sa.Column("deleted_at", sa.DateTime(timezone=True), nullable=True))
    op.create_index(op.f("ix_tasks_deleted_at"), "tasks", ["deleted_at"], unique=False)


def downgrade() -> None:
    op.drop_index(op.f("ix_tasks_deleted_at"), table_name="tasks")
    op.drop_column("tasks", "deleted_at")
