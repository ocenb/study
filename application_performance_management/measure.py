#!/usr/bin/env python3
"""Measure each route from the Actuator COUNT under the same HTTP load."""

import argparse
from collections import Counter
from concurrent.futures import ThreadPoolExecutor
from http.client import HTTPException, HTTPConnection
import json
import sys
from threading import Barrier, Event
import time
from urllib.parse import urlencode
from urllib.request import urlopen


HTTP_HOST = "127.0.0.1"
HTTP_PORT = 8080
CLIENTS = 64
WARMUP_SECONDS = 12
WINDOW_SECONDS = 10
POST_WINDOW_SECONDS = 10
IDLE_SECONDS = 12


def probe_route(route, expect_virtual):
    started = time.monotonic()
    with urlopen(f"http://{HTTP_HOST}:{HTTP_PORT}{route}", timeout=35) as response:
        virtual = json.load(response)["virtualThread"]
    if virtual is not expect_virtual:
        raise RuntimeError(f"{route} reported unexpected thread type: {virtual}")
    return {"virtual_thread": virtual, "elapsed_seconds": time.monotonic() - started}


def actuator_sample(route):
    query = urlencode([("tag", f"uri:{route}"), ("tag", "method:GET")])
    url = f"http://{HTTP_HOST}:{HTTP_PORT}/actuator/metrics/http.server.requests?{query}"
    started = time.monotonic()
    utc_started = time.time()
    with urlopen(url, timeout=5) as response:
        metric = json.load(response)
    offset = (time.monotonic() - started) / 2
    measurements = {entry["statistic"]: entry["value"] for entry in metric["measurements"]}
    return {
        "utc_epoch_seconds": utc_started + offset,
        "monotonic_seconds": started + offset,
        "count": int(measurements["COUNT"]),
        "total_time_seconds": measurements["TOTAL_TIME"],
    }


def send_load(route, ready, stop):
    outcomes = Counter()
    connection = HTTPConnection(HTTP_HOST, HTTP_PORT, timeout=35)
    try:
        ready.wait()
        while not stop.is_set():
            try:
                connection.request("GET", route)
                response = connection.getresponse()
                response.read()
                outcomes[str(response.status)] += 1
            except (HTTPException, OSError):
                outcomes["transport_error"] += 1
                connection.close()
                stop.wait(0.1)
    finally:
        connection.close()
    return outcomes


def measure(route, expect_virtual):
    probe = probe_route(route, expect_virtual)
    print(f"{route}: probe {probe['elapsed_seconds']:.3f}s, starting load",
          file=sys.stderr, flush=True)
    ready = Barrier(CLIENTS + 1)
    stop = Event()
    totals = Counter()
    with ThreadPoolExecutor(max_workers=CLIENTS) as pool:
        workers = [pool.submit(send_load, route, ready, stop) for _ in range(CLIENTS)]
        load_start = time.time()
        ready.wait()
        try:
            time.sleep(WARMUP_SECONDS)
            first = actuator_sample(route)
            deadline = first["monotonic_seconds"] + WINDOW_SECONDS
            time.sleep(max(0, deadline - time.monotonic()))
            second = actuator_sample(route)
            time.sleep(POST_WINDOW_SECONDS)
        finally:
            stop.set()
        stopped = time.time()
        for worker in workers:
            totals.update(worker.result())

    elapsed = second["monotonic_seconds"] - first["monotonic_seconds"]
    difference = second["count"] - first["count"]
    if difference <= 0:
        raise RuntimeError(f"{route}: COUNT did not grow")
    requests_per_second = difference / elapsed
    print(f"{route}: {first['count']} → {second['count']} in {elapsed:.3f}s"
          f" = {requests_per_second:.2f} req/s", file=sys.stderr, flush=True)
    return {
        "route": route,
        "probe": probe,
        "load_start_epoch_seconds": load_start,
        "stop_sending_epoch_seconds": stopped,
        "load_drained_epoch_seconds": time.time(),
        "first": first,
        "second": second,
        "elapsed_seconds": elapsed,
        "completed_requests": difference,
        "requests_per_second": requests_per_second,
        "mean_duration_seconds": (second["total_time_seconds"] - first["total_time_seconds"]) / difference,
        "client_outcomes_including_warmup_and_drain": totals,
    }


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--output", type=argparse.FileType("w"), default="-")
    args = parser.parse_args()

    graph_start = time.time()
    platform = measure("/work/platform", False)
    time.sleep(IDLE_SECONDS)
    virtual = measure("/work/virtual", True)
    time.sleep(IDLE_SECONDS)
    result = {
        "clients": CLIENTS,
        "target_window_seconds": WINDOW_SECONDS,
        "graph_from_epoch_ms": round(graph_start * 1_000),
        "graph_to_epoch_ms": round(time.time() * 1_000),
        "measurements": [platform, virtual],
    }
    with args.output as output:
        json.dump(result, output, indent=2)
        output.write("\n")


if __name__ == "__main__":
    main()
