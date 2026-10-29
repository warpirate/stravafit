# 09 · Details: Record

A catalog of small details in Strava Android's **record** flow, for StravaFit parity.

- **Gating** shows Strava's tier for reference only: `[FREE]`, `[SUB]` or `[?]` (unknown). StravaFit has no paywall, so every feature here is free in our app.
- **Our app** says whether we keep the behaviour, improve on it, or drop it, and gives a reason.
- Items marked *(unverified)* come from a single or weak source. Confirm them on a device before relying on them.
- Sources are public only. Text is paraphrased.

> Note on 2025 redesign: Strava rebuilt Record in July 2025, shipping on Android first. Stats now float over the map instead of switching hard between map and stats. Some older support-article details (the minimize icon, swipe-down to collapse) may describe the pre-redesign UI. Both are listed below, and the target behaviour for StravaFit is called out.

---

## 1. Entry point and navigation

| # | Detail | Behaviour | Gating | Our app |
|---|---|---|---|---|
| 1.1 | Record button in the bottom nav | A circular Record button sits in the centre of the bottom navigation bar. It is not a floating action button. | [FREE] | **Keep.** |
| 1.2 | Down-chevron closes Record | Record is a full-screen modal. A down-chevron at the top left slides it closed. There is no back arrow. | [FREE] | **Keep.** Also handle the system back gesture: minimise while recording, close when idle. |
| 1.3 | Background location permission | Recording needs fine location and background ("Allow all the time") location. Without them, tracking stops when the screen turns off. | [FREE] | **Improve.** Ask in context on the first Start, with a rationale screen and a deep link to settings. Show a persistent warning chip if background location is denied. |
| 1.4 | Battery-optimisation exemption | Strava's help pages tell users to exempt the app from OEM battery savers (Samsung, Huawei and others). | [FREE] | **Improve.** Prompt in the app with `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`, plus OEM-specific tips (dontkillmyapp-style), instead of leaving it to help docs. |

## 2. Pre-start screen (sport picker, GPS, route)

```
+--------------------------------+
| v                    [gear]    |
|                                |
|          (  o  )   <- GPS halo |
|           map                  |
|                                |
|                     [+Route]   |
| [shoe]    (  START  )   [HR]   |
+--------------------------------+
```

| # | Detail | Behaviour | Gating | Our app |
|---|---|---|---|---|
| 2.1 | Sport icon beside Start | The sport picker is a glyph (shoe, bike and so on) for the current sport, placed next to Start. Tapping it opens the sport list. | [FREE] | **Keep.** |
| 2.2 | Defaults to the last-used sport | A new recording starts with the previous activity type. | [FREE] | **Keep.** Store it per device. |
| 2.3 | Sport and route more prominent (2025) | The redesign makes sport and route selection more visible on the start screen. | [FREE] | **Keep.** |
| 2.4 | GPS halo as signal indicator | GPS accuracy shows as a halo around the location dot, and the halo shrinks as the fix improves. There is no bar meter. | [FREE] | **Improve.** Keep the halo, add a small text label ("Acquiring GPS…" / "GPS ready") for accessibility, and use the accuracy radius in metres to size the halo. |
| 2.5 | GPS fix can take minutes | Strava says a first fix can take a few minutes. Holding still with mobile data on (A-GPS) helps. | [FREE] | **Improve.** Show a hint after about 20 s without a fix. Allow Start before the fix and backfill once it locks. |
| 2.6 | Add Route button at bottom right | An icon on the record map loads a saved route to follow. | [?] | **Keep**, free. |

## 3. In-progress recording screen

```
Post-2025 (target)                Pre-2025 (legacy)
+-------------------------+       +-------------------------+
| v                    [] |       | v            [minimize] |
|     map + track line    |       |  TIME        01:02:03   |
| +---------------------+ |       |  DISTANCE    10.24 km   |
| | 5:12 /km   10.24 km | |       |  PACE        5:12 /km   |
| | 52:10      +120 m   | |       |                         |
| +---------------------+ |       |  [swipe down -> map]    |
|  [PAUSE]                |       |  [PAUSE]                |
|  ^ swipe-up sheet       |       |  ^ swipe-up sheet       |
+-------------------------+       +-------------------------+
```

