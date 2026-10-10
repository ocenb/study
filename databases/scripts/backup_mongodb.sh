#!/usr/bin/env bash
# Сжатый mongodump-архив одной БД MongoDB из Docker Compose.
set -euo pipefail
umask 077

script_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
compose_file="$script_dir/../docker-compose.yml"
backup_dir="${1:-$script_dir/../backups}"
mongo_db="${MONGO_DB:-shop_db}"
mkdir -p -- "$backup_dir"
temporary_file="$(mktemp "$backup_dir/mongodb_$(date -u +%Y%m%dT%H%M%SZ)_XXXXXX.archive.gz.partial")"
trap 'rm -f -- "$temporary_file"' EXIT

docker compose -f "$compose_file" exec -T mongodb \
    mongodump --db "$mongo_db" --archive --gzip > "$temporary_file"
test -s "$temporary_file"
backup_file="${temporary_file%.partial}"
mv -- "$temporary_file" "$backup_file"
printf '%s\n' "$backup_file"
