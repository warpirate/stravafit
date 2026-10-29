# Settings, Privacy & Units: minor-details catalog

Area: `settings-privacy-units`. Reference app: Strava for Android. Target: StravaFit (github.com/warpirate/stravafit).

Gating legend: **[FREE]** means free in Strava. **[SUB]** means Strava subscribers only. This column is for reference only, because **every feature is free in StravaFit** (no paywall).
"Our app" values: **keep** (match it), **improve** (match it and go further), **drop** (with a reason).
Items marked *(unverified)* come from secondary or low-confidence sources. Confirm them on a device before treating them as spec.

> Compiled quickly from public sources (Strava Support, developers.strava.com, DC Rainmaker, press). No extra web spot-checks were run in this pass, so low-confidence items are kept and marked *(unverified)* rather than verified or dropped.

---

## 1. Settings entry & tree

```
You tab                               [⚙]  <- gear, top-right
┌──────────────────────────────────────────┐
│ Settings                                 │
│  Account / My Profile                    │
│  Preferences > Appearance                │
│  Display (Units, Temperature, Default    │
│           tab, Autoplay videos)          │
│  Feed and maps                           │
│  Privacy Controls                        │
│  Training and performance (Beacon, zones)│
│  Push notifications                      │
│  Contacts                                │
│  Health data                             │
│  Applications, Services and Devices      │
│  About / Legal / Support                 │
│  Log out                                 │
│  Delete account                          │
└──────────────────────────────────────────┘
```
(Exact order and labels vary by app version. The tree is approximate.)

| # | Detail | Behaviour | Strava | Our app |
|---|---|---|---|---|
| 1.1 | Gear entry point | A gear icon at the top right of the You tab opens Settings. | [FREE] | keep |
| 1.2 | Settings sync to account | Changes save to the account and apply on web/other devices (units, privacy and so on). | [FREE] | keep: store on the server profile, with a local cache for offline use |
| 1.3 | Log out row | Near the bottom of the list. *(unverified position)* | [FREE] | keep |
| 1.4 | Delete account | Sits next to Log out. A confirmation warns that deletion is permanent and cannot be recovered. | [FREE] | improve: offer "export data first" in the same dialog and require the user to type a confirmation |
| 1.5 | Download your data | Request a bulk archive (original GPX/FIT plus CSV), delivered as an emailed zip after some hours. *(unverified: Android may link out to web)* | [FREE] | improve: export in the app immediately (zip with GPX/FIT/CSV/JSON), with no email wait |
| 1.6 | About section | App version, maps attribution, company info and a "Rate the app" link. | [FREE] | keep: version, OSS licenses, OSM/map attribution, GitHub link |
| 1.7 | Legal rows | Terms, Privacy Policy, Copyright. | [FREE] | keep: privacy policy and license |
| 1.8 | Support | Support chat with a virtual agent. | [FREE] | drop: we link to GitHub Issues or Discussions instead (no support staff) |
| 1.9 | Developer Hub link | Links to the API docs. | [FREE] | improve: link to our API/self-host docs |

## 2. Preferences: Appearance / Display

```
Appearance                Units                 Temperature
( ) Default (system)      ( ) Imperial  mi/ft   ( ) Celsius
( ) Light                 (•) Metric    km/m    ( ) Fahrenheit
( ) Dark
Default tab on launch:  [ Feed ▾ | Record ]
Autoplay videos:        [■ on ]
```

| # | Detail | Behaviour | Strava | Our app |
|---|---|---|---|---|
| 2.1 | Appearance 3-way picker | Default (follows the Android system), Light or Dark. The out-of-box value is Default. Shipped on 26 Jun 2024. | [FREE] | keep, plus improve: optional AMOLED black and Material You dynamic colour |
| 2.2 | Units: single switch | Imperial (mi/ft) or Metric (km/m). There are no per-sport or per-metric overrides. | [FREE] | improve: keep one master switch, plus an optional "advanced" per-metric override |
| 2.3 | Temperature unit is separate | °C/°F is chosen separately from distance units, so mixing is allowed. | [FREE] | keep |
| 2.4 | Default from locale | The first-run units are presumably derived from the locale/region. *(unverified)* | [FREE] | keep: US/LR/MM default to imperial, everyone else to metric |
| 2.5 | Default launch tab | The app opens on the Feed (Home) or on Record. | [FREE] | keep |
| 2.6 | Autoplay videos | On/off for feed video autoplay. Default is on *(unverified)*. | [FREE] | improve: add a third option, "Wi-Fi only" |
| 2.7 | Language | There is no in-app picker. The app follows the device language (about 20 locales), and date/number formats follow the device locale. *(unverified)* | [FREE] | improve: support the Android 13+ per-app language picker and fall back to the system language |
| 2.8 | Date/time format | Follows the device locale and 12/24 h system setting. *(unverified)* | [FREE] | keep: use `DateFormat.is24HourFormat` and locale formatters, never hard-coded formats |

