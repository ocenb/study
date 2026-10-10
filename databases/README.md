# Лабораторные работы по базам данных

## Стек технологий
- **Python**: 3.14 (управляется через `uv`)
- **Зависимости**:
  - `sqlalchemy`
  - `psycopg` (драйвер PostgreSQL)
  - `pymongo`
  - `redis`
  - `alembic`
- **Базы данных**: MongoDB, PostgreSQL, Redis (Docker Compose)

---

## 1. Запуск инфраструктуры (Docker)

Запуск приложения и всех БД (MongoDB + PostgreSQL + Redis):
```powershell
docker compose up -d
```

Проверить статус контейнеров:
```powershell
docker ps
```

---

## 2. Задание 1: PyMongo + MongoDB
Коллекция `shop_db.orders` моделирует заказы магазина. Документ содержит
вложенные словари `customer.address`, `metadata.delivery`, массив `items`
с вложенными `product.attributes`, даты, строки, числа и логические значения.
Все денежные суммы хранятся целыми числами в копейках; итог заказа равен сумме
стоимостей его позиций.

Из каталога `databases` выполните:
```sh
docker compose up -d mongodb
uv sync --locked
uv run python main.py
```
По умолчанию используется `mongodb://localhost:27017/`; другой адрес можно
передать через переменную окружения `MONGO_URI`.

Скрипт создаёт индексы и записывает 100 примеров. Повторный запуск заменяет
примеры с теми же `order_number`, не удаляя коллекцию и другие заказы.
Генератор использует фиксированный seed; время отсчитывается от момента запуска.

Класс `OrdersRepository` в `mongo_orders.py` предоставляет методы:
- `create_indexes()` — создание индексов, включая уникальный номер заказа;
- `seed(orders)` — пакетная запись примеров через upsert;
- `get_order(order_number)` — поиск по уникальному номеру;
- `customer_orders(email, limit)` — последние заказы покупателя;
- `update_status(order_number, status)` — изменение статуса и признака оплаты;
- `analytics(start, end, city)` — три аналитических отчёта;
- `explain_analytics(start, end, city)` — планы выполнения этих отчётов.

В учебной модели статусы `processing`, `shipped`, `delivered` означают оплату;
`new` и `cancelled` — отсутствие оплаты. Обновление статуса синхронно меняет
`is_paid` по этому правилу.

| Индекс | Поля | Использование |
| --- | --- | --- |
| `order_number_unique` | `order_number` (unique) | Поиск и обновление заказа, upsert |
| `customer_date` | `customer.email`, `created_at` ↓ | Заказы покупателя с сортировкой по дате |
| `status_date` | `status`, `created_at` | Выручка доставленных заказов по дням UTC |
| `paid_date` | `is_paid`, `created_at` | Проданные единицы и выручка по категориям |
| `city_date` | `customer.address.city`, `created_at` | Топ-10 покупателей города по сумме оплаченных заказов |

Все отчёты принимают полуоткрытый период `[start, end)` и начинают pipeline с
`$match` по полям индекса. Далее используются `$group`, `$sort`, а для категорий
также `$unwind` и `$multiply`. Для топа покупателей применяется `$limit`.
Индекс ускоряет начальный отбор документов; группировка и сортировка вычисленных
результатов выполняются после него. В запросах передаётся `hint` с именем индекса,
поэтому отчёты требуют предварительного вызова `create_indexes()`.

В конце демонстрации выводится `explain` с `executionStats`: проверьте `IXSCAN`,
`indexName`, `totalKeysExamined` и `totalDocsExamined` во вложенном плане.
На маленькой коллекции ускорение по времени может быть незаметным; `hint`
обеспечивает использование указанного индекса для учебной демонстрации.

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

---

## 5. Redis: кеш с TTL, Pub/Sub и очередь задач

Все команды выполняются из каталога `databases`. Зависимость `redis` (redis-py)
уже указана в `pyproject.toml`.

```sh
docker compose up -d redis
uv sync --locked
```

Адрес по умолчанию — `redis://localhost:6379/0`. Для другого сервера задайте
переменную окружения `REDIS_URL`. Redis использует AOF и Docker volume `redis_data`.

### Кеширование с TTL

```sh
uv run python redis_cache.py
```

