"""Учебный пример SQL-инъекции: только временная таблица текущего сеанса."""

import os

from sqlalchemy import create_engine, text
from sqlalchemy.engine import Connection, URL

ATTACK = "' OR 1=1 -- "


def application_url() -> URL:
    return URL.create(
        "postgresql+psycopg",
        username="study_app",
        password=os.getenv("POSTGRES_APP_PASSWORD", "study_app_password"),
        host=os.getenv("POSTGRES_HOST", "localhost"),
        port=int(os.getenv("POSTGRES_PORT", "5432")),
        database=os.getenv("POSTGRES_DB", "study_db"),
    )


def prepare_demo(connection: Connection) -> None:
    connection.execute(text(
        "CREATE TEMP TABLE injection_users (id integer PRIMARY KEY, username text NOT NULL) ON COMMIT DROP"
    ))
    connection.execute(text("INSERT INTO pg_temp.injection_users VALUES (:id, :username)"), [
        {"id": 1, "username": "alice"},
        {"id": 2, "username": "bob"},
        {"id": 3, "username": "O'Reilly"},
    ])


def unsafe_lookup(connection: Connection, username: str) -> list[str]:
    # Намеренная уязвимость для лабораторной работы. Не использовать в приложении.
    query = f"SELECT username FROM pg_temp.injection_users WHERE username = '{username}' ORDER BY id"
    return list(connection.exec_driver_sql(query).scalars())


def safe_lookup(connection: Connection, username: str) -> list[str]:
    query = text("SELECT username FROM pg_temp.injection_users WHERE username = :username ORDER BY id")
    return list(connection.execute(query, {"username": username}).scalars())


def main() -> None:
    engine = create_engine(application_url())
    try:
        with engine.begin() as connection:
            prepare_demo(connection)
            print("Обычный поиск:", safe_lookup(connection, "alice"))
            print("Ввод:", repr(ATTACK))
            print("Сырой SQL, фильтр обойдён:", unsafe_lookup(connection, ATTACK))
            print("Параметризация, совпадений нет:", safe_lookup(connection, ATTACK))
            print("Апостроф как часть значения:", safe_lookup(connection, "O'Reilly"))
    finally:
        engine.dispose()


if __name__ == "__main__":
    main()
