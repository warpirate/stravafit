# 08 — Feature Parity Checklist

Every Strava feature found across the teardown docs, with Strava's tier and our priority.
Follows [`DECISIONS.md`](../DECISIONS.md): **ADR-001**, no paywall, so every `SUB`/`PARTIAL` feature is free for us and prioritized on merit. **ADR-002**, map/route export is P0.
Minor-detail acceptance items (ADR-003) live in each `docs/09-details/*.md` "Acceptance checklist". Each row below links to the doc that covers it.

**Strava tier:** `FREE` · `SUB` (subscription only) · `PARTIAL` (free gets a limited version) · `BUNDLE` (separate product, e.g. Runna) · `—` (Strava does not offer it; ours goes further) · `?` (unverified).
**Our priority:** `P0` MVP (v0.1) · `P1` v0.2–v0.4 · `P2` v0.5–v1.0 · `Later` after v1.0 · `Won't` deliberately not built.

Doc shorthand: OV = [product-overview](../00-overview/product-overview.md), ON = [onboarding](../01-onboarding/onboarding.md), IA = [information-architecture](../02-navigation/information-architecture.md), CS = [core-screens](../03-wireframes/core-screens.md), SS = [secondary-screens](../03-wireframes/secondary-screens.md), REC = [recording](../04-features/recording.md), SEG = [segments-leaderboards](../04-features/segments-leaderboards.md), SOC = [social](../04-features/social.md), TRN = [training-analytics](../04-features/training-analytics.md), MAP = [maps-routes](../04-features/maps-routes.md), PRV = [privacy-safety](../04-features/privacy-safety.md), SUBN = [subscription-notifications](../04-features/subscription-notifications.md), INT = [integrations](../04-features/integrations.md), SHR = [share-export](../04-features/share-export.md), DS = [design-system](../05-design-system/design-system-observed.md), ARC = [strava-architecture](../06-architecture/strava-architecture.md), API = [data-model-api](../06-architecture/data-model-api.md), OSS = [landscape-legal](../07-oss/landscape-legal.md), WEAR = [wear-os](../99-gaps/wear-os-watch-app.md), L10N = [units-l10n-a11y](../99-gaps/units-localization-accessibility.md), QA = [gps-qa](../99-gaps/gps-qa-test-performance.md), MOD = [moderation](../99-gaps/moderation-admin-tooling.md), D-xxx = `../09-details/xxx.md`.

---

## 1. Onboarding, auth and account

| ☐ | Feature | Strava tier | Our priority | Source doc |
|---|---|---|---|---|
| [ ] | Splash / welcome carousel, no white flash in dark mode | FREE | P0 | ON §2, [D-onboarding-auth](../09-details/onboarding-auth.md) |
| [ ] | Email + password sign-up | FREE | P0 | ON §2 |
| [ ] | Email verification | FREE | P0 | ON §2 |
| [ ] | Email one-time code login (5-min expiry, countdown, rate limit) | FREE | P0 | D-onboarding-auth §3 |
| [ ] | Google sign-in | FREE | P0 | ON §3 |
| [ ] | Apple sign-in with relay email + "link existing account" | FREE | Later | D-onboarding-auth §2 |
| [ ] | Facebook sign-in | FREE | Won't | D-onboarding-auth §2 |
| [ ] | Multiple login methods on one account | FREE | P1 | D-onboarding-auth §3 |
| [ ] | Local-only mode (no account) with later linking | — | P0 | build-blueprint §4 |
| [ ] | Self-hosted instance URL picker | — | P0 | build-blueprint §1 |
| [ ] | Profile name, birthday (age gate), gender (optional, prefer not to say) | FREE | P0 | ON §2 |
| [ ] | Sport selection → default record sport | FREE | P0 | ON §2 |
| [ ] | Goals + fitness level screens (skippable) | FREE | P1 | ON §2 |
| [ ] | Contextual permission prompts (location on first Record, notifications after onboarding) | FREE | P0 | ON §2, D-onboarding-auth §4 |
| [ ] | Connect a device/app step | FREE | P1 | ON §2 |
| [ ] | Privacy defaults step + privacy-zone prompt before first recording | FREE | P0 | ON §2 |
| [ ] | Find friends / follow suggestions in onboarding | FREE | P1 | ON §2 |
| [ ] | Trial paywall step | SUB | Won't | ON §2 (ADR-001) |
| [ ] | Empty states and first-run tooltips | FREE | P0 | ON §2 |
| [ ] | First-activity coaching | FREE | P1 | ON §2 |
| [ ] | Forgot password via code → set password | FREE | P0 | ON §3 |
| [ ] | Logout | FREE | P0 | ON §3 |
| [ ] | Change email / switch login type | FREE | P1 | ON §3 |
| [ ] | Credential Manager autofill | FREE | P1 | D-onboarding-auth §3 |
| [ ] | Account deletion (typed confirm, export offer) | FREE | P0 | ON §4, PRV §9 |
| [ ] | 2FA / passkeys, active sessions | PARTIAL | P1 | PRV §11 |

