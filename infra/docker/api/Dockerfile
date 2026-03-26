# syntax=docker/dockerfile:1.7

FROM python:3.12-slim

ENV PYTHONDONTWRITEBYTECODE=1
ENV PYTHONUNBUFFERED=1

WORKDIR /app

COPY apps/api/requirements.txt /tmp/requirements.txt
RUN --mount=type=cache,target=/root/.cache/pip \
    pip install --default-timeout=300 --retries 20 -r /tmp/requirements.txt

COPY apps/api /app/apps/api
COPY infra/docker/api/start.sh /app/infra/docker/api/start.sh

RUN chmod +x /app/infra/docker/api/start.sh

WORKDIR /app/apps/api

CMD ["/app/infra/docker/api/start.sh"]
