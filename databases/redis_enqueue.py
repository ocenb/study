"""Постановка задачи: python redis_enqueue.py 10 20 30."""

import argparse

from redis_common import create_client
from redis_tasks import TaskQueue


def main() -> None:
    parser = argparse.ArgumentParser(description="Добавить задачу суммирования в очередь")
    parser.add_argument("numbers", nargs="+", type=int)
    args = parser.parse_args()
    with create_client() as client:
        task_id = TaskQueue(client).enqueue_sum(args.numbers)
        print(f"Задача поставлена в очередь: {task_id}")


if __name__ == "__main__":
    main()