## 2. Navigation and global chrome

| ☐ | Feature | Strava tier | Our priority | Source doc |
|---|---|---|---|---|
| [ ] | Bottom nav: Home, Maps, Record, Groups, You | FREE | P0 (Home/Record/You), P1 (Maps/Groups) | IA §1 |
| [ ] | Configurable default tab (incl. Record) | FREE | P1 | IA §8, D-settings-privacy-units §2 |
| [ ] | Bell (notifications) + messages icons with independent badges | FREE | P0 (bell), P2 (messages) | D-notifications-search §1 |
| [ ] | Deep links / app links for activities, athletes, segments, routes, clubs | FREE | P1 | IA §4 |
| [ ] | Back-stack behaviour per tab | FREE | P0 | IA §5 |
| [ ] | Launcher widget | FREE | P2 | IA §9, D-platform-extras §10 |
| [ ] | App shortcuts (start run/ride) | FREE | P2 | IA §9 |
| [ ] | Share-sheet target (import GPX/FIT/TCX from other apps) | FREE | P0 | IA §10, D-platform-extras §5 |
| [ ] | Pull-to-refresh across tabs | FREE | P0 | D-home-feed §7 |

## 3. Recording

| ☐ | Feature | Strava tier | Our priority | Source doc |
|---|---|---|---|---|
| [ ] | GPS recording, all sport types (SportType enum) | FREE | P0 | OV §3, REC |
| [ ] | Sport picker bottom sheet; last sport remembered | FREE | P0 | CS §3.2, D-record §2 |
| [ ] | GPS acquiring halo + "GPS ready" ≤ 20 m; hint after 20 s; start before fix | FREE | P0 | D-record §2 |
| [ ] | Foreground service, screen-off recording, ongoing notification with Pause/Finish | FREE | P0 | REC §5, D-record §4 |
| [ ] | Battery-optimisation exemption prompt | FREE | P0 | D-record §1 |
| [ ] | Stats overlay on map + big-stats view, swipe between | FREE | P0 | CS §3.4–3.5 |
| [ ] | Ride: current speed while moving, average when paused | FREE | P0 | D-record §3 |
| [ ] | Run: split-average pace default, switchable to current pace | FREE | P0 | D-record §3 |
| [ ] | Manual pause/resume (no auto-resume after manual pause) | FREE | P0 | REC §3 |
| [ ] | Auto-pause (GPS + accelerometer) | FREE | P0 | REC §6 |
| [ ] | Moving vs elapsed time | FREE | P0 | REC §7 |
| [ ] | Distance + GPS smoothing / point acceptance filter | FREE | P0 | REC §4.3, §8 |
| [ ] | Live elevation, barometer with GPS fallback | FREE | P0 | REC §9, D-platform-extras R7 |
| [ ] | Auto splits (km/mi) updated within 2 s | FREE | P0 | REC §13 |
| [ ] | Lap button + distance auto-lap | FREE | P0 | REC §13 |
| [ ] | Audio cues (start, pause, resume, splits, segments), each togglable | FREE | P0 | REC §12, D-platform-extras R5 |
| [ ] | Keep screen on toggle | FREE | P1 | D-platform-extras R4 |
| [ ] | GPS loss / low battery / interruption states | FREE | P0 | CS §3.7 |
| [ ] | Crash / process-death recovery ("resume recording?") | FREE | P0 | ARC §7.4, QA §6 |
| [ ] | Finish confirmation; discard with confirm + Undo | FREE | P0 | CS §3.8, D-record §4 |
| [ ] | Offline recording + upload queue + "sync now" | FREE | P0 | D-platform-extras §3–4 |
| [ ] | Upload on Wi-Fi only; zero data when map/Beacon off | FREE | P0 | D-platform-extras R11 |
| [ ] | BLE heart-rate strap, pair mid-recording | FREE | P0 | REC §11, D-platform-extras R9 |
| [ ] | BLE cycling power, speed/cadence, running S/C | FREE | P1 | REC §11.2 |
| [ ] | Indoor / treadmill / virtual activities | FREE | P1 | REC §10 |
| [ ] | Manual activity entry | FREE | P0 | OV §5.1 |
| [ ] | Map layers (heatmap, terrain) on record map | SUB | P1 | D-record §3 |
| [ ] | Add route to record map / follow route | SUB | P1 | D-record §10, MAP §9 |
| [ ] | Off-route alerts (default on when following) | SUB | P1 | MAP §9, D-platform-extras R8 |
| [ ] | Turn-by-turn navigation cues | SUB (?) | P2 | MAP §13.8 |
| [ ] | Live segments on phone | SUB | P2 | REC §14, D-record §3 |
| [ ] | Live segments on devices (via route/segment file sync) | SUB | Later | OV §5.1 |
| [ ] | Beacon → Trusted Contacts Live, ≥ 3 contacts, from record sheet | FREE/SUB mix | P1 | REC §15, PRV §6, D-record §9 |
| [ ] | Instant / guided workouts | PARTIAL (?) | Later | OV §5.1 |
| [ ] | Record state machine incl. Saving/Uploading/Synced | FREE | P0 | CS §3.9, ARC §7.4 |

