from contextlib import asynccontextmanager
from pathlib import Path

from fastapi import FastAPI, Request
from fastapi.exception_handlers import request_validation_exception_handler
from fastapi.exceptions import RequestValidationError
from fastapi.responses import JSONResponse

from . import analytics, auth, tasks
from .db import database_path, migrate


def create_app(path: Path | None = None):
    @asynccontextmanager
    async def lifespan(app: FastAPI):
        migrate(app.state.database)
        yield

    app = FastAPI(
        title="Трекер задач",
        version="1.0.0",
        lifespan=lifespan,
        description="Зарегистрируйтесь, получите access_token и введите его через Authorize.",
    )
    app.state.database = path or database_path()

    @app.exception_handler(RequestValidationError)
    async def validation_error(request: Request, exc: RequestValidationError):
        if request.scope.get("endpoint") in (auth.register, auth.login):
            # Даже ошибка отсутствующего поля может содержать всё тело запроса.
            errors = []
            for error in exc.errors():
                location = list(error["loc"])
                if location not in (["body", "username"], ["body", "password"]):
                    location = ["body"]
                errors.append({"loc": location, "type": error["type"], "msg": error["msg"]})
            return JSONResponse(status_code=422, content={"detail": errors})
        return await request_validation_exception_handler(request, exc)

    for router in (auth.router, tasks.router, analytics.router):
        app.include_router(router)
    return app


app = create_app()
