# Home-screen widget: spec

Status: phase 1 written on branch `feat/widgets` (module `:android:feature:widgets`), **not built or run yet**
(see "Implementation status" at the end). Visual mockup: `widgets-mockup.html` (same folder).
Source entries: `parity-gap-report.md` ("Home-screen widget", M) and `feature-parity-checklist.md` ("Launcher widget", P2).

## Principle

A widget is a remote control, not a second copy of the app. It exists only for things that are better done
**without opening the app**. Showing app data is not a reason to have a widget. We do not port app features
into widgets.

It covers the three moments when opening the app is friction:

| Moment | State | What you can do without opening the app |
|--------|-------|------------------------------------------|
| Before a run | **Ready** | See whether GPS is locked. Start a run (3 s countdown you can cancel). |
| During a run | **Recording** | Pause, resume, lap, finish. |
| Right after | **Done** | The run is already saved. Share the route sticker. |

## Persona

The app is your logbook. The widget is your running buddy. It answers one question: *should I go now, and
what should I do?* It has its own voice; the app does not speak this way.

Decided (2026-11-01):
- **Tone: dry coach.** Short, honest, a bit of wit. Never hype, never guilt, no emoji, no exclamation marks.
- **Proactive: suggests one run** (distance or route) that starts with one tap.
- **No name.** It's the StravaFit widget, with a voice.
- **Weather: opt-in once.** Location rounded to ~10 km is sent to Open-Meteo and cached 1 h. Off = no weather lines.
- **Widget only.** No notifications from the persona.

No mascot. The persona is typographic: one bold line plus the route mark.

### How a line is chosen

At each refresh, the rules are checked in priority order and the first match is shown. Every input is real local
data. There is no AI model, so it works offline and is private, except for weather (opt-in).

| # | Rule (when) | Example line | Primary tap |
|---|-------------|--------------|-------------|
| 1 | GPS accuracy > 20 m | "No GPS yet. Step outside." | Start anyway |
| 2 | 5+ activities in the last 7 days, or 3 hard ones | "3 hard runs this week. Rest is training." | Start (secondary) |
| 3 | Rain within 60 min (weather on) | "Rain at 7. Go now or go wet." | Start |
| 4 | < 60 min to sunset | "Sunset 18:04. 40 min of light left." | Start |
| 5 | ≥ 30 °C | "31°. Slow is smart today." | Start |
| 6 | 7+ days since the last activity | "9 days off. Easy 3 km?" | Start 3 km |
| 7 | Within ±45 min of your usual slot (learned from ≥ 4 past activities on that weekday) | "Tuesday 6:10. Your slot. 24°, dry." | Start suggested |
| 8 | Weekly goal gap > 0 and a suggested route fits it | "6.6 km short. The river loop fits." | Start + follow route |
| 9 | Fallback | "6.6 km to go this week." / "GPS locked." | Start |

After a run (Done state), the line compares the run with your own history:

| Rule | Example |
|------|---------|
| Personal best for a standard distance, all time | "New 5k best. 23:41." |
| Personal best this month | "Fastest 5k this month." |
| Top 3 on a route you've run ≥ 3 times | "Second fastest on this loop." |
| Slower than your 30-day median by > 5% while ≥ 28 °C | "Slower than usual. 31° will do that." |
| Longest run in 30 days | "Longest run in a month." |
| Fallback | "Saved. 7.89 km." |

Copy rules: ≤ 40 characters a line, never the same line twice in a row, numbers always formatted in the
user's units. Lines are stored in `strings.xml` so they can be translated. Hard rules (weather, PBs) never guess:
missing data → skip the rule.

## Customization

Each placed widget has its own settings, so you can keep a Run widget on page 1 and a Ride widget on page 2.

### How to open the settings (decided 2026-11-01)
1. **When adding the widget:** the settings screen opens once, and you can skip it (`widgetFeatures="reconfigurable|configuration_optional"`, Android 12+).
2. **Long-press → Settings** (Android 12+ reconfigure).
3. **Tap the route mark** in the widget corner. No gear icon.
4. **App → Settings → Widgets:** lists each placed widget and the defaults for new ones.

### The settings screen
- Live preview at the top, with a Ready / Recording / Done switch, and the size of the widget being edited.
- Changes apply instantly. No Save button.
- Grouped sections with good defaults. Most people never open them.

