# SPDX-License-Identifier: AGPL-3.0-or-later
"""Cross-validates lib/fit and lib/gpx-tcx writer output with independent third-party parsers.

Reads the golden outputs in testdata/files/golden (produced by the Kotlin writers) with
fitdecode (FIT) and gpxpy (GPX) and checks the values against the deterministic sample defined in
lib/fit/src/test/.../SampleActivity.kt and lib/gpx-tcx/src/test/.../WriterTest.kt.

Setup (any temp dir):  python -m pip install --target <dir> fitdecode gpxpy
Run:                    PYTHONPATH=<dir> python testdata/files/crossval/crossvalidate.py
Exit code 0 = all checks passed.
"""
import datetime as dt
import math
import sys
from pathlib import Path

import fitdecode
import gpxpy

FILES = Path(__file__).resolve().parent.parent
GOLDEN = FILES / "golden"
UTC = dt.timezone.utc
failures = []


def check(label, actual, expected, tol=None):
    ok = (abs(actual - expected) <= tol) if tol is not None else actual == expected
    if not ok:
        failures.append(f"{label}: expected {expected!r}, got {actual!r}")


def fit_messages(path):
    out = []
    with fitdecode.FitReader(str(path), check_crc=fitdecode.CrcCheck.RAISE) as reader:
        for frame in reader:
            if isinstance(frame, fitdecode.FitDataMessage):
                out.append((frame.name, {f.name: f.value for f in frame.fields}))
    return out


