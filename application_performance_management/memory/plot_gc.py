"""Plot heap occupancy after GC for the two G1 logs produced by run.sh."""

import argparse
from pathlib import Path
import re

import matplotlib.pyplot as plt


AFTER_GC = re.compile(
    r"^\[([\d.]+)s\]\[debug\]\[gc,heap\s*\] GC\((\d+)\) Heap after GC[^\n]*\n"
    r"\[[\d.]+s\]\[debug\]\[gc,heap\s*\] GC\(\2\)\s+"
    r"garbage-first heap\s+total\s+(\d+)K, used\s+(\d+)K",
    re.MULTILINE,
)
DUMP_PAUSE = re.compile(
    r"^\[([\d.]+)s\]\[info\s*\]\[gc\s*\] GC\(\d+\) Pause Full \(Heap Dump Initiated GC\)",
    re.MULTILINE,
)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("leak_log", type=Path)
    parser.add_argument("bounded_log", type=Path)
    parser.add_argument("output", type=Path)
    args = parser.parse_args()

    leak_data = args.leak_log.read_text()
    figure, axis = plt.subplots(figsize=(8.5, 4.4), layout="constrained")
    capacities = set()
    for data, label, color in [
        (leak_data, "Без ограничения", "#b64118"),
        (args.bounded_log.read_text(), "Не более 8 порций", "#165f88"),
    ]:
        samples = AFTER_GC.findall(data)
        if not samples:
            parser.error(f"No G1 heap-after-GC samples for {label}")
        capacities.update(int(capacity) for _, _, capacity, _ in samples)
        axis.plot(
            [float(timestamp) for timestamp, _, _, _ in samples],
            [int(used) / 1024 for _, _, _, used in samples],
            "o-", color=color, markersize=3.5, linewidth=1.6, label=label,
        )

    if len(capacities) != 1:
        parser.error("Both logs must use the same fixed heap capacity")
    heap_capacity_mib = capacities.pop() / 1024
    for timestamp in DUMP_PAUSE.findall(leak_data):
        axis.axvline(float(timestamp), color="#9da6b0", linewidth=1, linestyle=":")
    axis.axhline(heap_capacity_mib, color="#555", linewidth=1,
                 linestyle="--", label="Размер кучи")
    axis.set(xlabel="Время работы JVM, с", ylabel="Занято после GC, МиБ",
             title="Занятость кучи после сборок G1", xlim=(0, None),
             ylim=(0, heap_capacity_mib * 1.07))
    axis.grid(axis="y", alpha=0.25)
    axis.legend(loc="upper left", frameon=False)
    figure.savefig(args.output, dpi=160)


if __name__ == "__main__":
    main()
