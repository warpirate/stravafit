# Android

| Module | What it is |
|---|---|
| `:android:core:designsystem` | Ridgeline theme (colours, type, shapes, spacing, motion), bundled OFL fonts, shared components |
| `:android:core:database` | Room: activities, points, laps, splits, best efforts, recording journal, routes. Schemas in `schemas/`, checked by `MigrationTest` |
| `:android:core:datastore` | Settings in DataStore: units, theme, per-sport auto-pause, audio cues, default sport and visibility, privacy radii |
| `:android:core:location` | `LocationSource` (fused in gms, platform GPS in foss), `GpsStatusMonitor`, barometer `PressureSource` |
| `:android:core:data` | `ActivityRepository` (save finished recordings, stats, personal bests), `RouteRepository`, `SettingsRepository` |
| `:android:core:recording-service` | `RecordingController` (Hilt singleton) and `RecordingService` (foreground, type `location`): engine feed, Room journal and crash recovery, notification, wake lock, audio cues, permission sheets |
| `:android:app` | App shell: single activity, Hilt, type-safe Navigation Compose, bottom bar, Feed / Explore / Record / Groups / Me / Settings |

Package names use the placeholder `org.openactivity.*` (blueprint §2, ADR-004); `app_name` in
`android/app/src/main/res/values/strings.xml` is the one-line rename point.

## Flavours (ADR-011)

| Flavour | Purpose | Rule |
|---|---|---|
| `gms` | Play Store | Google-only dependencies go in `gmsImplementation` |
| `foss` | F-Droid | No Google services; `fossReleaseRuntimeClasspath` must contain no `com.google.android.gms` or Firebase |

Flavour differences are bound by DI (`src/<flavour>/.../DistributionModule.kt`), not by `if` checks.

## Build and test

```
./gradlew :android:app:assembleGmsDebug :android:app:assembleFossDebug
./gradlew :android:app:testGmsDebugUnitTest :android:core:designsystem:testDebugUnitTest
./gradlew :android:core:database:testDebugUnitTest :android:core:datastore:testDebugUnitTest   :android:core:location:testGmsDebugUnitTest :android:core:data:testDebugUnitTest   :android:core:recording-service:testGmsDebugUnitTest
```

`RecordingControllerTest` replays `testdata/gps-corpus` tracks through a fake `LocationSource` into the
real controller, engine, journal and Room database in virtual time, including a simulated process death.

## Screenshots (Roborazzi, Robolectric native graphics)

```
./gradlew :android:app:recordRoborazziGmsDebug            # writes android/app/screenshots/*.png
./gradlew :android:core:designsystem:recordRoborazziDebug # writes android/core/designsystem/screenshots/*.png
./gradlew :android:app:compareRoborazziGmsDebug           # diff against the committed images
```

The committed PNGs are reference renders from Windows. Font rasterisation differs slightly
between operating systems, so regenerate them on the CI OS before turning on `verifyRoborazzi*`.

## Sample data

The Record pre-start GPS status is real (`DeviceGpsStatusSource` over `:core:location`). The feed, profile
and route screens still read in-memory sample repositories (`data/Sample*Repository`) until they are
moved onto `:core:data`. Entry
points to screens that are not built yet show a short "isn't in this preview build yet" snackbar.
