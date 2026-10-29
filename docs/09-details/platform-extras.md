# 09 – Details: Platform Extras (Android)

Minor-details catalog covering Android-specific behaviour: record-screen platform quirks, offline mode, Health Connect, Wear OS, deep links/OAuth, share/import targets, upload states, widgets, battery and error/empty/loading states.

Legend: **Gating** is Strava's model for reference only ([FREE] / [SUB]). StravaFit has no paywall, so every row is free for us. **Our app** = keep / improve / drop (+reason). Items marked *(unverified)* come from low-confidence or inferred sources and were not re-checked for this pass.

Gaps not covered by the raw findings (need a later sweep): notification channels, app shortcuts (long-press launcher icon), TalkBack labels, font scaling, tablet/landscape layouts. Placeholder acceptance items for these are listed at the end so they aren't forgotten.

---

## 1. Record screen (in-progress activity)

| # | Detail | Behaviour | Gating | Our app |
|---|---|---|---|---|
| R1 | Swipe-down collapses stats | Swiping down on the stats panel, or tapping the minimize icon at top right, switches to a full map. Tapping the collapsed stats strip expands it again. | [FREE] | keep |
| R2 | Bottom sheet for controls | Sensors, settings, audio cues, Beacon and display options live in a swipe-up bottom sheet. | [FREE] | keep |
| R3 | GPS accuracy halo | A translucent halo around the location dot shrinks as accuracy improves. This is the main GPS-quality cue. | [FREE] | improve: also show a text/icon state ("GPS weak / good") for accessibility, not colour/size alone |
| R4 | "Screen Display" keep-awake | An Android-specific toggle under More/Display keeps the screen on during recording. | [FREE] | keep |
| R5 | Audio cue triggers | Separate toggles for start, pause, resume/stop, splits, and Live Segment start/finish. | [FREE] | keep |
| R6 | Auto-pause (GPS / accelerometer) | Detects rest from GPS speed or the accelerometer. With auto-pause off, moving time is computed server-side, so the live timer shows elapsed time. | [FREE] | improve: compute moving time on-device too, so the live and saved numbers match |
| R7 | Live elevation from GPS only | Android live elevation comes from GPS altitude (noisy). iOS can use the barometer. | [FREE] | improve: use the barometer (`TYPE_PRESSURE`) when the device has one, with GPS as fallback |
| R8 | Off-route alerts default ON | When following a route on Android, off-route alerts are on by default and can be toggled in navigation settings. | [SUB] | keep (free) |
| R9 | BLE HR pairing from sheet | Pair a BLE heart-rate strap straight from the record sheet. | [FREE] | keep |
| R10 | Beacon max 3 contacts | Live-location sharing to up to 3 safety contacts, set from the record sheet. | [SUB]; phone Beacon reportedly free since 2023 *(unverified)* | improve: allow more than 3 contacts, or document the limit |
| R11 | Data budget | No mobile data while recording unless map, Live Segments or Beacon are on. Upload uses about 1 MB per 2 h. | [FREE] | keep; add "upload on Wi-Fi only" setting |

```
 Record (stats)            swipe down / [_]        Record (map)
+----------------------+   ---------------->   +----------------------+
| 00:42:10       [_]   |                       |   map  (.)halo       |
| 7.21 km   5:51 /km   |                       |                      |
| ...                  |   <---------------    |----------------------|
|  [ Pause ]           |   tap strip           | 00:42:10 | 7.21 km ^ |
| ===== ^ sheet ====== |                       +----------------------+
+----------------------+
```

## 2. Save / Discard

| # | Detail | Behaviour | Gating | Our app |
|---|---|---|---|---|
| S1 | Save in top-right app bar | On Android, Save sits in the top-right app bar (iOS puts it at the bottom). Finish opens the save screen with title, photos and privacy. | [FREE] | keep (Material convention) |
| S2 | Discard is irreversible | A confirmation dialog appears, and confirmed discards can't be recovered. | [FREE] | improve: keep the discarded track locally for 24 h with an "Undo" snackbar |

```
+--------------------------------+
| <-  Save Activity       [SAVE] |
| Title: Morning Run             |
| [ + photos ]                   |
| Visibility: Everyone  v        |
|        Discard activity        |
+--------------------------------+
```

## 3. Offline behaviour

