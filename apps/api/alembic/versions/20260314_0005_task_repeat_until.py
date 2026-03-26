"""Add repeat-until date for recurring tasks."""

from typing import Sequence, Union

from alembic import op
import sqlalchemy as sa

# revision identifiers, used by Alembic.
revision: str = "20260314_0005"
down_revision: Union[str, None] = "20260314_0004"
branch_labels: Union[str, Sequence[str], None] = None
depends_on: Union[str, Sequence[str], None] = None


def upgrade() -> None:
    op.add_column("tasks", sa.Column("repeat_until", sa.Date(), nullable=True))


def downgrade() -> None:
    op.drop_column("tasks", "repeat_until")
