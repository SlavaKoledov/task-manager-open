"""add task client request id

Revision ID: 20260317_0006
Revises: 20260314_0005
Create Date: 2026-03-17 00:06:00.000000
"""

from alembic import op
import sqlalchemy as sa


revision = "20260317_0006"
down_revision = "20260314_0005"
branch_labels = None
depends_on = None


def upgrade() -> None:
    op.add_column("tasks", sa.Column("client_request_id", sa.String(length=64), nullable=True))
    op.create_index(op.f("ix_tasks_client_request_id"), "tasks", ["client_request_id"], unique=True)


def downgrade() -> None:
    op.drop_index(op.f("ix_tasks_client_request_id"), table_name="tasks")
    op.drop_column("tasks", "client_request_id")
