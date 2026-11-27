import os
import sqlite3
from contextlib import closing
from pathlib import Path
from typing import Annotated

from fastapi import Depends, Request


def database_path():
    return Path(os.getenv("TRACKER_DB", "data/tracker.sqlite3"))


def connect(path: Path):
    conn = sqlite3.connect(path, timeout=10, check_same_thread=False)
    conn.row_factory = sqlite3.Row
    conn.execute("PRAGMA foreign_keys = ON")
    return conn


def migrate(path: Path):
    path.parent.mkdir(parents=True, exist_ok=True)
    scripts = sorted(Path(__file__).with_name("migrations").glob("*.sql"))
    with closing(connect(path)) as conn, conn:
        conn.execute("BEGIN IMMEDIATE")
        current = conn.execute("PRAGMA user_version").fetchone()[0]
        if current > len(scripts):
            raise RuntimeError("Версия БД новее версии приложения")
        for version, script in enumerate(scripts, 1):
            if int(script.stem.split("_")[0]) != version:
                raise RuntimeError(f"Нарушен порядок миграций: {script}")
            if version <= current:
                continue
            statement = ""
            for line in script.read_text(encoding="utf-8").splitlines(True):
                statement += line
                if sqlite3.complete_statement(statement):
                    conn.execute(statement)
                    statement = ""
            if statement.strip():
                raise RuntimeError(f"Незавершённый SQL в {script}")
            conn.execute(f"PRAGMA user_version = {version}")


def connection(request: Request):
    with closing(connect(request.app.state.database)) as conn:
        yield conn


DB = Annotated[sqlite3.Connection, Depends(connection)]


if __name__ == "__main__":
    migrate(database_path())
