"""Фикстуры интеграционных тестов на отдельной PostgreSQL БД."""

import os
from uuid import uuid4

import pytest
from sqlalchemy import create_engine, text
from sqlalchemy.engine import make_url

from postgres_crud import create_user, init_db


@pytest.fixture(scope="session")
def faker_session_locale():
    return ["ru_RU"]


@pytest.fixture(scope="session")
def faker_seed():
    return 42


@pytest.fixture(scope="session")
def admin_engine():
    url = make_url(os.getenv(
        "TEST_DATABASE_URL",
        "postgresql+psycopg://postgres:postgres@localhost:5433/study_db_test",
    ))
    if url.get_backend_name() != "postgresql" or not (url.database or "").endswith("_test"):
        pytest.fail("TEST_DATABASE_URL должен указывать на PostgreSQL БД с суффиксом _test")
    engine = create_engine(url, connect_args={"connect_timeout": 5})
    try:
        with engine.connect() as connection:
            connection.execute(text("SELECT 1"))
        yield engine
    finally:
        engine.dispose()


@pytest.fixture
def db_engine(admin_engine):
    # CRUD сам открывает соединения и делает COMMIT: изоляция внешней
    # транзакцией не подходит. Каждому тесту выделяется отдельная схема.
    schema = f"test_{uuid4().hex}"
    with admin_engine.begin() as connection:
        connection.execute(text(f'CREATE SCHEMA "{schema}"'))
    engine = create_engine(
        admin_engine.url,
        connect_args={"options": f"-csearch_path={schema}", "connect_timeout": 5},
    )
    try:
        init_db(engine)
        yield engine
    finally:
        engine.dispose()
        with admin_engine.begin() as connection:
            connection.execute(text(f'DROP SCHEMA "{schema}" CASCADE'))


@pytest.fixture
def user_data_factory(faker):
    def generate(**overrides):
        data = {
            "name": faker.name(),
            "email": faker.unique.ascii_safe_email(),
            "age": faker.random_int(min=18, max=90),
        }
        return data | overrides
    return generate


@pytest.fixture
def user_factory(db_engine, user_data_factory):
    def create(**overrides):
        data = user_data_factory(**overrides)
        user_id = create_user(db_engine, **data)
        assert user_id is not None, "Не удалось подготовить пользователя"
        return {"id": user_id, **data}
    return create
