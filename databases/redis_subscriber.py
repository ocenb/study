"""Подписчик. Запустите перед publisher; Ctrl+C завершает чтение."""

from redis_common import CHANNEL, create_client


def main() -> None:
    with create_client() as client, client.pubsub() as pubsub:
        pubsub.subscribe(CHANNEL)
        try:
            for event in pubsub.listen():
                if event["type"] == "subscribe":
                    print(f"Подписка на {CHANNEL} готова", flush=True)
                elif event["type"] == "message":
                    print(f"Получено: {event['data']}", flush=True)
        except KeyboardInterrupt:
            print("Подписчик остановлен")


if __name__ == "__main__":
    main()
