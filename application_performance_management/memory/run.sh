#!/usr/bin/env bash
set -euo pipefail

lab_dir=$(cd -- "$(dirname -- "$0")" && pwd)
if [[ -n ${JAVA_HOME:-} ]]; then
    jdk="$JAVA_HOME/bin/"
else
    jdk=
fi
mkdir -p -- "$lab_dir/../target"
output=$(mktemp -d "$lab_dir/../target/memory.XXXXXX")
output=$(cd -- "$output" && pwd)
printf 'Artifacts: %s\n' "$output"

mkdir -p -- "$output/classes" "$output/leak" "$output/bounded"
"${jdk}java" -version 2>"$output/java-version.txt"
"${jdk}javac" --release 21 -d "$output/classes" "$lab_dir/MemoryPressure.java"

pids=()
trap 'for pid in "${pids[@]}"; do kill -- "$pid" 2>/dev/null || true; done' EXIT
trap 'exit 130' INT
trap 'exit 143' TERM

modes=(leak bounded)
for mode in "${modes[@]}"; do
    directory="$output/$mode"
    "${jdk}java" -Xms64m -Xmx64m -XX:+UseG1GC \
        "-Xlog:gc*,gc+heap=debug:file=$directory/gc.log:uptime,level,tags:filecount=0" \
        -XX:+HeapDumpOnOutOfMemoryError "-XX:HeapDumpPath=$directory/oom.hprof" \
        -XX:+ExitOnOutOfMemoryError -cp "$output/classes" MemoryPressure "$mode" "$directory" \
        >"$directory/application.log" 2>&1 &
    pids+=("$!")
done

leak_status=0
bounded_status=0
wait "${pids[0]}" || leak_status=$?
wait "${pids[1]}" || bounded_status=$?
pids=()
printf 'leak_exit=%s bounded_exit=%s\n' "$leak_status" "$bounded_status" | tee "$output/status.txt"

if [[ $leak_status -eq 0 || $bounded_status -ne 0 || ! -s "$output/leak/oom.hprof" ]] \
    || ! grep -q 'java.lang.OutOfMemoryError: Java heap space' "$output/leak/application.log"; then
    printf 'Unexpected outcome. Inspect %s\n' "$output" >&2
    exit 1
fi

for mode in "${modes[@]}"; do
    test -s "$output/$mode/early.hprof"
    test -s "$output/$mode/late.hprof"
done
