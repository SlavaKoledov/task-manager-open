from __future__ import annotations

from calendar import monthrange
from datetime import date, timedelta

from app.models.task import TaskCustomRepeatUnit, TaskRepeat
from app.schemas.tasks import TaskCustomRepeatConfig


def parse_repeat_config(raw_config: object | None) -> TaskCustomRepeatConfig | None:
    if raw_config is None:
        return None
    if isinstance(raw_config, TaskCustomRepeatConfig):
        return raw_config
    return TaskCustomRepeatConfig.model_validate(raw_config)


def resolve_next_due_date(
    repeat: TaskRepeat,
    due_date: date,
    repeat_config: TaskCustomRepeatConfig | None = None,
) -> date:
    if repeat == TaskRepeat.DAILY:
        return due_date + timedelta(days=1)

    if repeat == TaskRepeat.WEEKLY:
        return due_date + timedelta(days=7)

    if repeat == TaskRepeat.MONTHLY:
        return _resolve_next_monthly_due_date(due_date, due_date.day, 1)

    if repeat == TaskRepeat.YEARLY:
        return _resolve_safe_date(due_date.year + 1, due_date.month, due_date.day)

    if repeat == TaskRepeat.CUSTOM:
        config = repeat_config or TaskCustomRepeatConfig(unit=TaskCustomRepeatUnit.DAY)
        return resolve_next_custom_due_date(due_date, config)

    return due_date


def resolve_next_custom_due_date(current_date: date, config: TaskCustomRepeatConfig) -> date:
    if config.unit == TaskCustomRepeatUnit.DAY:
        next_date = current_date + timedelta(days=config.interval)
        return _shift_forward_past_weekend(next_date) if config.skip_weekends else next_date

    if config.unit == TaskCustomRepeatUnit.WEEK:
        current_weekday = current_date.isoweekday()
        later_weekdays = [weekday for weekday in config.weekdays if weekday > current_weekday]
        if later_weekdays:
            return current_date + timedelta(days=later_weekdays[0] - current_weekday)

        next_week_start = current_date - timedelta(days=current_weekday - 1) + timedelta(weeks=config.interval)
        return next_week_start + timedelta(days=config.weekdays[0] - 1)

    if config.unit == TaskCustomRepeatUnit.MONTH:
        next_date = _resolve_next_monthly_due_date(current_date, config.month_day or current_date.day, config.interval)
        return _shift_forward_past_weekend(next_date) if config.skip_weekends else next_date

    return _resolve_safe_date(
        current_date.year + config.interval,
        config.month or current_date.month,
        config.day or current_date.day,
    )


def _resolve_next_monthly_due_date(current_date: date, day_of_month: int, interval_months: int) -> date:
    next_month_index = (current_date.month - 1) + interval_months
    next_year = current_date.year + (next_month_index // 12)
    next_month = (next_month_index % 12) + 1
    return _resolve_safe_date(next_year, next_month, day_of_month)


def _resolve_safe_date(year: int, month: int, day: int) -> date:
    return date(year, month, min(day, monthrange(year, month)[1]))


def _shift_forward_past_weekend(target_date: date) -> date:
    if target_date.weekday() == 5:
        return target_date + timedelta(days=2)
    if target_date.weekday() == 6:
        return target_date + timedelta(days=1)
    return target_date
