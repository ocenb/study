"""Публикация сообщения: python redis_publisher.py 'Привет!'"""

import argparse
import json
from datetime import datetime, timezone

from redis_common import CHANNEL, create_client


def main() -> None:
    parser = argparse.ArgumentParser(description="Публикация события Redis")
    parser.add_argument("message", nargs="?", default="Заказ создан")
    args = parser.parse_args()
    event = {"message": args.message, "published_at": datetime.now(timezone.utc).isoformat()}
    with create_client() as client:
        subscribers = client.publish(CHANNEL, json.dumps(event, ensure_ascii=False))
        print(f"Опубликовано в {CHANNEL}; получателей: {subscribers}")


if __name__ == "__main__":
    main()
