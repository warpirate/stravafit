# 08 — Parity Gap Report

Audit of the [feature parity checklist](feature-parity-checklist.md) against the code on `master` (commit `8c5738c`, 2026-10-29).
Rules from [`DECISIONS.md`](../DECISIONS.md): every Strava paid feature is free here, and the app should behave like the real Strava app.

**385 rows audited: 69 done, 316 not done (77 partial, 236 missing, 3 in progress).**
"In progress" means route suggestions near you, being built in the `D:/stravafit-wt/suggest` worktree. They appear as 3 rows: Maps suggested routes, the Home carousel, and the Maps-tab entry point.

---

## 1. Spot-check of the 10 highest-impact claims

All 10 claims hold on `master`. One needed a small correction (row 4) and one an addition (row 6).

| # | Claim | Result | Evidence |
|---|---|---|---|
| 1 | No pull-to-refresh anywhere | Confirmed | No `PullToRefresh`/`pullToRefresh` in `android/**/*.kt` |
| 2 | No share-sheet/deep-link intent filters | Confirmed | `AndroidManifest.xml:21-24` has only MAIN/LAUNCHER |
| 3 | Default tab is hard-coded | Confirmed | `AppNavHost.kt:53` `startDestination = FeedRoute` |
| 4 | Bell unread dot is fake | Confirmed, corrected | The dot is now `!notificationsSeen` (`FeedViewModel.kt:74,93,117`). That flag is in-memory only, so the dot comes back on every launch, and there is still no inbox |
| 5 | No accelerometer auto-pause | Confirmed | No `TYPE_ACCELEROMETER`/`TYPE_STEP_*` in android, shared or lib |
| 6 | No distance auto-lap | Confirmed | Only `LapTrigger.MANUAL` and `SESSION_END` are used |
| 7 | No BLE sensors | Confirmed | No `BluetoothLeScanner`/`BluetoothGatt` code |
| 8 | GAP computed but never shown | Confirmed, plus | GAP is now also computed on import (`ImportedActivityConverter.kt:117`), but nothing in activity-detail displays it |
| 9 | Best efforts all show rank 1 | Confirmed | `LocalActivityRepository.kt:122` `distinctBy { target }`; `MeScreen.kt:392` `Medal(rank = 1)` |
| 10 | No off-route, goals, HR zones (UI) or Beacon | Confirmed | Off-route is only a comment (`RouteEntity.kt:34`). No goal code. `HeartRateZones` is never used in android. Beacon calls `notAvailable(...)` (`RecordRoute.kt:152`) |

---

## 2. Summary by section

The "not done" counts are exact. The "done" counts are the checklist rows minus the not-done rows, so they are approximate: 13 of the 69 done rows came from detail-doc additions and are not split out by section.

