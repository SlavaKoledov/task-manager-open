"""Add optional task start and end time fields."""

from typing import Sequence, Union

from alembic import op
import sqlalchemy as sa

# revision identifiers, used by Alembic.
revision: str = "20260416_0010"
down_revision: Union[str, None] = "20260415_0009"
branch_labels: Union[str, Sequence[str], None] = None
depends_on: Union[str, Sequence[str], None] = None


def upgrade() -> None:
    op.add_column("tasks", sa.Column("start_time", sa.String(length=5), nullable=True))
    op.add_column("tasks", sa.Column("end_time", sa.String(length=5), nullable=True))


def downgrade() -> None:
    op.drop_column("tasks", "end_time")
    op.drop_column("tasks", "start_time")