## 4. Save, edit and activity management

| ☐ | Feature | Strava tier | Our priority | Source doc |
|---|---|---|---|---|
| [ ] | Save screen (title first, Save in top app bar) | FREE | P0 | CS §4, D-save-edit-activity §1 |
| [ ] | Auto-title Morning/Afternoon/Evening/Night + sport, localised, activity TZ | FREE | P0 | D-save-edit-activity S1, L10N §4.3 |
| [ ] | Description, sport type change | FREE | P0 | CS §4 |
| [ ] | Audience Everyone / Followers / Only You, prefilled; per-sport default; fail-safe Only You | FREE | P0 | D-save-edit-activity S3–S5 |
| [ ] | Hidden details on save | FREE | P0 | D-save-edit-activity §4 |
| [ ] | Perceived exertion sheet | FREE | P0 | CS §4.2, TRN §4 |
| [ ] | Tags (race, long run, workout, commute, treadmill, with pet) | FREE | P0 | D-save-edit-activity §6 |
| [ ] | Gear preselected per sport | FREE | P1 | D-save-edit-activity S9 |
| [ ] | Photos on activities | FREE | P0 | D-save-edit-activity §7 |
| [ ] | Video on activities | FREE | P2 | D-save-edit-activity §7 |
| [ ] | Private notes (owner only, never in API) | FREE | P0 | D-save-edit-activity E2 |
| [ ] | Quick Edit sheet after device sync | FREE | P1 | D-save-edit-activity §2 |
| [ ] | Full Edit (overflow: Edit, Crop, Delete), unsaved-changes guard | FREE | P0 | D-save-edit-activity §3 |
| [ ] | Crop activity | FREE | P0 | D-save-edit-activity §8 |
| [ ] | Delete activity | FREE | P0 | D-save-edit-activity §9 |
| [ ] | Mute activity from feeds (still counts toward goals) + mute-by-default | FREE | P1 | D-save-edit-activity E5–E6 |
| [ ] | Map style selection incl. stat colouring | SUB (some) | P1 | D-save-edit-activity E4 |
| [ ] | Post-save celebration / summary | FREE | P0 | CS §5 |
| [ ] | Upload processing / error / duplicate states | FREE | P0 | D-platform-extras §4 |

## 5. Activity detail and analysis

| ☐ | Feature | Strava tier | Our priority | Source doc |
|---|---|---|---|---|
| [ ] | Header + stats grid (moving time default; elapsed for listed sports) | FREE | P0 | CS §2.1, D-activity-detail §1 |
| [ ] | Calories (estimated label) | FREE | P0 | D-activity-detail §1 |
| [ ] | Long-press stat definitions; elevation source label | FREE | P1 | D-activity-detail §1 |
| [ ] | Splits table (final partial split real distance) | FREE | P0 | D-activity-detail §2 |
| [ ] | Laps table; splits/laps toggle with fastest/slowest marked | FREE | P0 | D-activity-detail §3 |
| [ ] | Split/lap tap highlights map + profile | FREE | P0 | D-activity-detail §2 |
| [ ] | Workout Analysis (laps chart, full-screen) | SUB | P1 | TRN §11, D-activity-detail §3 |
| [ ] | Pace/speed, HR, elevation, cadence, power charts with scrubbing linked to map | FREE / SUB | P0 | CS §2.3, D-activity-detail §4 |
| [ ] | Grade Adjusted Pace (column + chart) | SUB (?) | P1 | TRN §6 |
| [ ] | HR zones | SUB | P1 | TRN §7 |
| [ ] | Pace zones (6, editable) | SUB | P1 | TRN §7, D-activity-detail §5 |
| [ ] | Power zones | SUB | P1 | TRN §7 |
| [ ] | Relative Effort → Effort Score per activity | SUB | P1 | TRN §2 |
| [ ] | Segment efforts list with crowns/trophies/medals | FREE | P1 | D-activity-detail §6 |
| [ ] | Best efforts on activity (medals for lifetime/year top 3) | FREE | P0 | D-profile-progress B5 |
| [ ] | Full-screen map | FREE | P0 | CS §2.6 |
| [ ] | Flyover (3D replay) | SUB | P2 | D-activity-detail §7 |
| [ ] | Matched activities (repeat-route comparison) | SUB | P2 | TRN §11, D-activity-detail §7 |
| [ ] | Social bar: kudos, comments, share | FREE | P0 | D-activity-detail §8 |
| [ ] | Device name shown on detail | FREE | P1 | D-home-feed §2 |
| [ ] | Athlete Intelligence → Activity insights | SUB | P2 | TRN §12 |

## 6. Share and export (ADR-002: P0)