| # | Detail | Behaviour | Gating | Our app |
|---|---|---|---|---|
| O1 | Offline Home state | With no connectivity, Home shows two sections: "Navigate offline" (downloaded routes, subscribers only, hidden for free users) and "Record offline" (everyone). | [SUB] for navigate; [FREE] for record | keep (both sections for everyone) |
| O2 | Offline route Start is two-step | Start on an offline route only opens it on the record screen. A second Start tap begins recording. | [SUB] | keep (prevents accidental starts) |
| O3 | "Download for offline use" toggle | On by default when a route is saved in the app. Turning it off deletes the download. Routes saved on the web are not auto-downloaded. About 10 MB per route, more for cycling. Downloads survive a lapsed subscription. | [SUB] | improve: show download size and storage used, and add a "download all saved routes" option |
| O4 | Auto-upload, no manual sync | Activities recorded offline upload automatically when a connection returns. There is no "sync now" button and no visible pending queue. | [FREE] | improve: add a visible pending-upload list with retry/"sync now" and a failure reason |

```
 Home (offline)
+------------------------------+
|  ! No connection             |
|  Navigate offline            |
|   > Lake Loop  12.4 km       |
|   > Hill Repeats  8.0 km     |
|  Record offline              |
|   [  Start recording  ]      |
+------------------------------+
```

## 4. Upload / processing (loading, error, empty states)

| # | Detail | Behaviour | Gating | Our app |
|---|---|---|---|---|
| U1 | Four processing states | Still processing, Ready, Error processing, and Created activity deleted. Mean processing time is under 2 s. Segment efforts can appear after the activity is already visible. | [FREE] | keep; show a "matching segments..." chip while efforts are pending |
| U2 | Duplicate error names existing activity | Error takes the form "`<file>` duplicate of activity `<id>`". external_id defaults to the filename. | [FREE] | improve: make the error tappable to open the existing activity |
| U3 | Poll at most 1/s | Upload status polling should run no more than once per second. | [FREE] | keep (with backoff after 10 s) |
| U4 | Error text may contain escaped HTML | Error strings must be unescaped or stripped before they go in a toast or banner. | [FREE] | keep (sanitize every server error string) |

## 5. Share targets / file import

| # | Detail | Behaviour | Gating | Our app |
|---|---|---|---|---|
| F1 | Accepted file types | FIT, TCX, GPX 1.1, JSON (set-based types only), plus .gz variants. Trackpoints need timestamps, except in set-based files. It's *(unverified)* whether Strava's Android app registers as a share target for these. | [FREE] | improve: register an ACTION_SEND / ACTION_VIEW intent filter for .fit/.tcx/.gpx(.gz) so files open in our app |
| F2 | Privacy on outbound shares | Basic-read connected apps see only Everyone/Followers activities, with privacy-zone points stripped. Only You and privacy-zone data need read_all. | [FREE] | keep: apply the same stripping to exports and shares to other apps by default |

## 6. Deep links / OAuth

| # | Detail | Behaviour | Gating | Our app |
|---|---|---|---|---|
| D1 | https App Link handoff | Third-party apps send ACTION_VIEW to `https://www.strava.com/oauth/mobile/authorize`. The installed app intercepts it as a verified App Link, otherwise it falls back to mobile web. The custom `strava://` scheme is iOS-only. | [FREE] | keep: use verified App Links (assetlinks.json) for our own domain |
| D2 | App OAuth needs v75.0+ | Older or missing installs fall back to the web endpoint. | [FREE] | drop: version-specific to Strava; just keep the web fallback |
| D3 | approval_prompt auto/force | "auto" skips consent when already granted. "force" always shows it. | [FREE] | keep if we expose an API |
| D4 | Scopes can be unticked | Users can uncheck individual scopes. The granted scope string comes back in the redirect, so partial grants must be handled. | [FREE] | keep |
| D5 | Short-lived tokens | Access tokens last 6 h, with refresh tokens. A refresh with more than 1 h left returns the same token. Refresh tokens may rotate, invalidating the old one. | [FREE] | keep: store the newest refresh token atomically |
| D6 | No Google sign-in in webview | Google blocks sign-in inside webviews, so use a browser or Custom Tab. | [FREE] | keep (Custom Tabs only) |

## 7. Health Connect

| # | Detail | Behaviour | Gating | Our app |
|---|---|---|---|---|
| H1 | Data direction | Writes time, distance and calories from GPS activities. Reads weight. Android only. Whether manual activities are written is *(unverified)*. | [FREE] | improve: also write heart rate, route (ExerciseRoute) and manual activities; read steps/HR optionally |
| H2 | Setup path and OS split | You > gear > Manage Apps and Devices > Health Connect, then the system permission prompts. Android 13 or lower needs the Health Connect app installed; 14+ has it in system Settings. The troubleshooting fix is Allow, then restart or pull to refresh. | [FREE] | keep; add an in-app "permissions missing" banner with a deep link to HC settings |
| H3 | Weight overlap with profile:write | Weight/FTP are editable profile values. Whether Health Connect weight overwrites the Strava weight is *(unverified)*. | [FREE] | improve: newest timestamp wins, shown with its source |

