"""add task reminder time

Revision ID: 20260318_0007
Revises: 20260317_0006
Create Date: 2026-03-18 00:07:00.000000
"""

from alembic import op
import sqlalchemy as sa


revision = "20260318_0007"
down_revision = "20260317_0006"
branch_labels = None
depends_on = None


def upgrade() -> None:
    op.add_column("tasks", sa.Column("reminder_time", sa.String(length=5), nullable=True))


def downgrade() -> None:
    op.drop_column("tasks", "reminder_time")
