"""Кеш JSON-значений с TTL и загрузкой при промахе."""

import json
import time
from collections.abc import Callable
from typing import Any

from redis import Redis

from redis_common import create_client


class RedisCache:
    def __init__(self, client: Redis, prefix: str = "study:cache:"):
        self.client = client
        self.prefix = prefix

    def get_or_load(self, key: str, loader: Callable[[], Any], ttl: int = 60) -> Any:
        if ttl <= 0:
            raise ValueError("TTL должен быть положительным числом секунд")
        cache_key = self.prefix + key
        cached = self.client.get(cache_key)
        if cached is not None:
            return json.loads(cached)
        value = loader()
        # SET с EX атомарно сохраняет значение и назначает срок жизни.
        self.client.set(cache_key, json.dumps(value, ensure_ascii=False), ex=ttl)
        return value

    def invalidate(self, key: str) -> bool:
        return bool(self.client.delete(self.prefix + key))


def main() -> None:
    with create_client() as client:
        cache = RedisCache(client)
        key = "demo:product:1"
        cache.invalidate(key)

        def load_product() -> dict:
            print("Загрузка товара из источника")
            return {"id": 1, "name": "Книга Python", "price_kopecks": 150000}

        print("Промах:", cache.get_or_load(key, load_product, ttl=2))
        print("Попадание:", cache.get_or_load(key, load_product, ttl=2))
        print("Осталось секунд:", client.ttl(cache.prefix + key))
        time.sleep(2.1)
        print("После истечения TTL:", cache.get_or_load(key, load_product, ttl=2))


if __name__ == "__main__":
    main()