| ☐ | Feature | Strava tier | Our priority | Source doc |
|---|---|---|---|---|
| [ ] | Route art: vector **SVG** | — | P0 | SHR B.1 SE-2 |
| [ ] | Route art: **transparent PNG** | — | P0 | SHR SE-2 |
| [ ] | Route art: PNG with background | FREE (images) | P0 | SHR SE-2 |
| [ ] | **PDF poster** | — | P0 | SHR SE-2 |
| [ ] | Share cards / stories (Minimal, Poster, Story 9:16, Square 1:1, Landscape 16:9, Sticker) | FREE (fewer) | P0 | SHR A.3, SE-5 |
| [ ] | Stats stickers for stories | FREE | P0 | SHR A.3, CS §5.2 |
| [ ] | Share private activities (rendered locally) | — | P0 | SHR SE-1 |
| [ ] | Privacy trimming before render/export | FREE | P0 | SHR SE-4, B.2 |
| [ ] | Export **GPX** | FREE | P0 | SHR SE-3, PRV §8.2 |
| [ ] | Export **TCX** | — | P0 | SHR SE-3 |
| [ ] | Export **FIT** | — | P0 | SHR SE-3 |
| [ ] | Export **GeoJSON** | — | P0 | SHR SE-3 |
| [ ] | Download original file | FREE | P0 | SHR A.7 |
| [ ] | Instagram Stories intent, FileProvider share, save to gallery | FREE | P0 | SHR SE-10 |
| [ ] | Bulk account export ZIP in-app (no email step) | FREE (email) | P0 | SHR SE-11, PRV §8.1 |
| [ ] | Activity link share | FREE | P0 | SHR A.5 |
| [ ] | Link previews / OG image | FREE | P2 | SHR SE-13 |
| [ ] | Gradient by pace/HR/elevation/power/speed | — | P1 | SHR SE-6 |
| [ ] | Optional basemap under trace (OSM attribution) | — | P1 | SHR SE-7 |
| [ ] | Elevation profile SVG, km/mi markers, start/end markers | — | P1 | SHR SE-8 |
| [ ] | Multi-activity collage, "all my runs" heatmap poster | — | P1 | SHR SE-9 |
| [ ] | Web renderer parity (TS) | — | P1 | SHR SE-12 |
| [ ] | Web embeds (activity, route, segment) | FREE | P2 | SHR A.6 |
| [ ] | Route GPX/TCX export | FREE/SUB | P1 | MAP §8 |
| [ ] | Accessible export (alt text, descriptions) | — | P1 | SHR B.7 |

## 7. Import and integrations

| ☐ | Feature | Strava tier | Our priority | Source doc |
|---|---|---|---|---|
| [ ] | Import FIT / GPX / TCX files | FREE (web) | P0 | INT, OV §5.1 |
| [ ] | Import Strava bulk-export ZIP (own data) | — | P0 | OSS §0 |
| [ ] | Health Connect read/write | FREE | P1 | INT §3.2, D-platform-extras §7 |
| [ ] | Garmin / Coros / Wahoo / Polar / Suunto sync | FREE | P2 (file-based first) | INT §3.1 |
| [ ] | Zwift / indoor platform uploads | FREE | P2 | INT §3.3 |
| [ ] | Wearables / recovery (Whoop, Oura etc.) | FREE | Later | INT §3.4 |
| [ ] | Route sync to devices | FREE | P2 | OV §5.1 |
| [ ] | Public API (OAuth2, scopes, rate limits) | FREE | P2 | API §1–3 |
| [ ] | Webhooks for third-party apps (HMAC, 24 h retry) | FREE | P2 | API §7.3 |
| [ ] | Data permissions & connected apps screen | FREE | P1 | D-settings-privacy-units §9 |

## 8. Wear OS

| ☐ | Feature | Strava tier | Our priority | Source doc |
|---|---|---|---|---|
| [ ] | Wear OS app: sport list, acquire, record, pause, save/discard | FREE | P2 | WEAR §4 |
| [ ] | Watch HR via Health Services | FREE | P2 | WEAR §8 |
| [ ] | Standalone and phone-tethered recording, sync + conflict resolution | FREE | P2 | WEAR §9–10 |
| [ ] | Route / map page on watch | SUB | P2 | WEAR §4.6 |
| [ ] | Off-route alerts on watch | SUB | P2 | WEAR §11 |
| [ ] | Live segments on watch | SUB | Later | WEAR §12 |
| [ ] | Beacon from watch | SUB | Later | WEAR §13 |
| [ ] | Tiles and complications | FREE | P2 | WEAR §14 |

## 9. Home feed and social

