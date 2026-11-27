import hashlib
import secrets
import time
from typing import Annotated

from argon2 import PasswordHasher
from argon2.exceptions import VerifyMismatchError
from fastapi import APIRouter, Depends, HTTPException
from fastapi.security import HTTPAuthorizationCredentials, HTTPBearer
from pydantic import BaseModel, ConfigDict, Field, StringConstraints

from .db import DB

router = APIRouter(prefix="/auth", tags=["Доступ"])
hasher = PasswordHasher()
bearer = HTTPBearer(auto_error=False)
Credentials = Annotated[HTTPAuthorizationCredentials | None, Depends(bearer)]
SESSION_SECONDS = 24 * 60 * 60


class Login(BaseModel):
    model_config = ConfigDict(extra="forbid")
    username: Annotated[str, StringConstraints(strip_whitespace=True, min_length=1, max_length=64)]
    password: str = Field(min_length=8, max_length=256)


def unauthorized():
    return HTTPException(
        401,
        "Проверьте учётные данные или токен",
        headers={"WWW-Authenticate": "Bearer"},
    )


def digest(token):
    return hashlib.sha256(token.encode()).hexdigest()


def current_user(db: DB, credentials: Credentials):
    if credentials:
        row = db.execute(
            "SELECT user_id FROM sessions WHERE token_hash = ? AND expires_at > ?",
            (digest(credentials.credentials), int(time.time())),
        ).fetchone()
        if row:
            return row["user_id"]
    raise unauthorized()


Owner = Annotated[int, Depends(current_user)]


@router.post("/register", status_code=201)
def register(body: Login, db: DB):
    password_hash = hasher.hash(body.password)
    with db:
        row = db.execute(
            "INSERT INTO users(username, password_hash) VALUES (?, ?) "
            "ON CONFLICT(username) DO NOTHING RETURNING id, username",
            (body.username, password_hash),
        ).fetchone()
        if row is None:
            raise HTTPException(409, "Имя уже занято")
    return dict(row)


@router.post("/login")
def login(body: Login, db: DB):
    row = db.execute("SELECT * FROM users WHERE username = ?", (body.username,)).fetchone()
    if row is None:
        raise unauthorized()
    try:
        hasher.verify(row["password_hash"], body.password)
    except VerifyMismatchError:
        raise unauthorized() from None
    token = secrets.token_urlsafe(32)
    now = int(time.time())
    with db:
        db.execute("DELETE FROM sessions WHERE expires_at <= ?", (now,))
        db.execute(
            "INSERT INTO sessions(token_hash, user_id, expires_at) VALUES (?, ?, ?)",
            (digest(token), row["id"], now + SESSION_SECONDS),
        )
    return {
        "access_token": token,
        "token_type": "bearer",
        "expires_in": SESSION_SECONDS,
    }


@router.post("/logout", status_code=204)
def logout(db: DB, owner: Owner, credentials: Credentials):
    with db:
        db.execute(
            "DELETE FROM sessions WHERE token_hash = ? AND user_id = ?",
            (digest(credentials.credentials), owner),
        )
