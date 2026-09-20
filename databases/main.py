"""MongoDB data generation and inspection script using PyMongo."""

from datetime import datetime, timedelta, timezone
import random
import sys
from pprint import pprint

# Ensure UTF-8 output encoding in Windows terminal environments
if sys.stdout.encoding.lower() != "utf-8":
    sys.stdout.reconfigure(encoding="utf-8")
if sys.stderr.encoding.lower() != "utf-8":
    sys.stderr.reconfigure(encoding="utf-8")

from pymongo import MongoClient
from pymongo.errors import ConnectionFailure


MONGO_URI = "mongodb://localhost:27017/"
DB_NAME = "shop_db"
COLLECTION_NAME = "orders"
SAMPLE_SIZE = 100


def generate_orders(count: int = 100) -> list[dict]:
    """Generate sample order documents with strings, numbers, dates, booleans, and nested structures."""
    first_names = [
        "Александр", "Дмитрий", "Максим", "Сергей", "Андрей",
        "Анна", "Мария", "Елена", "Ольга", "Екатерина"
    ]
    last_names = [
        "Иванов", "Смирнов", "Кузнецов", "Попов", "Васильев",
        "Петров", "Соколов", "Михайлов", "Новиков", "Федоров"
    ]
    statuses = ["new", "processing", "shipped", "delivered", "cancelled"]
    categories = ["электроника", "одежда", "книги", "спорт", "товары для дома"]
    devices = ["mobile", "desktop", "tablet"]

    base_time = datetime.now(timezone.utc)
    orders = []

    for i in range(1, count + 1):
        first = random.choice(first_names)
        last = random.choice(last_names)
        created_at = base_time - timedelta(
            days=random.randint(0, 30),
            hours=random.randint(0, 23),
            minutes=random.randint(0, 59),
        )
        status = random.choice(statuses)
        items_count = random.randint(1, 10)
        total_amount = round(random.uniform(500.0, 50000.0), 2)
        discount_percent = random.choice([0, 5, 10, 15, 20])
        is_paid = status in {"processing", "shipped", "delivered"}

        order = {
            "order_number": f"ORD-{i:05d}",
            "customer": {
                "full_name": f"{first} {last}",
                "email": f"user{i}@example.com",
            },
            "status": status,
            "items_count": items_count,
            "total_amount": total_amount,
            "discount_percent": discount_percent,
            "is_paid": is_paid,
            "tags": random.sample(categories, k=random.randint(1, 3)),
            "created_at": created_at,
            "metadata": {
                "client_device": random.choice(devices),
                "delivery_days": random.randint(1, 7),
            },
        }
        orders.append(order)

    return orders


def main() -> None:
    print(f"Connecting to MongoDB at {MONGO_URI}...")
    client: MongoClient = MongoClient(MONGO_URI, serverSelectionTimeoutMS=5000)

    try:
        # Check connection
        client.admin.command("ping")
        print("Successfully connected to MongoDB!\n")
    except ConnectionFailure as err:
        print(f"Failed to connect to MongoDB: {err}")
        return

    db = client[DB_NAME]
    collection = db[COLLECTION_NAME]

    # Clean collection for a fresh run
    collection.drop()
    print(f"Collection '{COLLECTION_NAME}' in database '{DB_NAME}' cleared/created.")

    # Generate and insert data
    sample_data = generate_orders(SAMPLE_SIZE)
    insert_result = collection.insert_many(sample_data)
    print(f"Inserted {len(insert_result.inserted_ids)} documents into '{COLLECTION_NAME}'.\n")

    # --- Print collection metadata ---
    total_count = collection.count_documents({})
    one_document = collection.find_one()

    print("=" * 60)
    print("МЕТАДАННЫЕ КОЛЛЕКЦИИ")
    print("=" * 60)
    print(f"База данных: {db.name}")
    print(f"Коллекция: {collection.name}")
    print(f"Общее количество записей: {total_count}")
    print("-" * 60)
    print("ПРИМЕР ОДНОЙ ЗАПИСИ В БД:")
    print("-" * 60)
    pprint(one_document, indent=2, sort_dicts=False)
    print("=" * 60)

    client.close()


if __name__ == "__main__":
    main()
