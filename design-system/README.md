# StravaFit Design System: "Ridgeline"

An original, open-source design system for StravaFit, an Android fitness tracker. It is calm, outdoorsy and precise: a deep **Fjord teal** primary, a **Lichen lime** signal for achievements, and cool **Slate** neutrals. **Nothing in the brand is orange**, so it stays clearly distinct from Strava (no #FC4C02/#FC5200 family anywhere in UI or brand roles). Every feature is free, so the system has **no subscription, premium, upsell, lock or paywall components or tokens**.

| File | What it is |
|---|---|
| `tokens.json` | Source of truth. W3C Design Tokens (DTCG) format: `$type` / `$value`, aliases like `{color.ref.slate.900}`. |
| `compose/Color.kt` | Reference palette `Ref`, M3-external roles `FitColors`, data-viz `VizColors` (light and dark). |
| `compose/Type.kt` | Fonts (bundled `res/font` by default, Google Fonts provider optional), M3 `Typography`, extra stat styles. |
| `compose/Shape.kt` | M3 `Shapes` plus pill, sheet and bar shapes. |
| `compose/Spacing.kt` | Spacing, elevation, motion durations, easings, springs, reduced-motion helper. |
| `compose/Theme.kt` | `StravaFitTheme { }` root plus `FitTheme.colors / .text / .motion / .spacing` accessors. |

Derived from `docs/05-design-system/design-system-observed.md` Part B, with four values changed after contrast verification (see "Changes from the doc").

---

## 1. Usage

```kotlin
// MainActivity
setContent { StravaFitTheme { AppNavHost() } }

// In a composable
Text("10.20", style = FitTheme.text.statM, color = MaterialTheme.colorScheme.onSurface)
Text("KM", style = FitTheme.text.overline, color = FitTheme.colors.textMuted)
Card(shape = MaterialTheme.shapes.large, modifier = Modifier.padding(FitTheme.spacing.cardPadding)) { ... }
val dur = FitTheme.motion.enter(FitTheme.motion.medium) // 0 when reduced motion is on
```

**Fonts** (all SIL OFL 1.1). Default build bundles TTFs so F-Droid builds have no Play Services dependency:

```
app/src/main/res/font/manrope_regular.ttf
app/src/main/res/font/manrope_semibold.ttf
app/src/main/res/font/manrope_bold.ttf
app/src/main/res/font/barlow_semi_condensed_semibold.ttf
app/src/main/res/font/barlow_semi_condensed_bold.ttf
```

Set `USE_DOWNLOADABLE_FONTS = true` in `Type.kt` for a Play build that uses the Google Fonts provider. That needs `androidx.compose.ui:ui-text-google-fonts` and `res/values/font_certs.xml`. Ship the OFL license text in `assets/licenses/`.

**Token pipeline.** Edit `tokens.json`, then regenerate the Kotlin. Style Dictionary v4 reads DTCG natively; a custom `compose/color` formatter emitting `Color(0xFFRRGGBB)` is enough. Until that exists, keep the Kotlin in sync by hand and run the contrast check in section 7 on any color change.

**Dynamic color is off on purpose.** Wallpaper-derived colors would break the brand and the contrast-verified zone palette.

---

## 2. Principles

1. **Numbers are the hero.** Big tabular numerals (Barlow Semi Condensed, `tnum`) with quiet uppercase labels.
2. **One filled primary button per screen.** Everything else is tonal, outlined or text.
3. **Color means something.** Teal means interactive. Lime means achievement. Zone colors mean intensity. Nothing is decorative.
4. **Never color alone.** Every color has a backup: a label, an icon, a shape, or a pattern.
5. **Free by default.** No paywall styling, lock badges, "Pro" chips, gold upsell cards or trial banners. Features that are gated elsewhere look like every other feature here.
6. **Fast, quiet motion.** Celebrations are short, can be skipped, and are removed under reduced motion. Live stats never animate.

---

## 3. Foundations

### 3.1 Color

**Ramps**

| Ramp | Steps |
|---|---|
| Primary "Fjord" | 50 `#E6F4F5` · 100 `#C2E3E6` · 200 `#8ECBD1` · 300 `#57B0B9` · 400 `#2B97A2` · 500 `#0F7E8A` · 600 `#0B6A75` · 700 `#08565F` · 800 `#064249` · 900 `#032C31` |
| Signal "Lichen" | 100 `#F3FBD6` · 300 `#DDF27F` · 500 `#B8DB2E` · 700 `#7E9A12` · 900 `#3F4E07` |
| Slate | 0 `#FFFFFF` · 25 `#F8FAFA` · 50 `#F1F4F5` · 100 `#E3E8EA` · 200 `#CBD3D6` · 300 `#A9B4B8` · 400 `#87939A` · 500 `#5E6B72` · 600 `#4D585E` · 700 `#363F44` · 800 `#22292D` · 850 `#1A2024` · 900 `#12171A` · 950 `#0B0F11` |
| Status (light / dark) | success `#187A4D` / `#5CC48F` · warning `#9A6416` / `#E5B45E` · error `#C2362F` / `#F08A84` · info `#2F6FB3` / `#86B4E6` |
| Medals | gold `#C99A2E` · silver `#8F9AA3` · bronze `#BA7842` (always with numeral 1/2/3 in slate-900) |

**Semantic roles**

| Role | Light | Dark | M3 slot |
|---|---|---|---|
| bg/canvas | slate-50 | slate-950 | `background` |
| bg/surface | white | slate-900 | `surface` |
| bg/surfaceRaised | white | slate-850 | `surfaceContainerHigh` |
| bg/surfaceSunken | slate-25 | slate-950 | `surfaceVariant` / `FitColors.surfaceSunken` |
| text/primary | slate-900 | slate-50 | `onSurface` |
| text/secondary | slate-600 | slate-300 | `onSurfaceVariant` |
| text/muted | slate-500 | slate-400 | `FitColors.textMuted` |
| border/hairline | slate-100 | slate-800 | `outlineVariant` |
| border/strong | slate-400 | slate-500 | `outline` |
| action/primary | primary-600 | primary-300 | `primary` |
| action/onPrimary | white | primary-900 | `onPrimary` |
| action/primaryContainer | primary-100 | primary-800 | `primaryContainer` |
| achievement/signal (fill only) | signal-500 | signal-300 | `tertiaryContainer` (light) / `tertiary` (dark) |
| achievement/onSignal | slate-850 | slate-850 | |
| map/routeSelf + casing | primary-500 + white | primary-300 + slate-950 | `FitColors.routeSelf` |
| focus/ring | primary-400 | primary-200 | `FitColors.focusRing` |
| scrim | slate-950 @ 32% | black @ 50% | |

### 3.2 Typography

Manrope for UI, Barlow Semi Condensed for numerals, JetBrains Mono for debug views only. All sizes in `sp`. The minimum size is 11 sp.

| Token | M3 slot | Family | Size/Line | Weight |
|---|---|---|---|---|
| displayXl | displayLarge | Barlow SC | 64/68 | 700 |
| displayL | displayMedium | Barlow SC | 44/48 | 700 |
| displayM | displaySmall | Barlow SC | 32/36 | 600 |
| headline | headlineLarge | Manrope | 24/30 | 700 |
| titleL | titleLarge | Manrope | 20/26 | 700 |
| titleM | titleMedium | Manrope | 17/22 | 700 |
| titleS | titleSmall | Manrope | 15/20 | 600 |
| statM | `FitTheme.text.statM` | Barlow SC | 22/26 | 600 |
| statS | `FitTheme.text.statS` | Barlow SC | 17/20 | 600 |
| bodyL / M / S | bodyLarge / Medium / Small | Manrope | 16/24 · 14/20 · 13/18 | 400 |
| labelL / M | labelLarge / Medium | Manrope | 15/20 · 13/16 | 600 |
| labelS (overline) | labelSmall | Manrope | 11/14, +0.4 tracking, UPPERCASE | 600 |
| caption | `FitTheme.text.caption` | Manrope | 12/16 | 400 |

Units sit next to values at 60% size in `textMuted` (`10.2 km`). Above 1.3× font scale, 3-column stat grids reflow to 2.

### 3.3 Spacing, layout, radii, elevation

- **Spacing scale (4 dp base):** 0, 2, 4, 8, 12, 16, 20, 24, 32, 40, 48, 64.
- Screen gutter 16 dp (20 dp at ≥600 dp). Card padding 16, card gap 8, stat-column gap 12. List rows ≥56 dp, touch targets ≥48 dp.
- **Grid:** 4 columns on phones; 8 columns plus NavigationRail at ≥600 dp; list-detail with a 640 dp feed cap above 840 dp.
- **Radii:** xs 4 (tags) · s 8 (inputs, map thumbs) · m 12 (buttons) · l 16 (cards) · xl 28 (sheets, dialogs) · full (chips, avatars, record button).
- **Elevation:** light mode uses soft shadows; dark mode uses M3 tonal overlay.

| Level | Light shadow | Dark tonal | M3 dp | Use |
|---|---|---|---|---|
| 0 | none | 0% | 0 | Canvas, flat lists |
| 1 | 0/1/2 @6% + 0/1/3 @10% | 5% | 1 | Cards |
| 2 | 0/2/6 @10% | 8% | 3 | Sticky headers, map chips |
| 3 | 0/6/16 @14% | 11% | 6 | Sheets, menus, FAB |
| 4 | 0/12/28 @18% | 14% | 8 | Dialogs, record button |

### 3.4 Motion

| Token | ms | Easing | Use |
|---|---|---|---|
| instant | 0 | | Reduced motion |
| micro | 100 | standard | Press, ripple |
| short | 180 | standard | Chip toggle, icon swap, metric-slot crossfade |
| medium | 280 | emphasized-decelerate | Sheet open, card expand, PR row reveal |
| long | 420 | emphasized | Screen transitions, shared map element |
| celebrate | 900–1600 | spring | Milestone burst (skippable, once per achievement) |

Easing: standard `(0.2, 0, 0, 1)`, decelerate `(0.05, 0.7, 0.1, 1)`, accelerate `(0.3, 0, 0.8, 0.15)`. Exits run at 70% of the enter duration with the accelerate curve. Springs: pop `0.55 / 500`, sheet `0.8 / 380`. **Reduced motion** (system animator scale 0 or the in-app switch) turns every animation into an instant change or a 100 ms fade, with no particles and no shimmer.

### 3.5 Iconography

Material Symbols Rounded (Apache-2.0) at weight 400, grade 0, optical size 24. FILL=1 marks the selected state. Custom sport glyphs use a 24 dp grid with a 2 dp round stroke. Sizes are 16, 20, 24 and 32. Icon-only buttons always get a 48 dp target. Achievement glyphs are original: **hex medal** (best effort), **summit flag** (segment leader), **footprint loop** (route regular).

### 3.6 Data visualization

**Intensity zones.** The ramp runs blue, yellow, orange, then wine, ordered by lightness. It is safe for red/green colorblindness and always printed with its label (`Z3 Tempo · 12:40 · 24%`).

| Zone | Name | Light | Dark | Pattern backup |
|---|---|---|---|---|
| Z1 | Recovery | `#BFD9EA` | `#9CC3DD` | none |
| Z2 | Endurance | `#6FA8D0` | `#6FA8D0` | dots |
| Z3 | Tempo | `#E9C46A` | `#EBCB7C` | diagonal |
| Z4 | Threshold | `#E07B39` | `#EA8F52` | cross-hatch |
| Z5 | Max | `#9E2A4B` | `#D0557A` | solid |

Z4 is the only orange in the system, and it is a data color only. It is never used as a brand, button or UI accent.

**Series:** pace `#0B6A75` / `#57B0B9` · heart rate `#B23A5A` / `#E07A97` · cadence `#6B5BB5` / `#A497E0` (dotted when shown with HR) · power `#3C8D5A` / `#7BC89A` · elevation is a slate area with a slate-400 line · comparison is a dashed slate-400 line · goal is a dashed slate-600 / slate-300 line. Categorical order is teal, wine, violet, green, amber, and each series also gets its own dash or marker.

**Chart rules:** at most 4 horizontal hairline gridlines and no vertical ones. Axis labels use `caption` in muted text. Pace axes are inverted and labelled "faster ↑". Scrub cursor is a 1 dp `onSurface` line with a 6 dp dot, synced to the map marker. Every chart has a "View as table" toggle and a TalkBack summary.

---

## 4. Component catalog

State layers follow M3: hover 8%, focus 10% plus a 2 dp `focusRing` at 2 dp offset, pressed 10% plus a 0.98 scale over `micro`, dragged 16%. Disabled is 38% content and 12% container.

### 4.1 Buttons

| Variant | Container | Content | Height | Radius | Rule |
|---|---|---|---|---|---|
| Filled | primary | onPrimary | 48 | 12 | Only one per screen |
| Tonal | primaryContainer | onPrimaryContainer | 48 | 12 | Secondary emphasis |
| Outlined | transparent + 1 dp outline | onSurface | 48 | 12 | Alternatives |
| Text | none | primary | 40 (48 target) | 12 | "See all", inline |
| Destructive | error | onError | 48 | 12 | Delete, discard |
| Small | as variant | as variant | 36 (48 target) | 10 | Inside cards |

States: default, pressed, focused, disabled, and **loading**, which swaps the label for a 20 dp spinner, keeps the width, and announces "Loading".

### 4.2 Record button
72 dp circle, elevation 4.
- **Acquiring GPS:** primary fill with a dashed, muted ring and the status text "Acquiring GPS…".
- **Ready:** solid primary ring and the label "Start".
- **Recording:** the button becomes a slate-900 stop/pause square with a primary progress ring. Long-press to stop is optional.
- **Paused:** splits into Resume (tonal) and Finish (filled).
- **Auto-paused:** a warningContainer banner reading "Auto-paused", and the hero stat dims to 60%.
- **High-contrast option** for sunlight: pure black or white plus primary.

### 4.3 Navigation
- **Navigation bar** (80 dp): Feed · Explore · Record · Groups · Me. The active indicator is a 64×32 pill in primaryContainer with a filled icon and `labelMedium` text.
- **Badges:** unread counts use error fill with white numerals; dot badges are 6 dp.
- **NavigationRail** at ≥600 dp.
- **Top tabs** use a 3 dp primary underline, scrollable when there are more than 4.
- **Segmented control** (M3 SegmentedButton): the selected segment uses primaryContainer.

### 4.4 Chips
32 dp tall with a 48 dp target, pill shape, `labelMedium` text.
- **Filter chip:** selected state is a primaryContainer fill plus a check icon.
- **Assist chip:** "Add photo".
- **Input chip:** has a trailing ×.
- Chip rows scroll horizontally with edge fades.

### 4.5 Cards
- **Activity card:** surface, radius 16, elevation 1, 8 dp gaps between cards. From top to bottom it contains:
  - header (40 dp avatar, name, meta line)
  - title (`titleM`)
  - inline stat block
  - achievements row
  - map (2:1, radius 8)
  - facepile and counts
  - action bar (High-five · Comment · Share)

  Its states:
  - **loading:** shimmer at 1200 ms, static under reduced motion
  - **uploading:** inline progress plus "Processing…"
  - **failed:** errorContainer plus Retry
  - **private:** lock icon in the meta line, which means visibility, never a paywall
  - **indoor:** no map, optional HR sparkline
- **Map card:** a MapLibre snapshot. The route is a 4 dp routeSelf stroke over a 6 dp casing. Start is a hollow 10 dp circle and finish a filled one, so they differ by shape rather than color. Privacy-zone trims end in a dotted segment.

### 4.6 Stat block
Labels are `labelSmall` uppercase in muted text. Values are `statM` with units at 60%. Variants: `inline` (3 columns), `grid` (2–3 columns with dividers), `hero` (`displayLarge`), and `compare` (adds a delta chip such as "▲ 0:12" that uses an arrow and a sign, not color alone). Empty values show "—", never "0". The block is merged into one semantics node, e.g. "Distance, 10.20 kilometres".

### 4.7 High-five and facepile
- **High-five button:** 24 dp palm glyph in a 48 dp target. Off: outline in textSecondary. On: filled primary.
- **Animation on tap:** pop spring, six lime dots that fade over 360 ms, a rolling count, and a CONFIRM haptic. Tapping again undoes it.
- **Content description:** "Give high-five" or "Remove high-five", plus the count.
- **Facepile:** up to 3 avatars at 24 dp, overlapping by 8 dp with a 2 dp surface ring, then a "+N" pill.

### 4.8 Avatars and badges
- **Avatar sizes:** 24, 32, 40, 56, 96. The fallback is initials on one of 8 deterministic tints, each ≥4.5:1 with its text.
- **Live-sharing ring:** 2 dp signal ring plus a "Live" label.
- **Badges** (20 dp):
  - hex medal: gold, silver or bronze with a slate-900 numeral
  - summit flag: primary
  - footprint loop: signal-700
  - count badge: error
  - "New": signal-300 fill with slate-900 text

### 4.9 Progress
- **Goal ring:** 8 dp stroke on a slate-100 / slate-800 track. The fill is primary. At 100% it turns signal with a check glyph and the text "Goal reached".
- **Linear bar:** 6 dp. Overflow past the target is shown as a signal segment.
- **Upload bar:** 4 dp.
- **Indeterminate:** M3 linear or wavy indicator.

### 4.10 Sheets, dialogs, snackbars, inputs, lists, empty states
- **Modal sheet:** top radius 28, a 32×4 handle, scrim, half and full detents.
- **Map sheet:** peek at 96 dp, then half and full. The map stays interactive at peek.
- **Dialog:** radius 28, surfaceContainerHigh, at most 2 actions. A destructive action uses error-colored text.
- **Snackbar:** inverseSurface background. The action uses inversePrimary (primary-200 in light, primary-600 in dark). Shows for 4 s, or 8 s with an action, anchored above the nav bar and the record button. Deletes show "Deleted · Undo".
- **Text field:** outlined, 56 dp, radius 8. Errors show error helper text plus an icon.
- **List rows:** 56–72 dp. Leading 24 dp icon or 40 dp avatar, a title and subtitle, and a trailing value, chevron or switch.
- **Empty state:** 32 dp icon in a 64 dp primaryContainer circle, a `titleMedium` title, `bodyMedium` muted text, one filled CTA and an optional text button.

### 4.11 Share / export templates
Templates are rendered offscreen from a Composable (`GraphicsLayer.toImageBitmap()`), written to the cache dir, and shared through a FileProvider with `ACTION_SEND`. Instagram uses `com.instagram.share.ADD_TO_STORY`.

| Template | Canvas (px) | Background | Content | Safe area |
|---|---|---|---|---|
| Story: stats | 1080×1920 | fjord `#032C31` | sport glyph; `displayXl` hero stat in white; 3 stats (labels in primary-200); date; wordmark in primary-200 | top 250, bottom 340, sides 72 |
| Story: map + stats | 1080×1920 | slate-900 | route in primary-300 with slate-950 casing on a transparent or dark map; stats band at the bottom | same |
| Story: splits | 1080×1920 | fjord | split bars in primary-300, fastest split tagged "Fastest" with a signal-300 fill and slate-850 text | same |
| Sticker (transparent PNG) | 1080×1920, content ~900×600 | transparent | white text on a slate-950 @ 40% pill scrim, so it is legible on any photo | centered |
| Square post | 1080×1080 | paper `#F1F4F5` or fjord | map, title and 3 stats | 64 all sides |
| Link preview | 1200×630 | fjord | title, 3 stats, route | 48 |
| Achievement card | 1080×1920 | fjord | hex medal with numeral, effort name, time; the signal burst is baked in statically | same |

States for the share composer:
- **Preview:** a template pager with a dot indicator, and a "Transparent background" toggle that applies to the sticker only.
- **Rendering:** the preview is overlaid with a spinner.
- **Offline:** the image can still be shared, but "Copy link" is disabled with the hint "Available after upload".
- **Error:** a snackbar with a Retry action.

Rules:
- Unit system follows the user setting.
- Never show other athletes' names without their visibility allowing it.
- No premium badges or "Pro" marks.
- Privacy zones stay trimmed in every template.
- **Alt text:** the share intent includes `EXTRA_TEXT` with a plain-language summary, e.g. "Morning run, 10.2 km in 53:04".

---

## 5. Do / Don't

| Do | Don't |
|---|---|
| Use teal primary for the single most important action | Use orange or red-orange for any brand, button or accent role |
| Pair every color meaning with text, an icon, a shape or a pattern | Use green vs. red alone (e.g. start/finish, up/down deltas) |
| Use signal lime as a **fill** with slate-850 text on it | Put lime text on a white background (1.4:1) |
| Show "—" for missing values | Show "0" for missing or unknown data |
| Respect reduced motion and keep live stats static | Animate numbers during recording |
| Let all features look the same, since everything is free | Add lock icons, "Pro" chips, gold upsell cards, trial banners or paywalls |
| Use `sp` for all text and reflow grids at large font sizes | Truncate stat values |
| Keep Z4 orange inside charts only | Reuse zone colors as UI accents |
| Use the hex medal, summit flag and footprint loop glyphs | Use crowns, laurels or any Strava-like marks or wording |

---

## 6. Accessibility requirements

- **Text:** ≥4.5:1 for normal text, ≥3:1 for large text (≥18.5 sp bold or ≥24 sp).
- **UI components:** ≥3:1 for borders, icons, focus rings and chart lines.
- **Touch targets:** ≥48×48 dp. Layouts work up to 200% font scale.
- **TalkBack:** stat blocks are merged nodes; charts get a summary plus a table; the high-five button exposes its state; maps get a route summary.
- **Flashing:** nothing flashes more than 3 times per second.
- **Haptics:** never the only feedback.
- **Localization:** RTL mirroring everywhere except maps and time axes; leave room for strings up to 40% longer.

---

## 7. Contrast verification (WCAG 2.1 relative luminance, computed)

All pairs were computed with the WCAG formula from the exact token hex values. **Every pair passes AA.**

| Mode | Pair | Colors | Ratio | Result |
|---|---|---|---|---|
| Light | text/primary on surface | `#12171A` on `#FFFFFF` | 18.05:1 | Pass AA |
| Light | text/primary on canvas | `#12171A` on `#F1F4F5` | 16.33:1 | Pass AA |
| Light | text/secondary on surface | `#4D585E` on `#FFFFFF` | 7.31:1 | Pass AA |
| Light | text/muted on surface | `#5E6B72` on `#FFFFFF` | 5.49:1 | Pass AA |
| Light | text/muted on canvas | `#5E6B72` on `#F1F4F5` | 4.97:1 | Pass AA |
| Light | onPrimary on action/primary | `#FFFFFF` on `#0B6A75` | 6.30:1 | Pass AA |
| Light | link primary-600 on surface | `#0B6A75` on `#FFFFFF` | 6.30:1 | Pass AA |
| Light | onPrimaryContainer on primaryContainer | `#064249` on `#C2E3E6` | 8.19:1 | Pass AA |
| Light | onSignal on signal-500 | `#1A2024` on `#B8DB2E` | 10.35:1 | Pass AA |
| Light | signal-900 text on surface | `#3F4E07` on `#FFFFFF` | 9.11:1 | Pass AA |
| Light | white on error | `#FFFFFF` on `#C2362F` | 5.44:1 | Pass AA |
| Light | white on success | `#FFFFFF` on `#187A4D` | 5.34:1 | Pass AA |
| Light | white on warning | `#FFFFFF` on `#9A6416` | 4.99:1 | Pass AA |
| Light | white on info | `#FFFFFF` on `#2F6FB3` | 5.19:1 | Pass AA |
| Light | error text on surface | `#C2362F` on `#FFFFFF` | 5.44:1 | Pass AA |
| Light | onErrorContainer on errorContainer | `#8A1E19` on `#FBE1DF` | 7.43:1 | Pass AA |
| Light | onWarningContainer on warningContainer | `#6B4409` on `#FBEFD8` | 7.51:1 | Pass AA |
| Light | snackbar text on slate-800 | `#F1F4F5` on `#22292D` | 13.35:1 | Pass AA |
| Light | snackbar action primary-200 on slate-800 | `#8ECBD1` on `#22292D` | 8.16:1 | Pass AA |
| Light | border/strong on surface | `#87939A` on `#FFFFFF` | 3.15:1 | Pass AA (UI, 3:1) |
| Light | focus ring on surface | `#2B97A2` on `#FFFFFF` | 3.47:1 | Pass AA (UI, 3:1) |
| Light | route primary-500 on white | `#0F7E8A` on `#FFFFFF` | 4.80:1 | Pass AA (UI, 3:1) |
| Dark | text/primary on surface | `#F1F4F5` on `#12171A` | 16.33:1 | Pass AA |
| Dark | text/primary on canvas | `#F1F4F5` on `#0B0F11` | 17.42:1 | Pass AA |
| Dark | text/secondary on surface | `#A9B4B8` on `#12171A` | 8.52:1 | Pass AA |
| Dark | text/muted on surface | `#87939A` on `#12171A` | 5.73:1 | Pass AA |
| Dark | text/muted on raised | `#87939A` on `#1A2024` | 5.23:1 | Pass AA |
| Dark | onPrimary on action/primary | `#032C31` on `#57B0B9` | 5.90:1 | Pass AA |
| Dark | link primary-300 on surface | `#57B0B9` on `#12171A` | 7.15:1 | Pass AA |
| Dark | onPrimaryContainer on primaryContainer | `#C2E3E6` on `#064249` | 8.19:1 | Pass AA |
| Dark | onSignal on signal-300 | `#1A2024` on `#DDF27F` | 13.39:1 | Pass AA |
| Dark | signal-300 text on surface | `#DDF27F` on `#12171A` | 14.69:1 | Pass AA |
| Dark | slate-950 on error | `#0B0F11` on `#F08A84` | 7.95:1 | Pass AA |
| Dark | error text on surface | `#F08A84` on `#12171A` | 7.46:1 | Pass AA |
| Dark | onErrorContainer on errorContainer | `#FBD3D0` on `#4A1411` | 10.96:1 | Pass AA |
| Dark | snackbar text on slate-100 | `#12171A` on `#E3E8EA` | 14.61:1 | Pass AA |
| Dark | snackbar action primary-600 on slate-100 | `#0B6A75` on `#E3E8EA` | 5.10:1 | Pass AA |
| Dark | border/strong on surface | `#5E6B72` on `#12171A` | 3.29:1 | Pass AA (UI, 3:1) |
| Dark | focus ring on surface | `#8ECBD1` on `#12171A` | 9.98:1 | Pass AA (UI, 3:1) |
| Share | white on fjord-900 | `#FFFFFF` on `#032C31` | 14.91:1 | Pass AA |
| Share | signal-300 on fjord-900 | `#DDF27F` on `#032C31` | 12.13:1 | Pass AA |
| Share | primary-200 label on fjord-900 | `#8ECBD1` on `#032C31` | 8.25:1 | Pass AA |
| Share | slate-900 on paper | `#12171A` on `#F1F4F5` | 16.33:1 | Pass AA |
| Mode | Pair | Colors | Ratio | Result |
|---|---|---|---|---|
| Light | onTertiary slate-900 on tertiary signal-700 | `#12171A` on `#7E9A12` | 5.60:1 | Pass AA |
| Dark | onTertiaryContainer signal-100 on signal-900 | `#F3FBD6` on `#3F4E07` | 8.50:1 | Pass AA |
| Both | medal numeral slate-900 on gold / silver / bronze | `#12171A` on `#C99A2E` / `#8F9AA3` / `#BA7842` | 5.72 / 6.29 / 5.03:1 | Pass AA |

Known exclusions (by design, not text): zone fills Z1/Z3 against white are below 3:1. Zone bars always carry a printed label and a pattern, and sit on a hairline track, so they don't depend on the fill's contrast. Signal-500 lime is never used as text on light surfaces.

**Re-check on any color change:** run the luminance script in the PR (or the Android Accessibility Scanner on device) and update this table.

### Changes from the doc (made to pass AA)

| Token | Doc value | New value | Reason |
|---|---|---|---|
| slate-500 (text/muted, light) | `#66737A` | `#5E6B72` | 4.42:1 on canvas, now 4.97:1 |
| success (light) | `#1E8E5A` | `#187A4D` | white on it was 4.14:1, now 5.34:1 |
| warning (light) | `#B7791F` | `#9A6416` | white on it now 4.99:1 |
| medal bronze | `#A8673A` | `#BA7842` | slate-900 numeral was 4.01:1, now 5.03:1 |
| border/strong (light) | slate-300 | slate-400 | slate-300 was below 3:1 for UI components; now 3.15:1 |
| categorical c5 | `#B7791F` | `#9A6416` | matches the new warning value |

---

## 8. Brand distinctness and IP

- **Distinct from Strava:** the primary is teal, not orange. Fonts are Manrope and Barlow, not a custom humanist face. Cards are rounded and inset with soft elevation, not full-bleed and flat. Achievement glyphs and names are original ("high-five", "summit flag", "route regular", "Live Share").
- **Licenses:** all assets are original or open-license. Fonts are SIL OFL 1.1, icons are Material Symbols (Apache-2.0), and the tokens and code are Apache-2.0.
