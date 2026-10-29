# Android

| Module | What it is |
|---|---|
| `:android:core:designsystem` | Ridgeline theme (colours, type, shapes, spacing, motion), bundled OFL fonts, shared components |
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
```

## Screenshots (Roborazzi, Robolectric native graphics)

```
./gradlew :android:app:recordRoborazziGmsDebug            # writes android/app/screenshots/*.png
./gradlew :android:core:designsystem:recordRoborazziDebug # writes android/core/designsystem/screenshots/*.png
./gradlew :android:app:compareRoborazziGmsDebug           # diff against the committed images
```

The committed PNGs are reference renders from Windows. Font rasterisation differs slightly
between operating systems, so regenerate them on the CI OS before turning on `verifyRoborazzi*`.

## Sample data

Until `:core:data`, `:core:location` and `:core:recording` exist, the app runs on in-memory sample
repositories (`data/Sample*Repository`) and a simulated GPS fix (`SimulatedGpsStatusSource`). Entry
points to screens that are not built yet show a short "isn't in this preview build yet" snackbar.
