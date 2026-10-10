"""Общее подключение для примеров redis-py."""

import os

from redis import Redis

CHANNEL = "study:events"
QUEUE = "study:tasks"
FAILED_QUEUE = "study:tasks:failed"


def create_client() -> Redis:
    return Redis.from_url(
        os.getenv("REDIS_URL", "redis://localhost:6379/0"),
        decode_responses=True,
        socket_connect_timeout=5,
    )
