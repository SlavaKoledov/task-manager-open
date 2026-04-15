"""Add custom repeat configuration support."""

from typing import Sequence, Union

from alembic import op
import sqlalchemy as sa

# revision identifiers, used by Alembic.
revision: str = "20260415_0009"
down_revision: Union[str, None] = "20260322_0008"
branch_labels: Union[str, Sequence[str], None] = None
depends_on: Union[str, Sequence[str], None] = None


TASK_REPEAT_CONSTRAINT_NAME = "task_repeat"
UPGRADE_REPEAT_CHECK = "repeat IN ('none', 'daily', 'weekly', 'monthly', 'yearly', 'custom')"
DOWNGRADE_REPEAT_CHECK = "repeat IN ('none', 'daily', 'weekly', 'monthly', 'yearly')"


def upgrade() -> None:
    op.add_column("tasks", sa.Column("repeat_config", sa.JSON(), nullable=True))
    op.drop_constraint(TASK_REPEAT_CONSTRAINT_NAME, "tasks", type_="check")
    op.create_check_constraint(TASK_REPEAT_CONSTRAINT_NAME, "tasks", UPGRADE_REPEAT_CHECK)


def downgrade() -> None:
    op.execute(sa.text("UPDATE tasks SET repeat = 'none', repeat_config = NULL WHERE repeat = 'custom'"))
    op.drop_constraint(TASK_REPEAT_CONSTRAINT_NAME, "tasks", type_="check")
    op.create_check_constraint(TASK_REPEAT_CONSTRAINT_NAME, "tasks", DOWNGRADE_REPEAT_CHECK)
    op.drop_column("tasks", "repeat_config")
