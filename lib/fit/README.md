# lib/fit

Apache-2.0 FIT (Flexible and Interoperable Data Transfer) encoder and decoder for activity files,
in pure Kotlin/JVM with no dependencies. Written from the public FIT protocol description; it does
not use or include any Garmin SDK code.

## High level

```kotlin
val bytes = FitActivityEncoder(FitEncodeOptions(compressTimestamps = true)).encode(activity)
val activity = FitActivityDecoder().decode(bytes)   // FitActivity
```

`FitActivity` holds `file_id`, `record` (timestamp, position, altitude, heart rate, cadence,
distance, speed, power, temperature), `event`, `lap`, `session` and `activity` messages in SI units.
Enumerations (`FitSport`, `FitLapTrigger`, ...) are `Int` constants so unknown codes survive.

Encoder layout is "summary last": `file_id` → events and records in time order → laps → sessions
→ `activity`. Record channels absent from every record are not defined; values that do not fit a
field are written as FIT invalid values; altitude and speed are written as both the 16-bit and
`enhanced_*` 32-bit fields. Timestamps are whole seconds. Optional compressed-timestamp headers.

## Low level (`protocol` package)

* `FitFileWriter`: definition/data messages (either byte order, arrays, strings, developer
  fields with protocol 2.0, compressed-timestamp headers), 14-byte header with CRC, file CRC.
* `FitFileReader`: generic decoder for any message: 12/14-byte headers, header and file CRC
  checks, both byte orders, compressed timestamps (with 5-bit roll-over), developer fields
  skipped but kept as raw bytes, odd field sizes read as bytes, chained files.
* `FitCrc`: the FIT CRC-16.

Not implemented (deferred): device_info/HRV/length/set mapping in the high-level model, decoding
developer field values via `field_description`, course files, and streaming decode of very large
files (the reader works on a byte array).

## Tests

`./gradlew :lib:fit:test`: round trips, golden bytes (`testdata/files/golden/*.fit`), corrupt-file
handling, and an independently handcrafted fixture (`testdata/files/fit/handcrafted-mixed.fit`,
built by `make_handcrafted_fit.py`). `testdata/files/crossval/crossvalidate.py` re-reads the golden
files with the Python `fitdecode` library.
