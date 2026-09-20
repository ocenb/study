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

---

## 4. Задание 3: SQLAlchemy ORM + Alembic (One-to-Many и Cascade Delete)

### Структура моделей ([`models.py`](models.py)):
- **`User`**: `id`, `username`, `email`, `bio` (добавлено в результате рефакторинга), `is_active` (добавлено в результате рефакторинга).
- **`Post`**: `id`, `title`, `content`, `views_count` (добавлено в результате рефакторинга), `user_id` (ForeignKey c `ondelete="CASCADE"`).
- Связь `OneToMany` с параметром `cascade="all, delete-orphan", passive_deletes=True`.

### Миграции Alembic:
1. **Первая миграция (создание таблиц `users` и `posts`)**:
   ```powershell
   uv run alembic revision --autogenerate -m "create_users_and_posts_tables"
   uv run alembic upgrade head
   ```
2. **Вторая миграция (рефакторинг: добавление `bio`, `is_active` в `users` и `views_count` в `posts`)**:
   ```powershell
   uv run alembic revision --autogenerate -m "add_bio_is_active_to_users_and_views_count_to_posts"
   uv run alembic upgrade head
   ```

### Скрипт ORM CRUD с каскадным удалением:
```powershell
uv run python orm_crud.py
```

#### Реализованные функции:
- **`create_user(session, username, email, bio, is_active, posts_data)`** — создание пользователя и связанных постов через ORM-связь.
- **`create_post(session, user_id, title, content)`** — создание отдельного поста для пользователя.
- **`get_user_by_id(session, user_id)`** — жадная выборка пользователя вместе со связанными постами (`selectinload`).
- **`get_all_users(session)`**, **`get_all_posts(session)`** — получение всех записей.
- **`update_user(session, user_id, **kwargs)`**, **`update_post(session, post_id, **kwargs)`** — обновление атрибутов сущностей через сессию.
- **`delete_user(session, user_id)`** — удаление пользователя с автоматическим **каскадным удалением** всех его постов из БД.
