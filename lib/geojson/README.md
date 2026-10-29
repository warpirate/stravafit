# lib/geojson

Apache-2.0 RFC 7946 GeoJSON writer and reader in pure Kotlin/JVM (stdlib + JDK only, no JSON
library). Self-contained model: `Position`, the seven geometry types, `Feature`, `FeatureCollection`.

```kotlin
val line = LineString(points.map { Position.fromLatLon(it.lat, it.lon, it.ele) })
val json = GeoJsonWriter(GeoJsonWriteOptions(coordinateDecimals = 6, includeBbox = true))
    .write(Feature(line, mapOf("name" to "Morning Ride", "distance_m" to 10_230.4)))
val parsed = GeoJsonReader().read(json)
```

* Coordinates are always written **longitude, latitude[, altitude]** (RFC 7946 §3.1.1);
  `Position.fromLatLon` exists so callers cannot swap them silently.
* Optional coordinate/altitude precision (default 6 / 1 decimals), optional 2-D `bbox`,
  compact or pretty output; deterministic member order, no `crs`.
* The reader is strict JSON (RFC 8259), depth-limited, ignores `bbox`, `crs`, foreign members and
  position elements beyond the third, and reports errors with a JSON path or line/column.
* Privacy trimming is the caller's job: pass trimmed geometry (a `MultiLineString` works well for a
  track cut into several visible runs).

Tests: `./gradlew :lib:geojson:test` (fixtures in `testdata/files/geojson`, golden in
`testdata/files/golden`, regenerate with `UPDATE_GOLDENS=true`).
