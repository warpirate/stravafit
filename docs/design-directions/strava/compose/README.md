# Compose theme for the "strava" direction

These files replace the files of the same name in `android/core/designsystem/src/main/kotlin/org/openactivity/core/designsystem/theme/`.

1. Copy `Color.kt`, `Type.kt`, `Shape.kt`, `Spacing.kt` and `Theme.kt` over the existing files.
2. Copy `res/font/figtree_{regular,medium,semibold,bold,extrabold}.ttf` to `core/designsystem/src/main/res/font/`.
3. Copy `assets/licenses/OFL-Figtree.txt` to `core/designsystem/src/main/assets/licenses/`.

The fonts are Figtree from github.com/erikdkennedy/figtree, under SIL OFL 1.1. If you would rather use the Google Fonts provider, `Type.kt` has the snippet.

Public names don't change (`RidgelineTheme`, `RidgelineColors`, `Ref.Slate900`, `Ref.InfoLight`, `Ref.Primary500`, `Spacing.*`, `Radius.*`), so no call sites need edits. The new roles are `RidgelineTheme.colors.accent`, `accentText`, `gap`, `float`, `pillInverse`, `paused`, `recording`, `location`, `chartLine`, `map.*` and others, plus `RidgelineTheme.text.display`, `statXl`, `nav` and `RidgelineTheme.sizes`.

On 2026-10-29 the swapped-in set compiled cleanly with `:android:core:designsystem`, `:android:core:map` and `:android:app:compileFossDebugKotlin`. The original files were restored after that check.

Roborazzi screenshot baselines will change and need to be re-recorded.
