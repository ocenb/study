FROM ghcr.io/astral-sh/uv:0.11.14 AS uv
FROM python:3.14-slim AS base

COPY --from=uv /uv /uvx /bin/
WORKDIR /app
ENV UV_COMPILE_BYTECODE=1 \
    UV_LINK_MODE=copy \
    PYTHONUNBUFFERED=1 \
    PATH="/app/.venv/bin:$PATH"

COPY pyproject.toml uv.lock README.md ./
RUN uv sync --locked --no-dev --no-install-project
COPY . .
RUN useradd --create-home app

FROM base AS test
RUN uv sync --locked --no-dev --group test --no-install-project
USER app
CMD ["pytest", "-v", "-p", "no:cacheprovider", "--junitxml=/tmp/postgres-tests.xml"]

FROM base AS runtime
USER app
CMD ["python", "main.py"]
