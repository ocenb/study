import sqlite3
import time
from contextlib import closing
from io import BytesIO
from pathlib import Path

import pytest
from fastapi.testclient import TestClient
from PIL import Image

from tracker.db import connect, migrate
from tracker.main import create_app


@pytest.fixture
def client(tmp_path):
    with TestClient(create_app(tmp_path / "tasks.sqlite3")) as client:
        yield client


def sign_in(client, username):
    credentials = {"username": username, "password": "тестовый пароль"}
    assert client.post("/auth/register", json=credentials).status_code == 201
    response = client.post("/auth/login", json=credentials)
    assert response.status_code == 200
    return {"Authorization": f"Bearer {response.json()['access_token']}"}


@pytest.fixture
def headers(client):
    return sign_in(client, "Анна")


def add(client, headers, **fields):
    response = client.post("/tasks", headers=headers, json={"title": "Проверить отчёт", **fields})
    assert response.status_code == 201, response.text
    return response.json()


def test_lifecycle(client, headers):
    task = add(client, headers)
    url = f"/tasks/{task['id']}"
    assert task["status"] == "new"
    assert client.get(url, headers=headers).json() == task
    for status in ["in_progress", "blocked", "done"]:
        changed = client.patch(url, headers=headers, json={"status": status, "assignee": "Ира"})
        assert changed.json()["status"] == status
        assert changed.json()["title"] == task["title"]
    assert client.patch(url, headers=headers, json={"assignee": None}).json()["assignee"] is None
    assert client.delete(url, headers=headers).status_code == 204
    assert client.get(url, headers=headers).status_code == 404


def test_auth(client, headers):
    credentials = {"username": "Анна", "password": "другой пароль"}
    assert client.post("/auth/register", json=credentials).status_code == 409
    assert client.post("/auth/login", json=credentials).status_code == 401
    for path in ["/tasks", "/statuses", "/analytics", "/analytics/chart.png"]:
        assert client.get(path).status_code == 401
    assert client.get("/tasks", headers={"Authorization": "Bearer invalid"}).status_code == 401
    assert client.post("/auth/logout", headers=headers).status_code == 204
    assert client.get("/tasks", headers=headers).status_code == 401


def test_session_storage_and_expiry(client, headers):
    with closing(connect(client.app.state.database)) as db, db:
        assert db.execute("SELECT password_hash FROM users").fetchone()[0].startswith("$argon2id$")
        assert (
            db.execute("SELECT token_hash FROM sessions").fetchone()[0]
            != headers["Authorization"][7:]
        )
        db.execute("UPDATE sessions SET expires_at = ?", (int(time.time()) - 1,))
    assert client.get("/tasks", headers=headers).status_code == 401


def test_owner_isolation(client, headers):
    task = add(client, headers)
    other = sign_in(client, "Олег")
    url = f"/tasks/{task['id']}"
    assert client.get(url, headers=other).status_code == 404
    assert client.patch(url, headers=other, json={"status": "done"}).status_code == 404
    assert client.delete(url, headers=other).status_code == 404
    assert client.get("/tasks", headers=other).json() == []
    assert client.get("/analytics", headers=other).json()["total"] == 0


def test_filters_and_paging(client, headers):
    first = add(client, headers, status="done", topic="API", assignee="Олег")
    last = add(client, headers, status="done", topic="База", assignee="Олег")
    add(client, headers, topic="API", assignee="Олег")
    params = {"status": "done", "topic": "API", "assignee": "Олег"}
    assert client.get("/tasks", headers=headers, params=params).json() == [first]
    assert client.get("/tasks?limit=1&offset=1", headers=headers).json() == [last]
    assert client.get("/analytics", headers=headers, params=params).json()["total"] == 1


@pytest.mark.parametrize(
    "group,expected",
    [
        ("status", {"new": 2, "done": 1}),
        ("topic", {"API": 2, None: 1}),
        ("assignee", {"Ира": 1, None: 2}),
    ],
)
def test_analytics(client, headers, group, expected):
    add(client, headers, topic="API", assignee="Ира", status="done")
    add(client, headers, topic="API")
    add(client, headers)
    params = {"group_by": group}
    result = client.get("/analytics", headers=headers, params=params).json()
    assert (result["total"], result["done"], result["completion_percent"]) == (
        3,
        1,
        33.3,
    )
    assert {g["value"]: g["count"] for g in result["groups"]} == expected
    response = client.get("/analytics/chart.png", headers=headers, params=params)
    assert response.headers["content-type"] == "image/png"
    with Image.open(BytesIO(response.content)) as image:
        assert image.format == "PNG"
        image.verify()


