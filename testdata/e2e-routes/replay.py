# SPDX-License-Identifier: AGPL-3.0-or-later
"""Replay a recorded track into the Android emulator's GPS, faster than real time.

Usage: python -I replay.py <track.replay.csv> [--speed 2] [--from 0] [--serial emulator-5554]
       [--detour-at 300 --detour-m 120 --detour-s 40 --detour-ramp 10]

Sends one `adb emu geo fix` per CSV row (t_s,lat,lon,alt_m,accuracy_m), waiting (dt / speed) between
them. The emulator stamps each fix as it arrives, so the app sees speed x the real pace: keep --speed
small enough that the pace stays plausible for the sport (2 turns a 5:40/km run into 2:50/km).
--detour-* pushes the track sideways for a while, to test off-route alerts while following a route.
"""
import csv
import os
import subprocess
import sys
import time

SDK = next(p for p in (os.environ.get("ANDROID_HOME", ""), "D:/dev/android-sdk") if p and os.path.isdir(p))
ADB = os.path.join(SDK, "platform-tools", "adb")


def arg(name, default, cast=float):
    if name in sys.argv:
        return cast(sys.argv[sys.argv.index(name) + 1])
    return default


def main():
    path = sys.argv[1]
    speed = arg("--speed", 2.0)
    start = arg("--from", 0.0)
    serial = arg("--serial", "emulator-5554", str)
    detour_at = arg("--detour-at", -1.0)
    detour_m = arg("--detour-m", 120.0)
    detour_s = arg("--detour-s", 40.0)
    ramp_s = arg("--detour-ramp", 10.0)
    rows = [r for r in csv.DictReader(open(path, newline="")) if float(r["t_s"]) >= start]
    prev_t = None
    stalls = 0
    began = time.monotonic()
    for i, r in enumerate(rows):
        t = float(r["t_s"])
        lat, lon, alt = float(r["lat"]), float(r["lon"]), float(r["alt_m"])
        if detour_at >= 0 and detour_at <= t < detour_at + detour_s:
            # Shift north by detour_m, ramped in and out over ramp_s seconds of track time. Keep the sideways
            # speed (detour_m / ramp_s * speed) plausible, or the app rightly drops the fixes as GPS glitches.
            ramp = min(1.0, (t - detour_at) / ramp_s, (detour_at + detour_s - t) / ramp_s)
            lat += ramp * detour_m / 111_320.0
        if prev_t is not None:
            due = began + (t - start) / speed
            lag = time.monotonic() - due
            if lag > 0.5:
                # adb stalled: shift the schedule instead of catching up, since a burst of late fixes would
                # look like a sprint to the app and skew lap and best-effort times.
                began += lag
                stalls += 1
            else:
                time.sleep(max(0.0, -lag))
        prev_t = t
        subprocess.run([ADB, "-s", serial, "emu", "geo", "fix", f"{lon:.7f}", f"{lat:.7f}", f"{alt:.1f}"],
                       capture_output=True, check=False)
        if i % 60 == 0:
            print(f"t={t:.0f}s ({i + 1}/{len(rows)})", flush=True)
    print(f"done ({stalls} adb stalls absorbed)", flush=True)


if __name__ == "__main__":
    main()
