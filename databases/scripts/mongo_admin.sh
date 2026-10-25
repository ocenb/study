#!/usr/bin/env bash
# Запуск mongosh/mongodump/mongorestore с административной аутентификацией.
set -euo pipefail
script_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
if [[ $# -eq 0 ]]; then
    printf 'Usage: %s mongosh|mongodump|mongorestore [arguments...]\n' "$0" >&2
    exit 2
fi
case "$1" in
    mongosh|mongodump|mongorestore) ;;
    *) printf 'Unsupported MongoDB tool\n' >&2; exit 2 ;;
esac
docker compose -f "$script_dir/../docker-compose.yml" exec -T mongodb sh -c '
    tool="$1"; shift
    exec "$tool" --username "$MONGO_INITDB_ROOT_USERNAME" \
        --password "$MONGO_INITDB_ROOT_PASSWORD" --authenticationDatabase admin "$@"
' sh "$@"