def test_empty_analytics(client, headers):
    result = client.get("/analytics", headers=headers).json()
    assert result == {
        "group_by": "status",
        "total": 0,
        "done": 0,
        "completion_percent": 0,
        "groups": [],
    }
    assert client.get("/analytics/chart.png", headers=headers).status_code == 200


def test_validation(client, headers):
    task = add(client, headers)
    for body in [
        {},
        {"title": None},
        {"title": " "},
        {"description": None},
        {"owner_id": 4},
        {"status": "finished"},
        {"topic": ""},
    ]:
        assert client.patch(f"/tasks/{task['id']}", json=body, headers=headers).status_code == 422
    assert client.get("/analytics?group_by=unknown", headers=headers).status_code == 422
    assert client.get("/tasks?limit=0", headers=headers).status_code == 422
    assert {s["code"] for s in client.get("/statuses", headers=headers).json()} == {
        "new",
        "in_progress",
        "done",
        "blocked",
    }


def test_sql_metacharacters_are_literal(client, headers):
    label = "O'Brien; --"
    task = add(client, headers, title=label, topic=label)
    add(client, headers)
    assert client.get("/tasks", params={"topic": label}, headers=headers).json() == [task]


def test_migration_from_v1(tmp_path):
    path = tmp_path / "previous.sqlite3"
    schema = Path(__file__).resolve().parents[1] / "tracker/migrations/001_core.sql"
    with closing(connect(path)) as db, db:
        db.executescript(schema.read_text())
        db.execute("PRAGMA user_version = 1")
        db.execute("INSERT INTO users(username, password_hash) VALUES ('user', 'hash')")
        db.execute("INSERT INTO tasks(owner_id, title) VALUES (1, 'Старая задача')")
    migrate(path)
    migrate(path)
    with closing(connect(path)) as db:
        assert db.execute("PRAGMA user_version").fetchone()[0] == 2
        assert "tasks_owner_topic" in {
            row["name"] for row in db.execute("PRAGMA index_list(tasks)")
        }
        assert db.execute("SELECT title FROM tasks").fetchone()[0] == "Старая задача"
        with pytest.raises(sqlite3.IntegrityError):
            db.execute("UPDATE tasks SET status='invalid'")


@pytest.mark.parametrize("path", ["/auth/register", "/auth/login"])
@pytest.mark.parametrize(
    "payload",
    [
        {"username": "demo", "password": "abc123"},
        {"password": "abc123"},
        [{"username": "demo", "password": "abc123"}],
    ],
)
def test_auth_validation_hides_submitted_values(client, path, payload):
    response = client.post(path, json=payload)
    assert response.status_code == 422
    assert "abc123" not in response.text
    for error in response.json()["detail"]:
        assert set(error) == {"type", "loc", "msg"}


def test_patch_contract(client, headers):
    document = client.get("/openapi.json").json()
    patch = document["paths"]["/tasks/{task_id}"]["patch"]
    model = patch["requestBody"]["content"]["application/json"]["schema"]["$ref"].split("/")[-1]
    schema = document["components"]["schemas"][model]
    assert schema["minProperties"] == 1
    assert "title" not in schema.get("required", [])
    assert schema["properties"]["title"]["type"] == "string"
    task = add(client, headers)
    url = f"/tasks/{task['id']}"
    assert (
        client.patch(url, headers=headers, json={"topic": "API"}).json()["title"] == task["title"]
    )
    assert client.patch(url, headers=headers, json={"title": None}).status_code == 422
    assert (
        client.patch(url, headers=headers, json={"title": "Новое название"}).json()["title"]
        == "Новое название"
    )


@pytest.mark.parametrize("field,empty_label", [("topic", "Без темы"), ("assignee", "Не назначен")])
def test_png_group_labels_are_distinct(client, headers, monkeypatch, field, empty_label):
    from matplotlib.axes import Axes

    prefix = "Проверка пользовательского интерфейса"
    values = [empty_label, prefix + " A", prefix + " Б"]
    add(client, headers)
    for value in values:
        add(client, headers, **{field: value})

    tick_labels = []
    set_ticks = Axes.set_yticks

    def capture_labels(ax, ticks, labels=None, **kwargs):
        if labels is not None:
            tick_labels.extend(labels)
        return set_ticks(ax, ticks, labels, **kwargs)

    monkeypatch.setattr(Axes, "set_yticks", capture_labels)
    response = client.get("/analytics/chart.png", params={"group_by": field}, headers=headers)
    assert response.status_code == 200
    assert {label.replace("\n", "") for label in tick_labels} == {
        empty_label,
        *(f'"{value}"' for value in values),
    }