## 3. Units behaviour across the app

| # | Detail | Behaviour | Strava | Our app |
|---|---|---|---|---|
| 3.1 | Pace vs speed by sport | There is no global switch. Run, walk and hike show pace (min/km or min/mi). Ride shows speed (km/h or mph). | [FREE] | improve: sport default plus a per-sport "show speed/pace" override |
| 3.2 | Swim pace | Shown per 100 m under metric and per 100 yd under imperial. *(unverified)* | [FREE] | improve: let the user pick m or yd independently (pool length matters) |
| 3.3 | Elevation | Metres under metric, feet under imperial. There is no separate toggle. | [FREE] | keep |
| 3.4 | Body weight | kg or lb according to the units setting. Used for calories and power estimates. *(unverified)* | [FREE] | keep |
| 3.5 | HR zones / FTP | Training/performance settings hold HR zones (defaults derived from max HR or age) and cycling FTP. *(unverified labels)* | [FREE]; zone analysis [SUB] | keep: editable zones and FTP, all free |

## 4. Feed and maps

| # | Detail | Behaviour | Strava | Our app |
|---|---|---|---|---|
| 4.1 | Feed highlight: map vs media | Chooses whether your feed cards lead with the route map or with your photos. | [FREE] | keep |
| 4.2 | Feed ordering | Personalized order or latest-first. *(unverified labels)* | [FREE] | improve: default to chronological, with personalized as opt-in |

## 5. Push notifications

| # | Detail | Behaviour | Strava | Our app |
|---|---|---|---|---|
| 5.1 | 7 category toggles | Activities, Friends, Challenges, Clubs, Events, Posts, Other. Defaults are assumed to be on. *(unverified defaults)* | [FREE] | keep: map each one to an Android NotificationChannel so the OS settings match |

```
Push notifications
 Activities  [■]   Friends [■]   Challenges [■]
 Clubs       [■]   Events  [■]   Posts      [■]   Other [■]
```

## 6. Privacy Controls

```
Privacy Controls
 Profile Page ............ Everyone | Followers
 Activities .............. Everyone | Followers | Only You
 Group Activity .......... Everyone | Followers | No One  (unverified)
 Flybys .................. Everyone | Followers | No One
 Mentions ................ Everyone | Followers | No One  (unverified)
 Local Legends ........... show / hide                    (unverified)
 ── Additional Controls ──
 Map Visibility  >
 [ ] Product improvement (Metro / Heatmap contribution)
```

| # | Detail | Behaviour | Strava | Our app |
|---|---|---|---|---|
| 6.1 | Activity visibility: 3 levels | Everyone, Followers or Only You. The account default applies to **new uploads only**. Override per activity via ⋮ > Edit Activity > Visibility. | [FREE] | keep, plus improve: offer an "apply to past activities" bulk action in the app (Strava does this on web only) |
| 6.2 | "Everyone" still hides some fields | Non-followers never see the start time or gear. Photos are visible to non-followers only if Profile Page is also set to Everyone. | [FREE] | keep |
| 6.3 | Leaderboard effects | Followers-only activities are left off public segment and challenge leaderboards. Only You activities are also removed from feeds and public stats, but may still count on challenge progress bars. Some challenges require Everyone. | [FREE] | keep |
| 6.4 | Profile Page picker | Everyone or Followers. Non-followers see a limited profile (name, photo, approximate location). *(labels unverified)* | [FREE] | keep |
| 6.5 | Follower approval | When Profile Page is set to Followers, follow requests need approval (pending, with Approve/Ignore). *(unverified)* | [FREE] | keep |
| 6.6 | Group Activity picker | Controls who sees you listed as "with" others in grouped activities. *(labels unverified)* | [FREE] | keep |
| 6.7 | Group matching rule | Activities are grouped at about 50% time overlap plus nearby locations. *(unverified, low confidence)* | [FREE] | keep: make the threshold a server config value |
| 6.8 | Flyby picker | Everyone, Followers or No One. Only You activities never appear in Flybys. Default is likely Everyone *(unverified)*. | [FREE] | keep, but improve: default to Followers |
| 6.9 | Mentions picker | Who can @mention you. *(unverified, low confidence)* | [FREE] | keep |
| 6.10 | Local Legends opt-out | Show or hide yourself on Local Legend boards. *(unverified, low confidence)* | [FREE] | keep if we build Local Legends |
| 6.11 | "Additional Controls" sub-group | Map Visibility sits below the pickers under this heading. | [FREE] | keep |
| 6.12 | Metro/Heatmap opt-out | The "Product improvement" checkbox opts you out of the Metro and Global Heatmap. Everyone activities contribute by default. | [FREE] | improve: **opt-in** by default (privacy-first) |
| 6.13 | Hide weather on activities | A toggle that hides weather conditions on your activities. | **[SUB]** | keep: free |

