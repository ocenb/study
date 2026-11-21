"""Summarize throughput, errors and elapsed time from the JMeter CSV results."""

import argparse
import csv
import gzip
import math
from pathlib import Path
from statistics import mean
import sys


def read_results(path):
    open_file = gzip.open if path.suffix == ".gz" else open
    with open_file(path, "rt", encoding="utf-8", newline="") as stream:
        return list(csv.DictReader(stream))


def metrics(samples, seconds):
    elapsed = sorted(int(row["elapsed"]) for row in samples)
    errors = sum(row["success"] != "true" for row in samples)

    def percentile(p):
        return elapsed[math.ceil(len(elapsed) * p) - 1]

    return {
        "samples": len(samples),
        "errors": errors,
        "error_pct": round(100 * errors / len(samples), 2),
        "rps": round(len(samples) / seconds, 2),
        "mean_ms": round(mean(elapsed), 2),
        "p50_ms": percentile(0.50),
        "p95_ms": percentile(0.95),
        "p99_ms": percentile(0.99),
        "max_ms": elapsed[-1],
    }


def summarize(path, users, skip, seconds):
    rows = read_results(path)
    if not rows:
        raise ValueError(f"No JMeter samples in {path}")
    start = min(int(row["timeStamp"]) for row in rows) + skip * 1000
    stop = start + seconds * 1000
    if max(int(row["timeStamp"]) for row in rows) < stop - 1000:
        raise ValueError(f"{path} ends before the requested measurement window")

    window = [row for row in rows if start <= int(row["timeStamp"]) < stop]
    if not window or any(int(row["grpThreads"]) != users for row in window):
        raise ValueError(f"{path} has no complete window with {users} active threads")
    labels = sorted({row["label"] for row in rows})
    for label in ["ALL", *labels]:
        complete = rows if label == "ALL" else [row for row in rows if row["label"] == label]
        measured = window if label == "ALL" else [row for row in window if row["label"] == label]
        if measured:
            yield {
                "users": users, "label": label,
                "run_samples": len(complete),
                "run_errors": sum(row["success"] != "true" for row in complete),
                **metrics(measured, seconds),
            }


def plot(rows, output):
    import matplotlib.pyplot as plt
    from matplotlib.ticker import MaxNLocator

    overall = [row for row in rows if row["label"] == "ALL"]
    groups = [str(row["users"]) for row in overall]
    figure, (throughput, latency) = plt.subplots(1, 2, figsize=(9.5, 4.0), layout="constrained")
    bars = throughput.bar(groups, [row["rps"] for row in overall], color="#167182")
    throughput.bar_label(bars, fmt="%.1f", padding=4)
    throughput.margins(y=0.15)
    throughput.set(title="Пропускная способность", ylabel="Запросов/с")

    latency.plot(groups, [row["p95_ms"] for row in overall], "o-",
                 color="#315688", markersize=4, label="p95")
    latency.plot(groups, [row["p99_ms"] for row in overall], "o--",
                 color="#a8422b", markerfacecolor="none", markersize=8, label="p99")
    latency.set(title="Время отклика", ylabel="мс", ylim=(0, None))
    latency.yaxis.set_major_locator(MaxNLocator(integer=True))
    latency.legend(frameon=False)
    for axis in (throughput, latency):
        axis.set_xlabel("Одновременные пользователи")
        axis.grid(axis="y", alpha=0.25)
        axis.set_axisbelow(True)
    figure.savefig(output, dpi=160)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("files", type=Path, nargs="+", help="users-<count>.jtl or .jtl.gz")
    parser.add_argument("--skip", type=int, default=5, help="seconds after the first sample")
    parser.add_argument("--window", type=int, default=18, help="duration of the measurement in seconds")
    parser.add_argument("--chart", type=Path, help="optional PNG file (requires Matplotlib)")
    args = parser.parse_args()
    if args.skip < 0 or args.window <= 0:
        parser.error("skip must be nonnegative and window must be positive")
    files = sorted((int(path.name.split(".")[0].split("-")[-1]), path) for path in args.files)
    rows = [row for users, path in files for row in summarize(path, users, args.skip, args.window)]
    writer = csv.DictWriter(sys.stdout, fieldnames=rows[0], lineterminator="\n")
    writer.writeheader()
    writer.writerows(rows)
    if args.chart:
        plot(rows, args.chart)


if __name__ == "__main__":
    main()
