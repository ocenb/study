# Лабораторные работы по базам данных

## Стек технологий
- **Python**: 3.14 (управляется через `uv`)
- **Зависимости**:
  - `sqlalchemy`
  - `psycopg` (драйвер PostgreSQL)
  - `pymongo`
  - `redis`
  - `alembic`
- **Базы данных**: MongoDB, PostgreSQL (Docker Compose)

---

## 1. Запуск инфраструктуры (Docker)

Запуск всех сервисов (MongoDB + PostgreSQL):
```powershell
docker compose up -d
```

Проверить статус контейнеров:
```powershell
docker ps
```

---

## 2. Задание 1: PyMongo + MongoDB
Скрипт генерации данных и вывода метаданных коллекции `orders`:
```powershell
uv run python main.py
```

---

## 3. Задание 2: SQLAlchemy Core + PostgreSQL (CRUD + Rollback)
Скрипт реализации базовых CRUD-операций для таблицы `users` с транзакционным откатом (`rollback`) при ошибках:
```powershell
uv run python postgres_crud.py
```

### Реализованные функции:
- **`create_user(engine, name, email, age)`** — создание пользователя с `returning(id)`. При ошибке (например, дубликат email) происходит `trans.rollback()`.
- **`get_user_by_id(engine, user_id)`** — получение пользователя по ID (read-only).
- **`get_all_users(engine)`** — выборка всех записей (read-only).
- **`update_user(engine, user_id, **values)`** — обновление переданных полей. При ошибке (например, нарушение ограничений) происходит `trans.rollback()`.
- **`delete_user(engine, user_id)`** — удаление пользователя по ID с `trans.rollback()` при исключении.
