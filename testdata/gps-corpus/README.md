<!-- SPDX-License-Identifier: CC0-1.0 -->
# GPS golden corpus

Synthetic GPS tracks, each replayed through the production `RecordingEngine`
(`android/core/recording`) and checked against golden files. The full test plan is in
[`docs/99-gaps/gps-qa-test-performance.md`](../../docs/99-gaps/gps-qa-test-performance.md) (§2 and §3).

| Path | What |
|---|---|
| `manifest.yaml` | One entry per scenario: id, sport, source, seed, license, tags, track file, golden file |
| `synthetic/*.sfraw.jsonl` | Raw recorder events, one JSON object per line (format below) |
| `golden/*.expected.json` | Expected metrics with per-metric tolerances |

All files here are **generated**. Do not edit them by hand.

## How the files are made

`TrackGen` (`android/core/recording/src/test/kotlin/.../corpus/`) builds the ground truth first: a path,
a piecewise speed profile and an altitude profile. It then corrupts the truth with seeded noise and faults:

- correlated (Gauss–Markov) position error
- accuracy values that are misreported
- multipath jumps
- GPS warm-up
- tunnels and other gaps
- wall-clock jumps
- barometer drift

Each scenario lives in `Scenarios.kt`. Every value in a golden file is **analytic ground truth from the
generator**. No golden value comes from engine output.

Each scenario's ID matches the scenario catalogue in the QA doc (§2.3), and each tolerance matches the
tolerance policy (§3.3).

```sh
# Regenerate after changing TrackGen or a scenario, then review the diff
./gradlew :android:core:recording:generateGpsCorpus

# Golden replay, determinism, snapshot/restore and corpus-drift checks
./gradlew :android:core:recording:test
```

`CorpusDriftTest` regenerates the corpus in memory and fails if any checked-in file differs. A golden file
can therefore change only when the generator or a scenario changes, and that change is visible in review.

## `.sfraw.jsonl` format

```
{"k":"meta","id":"S001","sport":"Run","autoPause":false,"units":"metric","seed":1001}
{"k":"act","t":<monotonic ns>,"a":"START","wall":<epoch ms>}
{"k":"loc","t":..,"wall":..,"lat":..,"lon":..,"acc":..,"alt":..,"vacc":..,"spd":..,"brg":..}
{"k":"baro","t":..,"hpa":..}
{"k":"hr","t":..,"bpm":..}
```

- `t` is the monotonic clock in nanoseconds. All durations come from it.
- `wall` is the device wall clock, which may jump.
- The file does not store ticks. The replay harness adds one tick per second, as the recording service does.

## Golden semantics

- **Observable window.** The window starts at the first fix with an accuracy of 50 m or better and ends at
  the last fix before FINISH. Distance, splits and laps are measured inside this window, because the
  engine cannot measure anything outside it.
- **Moving time.** If auto-pause is on, or the athlete paused manually, moving time is timer time. This
  matches the Strava-compatible rule. Otherwise, moving time is computed from the track.
- **Tolerances.** Each metric has a tolerance, written as `tol_abs`, `tol_pct`, or
  `tol_pct_over`/`tol_pct_under`. The allowed band is the larger of the absolute and the percentage
  tolerance.
- **Exact counts.** Split counts, lap counts and flags must match exactly. Scenario distances are chosen
  away from kilometre multiples so the split count is never ambiguous.
