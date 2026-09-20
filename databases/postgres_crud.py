"""CRUD operations using SQLAlchemy Core with PostgreSQL, including transaction management and error rollback."""

import sys
from typing import Any
from pprint import pprint

from sqlalchemy import (
    Column,
    Integer,
    MetaData,
    String,
    Table,
    create_engine,
    delete,
    insert,
    select,
    update,
)
from sqlalchemy.engine import Engine
from sqlalchemy.exc import SQLAlchemyError

# Ensure UTF-8 output encoding in Windows terminal environments
if sys.stdout.encoding.lower() != "utf-8":
    sys.stdout.reconfigure(encoding="utf-8")
if sys.stderr.encoding.lower() != "utf-8":
    sys.stderr.reconfigure(encoding="utf-8")

# --- Подключение к PostgreSQL ---
DB_URL = "postgresql+psycopg://postgres:postgres@localhost:5432/study_db"

metadata = MetaData()

# Определение таблицы "users" средствами SQLAlchemy Core
users_table = Table(
    "users",
    metadata,
    Column("id", Integer, primary_key=True, autoincrement=True),
    Column("name", String(100), nullable=False),
    Column("email", String(100), nullable=False, unique=True),
    Column("age", Integer, nullable=True),
)


def get_engine(db_url: str = DB_URL) -> Engine:
    """Создает и возвращает Engine для работы с БД."""
    return create_engine(db_url, echo=False)


def init_db(engine: Engine) -> None:
    """Создает таблицы в базе данных (DDL через SQLAlchemy Core)."""
    metadata.create_all(engine)
    print("[+] Таблица 'users' проверена/создана.")


# ==========================================
# 1. CREATE (Создание записи с откатом при ошибке)
# ==========================================
def create_user(engine: Engine, name: str, email: str, age: int | None = None) -> int | None:
    """Вставляет нового пользователя. При ошибке выполняет транзакционный rollback."""
    with engine.connect() as conn:
        trans = conn.begin()
        try:
            stmt = (
                insert(users_table)
                .values(name=name, email=email, age=age)
                .returning(users_table.c.id)
            )
            result = conn.execute(stmt)
            user_id = result.scalar_one()
            trans.commit()
            print(f"[+] Успешно создан пользователь id={user_id}: {name} ({email})")
            return user_id
        except SQLAlchemyError as exc:
            trans.rollback()
            print(f"[-] Ошибка при создании пользователя '{name}' ({email}): {exc.__class__.__name__}: {exc}")
            print("    -> Транзакция успешно откачена (ROLLBACK).")
            return None


# ==========================================
# 2. READ (Чтение записей - без необходимости rollback)
# ==========================================
def get_user_by_id(engine: Engine, user_id: int) -> dict[str, Any] | None:
    """Возвращает одного пользователя по id."""
    with engine.connect() as conn:
        stmt = select(users_table).where(users_table.c.id == user_id)
        row = conn.execute(stmt).mappings().first()
        return dict(row) if row else None


def get_all_users(engine: Engine) -> list[dict[str, Any]]:
    """Возвращает список всех пользователей."""
    with engine.connect() as conn:
        stmt = select(users_table).order_by(users_table.c.id)
        rows = conn.execute(stmt).mappings().all()
        return [dict(r) for r in rows]


# ==========================================
# 3. UPDATE (Обновление записи с откатом при ошибке)
# ==========================================
def update_user(engine: Engine, user_id: int, **values: Any) -> bool:
    """Обновляет поля пользователя по id. При ошибке выполняет транзакционный rollback."""
    with engine.connect() as conn:
        trans = conn.begin()
        try:
            stmt = (
                update(users_table)
                .where(users_table.c.id == user_id)
                .values(**values)
            )
            result = conn.execute(stmt)
            if result.rowcount == 0:
                print(f"[!] Пользователь с id={user_id} не найден для обновления.")
                trans.commit()
                return False

            trans.commit()
            print(f"[+] Пользователь id={user_id} успешно обновлен: {values}")
            return True
        except SQLAlchemyError as exc:
            trans.rollback()
            print(f"[-] Ошибка при обновлении пользователя id={user_id}: {exc.__class__.__name__}: {exc}")
            print("    -> Транзакция успешно откачена (ROLLBACK).")
            return False