### Decided sections
| Section | Options | Default |
|---------|---------|---------|
| Sports | Up to 3, in order. The 1×1 uses the first. | Run, Ride, Walk |
| Suggestions | On/off · route or distance only · maximum distance | On · route · none |
| During a run | Numbers shown: 3 on 4×1, 4 on 4×2. Choose from time, distance, current pace, avg pace, HR, lap, elevation, cadence, speed | Time, distance, current pace (+ avg on 4×2) |
| After a run | Share format: transparent sticker / PNG with route / SVG · Back to Ready after 1 h / 6 h / midnight | Sticker · 6 h |
| Privacy | Hide route on home screen (stats only) · hide the line on the lock screen | Off · off |
| Look | Theme: system / light / dark · background: solid / 80% / transparent | System · solid |
| Safety | Countdown 3 s / 5 s. Finish confirm always on | 3 s |

**Not customizable, on purpose:** the coach voice (the user decided it stays on as designed), colours and fonts,
turning off finish confirm, a discard button, auto-share to a fixed app.

### Candidates (brainstorm, not decided)
★ = recommended.

How runs started from this widget are saved (they are saved without opening the app):
- ★ Visibility: private / followers / everyone.
- ★ Title template ("{time-of-day} {sport}" or a fixed title such as "Commute").
- ★ Tags: commute, hide from feed, mute from followers.
- ★ Gear per widget (shoes / bike), so its mileage is counted.

Presets:
- ★ Widget preset, e.g. "Morning 7k": sport + route to follow + gear + save settings, started with one tap.
- Pinned route (always start and follow route X).
- Start a structured workout (once workouts exist).

On start:
- ★ Auto-connect sensors (HR, power, cadence).
- ★ Auto-start live location sharing (Beacon) to a chosen contact.
- Audio cues on/off and interval. Auto-pause on/off per widget. Countdown vibrate / beep / silent.

Display:
- Goal period (week / month / year) and metric.
- What a tap on the widget body does: start / open app / nothing.
- Discreet mode: only the Start button, no numbers or route.
- Hide the GPS accuracy number (dot only).
- Quiet hours for suggestions.

Accessibility:
- Larger text: fewer numbers, bigger buttons.
- High-contrast outline on light wallpapers.

Rejected: custom colours or fonts, disabling finish confirm, auto-share to a fixed app.

## Hard parts (checked 2026-11-01)

### H1. A clock that ticks every second without us redrawing it
- Widgets can't be redrawn every second: it costs battery and launchers lag. Use the system **`Chronometer`**
  view inside the widget. Android ticks it itself with zero updates from us.
