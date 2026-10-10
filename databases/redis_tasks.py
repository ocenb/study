"""FIFO-очередь задач на Redis List: LPUSH + BRPOP."""

import json
from datetime import datetime, timezone
from uuid import uuid4

from redis import Redis

from redis_common import QUEUE


class TaskQueue:
    def __init__(self, client: Redis):
        self.client = client

    def enqueue_sum(self, numbers: list[int]) -> str:
        if not numbers or any(type(number) is not int for number in numbers):
            raise ValueError("Нужен непустой список целых чисел")
        task_id = str(uuid4())
        task = {
            "id": task_id,
            "type": "sum",
            "payload": {"numbers": numbers},
            "created_at": datetime.now(timezone.utc).isoformat(),
        }
        self.client.lpush(QUEUE, json.dumps(task))
        return task_id

    def dequeue(self, timeout: int = 5) -> str | None:
        item = self.client.brpop(QUEUE, timeout=timeout)
        return item[1] if item is not None else None


def process_task(raw: str) -> dict:
    task = json.loads(raw)
    if not isinstance(task, dict) or task.get("type") != "sum":
        raise ValueError("Неизвестный тип задачи")
    if not isinstance(task.get("id"), str) or not task["id"]:
        raise ValueError("Отсутствует идентификатор задачи")
    payload = task.get("payload")
    numbers = payload.get("numbers") if isinstance(payload, dict) else None
    if not isinstance(numbers, list) or not numbers or any(type(n) is not int for n in numbers):
        raise ValueError("payload.numbers должен быть непустым списком целых чисел")
    return {"task_id": task["id"], "result": sum(numbers)}