# ==========================================
# 4. DELETE (Удаление записи с откатом при ошибке)
# ==========================================
def delete_user(engine: Engine, user_id: int) -> bool:
    """Удаляет пользователя по id. При ошибке выполняет транзакционный rollback."""
    with engine.connect() as conn:
        trans = conn.begin()
        try:
            stmt = delete(users_table).where(users_table.c.id == user_id)
            result = conn.execute(stmt)
            if result.rowcount == 0:
                print(f"[!] Пользователь с id={user_id} не найден для удаления.")
                trans.commit()
                return False

            trans.commit()
            print(f"[+] Пользователь id={user_id} успешно удален.")
            return True
        except SQLAlchemyError as exc:
            trans.rollback()
            print(f"[-] Ошибка при удалении пользователя id={user_id}: {exc.__class__.__name__}: {exc}")
            print("    -> Транзакция успешно откачена (ROLLBACK).")
            return False


def run_demonstration() -> None:
    """Демонстрация работы всех CRUD-операций и механизма отката транзакций."""
    print("=" * 65)
    print("ДЕМОНСТРАЦИЯ: SQLALCHEMY CORE + POSTGRESQL (CRUD И ROLLBACK)")
    print("=" * 65)

    engine = get_engine()

    init_db(engine)

    # Очистим таблицу перед демонстрацией для воспроизводимости
    with engine.connect() as conn:
        trans = conn.begin()
        conn.execute(delete(users_table))
        trans.commit()
    print()

    # 1. CREATE
    print("--- 1. CREATE (Вставка записей) ---")
    id1 = create_user(engine, name="Иван Иванов", email="ivan@example.com", age=25)
    id2 = create_user(engine, name="Анна Смирнова", email="anna@example.com", age=30)
    id3 = create_user(engine, name="Петр Кузнецов", email="petr@example.com", age=22)
    print()

    # 2. READ
    print("--- 2. READ (Чтение данных) ---")
    print(f"Пользователь с id={id1}:")
    pprint(get_user_by_id(engine, id1), indent=2, sort_dicts=False)
    print("\nВсе пользователи в БД:")
    pprint(get_all_users(engine), indent=2, sort_dicts=False)
    print()

    # 3. UPDATE
    print("--- 3. UPDATE (Обновление данных) ---")
    update_user(engine, id1, age=26, name="Иван Петров")
    print("Проверка после обновления:")
    pprint(get_user_by_id(engine, id1), indent=2, sort_dicts=False)
    print()

    # 4. DELETE
    print("--- 4. DELETE (Удаление записи) ---")
    delete_user(engine, id3)
    print("Пользователи после удаления id=3:")
    pprint(get_all_users(engine), indent=2, sort_dicts=False)
    print()

    # 5. ТЕСТИРОВАНИЕ ОБРАБОТКИ ОШИБОК И ROLLBACK
    print("--- 5. ТЕСТИРОВАНИЕ ОБРАБОТКИ ОШИБОК И ROLLBACK ---")
    print("Попытка вставки пользователя с уже существующим email (anna@example.com):")
    duplicate_res = create_user(engine, name="Двойник Анны", email="anna@example.com", age=40)
    print(f"Результат вызова create_user: {duplicate_res}")

    print("\nПопытка обновления email на уже занятый (id1 меняет email на anna@example.com):")
    update_res = update_user(engine, id1, email="anna@example.com")
    print(f"Результат вызова update_user: {update_res}")

    print("\nФинальное состояние таблицы (данные не повреждены благодаря rollback):")
    pprint(get_all_users(engine), indent=2, sort_dicts=False)
    print("=" * 65)


if __name__ == "__main__":
    run_demonstration()