## 7. Map Visibility

```
Owner view (Edit Map Visibility)
  start ●░░░░░━━━━━━━━━━━━━━░░░░● end
        └gray┘   orange       └gray┘
  Start hide  [====o------] 200 m
  End hide    [====o------] 200 m
  Hide entire map  [ ]
  > Global settings
Viewer view: gray parts not rendered at all
```

| # | Detail | Behaviour | Strava | Our app |
|---|---|---|---|---|
| 7.1 | Default 200 m hide | After the first upload, the first and last 200 m of future activities are hidden by default. | [FREE] | improve: on by default **from the first upload** |
| 7.2 | Global start/end slider | Ranges from 0 to 1600 m and applies to future uploads only. *(range medium confidence)* | [FREE] | keep |
| 7.3 | Address-based hiding | Hides starts and ends within about 1 mile of an address. Applies **retroactively** to past activities but skips ones edited individually. Where zones overlap, the larger one wins. | [FREE] | improve: support multiple addresses, each with its own radius |
| 7.4 | Hide entire map (account) | An account-level option hides the whole map on all future activities. | [FREE] | keep |
| 7.5 | Per-activity Edit Map Visibility | Separate start and end sliders, a "Hide the entire map" switch and a shortcut to the global settings. Overrides the account default. | [FREE] | keep |
| 7.6 | Owner vs viewer rendering | The owner sees hidden parts in gray and visible parts in orange. Viewers see nothing in hidden parts. | [FREE] | keep: hidden points must be **removed server-side** for viewers, not just styled |
| 7.7 | Segments in hidden zones | Still matched, but visible only to the owner. Not on leaderboards and not counted for segment challenges. Distance challenges still count the full activity. | [FREE] | keep |
| 7.8 | Map hiding is not visibility | Anyone who can see the activity still sees its stats. Help copy explains this. | [FREE] | keep: show the same explainer text |

## 8. Per-activity privacy (Edit Activity)

| # | Detail | Behaviour | Strava | Our app |
|---|---|---|---|---|
| 8.1 | Per-metric hide toggles | Heart rate, pace, power (rides) and calories. Free for everyone since Aug 2021. | [FREE] | keep, plus improve: add start time and elevation |
| 8.2 | Quick Edit after sync | Opening the app after a sync shows a prompt to quickly hide start time, pace/power, calories or HR before others engage. *(animation unverified)* | [FREE] | keep |
| 8.3 | Private Notes | An owner-only notes field, separate from the public description. | [FREE] | keep |
| 8.4 | Mute / don't publish to feeds | The activity stays on your profile but is not pushed to Home or Club feeds. *(label unverified)* | [FREE] | keep |

## 9. Data permissions & connections

| # | Detail | Behaviour | Strava | Our app |
|---|---|---|---|---|
| 9.1 | Contacts sync | When on, uploads the address book and re-syncs it periodically to suggest follows. Turning it off stops new syncing but does not explicitly delete contacts already uploaded. | [FREE] | improve: hash the contacts on the device and **delete uploaded data when the toggle is turned off** |
| 9.2 | Health data screen | Approve or deny access to health data such as HR. On Android this ties into Health Connect. *(unverified)* | [FREE] | keep: Health Connect read/write, with per-data-type toggles |
| 9.3 | Applications, Services and Devices | Lists connected devices and services (Garmin, Wahoo, Health Connect and others) and authorized API apps, each with Revoke Access. *(labels unverified)* | [FREE] | keep |
| 9.4 | OAuth scope checkboxes | The consent screen lists each scope with its own checkbox, so the user can untick some. Private (Only You) activities require `activity:read_all`. | [FREE] | keep |
| 9.5 | Beacon toggle | Live-location sharing with safety contacts, under Training and performance. | **[SUB]** | keep: free |
| 9.6 | Verified badge | Requires age 18+, a single account, a public active profile and at least 3 public activities per month. Comes with a free subscription. | [FREE] | drop: no subscriptions to grant, and verification needs moderation staff. Revisit later |

---

## Acceptance checklist