| ☐ | Feature | Strava tier | Our priority | Source doc |
|---|---|---|---|---|
| [ ] | Home feed: Following / You / Club switch | FREE | P0 | D-home-feed §1 |
| [ ] | Chronological default; unseen before seen, "all caught up" divider | FREE | P0 | D-home-feed §1 |
| [ ] | Personalized ranking (opt-in, signals explained) | FREE | Later | SOC §7 |
| [ ] | Favorite athletes surface first | FREE | P1 | D-home-feed 1.6 |
| [ ] | Feed filter chips | — | P1 | D-home-feed 1.9 |
| [ ] | Activity card (avatar, name, time, location, stats, map/photo carousel) | FREE | P0 | CS §1, D-home-feed §2 |
| [ ] | Map-first vs media-first card setting | FREE | P2 | D-settings-privacy-units §4 |
| [ ] | No-GPS sport tile | FREE | P0 | D-home-feed 2.5 |
| [ ] | Follow / unfollow; follow requests for private profiles | FREE | P0 | SOC §2 |
| [ ] | Kudos → High-five (optimistic, haptic, undo within 5 s, facepile) | FREE | P0 | SOC §3, D-home-feed §3 |
| [ ] | Comments, delete own, mute thread | FREE | P0 | SOC §4, D-home-feed §4 |
| [ ] | Mentions (@) | FREE | P1 | SOC §5 |
| [ ] | Group activities (auto-matched, merged card, add/remove me) | FREE | P1 | SOC §6 |
| [ ] | Posts (text + photos) | FREE | P1 | SOC §7.3 |
| [ ] | Achievement / challenge / goal cards in feed | FREE | P1 | D-home-feed §5 |
| [ ] | Suggested athletes / clubs cards | FREE | P1 | SOC §11.2 |
| [ ] | Long-press / overflow menus on cards | FREE | P0 | D-home-feed §7 |
| [ ] | Athlete search | FREE | P1 | SOC §11.1, D-notifications-search §7 |
| [ ] | Contacts sync (opt-in, server deletion on off) | FREE | P2 | SOC §11.3 |
| [ ] | Flyby → Pass-by | FREE | P2 | SOC, PRV §5.1 |
| [ ] | Block | FREE | P0 | SOC §12.1 |
| [ ] | Mute athlete | FREE | P0 | SOC §12.2 |
| [ ] | Report (athlete, activity, comment) | FREE | P0 | SOC §12.3 |
| [ ] | Photos & media in posts | FREE | P1 | SOC §13 |
| [ ] | Followers / following lists | FREE | P0 | SS §4.8 |
| [ ] | Athlete public profile | FREE | P0 | SS §5 |

## 10. Clubs, challenges, messaging

| ☐ | Feature | Strava tier | Our priority | Source doc |
|---|---|---|---|---|
| [ ] | Groups tab home | FREE | P1 | SS §3.1 |
| [ ] | Club search (keyword, location, sport); proximity ranking, no paid placement | FREE | P1 | D-groups-clubs-challenges §2 |
| [ ] | Create club wizard (sport, ≤ 3 tags, logo, privacy, location, slug) | FREE | P1 | D-groups-clubs-challenges §2 |
| [ ] | Club types, verified badge | FREE | P1 / P2 | SOC §8.1 |
| [ ] | Club page: Overview, Activities, Stats, Share | FREE | P1 | SS §3.2 |
| [ ] | Join public / request private club | FREE | P1 | D-groups-clubs-challenges §3 |
| [ ] | Roles: member, admin, owner; ban; approve requests | FREE | P1 | SOC §8.2 |
| [ ] | Club posts / discussions | FREE | P1 | SOC §8.4 |
| [ ] | Club events (group runs/rides), RSVP, reminders | FREE | P1 | SOC §8.5, SS §3.3 |
| [ ] | Club weekly leaderboard | FREE | P1 | SOC §8.6 |
| [ ] | Club Hub / channels | FREE | P2 | D-groups-clubs-challenges §7 |
| [ ] | Hosted challenges (instance-run) | FREE | P1 | SOC §9.1 |
| [ ] | Create group challenges | SUB | P1 | SOC §9.2 |
| [ ] | Challenge detail / progress, join, completion badges | FREE | P1 | SS §3.5 |
| [ ] | Sponsored challenges (non-tracking only) | FREE | Later | SUBN §12 |
| [ ] | Direct messages | FREE | P2 | SOC §10 |
| [ ] | Group chats | FREE | P2 | SOC §10 |
| [ ] | Message requests | FREE | P2 | D-notifications-search §5 |
| [ ] | Trophy case / challenge badges | FREE | P1 | SS §4.6 |

## 11. Segments and competition