```
 Settings > Health Connect
+------------------------------+
| Health Connect     [Connected]|
| Writes: time, distance, kcal |
| Reads:  weight               |
| [ Manage permissions  > ]    |
+------------------------------+
```

## 8. Wear OS

| # | Detail | Behaviour | Gating | Our app |
|---|---|---|---|---|
| W1 | Wear OS 3.0+, Tizen dropped | A Galaxy Watch pairs either via Galaxy Wearable or a Wear OS setup, not both. | [FREE] | keep (target Wear OS 3+) |
| W2 | Silent sign-in from phone | Signs in automatically if the phone is signed in; otherwise prompts to continue on the phone. | [FREE] | keep |
| W3 | About 48 sport types | Ride, Run, Swim (outdoor), Hike, Walk, ski types, racquet sports, Weight Training, Yoga, Wheelchair and others. Records time, distance and HR. The post-save summary has "open on phone". | [FREE] | keep (count approximate) |
| W4 | Last-started workout app wins | Concurrent workout apps are unsupported; the newest one ends the other's session. | [FREE] | keep; show a clear "session ended by another app" message |
| W5 | Beacon on watch | Contacts must be set on the phone first. Non-LTE watches need the phone connected for the start SMS. | [SUB] | keep (free) |
| W6 | Tile + complication | Tap to launch recording directly. Tile layout is *(unverified)*. | [FREE] | keep |
| W7 | Health Services recording | Uses Wear Health Services for battery efficiency and longer sessions (vendor testimonial). | [FREE] | keep |

```
 Tile                 Sport picker        Summary
+-----------+        +-----------+       +-----------+
|  STRAVAFIT|        |  > Run    |       | 5.02 km   |
|  [> Run ] |        |    Ride   |       | 28:41     |
|  last: 5k |        |    Walk   |       | HR 152    |
+-----------+        +-----------+       |[Open phone]|
                                         +-----------+
```

## 9. Battery saver / background

| # | Detail | Behaviour | Gating | Our app |
|---|---|---|---|---|
| B1 | OEM battery savers kill GPS | Strava help (Huawei and similar) says to set battery to normal/unrestricted, add the app as protected, and reset GPS. | [FREE] | improve: detect battery-optimization status (`isIgnoringBatteryOptimizations`) and show a one-time guided prompt; use a foreground service with a location type |

## 10. Launcher widget

| # | Detail | Behaviour | Gating | Our app |
|---|---|---|---|---|
| X1 | No first-party Android widget | iPhone has a weekly-mileage widget, but no Android equivalent was found *(unverified)*. | n/a | improve: ship Glance widgets (weekly distance and streak, plus a 1x1 "Start run") so we exceed parity |

```
+---------------------------+   +-----+
| This week     24.6 km     |   | (>) |
| M T W T F S S             |   | Run |
| # # . # . . .   [Start]   |   +-----+
+---------------------------+
```

---

## Acceptance checklist

Record
- [ ] R1 Swiping down on stats, or tapping the minimize icon, shows the full map; tapping the collapsed strip restores stats.
- [ ] R2 The record screen has a swipe-up bottom sheet with sensors, settings, audio cues, Beacon and display.
- [ ] R3 The location dot shows an accuracy halo whose radius tracks reported accuracy, plus a text GPS state readable by TalkBack.
- [ ] R4 The "Screen Display" toggle keeps the screen on for the whole recording when enabled.
- [ ] R5 Each audio cue trigger (start, pause, resume/stop, splits, segment) can be toggled independently.
- [ ] R6 Auto-pause works with GPS speed and the accelerometer, and live moving time equals saved moving time within 1 s.
- [ ] R7 Live elevation uses the barometer when present and falls back to GPS otherwise.
- [ ] R8 Off-route alerts are on by default when following a route and can be toggled from the record screen.
- [ ] R9 A BLE HR strap can be paired from the record sheet without leaving recording.
- [ ] R10 Beacon can be configured from the record sheet with at least 3 contacts, free.
- [ ] R11 Recording with map/Beacon off uses 0 bytes of mobile data, and an "upload on Wi-Fi only" setting exists.

