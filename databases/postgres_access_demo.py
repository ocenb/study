"""Проверка CRUD и запрета DDL для study_app на таблице администратора."""

from sqlalchemy import create_engine, text
from sqlalchemy.exc import DBAPIError

from sql_injection_demo import application_url


def check_permissions(engine) -> None:
    with engine.connect() as connection:
        # Внешняя транзакция откатывает тестовые изменения даже при ошибке проверки.
        with connection.begin() as transaction:
            row_id = connection.execute(text(
                "INSERT INTO app_data.access_demo (label) VALUES (:label) RETURNING id"
            ), {"label": "permission check"}).scalar_one()
            assert connection.execute(text(
                "SELECT label FROM app_data.access_demo WHERE id = :id"
            ), {"id": row_id}).scalar_one() == "permission check"
            connection.execute(text("UPDATE app_data.access_demo SET label = :label WHERE id = :id"),
                               {"label": "updated", "id": row_id})
            assert connection.execute(text("SELECT label FROM app_data.access_demo WHERE id = :id"),
                                      {"id": row_id}).scalar_one() == "updated"
            assert connection.execute(text("DELETE FROM app_data.access_demo WHERE id = :id"),
                                      {"id": row_id}).rowcount == 1
            for statement in [
                "DROP TABLE app_data.access_demo",
                "ALTER TABLE app_data.access_demo ADD COLUMN forbidden integer",
                "TRUNCATE app_data.access_demo",
                "CREATE TABLE app_data.forbidden (id integer)",
            ]:
                try:
                    with connection.begin_nested():
                        connection.execute(text(statement))
                except DBAPIError as error:
                    if getattr(error.orig, "sqlstate", None) != "42501":
                        raise
                    print(f"Запрещено: {statement}")
                else:
                    raise AssertionError(f"Операция неожиданно разрешена: {statement}")
            assert connection.execute(text("SELECT to_regclass('app_data.access_demo')")).scalar_one() is not None
            transaction.rollback()
    print("CRUD разрешён; DROP, ALTER, TRUNCATE и CREATE запрещены")


def main() -> None:
    engine = create_engine(application_url())
    try:
        check_permissions(engine)
    finally:
        engine.dispose()


if __name__ == "__main__":
    main()