| # | Detail | Behaviour | Gating | Our app |
|---|---|---|---|---|
| 3.1 | Stats overlaid on the map (2025) | Key metrics float over the map, with no hard map/stats switch. Run pace is emphasised. | [FREE] | **Keep** as the default layout. |
| 3.2 | Map/stats toggle (legacy) | Swiping down on the stats, or tapping the minimize icon at top right, collapses to the map. Tapping the stats strip expands them again. Only for GPS activities. | [FREE] | **Keep** as an optional "big stats" mode, collapsed by swipe or tap, so the numbers are readable at arm's length. |
| 3.3 | Ride main stat changes when paused | Riding shows current speed. While paused it shows average speed. | [FREE] | **Keep.** |
| 3.4 | Run pace is the split average | The live pace field shows the current split's average pace, not instantaneous pace. | [FREE] | **Improve.** Default to split average and let users switch the field to current or average pace. |
| 3.5 | Live elevation for some sports | Hike, Trail Run, MTB, e-MTB, Gravel, Road and the snow sports (Alpine Ski, Snowboard, Backcountry, Snowshoe) add Current Elevation and Total Gain/Loss. | [FREE] | **Improve.** Offer the pair for any outdoor sport as a configurable field. |
| 3.6 | Android elevation is GPS-derived | Android uses GPS altitude, not a barometer. Strava corrects elevation after upload, so totals can change. | [FREE] | **Improve.** Use `TYPE_PRESSURE` when the phone has a barometer, fall back to GPS, and say on the summary when a total was corrected. |
| 3.7 | Real-time splits list | Since 2025, splits update live during recording. | [FREE] | **Keep.** *(medium confidence)* |
| 3.8 | Map layers while recording | The new map engine supports Heatmap, Winter and 3D terrain layers during a recording. | [SUB] | **Keep**, free (layers from open tile sources). |
| 3.9 | Live Segments progress | Shows live progress against your PR or the KOM, with start and end cues. | [SUB] | **Keep**, free. This depends on our segments feature. |
| 3.10 | Live Laps | Announced in 2025 as "coming soon": laps by distance or interval. No source confirms the control. *(unverified)* | [?] | **Improve.** Ship a manual lap button plus auto-lap by distance. |

## 4. Controls: start, pause, resume, finish, discard

| # | Detail | Behaviour | Gating | Our app |
|---|---|---|---|---|
| 4.1 | Manual pause overrides Auto-Pause | After a manual pause, Auto-Pause never resumes the activity. The user must tap Resume. | [FREE] | **Keep.** |
| 4.2 | Finish opens the save form | Finish opens a save screen with title, photos, description and privacy. | [FREE] | **Keep.** |
| 4.3 | Save sits at top right | On Android, Save is at the top right. The activity reaches the feed at once, and details fill in after sync. | [FREE] | **Keep.** Also save locally first so nothing is lost offline. |
| 4.4 | Discard asks for confirmation and is permanent | Discard shows a confirmation dialog, and a discarded recording cannot be recovered. | [FREE] | **Improve.** Keep the dialog and add a 5 s "Undo" snackbar, or a recently-discarded bin for 24 h. |
| 4.5 | Lock-screen and notification controls | A foreground-service notification shows elapsed time and distance, with pause/resume. *(unverified: no public source in this sweep describes the exact actions)* | [?] | **Keep/Improve.** Use an ongoing notification with live time, distance and pace, plus Pause/Resume and Finish actions, visible on the lock screen. |
| 4.6 | Haptics | No public source documents haptic feedback on start, pause or lap. *(unverified)* | [?] | **Improve.** Short haptic on start, pause, resume and lap, and a long-press to Finish to stop accidental taps. |

## 5. Swipe-up bottom sheet and settings

```
+--------------------------------+
|           ----                 |  <- drag handle
|  Beacon            [ off ]     |
|  Heart rate        Pair >      |
|  Settings / More               |
|    Screen Display   >          |
|    Audio Cues       >          |
|    Auto-Pause  Off|Runs|Rides  |
+--------------------------------+
```

