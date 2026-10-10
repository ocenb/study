#!/usr/bin/env bash
# Резервная копия одной БД PostgreSQL из Docker Compose (custom format).
set -euo pipefail
umask 077

script_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
compose_file="$script_dir/../docker-compose.yml"
backup_dir="${1:-$script_dir/../backups}"
mkdir -p -- "$backup_dir"
temporary_file="$(mktemp "$backup_dir/postgres_$(date -u +%Y%m%dT%H%M%SZ)_XXXXXX.dump.partial")"
trap 'rm -f -- "$temporary_file"' EXIT

docker compose -f "$compose_file" exec -T postgres sh -c \
    'PGPASSWORD="$POSTGRES_PASSWORD" exec pg_dump --username="$POSTGRES_USER" --dbname="$POSTGRES_DB" --format=custom --no-owner --no-acl' \
    > "$temporary_file"
test -s "$temporary_file"
backup_file="${temporary_file%.partial}"
mv -- "$temporary_file" "$backup_file"
printf '%s\n' "$backup_file"