Save
- [ ] S1 On the save screen, the Save action is in the top-right app bar.
- [ ] S2 Discard asks for confirmation and offers Undo for at least 24 h.

Offline
- [ ] O1 With no connectivity, Home shows "Navigate offline" (downloaded routes) and "Record offline" for every user.
- [ ] O2 Start on an offline route opens the record screen without recording until Start is pressed again.
- [ ] O3 Saving a route offers "Download for offline use" (default on); turning it off deletes the file; size is shown.
- [ ] O4 Offline activities auto-upload when connectivity returns, and a pending-upload list with "sync now" and retry is visible.

Upload
- [ ] U1 Activity detail shows processing, ready, error and deleted states, plus a pending-segments indicator.
- [ ] U2 A duplicate upload error names and links to the existing activity.
- [ ] U3 Status polling never exceeds 1 request/s.
- [ ] U4 No error toast or banner ever shows raw or escaped HTML.

Share / import
- [ ] F1 Opening or sharing a .fit, .tcx, .gpx or .gz variant from another app imports it into StravaFit.
- [ ] F2 Exports and outbound shares strip privacy-zone points and exclude Only You activities unless explicitly allowed.

Deep links / auth
- [ ] D1 Our https links open the app through verified App Links and fall back to the web when it isn't installed.
- [ ] D4 The app works with partially granted scopes and degrades gracefully.
- [ ] D5 Token refresh persists the rotated refresh token, and a killed process mid-refresh does not log the user out.
- [ ] D6 All third-party sign-in uses Custom Tabs or a browser, never an embedded WebView.

Health Connect
- [ ] H1 A finished GPS activity appears in Health Connect with time, distance, calories, HR and route.
- [ ] H2 Setup works on Android 13 (HC app) and 14+ (system); missing permissions show a banner with a deep link.
- [ ] H3 Weight conflicts resolve by newest timestamp, and the source is shown.

Wear OS
- [ ] W1 The watch app installs and runs on Wear OS 3.0+.
- [ ] W2 The watch signs in silently when the phone is signed in, otherwise shows "continue on phone".
- [ ] W3 The watch sport picker offers all phone sport types and records time, distance and HR; the summary has "Open on phone".
- [ ] W4 When another workout app takes over, our watch app saves the partial session and explains why it stopped.
- [ ] W5 Beacon from the watch sends the start message over LTE or the paired phone.
- [ ] W6 A Tile and a complication each start recording in one tap.
- [ ] W7 The watch records through Health Services, and a 2 h run uses less than 40% battery on a reference device.

Battery
- [ ] B1 Recording runs as a location foreground service, and the app detects battery optimization and guides the user to exempt it.

Widget
- [ ] X1 A home-screen widget shows weekly distance, and a 1x1 widget starts a recording.

Not yet researched (placeholders)
- [ ] Notification channels are split (recording-ongoing, uploads, social, safety) and each can be muted separately.
- [ ] Launcher long-press shortcuts include "Record run" and "Record ride".
- [ ] Every interactive element has a TalkBack label, and stats are announced on focus.
- [ ] Layout has no clipping at 200% font scale.
- [ ] Tablet/landscape uses a two-pane layout for feed and activity detail, and the record screen supports landscape.

## Sources

- Strava Support, Recording an activity: https://support.strava.com/en-us/articles/15402137-recording-an-activity
- Strava Support, Recording an Activity (audio cues): https://support.strava.com/hc/en-us/articles/216917397-Recording-an-Activity
- Strava Support, How do I use Strava offline: https://support.strava.com/en-us/articles/17699138-how-do-i-use-strava-offline
- Strava Support, Health Connect and Strava: https://support.strava.com/hc/en-us/articles/43440435267597-Health-Connect-and-Strava
- Strava Support, Wear OS and Strava: https://support.strava.com/hc/en-us/articles/216919297-Wear-OS-and-Strava
- Strava Support, GPS/battery troubleshooting (Huawei etc.): https://support.strava.com/hc/en-us/articles/216942547
- Strava Developers, Authentication: https://developers.strava.com/docs/authentication/
- Strava Developers, Uploads: https://developers.strava.com/docs/uploads/
- Android Developers, Wear health & fitness gallery: https://developer.android.com/wear/gallery/health-fitness
- MixRank Play Store metadata (Wear tile/complication): https://mixrank.com/playstore/apps/com.strava
- Tom's Guide, Strava iPhone widget: https://www.tomsguide.com/wellness/fitness/this-easy-strava-trick-means-i-never-miss-a-run-how-to-add-the-strava-widget-to-your-iphone-homescreen
