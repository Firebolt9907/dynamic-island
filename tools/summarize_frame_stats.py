#!/usr/bin/env python3
"""Summarize per-window Android `dumpsys gfxinfo PACKAGE framestats` output.

Only Flags=0 rows with completed timestamps count. Latency includes scheduling and
rendering; this is a short diagnostic sample, not an isolated GPU benchmark.
"""
import argparse
import csv
import json
import math
import statistics
from pathlib import Path


def summarize(contents):
    windows = {}
    window = None
    header = None
    for line in contents.splitlines():
        if line.startswith("Window:"):
            window = line.partition(":")[2].strip()
            header = None
        elif line.startswith("Flags,") and window:
            header = next(csv.reader([line]))
            windows.setdefault(window, [])
        elif line == "---PROFILEDATA---":
            header = None
        elif header and line and line[0].isdigit():
            values = next(csv.reader([line]))
            windows[window].append({key: int(value) for key, value in zip(header, values) if key and value})

    results = {}
    for name, rows in windows.items():
        usable = [r for r in rows if r.get("Flags") == 0
                  and r.get("FrameCompleted", 0) < 9_223_372_036_854_775_807
                  and r.get("FrameCompleted", 0) > r.get("IntendedVsync", 0) > 0]
        times = sorted((r["FrameCompleted"] - r["IntendedVsync"]) / 1e6 for r in usable)
        def percentile(p):
            return round(times[max(0, math.ceil(len(times) * p) - 1)], 2) if times else None
        results[name] = {
            "valid_frames": len(usable),
            "excluded_rows": len(rows) - len(usable),
            "latency_ms": {"median": round(statistics.median(times), 2) if times else None,
                           "p90": percentile(.9), "p99": percentile(.99),
                           "max": round(max(times), 2) if times else None},
            "frames_over_reported_interval": sum(
                r["FrameCompleted"] - r["IntendedVsync"] > r["FrameInterval"]
                for r in usable if r.get("FrameInterval", 0) > 0),
            "deadline_misses": sum(r["FrameCompleted"] > r["FrameDeadline"]
                                   for r in usable if r.get("FrameDeadline", 0) > 0),
        }
    return results


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("capture", type=Path)
    args = parser.parse_args()
    print(json.dumps(summarize(args.capture.read_text()), indent=2))