| ☐ | Feature | Strava tier | Our priority | Source doc |
|---|---|---|---|---|
| [ ] | Create segment from activity (rules, derived attributes) | FREE | P1 | SEG §2 |
| [ ] | Automatic segment matching | FREE | P1 | SEG §3 |
| [ ] | Segment detail (stats, grade, climb category, attempts) | FREE | P1 | SS §2.1, D-segments-leaderboards §1 |
| [ ] | Your results: PR, 2nd, 3rd, effort history | FREE | P1 | SEG §7 |
| [ ] | Effort detail (HR, power, cadence) | FREE | P1 | D-segments-leaderboards §3 |
| [ ] | Top-10 leaderboard | FREE (?) | P1 | OV §5.1 |
| [ ] | Full leaderboards, all rows, paginated | SUB | P1 | SEG §4 |
| [ ] | Filters: gender, age, weight, following, club, today/week/month/year | SUB | P1 | SEG §4.2 |
| [ ] | KOM/QOM/CR → crowns | FREE | P1 | SEG §5 |
| [ ] | Medals / trophies frozen at upload | FREE | P1 | D-segments-leaderboards §5 |
| [ ] | Local Legend → Regulars (90-day), with opt-out | PARTIAL | P2 | SEG §6 |
| [ ] | Local Legend leaderboard / comparison | SUB | P2 | SS §2.3 |
| [ ] | Segment effort comparison | SUB | P2 | SS §2.4 |
| [ ] | Segment intensity % | SUB | P2 | D-segments-leaderboards §3 |
| [ ] | Starred segments (pages of 30, filter) | FREE | P1 | SEG §12 |
| [ ] | Segment explore map (sport, climb, length, surface) | FREE (?) | P2 | SS §1.7 |
| [ ] | Profile segment lists (starred, crowns, Regulars, created) | FREE | P2 | D-segments-leaderboards §7 |
| [ ] | Hide / unhide a single effort | FREE | P2 | D-segments-leaderboards §9 |
| [ ] | "Don't see the segment?" diagnostic | FREE | P2 | D-segments-leaderboards §9 |
| [ ] | Hazardous segment flag (waiver, no PR/crown) | FREE | P2 | SEG §10.1 |
| [ ] | Report segment; flag activity | FREE | P1 | SEG §10 |
| [ ] | Leaderboard integrity / anti-cheat | FREE | P1 | SEG §11, MOD §3 |
| [ ] | Lost-crown notifications (not on downhill) | FREE | P1 | D-notifications-search T9–T10 |
| [ ] | Privacy-zone efforts excluded until revealed | FREE | P1 | D-segments-leaderboards §9 |

## 12. Maps, routes and heatmaps

| ☐ | Feature | Strava tier | Our priority | Source doc |
|---|---|---|---|---|
| [ ] | Base map (standard, dark) | FREE | P0 | MAP §3.1 |
| [ ] | Satellite base map (if licensed) | FREE | P2 | MAP §3.1 |
| [ ] | Winter / slope-angle / 3D terrain layers | SUB | P2 | MAP §3.3, §10 |
| [ ] | Maps tab with Create Route FAB, map-only toggle | FREE/SUB | P1 | D-maps-routes §1 |
| [ ] | Route builder (draw, snap, modes, undo) | SUB | P1 | MAP §5, SS §1.6 |
| [ ] | Route builder heatmap overlay | SUB | P1 | MAP §4.4 |
| [ ] | Suggested routes (generated, distance filter) | SUB | P2 | MAP §5.5 |
| [ ] | Natural-language route search | SUB | Later | SS §1.3 |
| [ ] | Search tabs Routes / Places / Segments | FREE/SUB | P2 | D-maps-routes §2 |
| [ ] | Route detail with estimated time + elevation profile | FREE/SUB | P1 | MAP §6 |
| [ ] | Save route sheet; saved routes list, filter, search | SUB | P1 | SS §1.5 |
| [ ] | Route feedback / rating | FREE | P2 | D-maps-routes M7 |
| [ ] | Offline maps / routes download manager | SUB | P1 | SS §1.8, D-maps-routes §8 |
| [ ] | Offline home: "Navigate offline" / "Record offline" | FREE | P1 | D-platform-extras O1 |
| [ ] | GPX route import | FREE | P1 | MAP §8 |
| [ ] | Global heatmap low zoom | FREE | P2 | MAP §4.1 |
| [ ] | Global heatmap full zoom → community heatmap (opt-in, k-anon) | SUB | P2 | MAP §4.1, PRV §5.5 |
| [ ] | Personal heatmap (all time / year / range) | SUB | P1 | MAP §4.2 |
| [ ] | Heat colours (6 + colour-blind preset) | SUB | P1 | D-maps-routes H1 |
| [ ] | Night heatmap | SUB | P2 | MAP §4.3 |
| [ ] | Weekly heatmap | SUB | P2 | MAP §4.3 |
| [ ] | Clusters toggle | SUB | P2 | D-maps-routes H3 |
| [ ] | Multi-sport heatmap selection incl. winter/water | SUB | P2 | D-maps-routes H5 |
| [ ] | Photos layer + "Route here" | SUB | Later | D-maps-routes H8 |
| [ ] | POIs (9 categories) + user suggestions to moderation | PARTIAL | P2 | MAP §13.7, D-maps-routes §4 |
| [ ] | Start-point suggestions | SUB (?) | Later | MAP §5.6 |
| [ ] | Last map-data refresh date in settings | FREE | P2 | D-maps-routes M8 |

## 13. Training, progress and profile

