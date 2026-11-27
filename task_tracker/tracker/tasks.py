from enum import StrEnum
from typing import Annotated, Self

from fastapi import APIRouter, Depends, HTTPException, Path, Query
from pydantic import (
    MISSING,
    BaseModel,
    ConfigDict,
    Field,
    StringConstraints,
    model_validator,
)

from .auth import Owner
from .db import DB


class Status(StrEnum):
    new = "new"
    in_progress = "in_progress"
    done = "done"
    blocked = "blocked"


Title = Annotated[str, StringConstraints(strip_whitespace=True, min_length=1, max_length=200)]
Label = Annotated[str, StringConstraints(strip_whitespace=True, min_length=1, max_length=100)]
Description = Annotated[str, Field(max_length=5000)]
TaskId = Annotated[int, Path(ge=1, le=2**63 - 1)]


class TaskFields(BaseModel):
    model_config = ConfigDict(extra="forbid")
    title: Title
    description: Description = ""
    status: Status = Status.new
    topic: Label | None = None
    assignee: Label | None = None


class TaskUpdate(TaskFields):
    model_config = ConfigDict(json_schema_extra={"minProperties": 1})
    title: Title | MISSING = MISSING

    @model_validator(mode="after")
    def check_update(self) -> Self:
        if not self.model_fields_set:
            raise ValueError("Укажите хотя бы одно поле")
        return self


class Task(TaskFields):
    id: int
    created_at: str
    updated_at: str


class Selection:
    def __init__(
        self,
        owner: Owner,
        status: Status | None = None,
        topic: Label | None = None,
        assignee: Label | None = None,
    ):
        conditions = ["owner_id = ?"]
        self.values = [owner]
        for column, value in (
            ("status", status),
            ("topic", topic),
            ("assignee", assignee),
        ):
            if value is not None:
                conditions.append(f"{column} = ?")
                self.values.append(value)
        self.where = " AND ".join(conditions)


Filters = Annotated[Selection, Depends()]
COLUMNS = "id, title, description, status, topic, assignee, created_at, updated_at"
router = APIRouter(tags=["Задачи"])


def require_task(row):
    if row is None:
        raise HTTPException(404, "Задача не найдена")
    return dict(row)


@router.get("/statuses")
def statuses(db: DB, owner: Owner):
    return [dict(row) for row in db.execute("SELECT code, label FROM statuses ORDER BY rowid")]


@router.post("/tasks", status_code=201, response_model=Task)
def create_task(body: TaskFields, db: DB, owner: Owner):
    with db:
        row = db.execute(
            "INSERT INTO tasks(owner_id, title, description, status, topic, assignee) "
            f"VALUES (?, ?, ?, ?, ?, ?) RETURNING {COLUMNS}",
            (
                owner,
                body.title,
                body.description,
                body.status,
                body.topic,
                body.assignee,
            ),
        ).fetchone()
    return dict(row)


@router.get("/tasks", response_model=list[Task])
def list_tasks(
    db: DB,
    filters: Filters,
    limit: Annotated[int, Query(ge=1, le=100)] = 50,
    offset: Annotated[int, Query(ge=0, le=2**63 - 1)] = 0,
):
    rows = db.execute(
        f"SELECT {COLUMNS} FROM tasks WHERE {filters.where} ORDER BY id DESC LIMIT ? OFFSET ?",
        (*filters.values, limit, offset),
    )
    return [dict(row) for row in rows]


@router.get("/tasks/{task_id}", response_model=Task)
def read_task(task_id: TaskId, db: DB, owner: Owner):
    return require_task(
        db.execute(
            f"SELECT {COLUMNS} FROM tasks WHERE owner_id = ? AND id = ?",
            (owner, task_id),
        ).fetchone()
    )


@router.patch("/tasks/{task_id}", response_model=Task)
def update_task(task_id: TaskId, body: TaskUpdate, db: DB, owner: Owner):
    fields = body.model_dump(exclude_unset=True)
    # Имена столбцов получены из схемы TaskUpdate, дополнительные поля запрещены.
    assignments = ", ".join(f"{column} = ?" for column in fields)
    with db:
        row = db.execute(
            f"UPDATE tasks SET {assignments}, updated_at = strftime('%Y-%m-%dT%H:%M:%fZ','now') "
            f"WHERE owner_id = ? AND id = ? RETURNING {COLUMNS}",
            (*fields.values(), owner, task_id),
        ).fetchone()
        return require_task(row)


@router.delete("/tasks/{task_id}", status_code=204)
def delete_task(task_id: TaskId, db: DB, owner: Owner):
    with db:
        deleted = db.execute("DELETE FROM tasks WHERE owner_id = ? AND id = ?", (owner, task_id))
        if not deleted.rowcount:
            raise HTTPException(404, "Задача не найдена")
