"""Создание заказов, индексов и демонстрация аналитики MongoDB."""

import os
import random
from datetime import datetime, timedelta, timezone
from pprint import pprint

from pymongo import MongoClient

from mongo_orders import OrdersRepository

MONGO_URI = os.getenv("MONGO_URI", "mongodb://localhost:27017/")
DB_NAME = "shop_db"
COLLECTION_NAME = "orders"
SAMPLE_SIZE = 100


def generate_orders(count: int = SAMPLE_SIZE, *, seed: int = 42, now: datetime | None = None) -> list[dict]:
    """Воспроизводимые примеры с адресами, товарами и вложенными характеристиками."""
    if count < 0:
        raise ValueError("Количество заказов не может быть отрицательным")
    rng = random.Random(seed)
    now = now or datetime.now(timezone.utc)
    catalog = [
        ("BOOK-01", "Python", "книги", 150000),
        ("TECH-01", "Наушники", "электроника", 500000),
        ("SPORT-01", "Мяч", "спорт", 200000),
    ]
    statuses = sorted(OrdersRepository.STATUSES)
    orders = []
    for i in range(1, count + 1):
        customer_id = (i - 1) % 20 + 1
        items = []
        for sku, title, category, price in rng.sample(catalog, k=rng.randint(1, 3)):
            items.append({
                "product": {"sku": sku, "title": title, "category": category,
                            "attributes": {"brand": "Учебный магазин", "warranty_months": 12}},
                "quantity": rng.randint(1, 3),
                "unit_price_kopecks": price,
            })
        status = statuses[(i - 1) % len(statuses)]
        orders.append({
            "order_number": f"ORD-{i:05d}",
            "customer": {
                "full_name": f"Покупатель {customer_id}",
                "email": f"user{customer_id}@example.com",
                "address": {"city": "Москва" if customer_id % 2 else "Екатеринбург",
                            "street": f"Учебная, {customer_id}", "postal_code": "620000"},
            },
            "items": items,
            "items_count": sum(item["quantity"] for item in items),
            "total_amount_kopecks": sum(item["quantity"] * item["unit_price_kopecks"] for item in items),
            "status": status,
            "is_paid": status in OrdersRepository.PAID_STATUSES,
            "created_at": now - timedelta(days=rng.randint(0, 30), hours=rng.randint(0, 23)),
            "metadata": {"client_device": rng.choice(["mobile", "desktop", "tablet"]),
                         "delivery": {"method": "courier", "estimated_days": rng.randint(1, 7)}},
        })
    return orders


def main() -> None:
    now = datetime.now(timezone.utc)
    with MongoClient(MONGO_URI, serverSelectionTimeoutMS=5000) as client:
        client.admin.command("ping")
        orders = OrdersRepository(client[DB_NAME][COLLECTION_NAME])
        print("Индексы:", orders.create_indexes())
        print("Записано примеров:", orders.seed(generate_orders(now=now)))
        print("Пример заказа:")
        pprint(orders.get_order("ORD-00001"), sort_dicts=False)
        print("Последние заказы покупателя:")
        pprint(orders.customer_orders("user1@example.com", limit=3))
        print("Обновление статуса:", orders.update_status("ORD-00001", "delivered"))
        start, end = now - timedelta(days=31), now + timedelta(seconds=1)
        print("Аналитика (суммы в копейках):")
        pprint(orders.analytics(start, end, "Москва"))
        print("Планы выполнения — ищите IXSCAN и indexName:")
        pprint(orders.explain_analytics(start, end, "Москва"))


if __name__ == "__main__":
    main()