| ☐ | Feature | Strava tier | Our priority | Source doc |
|---|---|---|---|---|
| [ ] | You tab: Progress + Activities; avatar → full profile | FREE | P0 | SS §4.1, D-profile-progress §1 |
| [ ] | Progress chart 1W/1M/3M/6M/YTD/1Y, sport picker, scrub with haptics | FREE/PARTIAL | P0 | D-profile-progress §3 |
| [ ] | Compare date range | PARTIAL | P1 | D-profile-progress C5 |
| [ ] | Activities list | FREE | P0 | SS §4.2 |
| [ ] | Weekly/monthly/yearly totals; stats by sport | FREE | P0 | SS §4.4 |
| [ ] | Training Log calendar (circles by sport, filters, sizing metric) | SUB | P1 | TRN §9.2, D-profile-progress §4 |
| [ ] | Streaks (weekly) | FREE | P1 | SUBN §9.1 |
| [ ] | Best Efforts list (run + ride), top 3, remove effort | FREE | P0 | TRN §8, D-profile-progress §5 |
| [ ] | Best-effort yearly trend analysis | SUB | P1 | D-profile-progress B2 |
| [ ] | Fitness & Freshness | SUB | P1 | TRN §3 |
| [ ] | Weekly effort range | SUB | P1 | TRN §2.3 |
| [ ] | Power curve | SUB | P1 | TRN §5.2 |
| [ ] | Estimated FTP | SUB | P1 | TRN §5.3 |
| [ ] | Running power | SUB | P2 | TRN §5.4 |
| [ ] | Training load / intensity | SUB | P1 | TRN §16 |
| [ ] | Goals (distance, time, elevation, segment) | SUB | P1 | TRN §10 |
| [ ] | Performance predictions | SUB | P2 | TRN §14.1 |
| [ ] | Route-based time estimates | FREE | P1 | TRN §14.2 |
| [ ] | Activity insights (AI summaries) | SUB | P2 | TRN §12 |
| [ ] | Year in Sport → Year Recap | SUB | P2 | TRN §13, SUBN §10 |
| [ ] | Monthly stat cards / summary | SUB | P2 | SUBN §10 |
| [ ] | Training plans | BUNDLE | Later | TRN §15 |
| [ ] | Gear (shoes, bikes), default per sport, mileage, retirement alerts | FREE | P1 | SS §4.7, D-profile-progress §8 |
| [ ] | Trophy case | FREE | P1 | SS §4.6 |
| [ ] | Profile visibility of gear / start times to non-followers | FREE | P0 | D-profile-progress P3 |

## 14. Privacy and safety

| ☐ | Feature | Strava tier | Our priority | Source doc |
|---|---|---|---|---|
| [ ] | Activity visibility Everyone / Followers / Only You | FREE | P0 | PRV §2.1 |
| [ ] | Default visibility; "apply to past" bulk update | FREE | P0 / P1 | D-settings-privacy-units §6 |
| [ ] | Profile visibility (Everyone / Followers) | FREE | P0 | PRV §2.2 |
| [ ] | Privacy zones / hide start-end radius | FREE | P0 | PRV §3 |
| [ ] | Hide entire map | FREE | P0 | PRV §3.3 |
| [ ] | Hide individual stats (owner still sees; HR hides effort) | FREE | P0 | PRV §4 |
| [ ] | Inference warning on hidden details | — | P1 | D-save-edit-activity H8 |
| [ ] | Opt-outs: group activity, Flyby, mentions, messaging | FREE | P1 | PRV §5 |
| [ ] | Leaderboard / Local Legend opt-out | FREE | P1 | PRV §5.4 |
| [ ] | Aggregated data usage (heatmap) opt-out → opt-in for us | FREE | P1 | PRV §5.5 |
| [ ] | Beacon → Trusted Contacts Live | FREE/SUB | P1 | PRV §6 |
| [ ] | Block / report | FREE | P0 | PRV §7 |
| [ ] | Bulk data export | FREE | P0 | PRV §8.1 |
| [ ] | Account deletion and retention | FREE | P0 | PRV §9 |
| [ ] | Age requirements and regional defaults | FREE | P0 | PRV §10 |
| [ ] | Account security (2FA etc.) | PARTIAL | P1 | PRV §11 |
| [ ] | Contacts data deletion on opt-out | FREE | P2 | D-onboarding-auth 4.3 |

## 15. Notifications

| ☐ | Feature | Strava tier | Our priority | Source doc |
|---|---|---|---|---|
| [ ] | In-app notification inbox (grouped kudos, relative age, unread tint, mark all read) | FREE | P0 | IA §6, D-notifications-search §2 |
| [ ] | Inline approve/decline follow requests | FREE | P0 | D-notifications-search N5 |
| [ ] | Push notifications (FCM / UnifiedPush) | FREE | P1 | SUBN §11 |
| [ ] | 7 categories with matching Android channels | FREE | P1 | D-settings-privacy-units §5 |
| [ ] | Correct tap destinations for every type (T1–T18) | FREE | P1 | D-notifications-search §3 |
| [ ] | Email notifications / digests | FREE | P2 | SUBN §11.1 |
| [ ] | Per-club notification settings | FREE | P1 | D-notifications-search T15 |
| [ ] | Re-engagement nudges (capped, opt-in) | FREE | P2 | SUBN §11.4 |
| [ ] | Marketing / upsell notifications | SUB | Won't | SUBN (ADR-001) |

