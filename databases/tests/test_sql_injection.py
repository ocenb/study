"""Проверка уязвимого примера и защиты на настоящей PostgreSQL."""

import pytest
from sqlalchemy import text

from sql_injection_demo import ATTACK, prepare_demo, safe_lookup, unsafe_lookup

pytestmark = pytest.mark.postgres


@pytest.fixture
def demo_connection(db_engine):
    with db_engine.begin() as connection:
        prepare_demo(connection)
        yield connection


def test_raw_sql_allows_filter_bypass(demo_connection):
    assert unsafe_lookup(demo_connection, "alice") == ["alice"]
    assert set(unsafe_lookup(demo_connection, ATTACK)) == {"alice", "bob", "O'Reilly"}


@pytest.mark.parametrize("username, expected", [
    ("alice", ["alice"]),
    ("O'Reilly", ["O'Reilly"]),
    ("missing", []),
    ("", []),
    (ATTACK, []),
    ("'; DROP TABLE pg_temp.injection_users; --", []),
])
def test_parameterized_query_treats_input_as_data(demo_connection, username, expected):
    assert safe_lookup(demo_connection, username) == expected
    assert demo_connection.execute(text("SELECT count(*) FROM pg_temp.injection_users")).scalar_one() == 3


def test_attack_can_be_stored_as_an_ordinary_value(demo_connection):
    demo_connection.execute(text("INSERT INTO pg_temp.injection_users VALUES (:id, :username)"),
                            {"id": 4, "username": ATTACK})
    assert safe_lookup(demo_connection, ATTACK) == [ATTACK]
