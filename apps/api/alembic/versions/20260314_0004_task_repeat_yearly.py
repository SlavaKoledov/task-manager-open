"""Extend task repeat constraint with yearly."""

from typing import Sequence, Union

from alembic import op
import sqlalchemy as sa

# revision identifiers, used by Alembic.
revision: str = "20260314_0004"
down_revision: Union[str, None] = "20260314_0003"
branch_labels: Union[str, Sequence[str], None] = None
depends_on: Union[str, Sequence[str], None] = None


TASK_REPEAT_CONSTRAINT_NAME = "task_repeat"
UPGRADE_REPEAT_CHECK = "repeat IN ('none', 'daily', 'weekly', 'monthly', 'yearly')"
DOWNGRADE_REPEAT_CHECK = "repeat IN ('none', 'daily', 'weekly', 'monthly')"


def upgrade() -> None:
    op.drop_constraint(TASK_REPEAT_CONSTRAINT_NAME, "tasks", type_="check")
    op.create_check_constraint(TASK_REPEAT_CONSTRAINT_NAME, "tasks", UPGRADE_REPEAT_CHECK)


def downgrade() -> None:
    op.execute(sa.text("UPDATE tasks SET repeat = 'none' WHERE repeat = 'yearly'"))
    op.drop_constraint(TASK_REPEAT_CONSTRAINT_NAME, "tasks", type_="check")
    op.create_check_constraint(TASK_REPEAT_CONSTRAINT_NAME, "tasks", DOWNGRADE_REPEAT_CHECK)
