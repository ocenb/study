"""Обработчик очереди. Результаты хранятся час; Ctrl+C завершает работу."""

import json

from redis_common import FAILED_QUEUE, create_client
from redis_tasks import TaskQueue, process_task

RESULT_TTL = 3600


def main() -> None:
    with create_client() as client:
        queue = TaskQueue(client)
        print("Обработчик ожидает задачи", flush=True)
        try:
            while True:
                raw = queue.dequeue(timeout=5)
                if raw is None:
                    continue
                try:
                    result = process_task(raw)
                except (ValueError, TypeError) as error:
                    client.lpush(FAILED_QUEUE, json.dumps({"task": raw, "error": str(error)}, ensure_ascii=False))
                    print(f"Некорректная задача: {error}", flush=True)
                    continue
                key = f"study:result:{result['task_id']}"
                client.set(key, json.dumps(result), ex=RESULT_TTL)
                print(f"Готово: {result}; ключ: {key}", flush=True)
        except KeyboardInterrupt:
            print("Обработчик остановлен")


if __name__ == "__main__":
    main()
