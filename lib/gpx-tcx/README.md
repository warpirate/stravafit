# lib/gpx-tcx

Apache-2.0 GPX 1.1 / TCX v2 reader and writer in pure Kotlin/JVM (stdlib only, with a built-in XML pull
tokenizer, so it runs unchanged on Android, where `javax.xml.stream` is missing). Self-contained: it owns its model (`TrackPoint`, `GpxDocument`, `TcxDocument`) and does
not depend on the app's AGPL modules.

## Reading

```kotlin
val gpx = GpxReader(recoverPartial = true).read(input)   // GpxDocument
val tcx = TcxReader().read(input)                         // TcxDocument
```

* Namespace-agnostic: GPX 1.0/1.1, any prefix, Garmin TrackPointExtension v1/v2, Cluetrust
  `gpxdata`, Garmin PowerExtension, and generic `<heartrate>`, `<cadence>`, `<power>` tags.
* Tolerant: BOM / whitespace before `<?xml`, zone-less or offset timestamps, `131.0`-style integers,
  garbage values become `null`, invalid positions are skipped (GPX) or nulled (TCX indoor files),
  missing TCX lap totals are derived from trackpoints.
* `recoverPartial` returns what was read from a file that broke off part-way (`truncated = true`).
* Hardened: DTDs and external entities are rejected (no XXE).
* Waypoints, TCX `Courses` and `Workouts` are not read.

## Writing

```kotlin
GpxWriter(GpxWriteOptions(extensionVersion = TrackPointExtensionVersion.V2)).write(doc, out)
TcxWriter().write(tcxDoc, out)
```

Output is deterministic (fixed indentation, `\n`, trimmed decimals, UTC timestamps) so it can be
golden-tested. Writers do **not** apply privacy trimming; pass already-trimmed tracks.

GPX power defaults to Garmin PowerExtension `<pwr:PowerInWatts>`, which keeps the file valid
against the GPX 1.1 XSD. `GpxPowerFormat.PLAIN` writes the popular but schema-invalid bare
`<power>` element instead. `TcxLap.fromPoints(points, startDistance = ...)` derives a lap's
distance from activity-cumulative trackpoint distances.

## Tests

`./gradlew :lib:gpx-tcx:test` reads fixtures from `testdata/files/{gpx,tcx}` and compares writer
output with `testdata/files/golden`. Regenerate goldens with `UPDATE_GOLDENS=true`.
