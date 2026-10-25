"""Позитивные и негативные проверки реальных CRUD-операций PostgreSQL."""

import pytest
from sqlalchemy import text

from postgres_crud import (
    create_user,
    delete_user,
    get_all_users,
    get_user_by_id,
    update_user,
)

pytestmark = pytest.mark.postgres


@pytest.mark.parametrize("age", [None, 0, 18, 100], ids=["nullable", "zero", "adult", "hundred"])
def test_create_and_read_user(db_engine, user_data_factory, age):
    data = user_data_factory(age=age)
    user_id = create_user(db_engine, **data)

    assert isinstance(user_id, int)
    # Чтение открывает новое соединение и подтверждает сохранение после COMMIT.
    assert get_user_by_id(db_engine, user_id) == {"id": user_id, **data}


def test_create_without_optional_age(db_engine, user_data_factory):
    data = user_data_factory()
    del data["age"]
    user_id = create_user(db_engine, **data)

    assert user_id is not None
    assert get_user_by_id(db_engine, user_id) == {"id": user_id, **data, "age": None}


def test_create_duplicate_email_rolls_back(db_engine, user_factory, user_data_factory):
    existing = user_factory()
    duplicate = user_data_factory(email=existing["email"])

    assert create_user(db_engine, **duplicate) is None
    assert get_all_users(db_engine) == [existing]
    # Ошибка не мешает следующей корректной записи.
    another = user_factory()
    assert get_all_users(db_engine) == [existing, another]


INVALID_VALUES = [
    pytest.param({"name": None}, id="null-name"),
    pytest.param({"email": None}, id="null-email"),
    pytest.param({"name": "x" * 101}, id="name-too-long"),
    pytest.param({"email": "x" * 101}, id="email-too-long"),
    pytest.param({"age": "not-an-integer"}, id="invalid-age-type"),
]


@pytest.mark.parametrize("invalid", INVALID_VALUES)
def test_create_invalid_data_rolls_back(db_engine, user_data_factory, user_factory, invalid):
    existing = user_factory()

    assert create_user(db_engine, **user_data_factory(**invalid)) is None
    assert get_all_users(db_engine) == [existing]
    another = user_factory()
    assert get_user_by_id(db_engine, another["id"]) == another


def test_get_all_users_from_empty_table(db_engine):
    assert get_all_users(db_engine) == []


def test_get_all_users_ordered_by_id(db_engine, user_factory):
    expected = [user_factory() for _ in range(3)]

    assert get_all_users(db_engine) == expected


def test_get_missing_user_does_not_change_existing_data(db_engine, user_factory):
    existing = user_factory()

    assert get_user_by_id(db_engine, existing["id"] + 1) is None
    assert get_all_users(db_engine) == [existing]


def test_update_user(db_engine, user_factory, user_data_factory):
    existing = user_factory()
    changes = user_data_factory()

    assert update_user(db_engine, existing["id"], **changes) is True
    assert get_user_by_id(db_engine, existing["id"]) == {"id": existing["id"], **changes}


def test_update_only_one_field(db_engine, user_factory):
    existing = user_factory()

    assert update_user(db_engine, existing["id"], age=None) is True
    assert get_user_by_id(db_engine, existing["id"]) == existing | {"age": None}


def test_update_missing_user(db_engine, user_factory):
    existing = user_factory()

    assert update_user(db_engine, existing["id"] + 1, age=25) is False
    assert get_all_users(db_engine) == [existing]


def test_update_duplicate_email_is_atomic(db_engine, user_factory, faker):
    first, second = user_factory(), user_factory()

    assert update_user(db_engine, second["id"], email=first["email"], name=faker.name()) is False
    assert get_all_users(db_engine) == [first, second]
    assert update_user(db_engine, second["id"], age=25) is True
    assert get_user_by_id(db_engine, second["id"]) == second | {"age": 25}


@pytest.mark.parametrize("invalid", INVALID_VALUES)
def test_update_invalid_data_rolls_back(db_engine, user_factory, invalid):
    existing = user_factory()

    assert update_user(db_engine, existing["id"], **invalid) is False
    assert get_user_by_id(db_engine, existing["id"]) == existing
    assert update_user(db_engine, existing["id"], age=25) is True
    assert get_user_by_id(db_engine, existing["id"]) == existing | {"age": 25}


def test_delete_user_preserves_other_users(db_engine, user_factory):
    first, second = user_factory(), user_factory()

    assert delete_user(db_engine, first["id"]) is True
    assert get_user_by_id(db_engine, first["id"]) is None
    assert get_all_users(db_engine) == [second]


def test_delete_missing_user(db_engine, user_factory):
    existing = user_factory()

    assert delete_user(db_engine, existing["id"] + 1) is False
    assert get_all_users(db_engine) == [existing]


def test_delete_same_user_twice(db_engine, user_factory):
    existing = user_factory()

    assert delete_user(db_engine, existing["id"]) is True
    assert delete_user(db_engine, existing["id"]) is False
    assert get_all_users(db_engine) == []


def test_delete_with_foreign_key_rolls_back(db_engine, user_factory):
    existing = user_factory()
    with db_engine.begin() as connection:
        connection.execute(text("CREATE TABLE user_links (user_id integer REFERENCES users(id))"))
        connection.execute(text("INSERT INTO user_links (user_id) VALUES (:id)"), {"id": existing["id"]})

    assert delete_user(db_engine, existing["id"]) is False
    assert get_user_by_id(db_engine, existing["id"]) == existing
    with db_engine.begin() as connection:
        assert connection.execute(text("SELECT user_id FROM user_links")).scalar_one() == existing["id"]
        connection.execute(text("DELETE FROM user_links"))
    assert delete_user(db_engine, existing["id"]) is True
    assert get_all_users(db_engine) == []