`RedisCache.get_or_load(key, loader, ttl)` читает JSON из кеша. При промахе
вызывает `loader`, сохраняет результат через атомарный `SET ... EX` и возвращает
его. `invalidate(key)` удаляет запись. Демонстрация устанавливает TTL 2 секунды:
первый вызов загружает товар, второй берёт его из кеша, после паузы источник
вызывается снова. Ошибки загрузки не кешируются. Одновременные промахи могут
привести к нескольким вызовам источника — блокировка в этом простом примере
не реализована.

### Pub/Sub

В первом терминале запустите подписчика и дождитесь сообщения о готовности:

```sh
uv run python redis_subscriber.py
```

Во втором терминале опубликуйте сообщение:

```sh
uv run python redis_publisher.py "Заказ ORD-00001 создан"
```

Канал — `study:events`, сообщения передаются в JSON с временем публикации.
Publisher выводит число получателей, subscriber печатает сообщения до Ctrl+C.
Pub/Sub не хранит историю: сообщение получают только подключённые подписчики.

### Очередь задач

Поставьте задачи в очередь (можно до запуска обработчика):

```sh
uv run python redis_enqueue.py 10 20 30
uv run python redis_enqueue.py 5 7
```

Затем запустите обработчик в отдельном терминале:

```sh
uv run python redis_worker.py
```

`TaskQueue` сохраняет JSON-задачи с UUID, типом `sum`, списком целых чисел
и временем создания. `LPUSH` добавляет задачи, `BRPOP` забирает их в порядке FIFO
и ожидает новые без активного опроса. Обработчик суммирует числа (результаты
примеров: 60 и 12), печатает результат и сохраняет его на час по ключу
`study:result:<UUID>`. Для проверки подставьте UUID из вывода:

```sh
docker compose exec redis redis-cli GET study:result:<UUID>
docker compose exec redis redis-cli LRANGE study:tasks:failed 0 -1
```

Некорректные задачи попадают в список `study:tasks:failed` с причиной ошибки;
обработчик продолжает работу. Несколько обработчиков могут читать одну очередь:
каждый элемент получает один из них. Ctrl+C завершает обработчик.

Это простая очередь без подтверждения выполнения: `BRPOP` удаляет задачу сразу,
поэтому при сбое обработчика после получения задача может быть потеряна.
Автоматические повторы и восстановление выполняемых задач не реализованы.

---

## 6. Docker: приложение и базы данных

`Dockerfile` собирает Python 3.14 с зависимостями из `uv.lock`. Приложение
запускается от непривилегированного пользователя. `.dockerignore` исключает
локальное окружение, секреты `.env` и резервные копии из контекста сборки.

Из каталога `databases`:

```sh
docker compose up --build -d
docker compose logs -f app
docker compose ps -a
```

Compose запускает MongoDB, PostgreSQL, Redis и приложение. Приложение ждёт
успешных healthcheck всех БД. Внутри Docker оно использует имена сервисов
`mongodb` и `redis` вместо `localhost`. Данные БД сохраняются в именованных
volumes. PostgreSQL доступен для предыдущих лабораторных работ и копирования.

Текущее приложение — консольная демонстрация `main.py`: создаёт примеры MongoDB,
индексы, выводит аналитику и завершает работу с кодом 0. Статус `app: Exited (0)`
означает успешное выполнение. Для повторного запуска и других примеров:

```sh
docker compose run --rm app
docker compose run --rm app python redis_cache.py
docker compose run --rm app python redis_subscriber.py
# В другом терминале:
docker compose run --rm app python redis_publisher.py "Привет из Docker"
```

Если нужны только базы, запускайте их явно:

```sh
docker compose up -d --wait mongodb postgres redis
```

Остановка с сохранением данных:

```sh
docker compose down
```

`docker compose down -v` дополнительно удаляет volumes и все данные БД.
Compose предназначен для локальной лабораторной работы; используются прежние
учебные настройки подключения и пароли.

## 7. Резервное копирование и восстановление

Скрипты требуют Bash, Docker Compose и запущенных сервисов `postgres`/`mongodb`.
Утилиты `pg_dump` и `mongodump` выполняются внутри соответствующих контейнеров;
на хост устанавливать их не нужно. Скрипты находят Compose относительно своего
расположения и могут запускаться из любого рабочего каталога.