| # | Detail | Behaviour | Gating | Our app |
|---|---|---|---|---|
| 5.1 | Sheet holds secondary controls | Swiping up opens Beacon, heart-rate pairing and More/Settings (Screen Display, Audio Cues, Auto-Pause). | [FREE] | **Keep.** |
| 5.2 | Entry labelled "Settings" or "Customize" | Recent help calls it Settings at the bottom of the sheet. An older article says Customize. | [FREE] | **Keep** "Settings". |
| 5.3 | "Screen Display" (keep screen on) | The Android label is Screen Display. It keeps the screen awake while recording. | [FREE] | **Keep.** Options: Off, Always on, On while unlocked. Use `FLAG_KEEP_SCREEN_ON` only while the Record screen is visible. |
| 5.4 | Settings are stored per device | Record and audio settings reset to defaults on logout. They are not synced. | [FREE] | **Improve.** Sync them with the account and also keep a local copy. |

## 6. Auto-Pause

| # | Detail | Behaviour | Gating | Our app |
|---|---|---|---|---|
| 6.1 | Three-way selector | One choice: Off, Runs only or Rides only. It is not a per-sport list. | [FREE] | **Improve.** Use a per-sport toggle so walks, hikes and other sports are covered. |
| 6.2 | Ride Auto-Pause on by default | Rides default to on. The run default isn't stated *(unverified, probably off)*. | [FREE] | **Keep:** on for rides, off for runs, walks and hikes. |
| 6.3 | Detection source differs by sport | Rides use GPS speed. Runs use the accelerometer. | [FREE] | **Keep.** Use the motion sensor plus GPS for runs and GPS speed for rides. |
| 6.4 | With Auto-Pause off, moving time is computed later | The server derives rest time after upload. With manual pauses, moving time is taken from the timer instead. | [FREE] | **Keep.** Compute it on the device, with the same rule. |
| 6.5 | Discouraged for walks and hikes | Slow speeds cause false pauses and resumes. | [FREE] | **Keep** default off, and add a hint if a user turns it on for these sports. |
| 6.6 | False "moving" indoors | Indoors, Auto-Pause can miss stops. Strava advises pausing manually. | [FREE] | **Improve.** Suppress auto-resume when GPS accuracy is above about 30 m. |
| 6.7 | GPS re-wakes after a long stop | The OS may power GPS down during a long stop. Reopening Record turns it back on. | [FREE] | **Improve.** Keep the location request active in the foreground service so no manual re-wake is needed. |

## 7. Audio cues

| # | Detail | Behaviour | Gating | Our app |
|---|---|---|---|---|
| 7.1 | Cue categories | Start, stop and pause, splits, and Live Segment events. | [FREE] / segments [SUB] | **Keep**, all free. |
| 7.2 | Start/stop/pause per sport | Available for runs, rides and walks. Split cues are runs only. | [FREE] | **Improve.** Allow split cues for every sport. |
| 7.3 | Distance interval of half or full unit | Every 0.5 or 1 km/mi, following the metric/imperial setting. Run and Trail Run only. | [FREE] | **Improve.** Custom intervals plus time-based cues, for all sports. |
| 7.4 | Auto-Pause cues are conditional | Pause and resume are spoken only if split or segment audio is on. There is no separate toggle. | [FREE] | **Improve.** Add an explicit toggle. |
| 7.5 | System TTS on the media stream | Cues use Android TTS on the media volume. A TTS language mismatch can silence them. | [FREE] | **Keep.** Also request transient audio focus with ducking, and detect a missing TTS language and warn. |
| 7.6 | Live Segment audio | Spoken updates on starred and popular segments. | [SUB] | **Keep**, free. |
| 7.7 | Navigation path | Record, sheet up, Settings, Audio Cues. | [FREE] | **Keep.** |

## 8. Sensors

| # | Detail | Behaviour | Gating | Our app |
|---|---|---|---|---|
| 8.1 | BLE heart-rate pairing only | The sheet documents BLE HR straps only. Power and cadence are not listed. *(unverified for current builds)* | [FREE] | **Improve.** Support BLE HR, cadence/speed (CSC) and power (CPS), with auto-reconnect and a status chip. |

## 9. Beacon (live safety tracking)