def check_sample_fit(path):
    """Mirrors SampleActivity.kt."""
    start = dt.datetime(2026, 5, 10, 7, 0, 0, tzinfo=UTC)
    msgs = fit_messages(path)
    names = [n for n, _ in msgs]
    check(f"{path.name} message order head", names[:2], ["file_id", "event"])
    check(f"{path.name} summary tail", names[-4:], ["lap", "lap", "session", "activity"])
    file_id = msgs[0][1]
    check("file_id.type", file_id["type"], "activity")
    check("file_id.manufacturer", file_id["manufacturer"], "development")
    check("file_id.serial_number", file_id["serial_number"], 42424242)
    check("file_id.time_created", file_id["time_created"], start)
    check("file_id.product_name", file_id["product_name"], "lib-fit test")

    records = [f for n, f in msgs if n == "record"]
    check(f"{path.name} record count", len(records), 60)
    for i, r in enumerate(records):
        check(f"record[{i}].timestamp", r["timestamp"], start + dt.timedelta(seconds=i))
        check(f"record[{i}].lat", r["position_lat"], 536870912 + 1200 * i)
        check(f"record[{i}].lon", r["position_long"], 83521436 + 1800 * i)
        check(f"record[{i}].altitude", r["enhanced_altitude"], (1250 + i) / 5 - 500, tol=1e-9)
        check(f"record[{i}].altitude16", r["altitude"], (1250 + i) / 5 - 500, tol=1e-9)
        check(f"record[{i}].heart_rate", r["heart_rate"], 120 + i % 30)
        check(f"record[{i}].cadence", r["cadence"], 85 + i % 5)
        check(f"record[{i}].distance", r["distance"], i * 3.25, tol=1e-9)
        check(f"record[{i}].speed", r["enhanced_speed"], (3250 + 10 * (i % 7)) / 1000, tol=1e-9)
        check(f"record[{i}].power", r["power"], None if i % 10 == 9 else 250 + i)
        check(f"record[{i}].temperature", r["temperature"], 18 - i // 30)

    events = [f for n, f in msgs if n == "event"]
    check("events", [(e["event"], e["event_type"]) for e in events], [("timer", "start"), ("timer", "stop_all")])
    laps = [f for n, f in msgs if n == "lap"]
    check("lap[0].total_distance", laps[0]["total_distance"], 94.25, tol=1e-9)
    check("lap[0].lap_trigger", laps[0]["lap_trigger"], "distance")
    check("lap[0].avg_power", laps[0]["avg_power"], 264)
    check("lap[1].intensity", laps[1]["intensity"], "cooldown")
    check("lap[1].total_elapsed_time", laps[1]["total_elapsed_time"], 29.5, tol=1e-9)
    session = next(f for n, f in msgs if n == "session")
    check("session.sport", session["sport"], "running")
    check("session.sub_sport", session["sub_sport"], "trail")
    check("session.total_distance", session["total_distance"], 191.75, tol=1e-9)
    check("session.num_laps", session["num_laps"], 2)
    check("session.enhanced_avg_speed", session["enhanced_avg_speed"], 3.255, tol=1e-9)
    activity = next(f for n, f in msgs if n == "activity")
    check("activity.num_sessions", activity["num_sessions"], 1)
    check("activity.total_timer_time", activity["total_timer_time"], 58.5, tol=1e-9)
    local = activity["local_timestamp"].replace(tzinfo=None)
    check("activity.local_timestamp", local, dt.datetime(2026, 5, 10, 9, 0, 59))


def check_handcrafted_fit():
    msgs = fit_messages(FILES / "fit" / "handcrafted-mixed.fit")
    records = [f for n, f in msgs if n == "record"]
    seconds = [int(r["timestamp"].timestamp()) for r in records]
    check("handcrafted roll-over", [s - seconds[0] for s in seconds], [0, 1, 2, 5])
    check("handcrafted dev field", records[0].get("doughnuts"), 7)


def check_gpx(name, has_speed):
    """Mirrors WriterTest.kt samplePoints."""
    start = dt.datetime(2026, 5, 10, 7, 0, 0, tzinfo=UTC)
    with open(GOLDEN / name, encoding="utf-8") as fh:
        gpx = gpxpy.parse(fh)
    check(f"{name} creator", gpx.creator, "golden test")
    check(f"{name} metadata name", gpx.name, "Golden <ride> & co")
    track = gpx.tracks[0]
    check(f"{name} track", (track.name, track.type), ("Golden ride", "cycling"))
    check(f"{name} segments", [len(s.points) for s in track.segments], [4, 2])
    points = [p for s in track.segments for p in s.points]
    decimals = 6 if has_speed else 7
    for i, p in enumerate(points):
        check(f"{name}[{i}].lat", p.latitude, round(45 + i * 0.0001, decimals), tol=1e-12)
        check(f"{name}[{i}].lon", p.longitude, round(7 + i * 0.00015, decimals), tol=1e-12)
        check(f"{name}[{i}].ele", p.elevation, 250 + i * 0.5, tol=1e-9)
        check(f"{name}[{i}].time", p.time, start + dt.timedelta(seconds=i))
        ext = {}
        for e in p.extensions:
            if e.tag.endswith("power") or e.tag.endswith("PowerInWatts"):
                ext["power"] = e.text
            for child in e:
                ext[child.tag.split("}")[-1]] = child.text
        check(f"{name}[{i}].hr", int(ext["hr"]), 120 + i)
        check(f"{name}[{i}].cad", int(ext["cad"]), 85 + i % 3)
        check(f"{name}[{i}].atemp", float(ext["atemp"]), 18.5)
        check(f"{name}[{i}].power", int(ext["power"]), 200 + 5 * i)
        if has_speed:
            check(f"{name}[{i}].speed", float(ext["speed"]), 6 + i * 0.125, tol=1e-9)
        else:
            check(f"{name}[{i}].no speed", "speed" in ext, False)


if __name__ == "__main__":
    check_sample_fit(GOLDEN / "encoder-sample-run.fit")
    check_sample_fit(GOLDEN / "encoder-sample-run-compressed.fit")
    check_handcrafted_fit()
    check_gpx("writer-ride-tpx-v1.gpx", has_speed=False)
    check_gpx("writer-ride-tpx-v2.gpx", has_speed=True)
    if failures:
        print("\n".join(failures))
        print(f"FAILED: {len(failures)} check(s)")
        sys.exit(1)
    print(f"OK: fitdecode {fitdecode.__version__}, gpxpy {gpxpy.__version__} agree with the Kotlin writers")