Из каталога `databases`:

```sh
bash scripts/backup_postgres.sh
bash scripts/backup_mongodb.sh
# Необязательный каталог назначения (пробелы допустимы):
bash scripts/backup_postgres.sh "/tmp/study backups"
# Необязательное имя БД MongoDB (по умолчанию shop_db):
MONGO_DB=shop_db bash scripts/backup_mongodb.sh
```

По умолчанию архивы сохраняются в `databases/backups/` (исключён из Git).
Имена содержат время UTC и случайный суффикс, поэтому повторные запуски
не перезаписывают копии. Файлы доступны только владельцу (`0600`).
При неуспешном или пустом дампе скрипт завершается с ошибкой и удаляет временный
файл `.partial`; готовое имя появляется только после успешной записи.
Путь к готовому архиву выводится в stdout, сообщения утилит — в stderr.

PostgreSQL копируется в custom format (`.dump`) без владельцев и ACL. Это
логическая копия одной БД; роли и глобальные объекты кластера в неё не входят.
Для проверки восстановите архив в **новую** базу:

```sh
pg_backup=$(bash scripts/backup_postgres.sh)
docker compose exec -T postgres createdb -U postgres study_db_restore
docker compose exec -T postgres pg_restore -U postgres -d study_db_restore --exit-on-error --no-owner --no-acl < "$pg_backup"
docker compose exec -T postgres psql -U postgres -d study_db_restore -c '\dt'
```

MongoDB копируется в сжатый архив (`.archive.gz`). Восстановление с переименованием
пространства имён сохраняет исходную базу:

```sh
mongo_backup=$(bash scripts/backup_mongodb.sh)
docker compose exec -T mongodb mongorestore --archive --gzip --nsFrom='shop_db.*' --nsTo='shop_db_restore.*' < "$mongo_backup"
docker compose exec -T mongodb mongosh --quiet --eval 'db.getSiblingDB("shop_db_restore").orders.countDocuments({})'
```

Для повторной проверки используйте новые имена тестовых баз.
Во время `mongodump` в этой учебной standalone-конфигурации остановите запись
в БД, если нужна согласованность между коллекциями. Скрипты не настраивают
расписание, удаление старых копий или отправку во внешнее хранилище.

## 8. GitHub Actions

GitHub Actions выполняет автоматические workflows из `.github/workflows/*.yml`.
События (например, push, pull request или ручной запуск) запускают jobs на runners;
каждая job состоит из steps с shell-командами или готовыми actions. Jobs могут
выполняться параллельно, зависеть друг от друга, использовать матрицы окружений,
секреты, кеши зависимостей и артефакты. Расписание задаётся через `schedule`.
Права встроенного `GITHUB_TOKEN` ограничиваются секцией `permissions`.
См. [официальный справочник синтаксиса workflows](https://docs.github.com/en/actions/reference/workflows-and-actions/workflow-syntax).

В репозитории добавлен `.github/workflows/databases.yml`:

1. Запуск при push/PR, затрагивающем `databases` или workflow, и вручную
   (`workflow_dispatch`).
2. Проверка Bash через `bash -n`/ShellCheck и тесты backup-скриптов.
3. Проверка Compose, сборка образа приложения, проверка синтаксиса Python.
4. Запуск БД с ожиданием healthcheck, демонстраций MongoDB и кеша Redis.
5. Создание дампов и восстановление в отдельные БД: PostgreSQL проверяется
   чтением контрольной строки, MongoDB — наличием 100 заказов.
6. Вывод логов и удаление контейнеров/volumes CI даже при ошибке (`always()`).

Workflow имеет лимит 20 минут и права `contents: read`. Он проверяет сборку
и работу приложения, не публикует образ и не выполняет внешнее развёртывание.
После отправки коммита на GitHub результаты появятся во вкладке **Actions**.
Для ручного запуска workflow должен присутствовать в основной ветке; откройте
**Actions → Databases CI → Run workflow**.
См. [ручной запуск workflow](https://docs.github.com/en/actions/how-tos/manage-workflow-runs/manually-run-a-workflow).

Локальные проверки без Docker:

```sh
bash -n scripts/backup_postgres.sh scripts/backup_mongodb.sh
python -m unittest discover -s tests -v
```
