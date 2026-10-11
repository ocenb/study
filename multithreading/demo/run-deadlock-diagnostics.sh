#!/bin/sh
# Запускает зависшую JVM, снимает настоящий jstack и завершает только созданный процесс.
set -eu
cd "$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)"
output_dir=${1:-target/deadlock-diagnostics}
mkdir -p "$output_dir" target/liveness-demo
if [ -n "${JAVA_HOME:-}" ]; then
    java_cmd="$JAVA_HOME/bin/java"
    javac_cmd="$JAVA_HOME/bin/javac"
    jstack_cmd="$JAVA_HOME/bin/jstack"
else
    java_cmd=java
    javac_cmd=javac
    jstack_cmd=jstack
fi
"$javac_cmd" -Xlint:all -encoding UTF-8 -d target/liveness-demo \
    src/main/java/com/example/demo/liveness/*.java
"$java_cmd" -cp target/liveness-demo com.example.demo.liveness.DeadlockDemo \
    > "$output_dir/process.log" 2>&1 &
deadlock_pid=$!
cleanup() {
    # interrupt не выводит потоки из ожидания synchronized; завершаем отдельную JVM.
    kill -KILL "$deadlock_pid" 2>/dev/null || true
    wait "$deadlock_pid" 2>/dev/null || true
}
trap cleanup EXIT
trap 'exit 130' INT
trap 'exit 143' TERM
attempt=0
until grep -q DEADLOCK_CONFIRMED "$output_dir/process.log"; do
    if ! kill -0 "$deadlock_pid" 2>/dev/null; then
        cat "$output_dir/process.log"
        exit 1
    fi
    attempt=$((attempt + 1))
    if [ "$attempt" -ge 100 ]; then
        echo 'Deadlock не подтверждён за время ожидания' >&2
        exit 1
    fi
    sleep 0.1
done
# timeout ограничивает ожидание подключения диагностического инструмента.
timeout 15 "$jstack_cmd" -l "$deadlock_pid" > "$output_dir/jstack.txt"
grep -q 'Found one Java-level deadlock' "$output_dir/jstack.txt"
cat "$output_dir/process.log"
printf 'Дамп сохранён: %s/jstack.txt\n' "$output_dir"