| # | Detail | Behaviour | Gating | Our app |
|---|---|---|---|---|
| 9.1 | Up to 3 contacts by SMS | Each contact gets an SMS with a unique link. They need no account. | [FREE] (phone) | **Keep.** |
| 9.2 | What the page shows | Live location, the route so far, the start point and phone battery %, updated about every 15 s. | [FREE] | **Keep.** |
| 9.3 | Editable message and manual share | The SMS text can be edited, and the link can be shared through any app. | [FREE] | **Keep.** |
| 9.4 | Needs data and stops on finish | Beacon needs cellular data and ends automatically when the recording ends. | [FREE] | **Keep.** Also queue points while offline and flush them when back online. |
| 9.5 | Beacon from a watch or bike computer | Beacon without the phone requires a subscription. | [SUB] | **Keep**, free, where the device integration allows it. |

## 10. Routes during recording

| # | Detail | Behaviour | Gating | Our app |
|---|---|---|---|---|
| 10.1 | Off-route alert on by default | When following a route, an alert is on by default. | [?] | **Keep.** |
| 10.2 | Banner auto-dismisses | A banner appears when the user leaves the route and hides itself once they rejoin. Small GPS drift is ignored. | [?] | **Keep**, with about 30–50 m hysteresis, plus a haptic and audio alert. |
| 10.3 | Navigation settings toggle | The off-route toggle is in navigation settings, opened from Record. | [?] | **Keep.** |

## 11. Data and sync

| # | Detail | Behaviour | Gating | Our app |
|---|---|---|---|---|
| 11.1 | No data used while recording | Recording itself uses no mobile data. Map tiles, Live Segments and Beacon do. | [FREE] | **Keep.** Also pre-cache tiles for the selected route. |
| 11.2 | Small uploads that can wait for Wi-Fi | About 1 MB per 2 h of activity. Upload can be deferred to Wi-Fi. | [FREE] | **Keep** (WorkManager, unmetered constraint optional). |
| 11.3 | GPS gaps cannot be repaired | Distance lost to GPS dropouts cannot be fixed after saving. | [FREE] | **Improve.** Show a gap warning on the summary, and offer optional straight-line or route-snapped gap fill. |

---

## Acceptance checklist

**Entry point and navigation**
- [ ] 1.1 Tapping the centre Record button in the bottom nav opens the Record screen.
- [ ] 1.2 The down-chevron (or back) closes Record when idle and minimises it while recording, without stopping the recording.
- [ ] 1.3 The first Start asks for location, then background location, with a rationale. If background location is denied, a warning chip shows.
- [ ] 1.4 The user is prompted to exempt the app from battery optimisation, and the prompt is not repeated once the exemption is granted.

**Pre-start screen**
- [ ] 2.1 A sport glyph next to Start reflects the current sport, and tapping it opens the sport list.
- [ ] 2.2 After recording sport X, the next Record session preselects X, including after an app restart.
- [ ] 2.4 The GPS halo radius tracks the reported accuracy, and a text label switches to "GPS ready" when accuracy is 20 m or better.
- [ ] 2.5 A hint appears after 20 s without a fix, and Start works before a fix.
- [ ] 2.6 The Add Route button loads a saved route onto the record map.

**In-progress recording**
- [ ] 3.1 Stats overlay the map during recording.
- [ ] 3.2 Swipe or tap switches between the overlay and big-stats mode for GPS sports.
- [ ] 3.3 During a ride, the main stat is current speed while moving and average speed while paused.
- [ ] 3.4 Run pace defaults to the split average and can be switched to current pace.
- [ ] 3.5 / 3.6 The elevation fields show current elevation and gain/loss, using the barometer when present.
- [ ] 3.7 The splits list updates within 2 s of a split completing.
- [ ] 3.8 Map layers (heatmap, terrain) can be toggled during recording with no paywall.
- [ ] 3.9 Live segment progress appears on the Record screen with no paywall.
- [ ] 3.10 A Lap button and distance-based auto-lap create lap markers.

**Controls**
- [ ] 4.1 After a manual pause, moving does not auto-resume.
- [ ] 4.2 / 4.3 Finish opens the save form (title, photos, description, privacy) with Save at the top right. The activity is persisted locally before upload.
- [ ] 4.4 Discard needs confirmation and offers Undo for at least 5 s.
- [ ] 4.5 An ongoing notification shows live time and distance, with Pause/Resume and Finish actions that work from the lock screen.
- [ ] 4.6 Start, pause, resume and lap give haptic feedback, and Finish needs a long-press.

