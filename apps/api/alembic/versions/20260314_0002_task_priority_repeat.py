"""Add task priority and repeat columns."""

from typing import Sequence, Union

from alembic import op
import sqlalchemy as sa

# revision identifiers, used by Alembic.
revision: str = "20260314_0002"
down_revision: Union[str, None] = "20260313_0001"
branch_labels: Union[str, Sequence[str], None] = None
depends_on: Union[str, Sequence[str], None] = None


task_priority_enum = sa.Enum(
    "urgent_important",
    "not_urgent_important",
    "urgent_unimportant",
    "not_urgent_unimportant",
    name="task_priority",
    native_enum=False,
    create_constraint=True,
)

task_repeat_enum = sa.Enum(
    "none",
    "daily",
    "weekly",
    "monthly",
    name="task_repeat",
    native_enum=False,
    create_constraint=True,
)


def upgrade() -> None:
    op.add_column(
        "tasks",
        sa.Column(
            "priority",
            task_priority_enum,
            nullable=False,
            server_default="not_urgent_unimportant",
        ),
    )
    op.add_column(
        "tasks",
        sa.Column(
            "repeat",
            task_repeat_enum,
            nullable=False,
            server_default="none",
        ),
    )


def downgrade() -> None:
    op.drop_column("tasks", "repeat")
    op.drop_column("tasks", "priority")
