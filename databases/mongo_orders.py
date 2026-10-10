"""Операции и индексируемая аналитика коллекции orders."""

from datetime import datetime

from pymongo import ASCENDING, DESCENDING, IndexModel, ReplaceOne
from pymongo.collection import Collection


class OrdersRepository:
    """Хранение заказов магазина; суммы в документах выражены в копейках."""

    STATUSES = {"new", "processing", "shipped", "delivered", "cancelled"}
    PAID_STATUSES = {"processing", "shipped", "delivered"}

    def __init__(self, collection: Collection):
        self.collection = collection

    def create_indexes(self) -> list[str]:
        return self.collection.create_indexes([
            IndexModel([("order_number", ASCENDING)], unique=True, name="order_number_unique"),
            IndexModel([("customer.email", ASCENDING), ("created_at", DESCENDING)], name="customer_date"),
            IndexModel([("status", ASCENDING), ("created_at", ASCENDING)], name="status_date"),
            IndexModel([("is_paid", ASCENDING), ("created_at", ASCENDING)], name="paid_date"),
            IndexModel([("customer.address.city", ASCENDING), ("created_at", ASCENDING)], name="city_date"),
        ])

    def seed(self, orders: list[dict]) -> int:
        """Повторный запуск обновляет примеры по номеру, сохраняя остальные заказы."""
        if not orders:
            return 0
        self.collection.bulk_write([
            ReplaceOne({"order_number": order["order_number"]}, order, upsert=True)
            for order in orders
        ])
        return len(orders)

    def get_order(self, order_number: str) -> dict | None:
        return self.collection.find_one({"order_number": order_number})

    def customer_orders(self, email: str, limit: int = 10) -> list[dict]:
        if limit < 1:
            raise ValueError("limit должен быть положительным")
        return list(self.collection.find({"customer.email": email}).sort("created_at", DESCENDING).limit(limit))

    def update_status(self, order_number: str, status: str) -> bool:
        """Одновременно обновляет статус и признак оплаты демонстрационного заказа."""
        if status not in self.STATUSES:
            raise ValueError(f"Неизвестный статус: {status}")
        result = self.collection.update_one(
            {"order_number": order_number},
            {"$set": {"status": status, "is_paid": status in self.PAID_STATUSES}},
        )
        return result.matched_count == 1

    @staticmethod
    def _period(start: datetime, end: datetime) -> dict:
        if start >= end:
            raise ValueError("Начало периода должно предшествовать концу")
        return {"$gte": start, "$lt": end}

    def analytics_queries(self, start: datetime, end: datetime, city: str) -> dict:
        """Каждый pipeline начинается с $match по префиксу составного индекса."""
        period = self._period(start, end)
        return {
            "daily_revenue": ("status_date", [
                {"$match": {"status": "delivered", "created_at": period}},
                {"$group": {
                    "_id": {"$dateToString": {"format": "%Y-%m-%d", "date": "$created_at", "timezone": "UTC"}},
                    "orders": {"$sum": 1},
                    "revenue_kopecks": {"$sum": "$total_amount_kopecks"},
                    "average_order_kopecks": {"$avg": "$total_amount_kopecks"},
                }},
                {"$sort": {"_id": 1}},
            ]),
            "category_sales": ("paid_date", [
                {"$match": {"is_paid": True, "created_at": period}},
                {"$unwind": "$items"},
                {"$group": {
                    "_id": "$items.product.category",
                    "units": {"$sum": "$items.quantity"},
                    "revenue_kopecks": {"$sum": {"$multiply": ["$items.unit_price_kopecks", "$items.quantity"]}},
                }},
                {"$sort": {"revenue_kopecks": -1, "_id": 1}},
            ]),
            "city_customers": ("city_date", [
                {"$match": {"customer.address.city": city, "created_at": period, "is_paid": True}},
                {"$group": {
                    "_id": "$customer.email",
                    "orders": {"$sum": 1},
                    "spent_kopecks": {"$sum": "$total_amount_kopecks"},
                }},
                {"$sort": {"spent_kopecks": -1, "_id": 1}},
                {"$limit": 10},
            ]),
        }

    def analytics(self, start: datetime, end: datetime, city: str) -> dict:
        return {
            name: list(self.collection.aggregate(pipeline, hint=index))
            for name, (index, pipeline) in self.analytics_queries(start, end, city).items()
        }

    def explain_analytics(self, start: datetime, end: datetime, city: str) -> dict:
        """executionStats показывает IXSCAN, имя индекса и число прочитанных записей."""
        return {
            name: self.collection.database.command(
                "explain",
                {"aggregate": self.collection.name, "pipeline": pipeline, "cursor": {}, "hint": index},
                verbosity="executionStats",
            )
            for name, (index, pipeline) in self.analytics_queries(start, end, city).items()
        }