**Bottom sheet and settings**
- [ ] 5.1 Swiping up opens the sheet with Beacon, Sensors and Settings.
- [ ] 5.3 With Screen Display set to Always on, the screen does not time out while Record is visible.
- [ ] 5.4 Record settings survive logout and login, and sync across devices.

**Auto-Pause**
- [ ] 6.1 Auto-Pause can be set per sport.
- [ ] 6.2 Defaults are on for rides and off for runs, walks and hikes.
- [ ] 6.3 Run Auto-Pause triggers from motion when GPS is poor, and ride Auto-Pause triggers on GPS speed.
- [ ] 6.4 Moving time excludes rest. With manual pauses, it equals timer time.
- [ ] 6.5 Turning on Auto-Pause for a walk or hike shows a hint.
- [ ] 6.6 Auto-resume does not fire while GPS accuracy is above 30 m.
- [ ] 6.7 After a stop of 10 minutes or more with the screen off, recording resumes on movement without the user opening the app.

**Audio cues**
- [ ] 7.1 / 7.2 Start, stop, pause and split cues can be enabled for every sport.
- [ ] 7.3 The distance interval can be set to 0.5, 1 or a custom value, and a time interval is also available.
- [ ] 7.4 A separate toggle controls Auto-Pause and resume cues.
- [ ] 7.5 Cues play on the media stream, duck other audio, and warn if the TTS language is missing.
- [ ] 7.6 Live Segment audio is announced with no paywall.

**Sensors**
- [ ] 8.1 BLE HR, CSC and power sensors pair, show live values, and auto-reconnect within 10 s of a dropout.

**Beacon**
- [ ] 9.1 Up to 3 contacts can be chosen, and each receives an SMS link.
- [ ] 9.2 The web page shows location, the route so far, the start point and battery %, refreshing about every 15 s, with no login.
- [ ] 9.3 The SMS text is editable, and the link can be shared through the Android share sheet.
- [ ] 9.4 Beacon stops when the recording ends, and points queued offline upload once the phone reconnects.

**Routes**
- [ ] 10.1 / 10.2 With a route loaded, going more than about 50 m off it shows a banner with a haptic and audio alert. Returning hides the banner, and drift of 30 m or less does not trigger it.
- [ ] 10.3 Off-route alerts can be toggled from navigation settings on Record.

**Data and sync**
- [ ] 11.1 Recording in airplane mode (with GPS on) completes and saves locally.
- [ ] 11.2 Upload can be restricted to Wi-Fi and runs automatically once the phone is connected.
- [ ] 11.3 GPS gaps over 100 m are flagged on the summary.

## Sources

- Strava Support, "Recording an Activity": https://support.strava.com/hc/en-us/articles/216917397-Recording-an-Activity and https://support.strava.com/en-us/articles/15402137-recording-an-activity
- Strava Support, "Auto-Pause (Android)": https://support.strava.com/hc/en-us/articles/216919277-Auto-Pause-Android
- Strava Support, "Having issues with Auto-Pause for the mobile app": https://support.strava.com/hc/en-us/articles/216929827-Having-issues-with-Auto-Pause-for-the-mobile-app-
- Strava Support, "Audio Announcements": https://support.strava.com/en-us/articles/15402180-audio-announcements
- Strava Support, "Troubleshooting Android GPS Issues": https://support.strava.com/hc/en-us/articles/216918967-Troubleshooting-Android-GPS-Issues
- the5krunner, "Strava app redesign" (Jul 2025): https://the5krunner.com/2025/07/16/strava-app-redesign/
- Advnture, record redesign coverage: https://www.advnture.com/tech/apps-software/no-more-switching-between-screens-strava-updates-record-function-for-a-smoother-and-smarter-experience
- BikeRadar, "Strava redesigned record feature": https://www.bikeradar.com/news/strava-redesigned-record-feature
- Coach, "Strava's Beacon safety feature is now free": https://www.coachweb.com/fitness-apps/9051/strava-s-beacon-safety-feature-is-now-free-to-use