## 16. Settings, units, localization, accessibility

| ☐ | Feature | Strava tier | Our priority | Source doc |
|---|---|---|---|---|
| [ ] | Settings tree; settings synced to server | FREE | P0 | IA §8, D-settings-privacy-units §1 |
| [ ] | Appearance Default / Light / Dark, live switch | FREE | P0 | D-settings-privacy-units §2 |
| [ ] | Units Metric / Imperial (locale default) | FREE | P0 | L10N §2 |
| [ ] | Temperature unit independent | FREE | P1 | D-settings-privacy-units §3 |
| [ ] | Pace vs speed by sport + per-sport override; swim per 100 m/yd | FREE | P0 | L10N §2.4 |
| [ ] | Mixed units (e.g. km distance, ft elevation) | — | P2 | L10N B |
| [ ] | Date/time per locale, 12/24 h, first day of week, TZ handling | FREE | P0 | L10N §3 |
| [ ] | Per-app language (Android 13+) | FREE | P0 | L10N §4 |
| [ ] | Autoplay video setting | FREE | P2 | D-settings-privacy-units §2 |
| [ ] | About (version, licences, map attribution, source link) | FREE | P0 | D-settings-privacy-units §1 |
| [ ] | TalkBack labels for stats, charts, maps | PARTIAL | P0 | L10N §5 |
| [ ] | Font scaling / dense grid handling, touch targets, reduced motion | PARTIAL | P1 | L10N I–J |

## 17. Design system and motion

| ☐ | Feature | Strava tier | Our priority | Source doc |
|---|---|---|---|---|
| [ ] | Original tokens (color, type, spacing, radii, elevation) | — | P0 | DS Part B |
| [ ] | Record button, chips, sheets, stat block, activity/map card, high-five + facepile, avatars, badges | FREE | P0 | DS B7 |
| [ ] | Celebrations (PR, crown, challenge complete) | FREE | P1 | DS A8, B5 |
| [ ] | Data-viz conventions for charts | FREE | P0 | DS A9 |
| [ ] | Dark mode | FREE | P0 | DS A10 |

## 18. Monetization surfaces (not built)

| ☐ | Feature | Strava tier | Our priority | Source doc |
|---|---|---|---|---|
| [ ] | Paywall sheets / locked-feature teasers | SUB | Won't | SUBN §4 |
| [ ] | Free trials, Play Billing | SUB | Won't | SUBN §5–6 |
| [ ] | Family plan | SUB | Won't (everything is free) | SUBN §7 |
| [ ] | Partner perks / discounts | SUB | Won't | SUBN §8 |
| [ ] | Ads / paid placement | FREE | Won't | SUBN §12 |
| [ ] | Donations / OpenCollective link | — | P2 | build-blueprint §1 |

## 19. Platform, admin and quality

| ☐ | Feature | Strava tier | Our priority | Source doc |
|---|---|---|---|---|
| [ ] | Moderation queue, actions, strikes, appeals, audit log | (internal) | P1 min / P2 full | MOD §4–5 |
| [ ] | Admin web UI (queue, user lookup, activity review, leaderboard recompute) | (internal) | P2 | MOD §6 |
| [ ] | Spam / bot rate limits, harmful text filter | (internal) | P1 | MOD §7 |
| [ ] | Photo hash matching (CSAM) | (internal) | P1 (before photos go public) | MOD §8 |
| [ ] | GDPR / DSA request desk | (internal) | P2 | MOD §6.6 |
| [ ] | GPS golden corpus + replay harness | (internal) | P0 | QA §2–3 |
| [ ] | Doze / OEM-killer survival matrix | (internal) | P0 | QA §5 |
| [ ] | Performance + battery budgets in CI | (internal) | P0 | QA §9 |
| [ ] | Self-hosted docker-compose deployment | — | P0 | ARC §7.8, ADR-006 |
| [ ] | `foss` flavor (no GMS) for F-Droid | — | P0 | OSS §7.2 |
| [ ] | iOS app | FREE | Later | ADR-005 |
| [ ] | Web app / public activity pages | FREE | P2 | ARC §7.3 |
| [ ] | Federation (ActivityPub) | — | Later | OSS §8.4 |

---

**Totals by priority** (336 rows; rows with a split priority are counted under the first one): P0 134, P1 116, P2 64, Later 14, Won't 8. Every Strava `SUB`/`PARTIAL` row has a real priority (none is `Won't` except monetization surfaces themselves), as ADR-001 requires.