- Glance has no Chronometer composable, so use `AndroidRemoteViews` with a small XML layout and
  `RemoteViews.setChronometer(id, base, format, started)`.
  - `base = SystemClock.elapsedRealtime() - timerTime` (the app's main clock: pauses excluded). On pause, auto-pause or resume, push one update with
    `started = false/true`. That is the only time the clock needs us.
  - The 3-2-1 countdown and the "Tap again to save · 3" use `setChronometerCountDown(id, true)` (API 24+).
  - Chronometer switches MM:SS → H:MM:SS by itself at one hour. The width for H:MM:SS is reserved from the start,
    so nothing shifts.
- Distance and pace: pushed every ~5 s or every 10 m (whichever first), and only while the screen is on.
  Every state change (pause, lap, finish) is pushed at once.

### H2. Starting GPS recording from the widget without opening the app
Android docs (Restrictions on starting a foreground service from the background) list both exemptions we need:
- Background FGS start: allowed when "the user performs an action on a UI element related to your app. For
  example, they might interact with a bubble, notification, **widget**, or activity."
- While-in-use permissions (location): exempt when "the service starts by interacting with **app widgets**."

So a `location` foreground service started by a widget tap may use location with only "while using the app" permission. No
background-location permission is needed. Requirements:
- The tap must start the service **directly**: Glance `actionStartService(intent, isForegroundService = true)`.
  Do not go through `actionRunCallback` → coroutine → `startForegroundService`, because the exemption may be lost
  on the extra hop.
- `startForeground(…, FOREGROUND_SERVICE_TYPE_LOCATION)` is called immediately in `onStartCommand`, before the
  countdown. The countdown runs inside the service.
- Location off or permission missing → open the permission sheet that already exists in `:core:recording-service`.
- **Verify on real phones before promising it:** Pixel (stock), Samsung, Xiaomi/HyperOS. If an OEM blocks it, the
  fallback is a transparent trampoline activity that opens for about 200 ms and closes.

### H3. A ticking clock while the app is dead
Chronometer keeps ticking even if the OEM kills our process. The widget would show a run that isn't recording.
- `RecordingService` already restarts after process death and calls `recoverInterrupted()` / shows
  "Recording stopped unexpectedly". That same path must also push the widget into an **Interrupted** state
  ("Recording stopped. Tap to recover.").
- Backstop: when recording starts, set an `AlarmManager` watchdog (inexact, ~2 min, re-armed by the service).
  If it fires and the service isn't alive, the widget is set to Interrupted.
- Accept: on aggressive OEMs there can be a short window of a stale clock. Settings → Widgets links to the
  "don't kill StravaFit" battery guide.

## Shape

**1 widget, 3 sizes, 3 states.** One entry in the widget picker that resizes (Glance `SizeMode.Responsive`).
Its state follows the recording service, not the size.

| Size | Px at 84×96 cell | Ready | Recording | Done |
|------|------------------|-------|-----------|------|
| 1×1 | 68×80 | One sport button + GPS dot | Pause/resume button + time | Share button + distance |
| 4×1 | 320×80 | GPS status, goal line, Run/Ride/Walk | Time, distance, pace, Pause, Finish | Saved title, distance, time, Share, ✕ |
| 4×2 | 320×176 | GPS + accuracy, weekly goal bar, big Start + Ride/Walk | Time, distance, pace, lap; Pause, Lap, Finish | Route (privacy applied), title, stats, Share sticker, ✕ |

Transitional states, at every size: **Countdown** (3-2-1 + Cancel), **Weak GPS** (amber dot, "Start anyway"),
**Paused** (paused strip, Resume), **Finish confirm** ("Tap again to save · 3").

## What was cut and why

| Earlier idea | Now | Why |
|--------------|-----|-----|
| Quick start widget | Ready state | It's an action, and it now starts the run itself. |
| Live recording widget | Recording state | Controls mid-run are the biggest win. |
| Last activity widget | Done state | A route on display does nothing. Sharing it right after a run is the action people take. |
| This week / goal widget | One line in Ready | Useful only as context before starting. Charts stay in the app. |
| Streak widget | Dropped | A number to look at, with nothing to do. It stays in the app and in notifications. |

## Behaviour

**Start**
- Tapping a sport starts a 3 s countdown in the widget and the notification. Cancel stops it before anything is recorded.
- The app does not open. The foreground recording service starts directly.
- Location permission missing → opens the permission flow (the only case where Start opens the app).
- Weak GPS (accuracy > 20 m) → amber "Finding GPS · ±38 m". Start stays possible as "Start anyway" (tinted, not orange).
- 1×1: the sport is chosen in the config screen when you add the widget. Default is the last-used sport.
- 4×1 / 4×2: the last-used sport is the orange button, the others are tinted.

**Recording**
- Numbers are pushed by the recording service. They must match the notification exactly.
- Time updates every 1 s while the launcher is visible. Distance and pace update on each GPS fix.
- **Pace is current pace, not average.** It comes from `RecordingState.currentPaceSecondsPerKm`: the engine's
  smoothed speed (EMA, α = 0.3, about one GPS fix per second). After you slow down, it shows about 60% of the change
  within ~3 s and about 90% within ~6 s, without jumping on every GPS wobble.
  Average pace shows as a secondary value on 4×2 ("Avg 4:55"). Done and Finish confirm show average.
- When you stop (auto-pause / stationary), the engine reports speed 0. Pace shows "--:--", never a huge number like 59:59.
- Rides show speed instead of pace, with the same live and average split.
- Lap is on 4×2 only, and only if auto-lap is off or manual laps are allowed.
- Finish: the first tap turns the button into "Tap again to save · 3". A second tap within 3 s saves.
  Otherwise it goes back to Recording.
  (Widgets can't detect a long press, so this replaces "hold to finish".)
- Discard is **never** in the widget. It happens only in the app.

**Done**
- Saved straight away with an automatic title ("Morning Run", same rule as the app) and the user's default
  privacy settings. Edit in the app later.
- Share opens the system share sheet with the route sticker PNG, through a transparent trampoline activity, so the app UI doesn't open.
- The route uses the share/export renderer: privacy zones and hidden start or end are applied.
- Activities without GPS show the sport icon instead of a route.
- The widget returns to Ready after 6 h, at midnight, or on ✕.

## Look

- Tokens from `core/designsystem/theme/Color.kt`: `#FC5200` accent, white / `#1C1C1F` surface, Figtree, tabular numbers.
- Follows system dark mode. No dynamic colour.
- Orange is only on the primary action (Start, Finish, Share). Everything else is neutral.
- Our own icons. Corner radius is the system widget radius (16 dp fallback).
- Touch targets are ≥ 48 dp on 4×2 and ≥ 40 dp on 4×1 / 1×1.

## Data and rules

- Real data only. No sample numbers in previews. The picker preview shows the Ready state with a live GPS read where possible,
  and "GPS off" otherwise.
- Units and week start come from app settings.
- Data comes from Room and the recording service, so the widget works offline and when signed out.
- TalkBack labels on every button and number. At font scale > 1.3×, lower-priority lines are hidden instead of clipped.

## Tech

- Jetpack Glance, one `GlanceAppWidget` + receiver, module `:feature:widgets`.
- State comes from a `RecordingStateRepository` flow, shared with the notification.
- Actions use `actionRunCallback` → recording service intents (start / pause / resume / lap / finish). There is no Activity, except for share and permission.
- `appwidget-provider`: `targetCellWidth/Height` 4×1 default, `minResize` 1×1, `maxResize` 4×2.

## Test plan

Release gate: layers 1–3 green **and** at least one real-phone run (layer 4). If the real-phone run is
not done, the release notes say so; the widget is never called "verified" without it.

1. **Unit (JVM, every build):** voice-line rules (each rule, priority, no repeat, ≤ 40 chars, skip on missing
   data) · number formatting (5:02, H:MM:SS, `--:--` when stopped / > 30:00/km, km/mi, decimal comma,
   native digits) · widget state machine incl. Interrupted / Recovery · finish confirm (1 tap no save, 2 taps
   within 3 s save, timeout → Recording) · suggested-run picker · chronometer base across pause/resume.
2. **Glance UI (glance-appwidget-testing, no emulator):** every size × state shows the right text, buttons,
   content descriptions and actions · font scale 1.3× / 2× · discreet mode, hidden route, transparent background.
3. **Emulator E2E (AVD "dwell"):** start from the widget with the app killed (the app UI never opens) · cancel
   during the countdown leaves no activity · replay `pune-loop-7k` faster than real time: widget = notification
   = in-app numbers · pause / lap / finish from the widget, saved with the right title, privacy and laps ·
   `am kill` mid-run → Interrupted → recovery · weak GPS (±38 m mock fixes) · permission revoked →
   permission sheet · screenshots of every size × state, light and dark, into `docs/screenshots/widgets/`, each one reviewed.
4. **Real phones (manual):** start from the widget on Pixel, Samsung and Xiaomi · 30-min screen-off run on
   Xiaomi and Samsung (OEM kill) · One UI, Pixel and Nova launcher cell sizes · one real outdoor run: clock,
   pace response, battery.

## Test checklist

- [ ] 3 sizes × 3 states + 4 transitional states, light and dark, on 4-col and 5-col grids. Screenshots in `docs/screenshots/`.
- [ ] Start from widget with the app killed: the countdown runs, recording starts, the app never opens.
- [ ] Cancel during countdown leaves no activity behind.
- [ ] Replay `testdata/e2e-routes/pune-loop-7k`: widget = notification = in-app numbers.
- [ ] Pause / resume / lap / finish from the widget. A single Finish tap does not save.
- [ ] Done shows privacy-zone-trimmed route; Share sheet gets the sticker PNG.
- [ ] Weak GPS state on emulator with high accuracy values.
- [ ] Missing location permission → permission flow.
- [ ] km ↔ mi switch, font scale 1.3× and 2×, TalkBack.

## Corrections found while coding (2026-11-01)

- **No GPS status in Ready.** A GPS fix exists only while something is using GPS, and keeping it on
  for a widget would drain the battery. GPS status shows from the countdown on (the recording service
  starts location then). The mockup's "GPS locked" in Ready is wrong. Rule 1 of the voice ("No GPS yet")
  applies only during the countdown.
- **No Figtree in the widget.** RemoteViews/Glance text can only use system font families. The widget uses
  `sans-serif` with the same sizes and weights. The clock is a `Chronometer` with `fontFeatureSettings="tnum"`.
- **Done opens the activity for now.** The one-tap share sheet needs a transparent trampoline activity
  in the app module. It is the next step, not in phase 1.

## Implementation status

Phase 1 (branch `feat/widgets`):

| Part | Where | State |
|------|-------|-------|
| Start / cancel from widget, countdown in the service | `core/recording-service` `StartCountdown.kt`, `RecordingService` (`ACTION_WIDGET_START`, `ACTION_CANCEL_START`) | written |
| Latest-activity query (LIMIT 1) | `ActivityDao.observeLatest`, `ActivityRepository.observeLatestActivity` | written |
| State machine (Ready, Countdown, Recording, Done, Interrupted) + finish confirm + Done expiry | `feature/widgets` `WidgetState.kt` | written + unit tests |
| Number formatting, `--:--` rule, TalkBack clock text | `WidgetFormat.kt` | written + unit tests |
| Update throttle (5 s / 10 m, status changes only with the screen off) | `WidgetRepository.kt` | written + unit tests (render key) |
| Glance UI: 3 sizes × states, Chronometer clock | `StravaFitWidget.kt` | written |
| App wiring | `OpenActivityApplication` starts `WidgetUpdater` | written |

Not yet: build, Glance UI tests, emulator E2E, real-phone tests, share trampoline, H3 watchdog alarm,
TalkBack text on the clock view, voice lines, settings screen, picker preview image.