**Settings shell**
- [ ] A gear icon at the top right of the You tab opens Settings.
- [ ] Every setting persists to the server and appears on a second device after login.
- [ ] Log out and Delete account are at the bottom. Delete requires a typed confirmation, warns that deletion is permanent and offers an export first.
- [ ] Data export produces a zip with the original files plus CSV/JSON, with no email step.
- [ ] About shows the app version, licenses, map attribution and a GitHub link.

**Display / units / locale**
- [ ] The Appearance picker offers Default, Light and Dark. Default follows the system theme and is selected on first run.
- [ ] Changing the theme applies right away without restarting the app.
- [ ] One Units switch (Metric/Imperial) changes distance, elevation, weight and pace/speed units on every screen.
- [ ] The first-run units default to imperial only for US/LR/MM locales.
- [ ] Temperature (°C/°F) can be set independently of distance units.
- [ ] Runs, walks and hikes show pace. Rides show speed. Swims show pace per 100 m or 100 yd. A per-sport override works.
- [ ] Choosing Record as the default tab makes a cold launch open on Record.
- [ ] Autoplay videos offers On, Wi-Fi only and Off, and the feed respects it.
- [ ] Dates and times follow the device locale and the 12/24 h setting. A per-app language override works on Android 13+.

**Feed & notifications**
- [ ] The feed highlight setting switches your feed cards between the map-first and media-first layouts.
- [ ] Feed order offers Chronological (the default) and Personalized.
- [ ] Each of the 7 notification categories has its own toggle and matching Android channel. Disabling one stops that category.

**Privacy**
- [ ] The default activity visibility (Everyone/Followers/Only You) applies only to new uploads. The bulk "apply to past" option updates all past activities.
- [ ] Non-followers never see the start time or gear, even on Everyone activities.
- [ ] Followers and Only You activities never appear on public leaderboards.
- [ ] Profile Page set to Followers limits non-followers to name, photo and approximate location, and turns follows into pending requests with Approve/Ignore.
- [ ] Group Activity, Flybys and Mentions pickers each enforce their audience. Only You activities never appear in Flybys.
- [ ] Heatmap/aggregate contribution is off until the user opts in.
- [ ] Hide weather works for all users.

**Map visibility**
- [ ] A new account hides the first and last 200 m from the first upload onward.
- [ ] The global slider ranges from 0 to 1600 m and affects future uploads only.
- [ ] Address hiding (1 mile by default, adjustable) re-processes past activities, skips ones edited individually, and uses the largest zone where zones overlap.
- [ ] The account-level "hide entire map" setting applies to future uploads. The per-activity setting overrides it.
- [ ] The owner sees hidden parts in gray. The API response for viewers contains no coordinates inside hidden zones.
- [ ] Segment efforts in hidden zones are visible only to the owner, excluded from leaderboards and segment challenges, and the distance still counts toward distance challenges.

**Per-activity**
- [ ] Heart rate, pace, power, calories, start time and elevation can each be hidden per activity. Viewers do not receive hidden fields.
- [ ] The Quick Edit sheet appears on first open after a new activity syncs.
- [ ] Private Notes never appear to anyone but the owner, including in API responses.
- [ ] A muted activity stays on the profile but appears in no Home or Club feed.

**Permissions & connections**
- [ ] Turning off Contacts sync deletes uploaded contact hashes on the server.
- [ ] Health Connect permissions are requested for each data type, and revoking them in the OS is reflected in the app.
- [ ] Applications, Services and Devices lists every connection, and Revoke removes the token right away.
- [ ] The OAuth consent screen shows one checkbox per scope. Without `activity:read_all`, private activities are not returned.
- [ ] Beacon is available to every user with no paywall.

---

## Sources
- Strava Support: Android app settings. https://support.strava.com/en-us/articles/15402004-android-app-settings
- Strava Support: Privacy Controls. https://support.strava.com/hc/en-us/articles/216919377-Privacy-Controls
- Strava Support: Map Visibility. https://support.strava.com/hc/en-us/articles/115000173384-Map-Visibility
- Strava Support: Verified athletes. https://support.strava.com/hc/en-us/articles/115000160850
- Strava Developers: Authentication / OAuth scopes. https://developers.strava.com/docs/authentication/
- DC Rainmaker: Strava privacy features (Aug 2021). https://dcrainmaker.com/2021/08/privacy-features-options.html
- Advnture: Strava dark mode launch. https://www.advnture.com/news/strava-dark-mode
- Tom's Guide: Strava Quick Edit. https://www.tomsguide.com/wellness/fitness/strava-just-released-a-new-feature-and-its-available-to-all-users-5-things-you-can-now-change-with-quick-edit