| Section | Done | Partial | Missing | In progress |
|---|---:|---:|---:|---:|
| 1 Onboarding, auth, account | 2 | 7 | 18 | 0 |
| 2 Navigation and chrome | 0 | 2 | 7 | 1 |
| 3 Recording | 16 | 9 | 11 | 0 |
| 4 Save, edit, manage | 5 | 8 | 10 | 0 |
| 5 Activity detail | 1 | 10 | 13 | 0 |
| 6 Share and export | 11 | 8 | 8 | 0 |
| 7 Import and integrations | 1 | 2 | 8 | 0 |
| 8 Wear OS | 0 | 0 | 8 | 0 |
| 9 Home feed and social | 1 | 6 | 22 | 1 |
| 10 Clubs, challenges, messaging | 0 | 1 | 21 | 0 |
| 11 Segments | 0 | 0 | 25 | 0 |
| 12 Maps, routes, heatmaps | 3 | 5 | 25 | 1 |
| 13 Training, progress, profile | 2 | 5 | 21 | 0 |
| 14 Privacy and safety | 0 | 4 | 13 | 0 |
| 15 Notifications | 1 | 1 | 9 | 0 |
| 16 Settings, units, a11y | 2 | 6 | 7 | 0 |
| 17 Design system | 3 | 1 | 1 | 0 |
| 18 Monetization (mostly Won't) | 5 | 0 | 1 | 0 |
| 19 Platform | 3 | 2 | 8 | 0 |
| Not split by section | 13 | | | |
| **Total** | **69** | **77** | **236** | **3** |

What stands out:
- Recording and share/export are in good shape.
- Segments (0/25), clubs (0/22), Wear OS (0/8) and social have almost nothing.
- Most of the everyday gap can be built **on the device with free APIs**. Only the social layer truly needs our Supabase backend.

---

## 3. Ranked gaps

Size: **S** is up to about 1 day, **M** is 2 to 5 days, **L** is over a week or a new module.

### Tier A — used every day, buildable now on the device or with free APIs

| # | Gap | What Strava does | What we have | Approach | Size |
|---|---|---|---|---|---|
| A1 | **Follow a route while recording** (progress, remaining distance, ETA, off-route alert) | Shows the route line with remaining distance. Off-route alerts are on by default when following a route | The route is drawn on the live map (`RecordingPages.kt:75`). No progress, no off-route | Snap each fix to the polyline using `RoutePointEntity` cumulative distance. Show remaining distance and ETA on the panel. Above 40 m cross-track for N fixes (with hysteresis), show a banner, vibrate and speak a TTS cue | M |
| A2 | **Goals and streaks** (weekly, monthly, yearly) | Progress ring on You and a weekly streak | Nothing | DataStore goal per sport and period. Progress ring on Progress and Home from `StatsAggregator`. Streak = consecutive ISO weeks with at least one activity | M |
| A3 | **Local notifications inbox** (bell) | Inbox with unread tint and mark all read | Bell dot that resets each launch, then a "needs a server" snackbar | Room table fed by events on the device: PR, best effort, goal reached, weekly summary, import done, routes ready. `NotificationsRoute` screen. Bell badge shows the real unread count | M |
| A4 | **Personal segments** (create, auto-match, PR/2nd/3rd, list on activity detail) | Segments matched on every upload, with medals on the activity | Nothing (the Segments chip shows a toast) | Room `SegmentEntity` and `SegmentEffortEntity`. "Create segment" with a range picker on activity detail. Matcher after save: bbox prefilter, start/end within 25 m, along-track check. Rank against your own efforts and freeze the medal at save | L |
| A5 | **Best-effort medals 2nd/3rd and yearly** | Gold, silver and bronze, lifetime and yearly | Only rank 1 (`distinctBy target`) | Keep the top 3 per target. Rank each effort against all-time and current-year efforts. Solid vs outlined medals on detail and on You | S |
| A6 | **Progress chart ranges, metric and scrub** | 1W/1M/3M/6M/YTD/1Y, distance/time/elevation, scrub with haptics | Fixed 12 weeks, no interaction | Range chips, a metric toggle, and `pointerInput` scrub with a haptic tick. Tap a week to list its activities. Add monthly and previous-year totals | M |
| A7 | **Full profile page from the avatar** | Header, counts, stats, trophies, gear | Avatar opens Edit profile. `ProfileHeader` is unused | `ProfileRoute` using `ProfileHeader`, all-time stats, best efforts and trophy slots from Room | M |
| A8 | **Beacon (no-server version)** | Shares live location with safety contacts, free | Toast "needs a server" | Pick up to 3 contacts. Send an SMS (`SmsManager`) or a share intent with an OSM link and battery % at start, every N minutes and at the end. A live web page comes later with Supabase (Tier B) | M |
| A9 | **Auto-pause with motion sensors** | GPS plus accelerometer for foot sports; no false resume when accuracy is poor | GPS only | Step detector/accelerometer as a new `EngineInput`, fused in `MotionDetector`. Ignore auto-resume while accuracy is over 30 m | M |
| A10 | **Audio cues per category plus time cues** | Each cue can be toggled; time and distance intervals | One master switch plus a distance interval | Per-category booleans in `AudioCueSettings` and a time-interval milestone in `AudioCuePlanner` | S |
| A11 | **Cadence and power charts, GAP display** | Charts for cadence, power and GAP | `ChartMetric` has speed, elevation and HR only. GAP is stored but not shown | Add `CADENCE`/`POWER` to `ChartMath`. Show an "Avg GAP" stat, a GAP column per split and a GAP series in the chart | S |
| A12 | **Elapsed time as headline for listed sports** | Elapsed time is the headline for races, yoga, weights, ski and similar | Moving time is always first | Elapsed-headline rule per `SportType` in `DetailRows.baseStats`. Reuse it on the feed card | S |
| A13 | **Feed card photo carousel** | Map plus photos with a page indicator | Map only | `HorizontalPager` over the map and `ActivityExtras` photos | S |
| A14 | **Stats sticker and photo + stats share templates** | Transparent stats sticker; a photo used as the background | Route-only sticker | Stats-sticker template in `lib/route-art`. Photo template that composites an activity photo with stats and the trace. Copy to clipboard | M |
| A15 | **Safe audience fallback and per-sport default** | Per-sport default; unknown falls back to the most private | Fallback is `FOLLOWERS` (`Visibility.kt:23`, `EntityMappers.kt:39`) | Fallback becomes `ONLY_ME`. Add a `Map<SportType, Visibility>` override in `AppSettings` | S |
| A16 | **Crop activity** (overflow item) | Edit, Crop, Delete | No Crop | Range-handle screen. Drop points outside the range and re-run `ActivityFinisher` | M |
| A17 | **Pace vs speed override per sport** | Per-sport choice; swim m/yd | Defaults by sport only | `Map<SportType, SpeedDisplay>` in `AppSettings`, passed to the formatters | S |
| A18 | **Localised auto-titles** | "Morning Run" in the user's language | Hard-coded English | Move part-of-day and sport names to string resources | S |
| A19 | Unseen-before-seen divider; achievement badge component | "You're all caught up" divider; badge chips | Static footer; facepile only in a preview | Last-seen marker in DataStore; a `Badge` component for PRs. Pays off more once the social feed exists | S |
| A0 | *(In progress)* Suggested routes near you, Home carousel, Maps chips | Generated loops near you | Worktree `suggest` | Merge, then add route feedback (thumbs) and pull-to-refresh | — |

### Tier B — used every day, needs our Supabase backend

Every row here depends on **B0**, which is only rated "everyday 2" itself but blocks everything below it.

| # | Gap | What Strava does | What we have | Approach | Size |
|---|---|---|---|---|---|
| B0 | **Accounts + activity sync (prerequisite)**: email/OTP and Google sign-in, link local data, upload queue | Account required; activities sync to the cloud | Local-only. No network client in android | `supabase-kt` (gotrue, postgrest, realtime). Optional sign-in in Settings that uploads local activities on first link. WorkManager outbox; `syncState` column | L |
| B1 | **Following feed with friends' activities** (Following/You/Club switch) | Chronological feed of people you follow | The switch exists, but both scopes show your own activities | Call the existing `get_home_feed` RPC (`read_models.sql:256`). Paginated, with the local cache merged in | L |
| B2 | **Follow / unfollow, follow requests, follower counts and lists** | Follow, private-profile requests, counts on profile | "Needs a server" toast | Existing `follow_user`/`unfollow_user`/`respond_follow_request` RPCs. List screens. Counts in `ProfileHeader` | M |
| B3 | **High-fives (kudos)**: optimistic, haptic, undo within 5 s, facepile | Kudos with a facepile | The count is hard-coded to 0 | `high_fives` table (`engagement.sql:86`). Optimistic UI and the existing `Facepile` | M |
| B4 | **Comments** (delete own, mute thread) | Comment thread on each activity | Static "0" | `comments` table (`engagement.sql:125`) plus realtime. A comments sheet on detail | M |
| B5 | **Social notifications + push** | Kudos, comments and follows in the inbox and as push | No push at all | `notifications` table (`engagement.sql:183`) feeding the A3 inbox. UnifiedPush in the foss build, FCM in the gms build. Notification channels per category | M |
| B6 | **Segment leaderboards and crowns** (top 10, KOM/QOM/CR) | Public leaderboards and crowns | Nothing | `segments` and `segment_efforts` tables with RLS by privacy, a leaderboard RPC and a crowns view. Builds on A4 | L |
| B7 | **Visibility enforced on the server** | Everyone / Followers / Only You enforced | Stored; only applied when trimming share/export | RLS on activities by visibility; privacy trimming on the server for others' views | M |
| B8 | **Garmin / Coros / Wahoo / Polar / Suunto auto-sync** | Automatic device sync | Manual file import only | Short term on the device: share-sheet target plus Health Connect read (Tier C). Real auto-sync needs partner OAuth and webhooks in edge functions | L |

### Tier C — used often (everyday 2)

**Recording**
- Distance auto-lap (S)
- Low-battery banner (S)
- BLE heart rate, power, cadence and speed sensors (L)
- Indoor/treadmill toggle (M)
- Manual activity entry (M)
- Layers button and personal heatmap on the record map (M)
- Turn-by-turn cues (M)
- Live personal segments (M, after A4)
- Pre-cache map tiles (M)
- Route back to start (S)
- Guided workouts are Tier D

**Save and edit**
- Tags such as Race, Long run, Commute (S)
- Gear with a default per sport and mileage (M)
- Private notes (S)
- Hidden stats and hide map (M)
- Quick Edit sheet after import (S)
- Post-save summary/celebration with a share prompt (M)
- Commute/trainer flags (S)
- Per-activity hide start/end (S)

**Activity detail**
- Calories estimate (S)
- Splits/Laps toggle (S)
- Split highlight on chart and map (S)
- Workout Analysis screen (M)
- HR zones and Relative Effort (M)
- Full-screen map (S)
- Matched activities (M)
- Photo viewer (S)

**Navigation**
- Default tab (S)
- Deep links (S)
- Reselect a tab to pop to root (S)
- Home-screen widget (M)
- App shortcuts (S)
- Share-sheet/`ACTION_VIEW` import (S)
- Pull-to-refresh (S)
- Point Home search at local search (S)

**Import**
- Strava bulk ZIP with `activities.csv` (M)
- Health Connect read/write (M)

**Maps**
- Route builder with FOSSGIS legs (L)
- Places search with Photon (S)
- "Your routes" list and save sheet (S)
- Offline maps manager (M)
- Personal heatmap (M)
- Save an activity as a route (S)
- Elevation profile scrub (S)
- Personalised route ETA (S)
- POIs from Overpass (M)
- Satellite layer, EOX Sentinel-2 (S)

**Progress**
- Compare to the previous period (S)
- Training log calendar (M)
- Fitness & Freshness (M)
- Weekly streaks (part of A2)
- Monthly cards (S)
- Gear (above)
- Athlete profile data: weight, max HR, birthday (S)

**Feed and social (on the device)**
- More filter chips (S)
- Own-card menu with Edit, Share, Mute (S)
- Local challenges, trophy case and badges (M)

**Privacy**
- Address-based privacy zones and a first-record prompt (M)
- Apply default visibility to past activities (S)

**Notifications and settings**
- Notification channels and a Settings > Notifications screen (S)
- Tap destinations (S)
- Post-save notification (S)
- Weekly summary (S)
- Locale first day of week (S)
- Per-app language and string extraction (L)
- Accessibility test suite and font scaling (M)

**Server-backed (after B0)**
- Email and Google sign-in (part of B0)
- Athlete search
- Mentions
- Group activities
- Athlete public profile
- Club pages, join and events
- Club leaderboard
- Direct messages
- Full and filtered leaderboards
- Regulars (Local Legend)
- Lost-crown notifications
- Global heatmap
- Block and report

**Platform**
- Wear OS app with Health Services HR and phone sync (L)
- Doze/OEM test matrix (S)
- iOS (Later, ADR-005)

### Tier D — niche or rarely used (everyday 1)

These are grouped by theme. Pick them up opportunistically.

- **Onboarding:**
  - Welcome carousel and SplashScreen API
  - Birthday and gender fields
  - Goals and level steps
  - Connect-device step
  - Coach marks
  - First-activity coaching
  - Delete all data on this device
  - Apple sign-in, 2FA and passkeys, change email, logout (with B0)
- **Recording and detail:**
  - Watch segment/route file sync
  - Guided workouts
  - Pace and power zones
  - Flyover
  - Activity insights
  - Correct distance and elevation
  - Spike-filtered max speed
  - Per-activity device name
  - Stat glossary on long-press
- **Share:**
  - Original file download
  - Full bulk ZIP (FIT, CSV, photos)
  - Power/speed gradients
  - km ticks on art
  - Collage/poster entry point
  - Route TCX
  - Alt text
  - Web renderer, embeds, OG images and link sharing (server)
- **Import:**
  - Zwift
  - Wearables via Health Connect
  - Public API and webhooks (server)
  - Connected-apps screen
- **Wear OS extras:** route page, off-route, segments and Beacon on the watch; tiles
- **Social and clubs:**
  - Ranking
  - Map-first vs media-first setting
  - Posts
  - Suggested athletes
  - Contacts sync
  - Pass-by
  - Mute athlete
  - Club search, create, roles, posts, hub
  - Group challenges
  - Message requests and group chats
- **Segments extras:**
  - Effort comparison
  - Intensity
  - Hide an effort
  - "Don't see the segment?" diagnostic
  - Hazardous flag, report, anti-cheat
  - Privacy-zone efforts
  - Profile segment lists
- **Maps extras:**
  - 3D terrain and winter layers
  - Natural-language search
  - Route feedback
  - Offline home
  - Heat colours, night, weekly and clusters
  - Photos layer
  - Start-point suggestions
  - Map data date
  - Edit, rename and reverse a route
  - Share a route via the share sheet
- **Training:**
  - Best-effort trends
  - Power curve and FTP
  - Running power
  - Training load
  - Predictions
  - Year in Sport
  - Training plans
- **Settings and platform:**
  - Temperature unit
  - Mixed units
  - Autoplay
  - About links
  - Donations link
  - Moderation and admin
  - Spam filter
  - CSAM hashing
  - GDPR desk
  - CI performance budgets
  - Web app
  - Federation

---

## 4. Recommended build order

**Wave 1: "Strava every day, offline"** (on the device only; can run in parallel with merging route suggestions)
1. Merge the `suggest` worktree, then add pull-to-refresh on Home and Maps, plus route feedback.
2. A1: follow a route while recording, with progress, remaining distance, ETA, off-route alerts and route back to start.
3. A2 + A3: goals and streaks, plus the local notifications inbox (this replaces the fake bell dot).
4. Quick wins: A5 medals, A10 audio cues, A11 charts and GAP, A12 elapsed headline, A15 fail-safe audience, A17 pace override, A18 localised titles, plus distance auto-lap and default tab.
5. A2-adjacent: share-sheet import, deep links, app shortcuts.

**Wave 2: depth on the device**
1. A4 personal segments (create, match, medals, list on detail), then live segment cues.
2. A6 progress chart ranges, A7 profile page, training log calendar, trophy case, local challenges.
3. A8 Beacon over SMS, A9 sensor auto-pause, BLE HR/power/cadence sensors.
4. A13 photo carousel, A14 sticker and photo templates, A16 crop, tags, gear, private notes, hide map and stats.
5. Route builder (FOSSGIS legs), personal heatmap, offline maps manager.

**Wave 3: going social (Supabase)**
1. B0 accounts and sync, starting with email OTP and Google sign-in, with the upload outbox and `syncState`.
2. B1 Following feed, B2 follows, B3 high-fives, B4 comments, B7 server-side visibility.
3. B5 social notifications and push (UnifiedPush and FCM), wired into the inbox from A3.
4. B6 segment leaderboards and crowns, built on the local segment model from Wave 2.
5. Then clubs, direct messages and the global heatmap.
