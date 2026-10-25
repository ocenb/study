"""Проверка MongoDB: доступ с паролем, отказ без пароля и при неверном пароле."""

import os

from pymongo import MongoClient
from pymongo.errors import OperationFailure


def main() -> None:
    uri = os.getenv("MONGO_URI", "mongodb://localhost:27017/")
    username = os.getenv("MONGO_USERNAME", "study_app")
    password = os.getenv("MONGO_PASSWORD", "study_app_password")
    with MongoClient(uri, username=username, password=password, authSource="shop_db") as client:
        print("Доступ study_app:", client.shop_db.orders.count_documents({}), "заказов")
    for description, credentials, expected_code in [
        ("без пароля", {}, 13),
        ("неверный пароль", {"username": username, "password": password + "_wrong", "authSource": "shop_db"}, 18),
    ]:
        try:
            with MongoClient(uri, serverSelectionTimeoutMS=5000, **credentials) as client:
                client.shop_db.orders.count_documents({})
        except OperationFailure as error:
            if error.code != expected_code:
                raise
            print(f"Доступ {description} запрещён")
        else:
            raise AssertionError(f"Доступ {description} неожиданно разрешён")


if __name__ == "__main__":
    main()
