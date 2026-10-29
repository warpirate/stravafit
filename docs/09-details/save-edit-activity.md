# Save / Edit Activity: minor-details catalog

Area: `save-edit-activity`. This covers the save screen, Quick Edit, full (Advanced) Edit, Hidden Details, map visibility, perceived exertion, tags and flags, media, crop, mute and delete.

The Strava gating column is for reference only. **StravaFit has no paywall, so every item is free in our app.**

How to read the Strava gating column:
- [FREE] means the feature is free in Strava.
- [SUB] means Strava limits it to subscribers.
- [?] means the gating is unknown.

How to read the "Our app" column:
- **keep** means we copy Strava's behaviour.
- **improve** means we copy it and fix a known weakness.
- **drop** means we leave it out, with a reason.

Items marked "(unverified)" come from a single source or low-confidence reports. Confirm them on a device before treating them as spec.

Paraphrasing: every item here is paraphrased from public sources. No Strava assets, logos or long verbatim text are reproduced.

Dropped during dedup: duplicate entries covering Quick Edit scope, the tag list, mobile-only tags, perceived-exertion scale anchors and the hideable-stat list were merged. No finding was judged clearly wrong. The low-confidence items are kept but flagged.

---

## 1. Save Activity screen (after recording in the app)

```
+--------------------------------------+
| <  Save Activity              [Save] |
|--------------------------------------|
| Title:  [ Morning Run            ]   |
| Description: [ How'd it go?      ]   |
| Sport: [Run v]   Tags: [Race, ...]   |
| [+ Add photos / video]               |
| Gear: [Shoes A v]                    |
| How did that activity feel?     >    |
| Audience: (Everyone|Followers|Only You)
| Map visibility                  >    |
| Hidden Details (eye icon)       >    |
| Private notes                        |
| Mute activity                 [ ]    |
| Commute [ ]   Indoor/Trainer [ ]     |
|           [ Discard activity ]       |
+--------------------------------------+
```

| # | Detail | Behaviour | Strava | Our app |
|---|---|---|---|---|
| S1 | Default title `<Time-of-day> <Sport>` | Untitled activities, both recorded and synced, get a name such as "Morning Run", "Afternoon Ride", "Evening Run" or "Night Walk". The name comes from the **local start time** of the activity, so a wrong device timezone gives the wrong part of day. Hour cutoffs are not documented. A likely split is morning 05-12, afternoon 12-17, evening 17-21, night 21-05 (unverified). | [FREE] | **improve**: use those cutoffs and document them, localise the strings, and compute the name from the activity's own timezone offset. Generate the name only when the title is empty, and never overwrite a title the user typed. |
| S2 | Title prominence | Strava says titled activities get about 4x more kudos, so the title sits at the top and is prominent. | [FREE] | **keep**: put the title field first. Optionally show a subtle "Give it a name?" hint while the default title is unchanged. |
| S3 | Audience picker | Three options: Everyone, Followers, Only You. It is pre-filled from the account default. | [FREE] | **keep** |
| S4 | One account-wide audience default | The default cannot differ by sport, and there is no "some followers" option. Changing the default affects future uploads only. | [FREE] | **improve**: also allow an optional per-sport default override. |
| S5 | Fail-closed privacy | If the default can't be read, the activity is saved as Only You. | [FREE] | **keep**: fail closed on any missing privacy setting. |
| S6 | Hidden Details on Save | Stats can be hidden before the activity is published. | [FREE] | **keep** |
| S7 | Perceived exertion row | A row reads "How did that activity feel?". Tapping it opens a 1-10 slider (see section 5). | [FREE] | **keep** |
| S8 | Moving vs elapsed time | Both times are shown. Moving time drives pace and speed by default. Elapsed time is used for Race-tagged runs, indoor sports such as Yoga and Weight Training, and Ski and Surf sports. On a phone-recorded run, manual pauses define moving time. | [FREE] | **improve**: add a per-activity override that lets the user pick the time basis. |
| S9 | Gear selector | The activity's gear picker (shoes or bike) uses the sport's default gear. In Quick Edit it is reached only through Advanced Edit. | [FREE] | **keep**: pre-select the default gear for the sport. |

## 2. Quick Edit sheet (after a device sync)

```
+--------------------------------------+
|  Quick Edit                     [x]  |
|  Title  [ Afternoon Ride          ]  |
|  Audience  Everyone v                |
|  Hidden details           >          |
|  Hide map & route        [ ]         |
|  [+ Photos/Video]  (about 2 taps)    |
|  Advanced edit  >                    |
|                       [ Save ]       |
+--------------------------------------+
```

| # | Detail | Behaviour | Strava | Our app |
|---|---|---|---|---|
| Q1 | Auto-open after sync | When the user opens the app after a watch or bike-computer sync, a lightweight sheet appears on its own. It was previewed in July 2024 and rolled out to everyone in Sept 2024. | [FREE] | **keep**: add a setting that turns the auto-open off. |
| Q2 | Five areas | The sheet covers title, audience, hidden details, hide entire map/route, and media. | [FREE] | **keep** |
| Q3 | Untouched = defaults | If the user dismisses the sheet without changes, the account defaults apply. | [FREE] | **keep** |
| Q4 | Advanced Edit link | This link opens the full Edit screen, which holds sport type, gear, private notes, map style and workout type. | [FREE] | **keep** |
| Q5 | Tags reachable from Quick Edit | Tags are reached through the description edit flow and then the Activity Tag section (unverified). | [FREE] | **improve**: show tag chips directly on the sheet. |
| Q6 | Media in about 2 taps | The add-media control is placed so photos can be attached in about 2 taps. | [FREE] | **keep** |

## 3. Edit Activity screen (full / Advanced)

The entry point is the three-dot overflow menu on activity detail, then Edit Activity.

| # | Detail | Behaviour | Strava | Our app |
|---|---|---|---|---|
| E1 | Overflow menu | Edit, Crop, Delete and similar actions sit in the top-right three-dot menu. There is no swipe gesture for them. | [FREE] | **keep** |
| E2 | Private Notes | This field is visible only to the owner and never shown to followers. It was added in the Aug 2021 update. | [FREE] | **keep** |
| E3 | Sport type | The sport can be changed in full Edit. Quick Edit does not offer it. | [FREE] | **keep** |
| E4 | Map style | The route line can be coloured by HR, pace/speed, elevation, gradient, temperature, power or time. Colour rules: darker blue = faster, darker red = higher HR, yellow = higher elevation, green/red = descent/climb. Seasonal and cause styles are free. Data-driven stat maps are subscriber-only. | [SUB] (stat maps) | **keep**: all styles are free in our app. Use a colour-blind-safe palette option. |
| E5 | Mute Activity toggle | The toggle is near the bottom of the screen. A muted activity leaves the home and club feeds but stays on the profile and still counts toward goals, progress and challenges. | [FREE] | **keep** |
| E6 | No mute-by-default | Each activity has to be muted on its own. The help text points to Only You for full hiding. | [FREE] | **improve**: add an optional account setting to mute by default, per sport. |
| E7 | Save commits | Changes, including the Hidden Details toggles, apply only when the user taps Save/Update. | [FREE] | **keep**: warn about unsaved changes when the user taps Back. |

## 4. Hidden Details and map visibility

```
Hidden Details  (eye icon)
 Start time   [====o]  <- orange = hidden
 Pace         [o====]
 Speed        [o====]
 Calories     [====o]
 Heart rate   [o====]
 Power        [o====]
```

| # | Detail | Behaviour | Strava | Our app |
|---|---|---|---|---|
| H1 | Six hideable stats | Start Time, Pace (runs), Speed, Calories, Power (rides) and Heart Rate. Each has its own toggle. | [FREE] | **keep**: show only the stats that apply to the sport. |
| H2 | Toggle colour | A toggle shows the brand accent (orange) when the stat is hidden. | [FREE] | **keep**: use our own accent colour. |
| H3 | Owner always sees | Hidden stats stay visible to the owner. | [FREE] | **keep** |
| H4 | Hiding start time keeps date | Hiding the start time still shows the date. Non-followers never see the start time anyway. | [FREE] | **keep** |
| H5 | HR hide hides Relative Effort | Hiding HR also hides the Relative Effort score from others. Hidden data still feeds the owner's Fitness, Freshness and Relative Effort. | [FREE] | **keep** |
| H6 | Account defaults are forward-only | Defaults live under Privacy Controls > Hidden Details and pre-fill future uploads only. | [FREE] | **improve**: offer "apply to past activities too". |
| H7 | Bulk hide only for HR | Of the hidden stats, only HR can be bulk-hidden on past activities. | [FREE] | **improve**: allow bulk hide for all six stats. |
| H8 | Inference warning | Help text warns that hidden values may be inferable from other visible data, for example pace from distance and time. | [FREE] | **keep**: show helper text. |
| H9 | Hide entire map | Hides the map and route from others for this activity only. The owner still sees them. | [FREE] | **keep** |
| H10 | Hide start/end sections | Hides the first or last part of the route from others. It can be set per activity or as an account default. The owner still sees it. | [FREE] | **keep** |
| H11 | Indoor flag hides map for everyone | Marking an activity indoor hides the map entirely, including from the owner. | [FREE] | **improve**: show the owner a "hidden because indoor" notice with a way to undo it. |
| H12 | Flyby eligibility | Only Everyone-audience activities with the map shown appear in Flyby-style features. | [FREE] | **keep**: show helper text next to the toggle. |
| H13 | Not passed to third parties | Map Visibility is not enforced on API consumers that have the private-read scope. | [FREE] | **improve**: our API should honour map hiding for every scope except the owner's own token. |
| H14 | Leaderboard impact | Changing the audience away from Everyone removes the activity's efforts from All-Time leaderboards. They remain under "My Results". | [FREE] | **keep**: show a confirm hint when the user changes the audience. |
| H15 | Only You hides photos | Photos on an Only You activity are hidden everywhere. Geotagged photos inside hidden map zones are not pinned on the map. | [FREE] | **keep** |
| H16 | Group activity privacy | Activities are auto-grouped at about 50% overlap. A separate control offers Followers or No One. | [FREE] | **keep** (the control lives in Settings) |

## 5. Perceived exertion

```
How did that activity feel?
 Easy |--|--|--|--|--|--|--|--|--| Max Effort
      1  2  3  4  5  6  7  8  9  10
 [ ] Use perceived exertion instead of HR/power
```

| # | Detail | Behaviour | Strava | Our app |
|---|---|---|---|---|
| P1 | 1-10 slider | The ends are labelled Easy and Max Effort, and each band has a short cue. For example, "Hard" is roughly "breathing heavily, could barely talk". Exact text per level is unverified. It works without an HR monitor. | [FREE] | **keep**: write our own cue text. |
| P2 | Use PE for training load | A per-activity option uses PE instead of HR or power for Relative Effort and Fitness. HR or power is the default. | [SUB] (medium confidence) | **keep** (free in our app) |
| P3 | Past activities, no bulk | PE can be added to past activities one at a time only. | [FREE] | **keep** (bulk PE edit is out of scope) |

## 6. Tags and flags

| # | Detail | Behaviour | Strava | Our app |
|---|---|---|---|---|
| T1 | Tag list | Race, Long Run, Commute, Workout, Competition, Recovery, With Pet, With Kid and For a Cause. Several can be selected. | [FREE] | **keep** |
| T2 | Mobile-only tags lost on web edit | Some tags are mobile-only, and editing the activity on the web strips them. | [FREE] | **drop** the inconsistency: use one canonical tag set on every platform. |
| T3 | Race tag effects | A Race tag makes the activity open on the Race Analysis tab and calculates pace from elapsed time. Tags show next to the title in the feed and are colour-coded in the Training Log. | [FREE] | **keep** |
| T4 | Commute flag | A boolean that is independent of Trainer. | [FREE] | **keep** |
| T5 | Trainer / Indoor flag | A boolean. A ride can be both commute and trainer. Imports from indoor apps such as TrainerRoad arrive pre-flagged. | [FREE] | **keep**: auto-set the flag when the file has no GPS or the source app is indoor. |

## 7. Media (photos and video)

| # | Detail | Behaviour | Strava | Our app |
|---|---|---|---|---|
| M1 | Add media on Save, Quick Edit and Edit | Photos and video can be attached from all three screens. | [FREE] | **keep** |
| M2 | Set as highlight | On Android, long-pressing a media item offers "Set as highlight", which makes it the feed-card cover instead of the map. | [FREE] | **improve**: also expose it as a visible menu item. |
| M3 | Video 30-second clip | Videos of any length are accepted, but only the first 30 seconds are kept. Video was possibly subscriber-only at launch (unverified). | [?] | **improve**: let the user pick which 30 seconds to keep. |
| M4 | Media count cap | Reports say the app shows 6 items and the web shows 12. Unclear whether this is an upload or display limit (unverified). | [?] | **improve**: use a single documented limit, proposed at 12. |

## 8. Crop activity

```
 Elevation / map preview
 grey====|#########ORANGE#########|====grey
         ^ start [<][>]   end [<][>] ^
                 1 s nudges
```

| # | Detail | Behaviour | Strava | Our app |
|---|---|---|---|---|
| C1 | Two handles | The start and end handles sit over a chart or map. The kept part is orange. The removed part is grey on Android and blue on iOS. Changes apply on Save. | [FREE] | **keep** |
| C2 | 1-second nudge arrows | Arrow buttons move the start or end by 1 s per tap. | [FREE] | **keep** |
| C3 | Coarse drag on mobile | Dragging on mobile moves in larger steps than on the web (unverified, low confidence). | [FREE] | **improve**: make the drag step scale with zoom. |
| C4 | Ends only | Crop cannot cut a section out of the middle. | [FREE] | **improve**: add an optional "remove middle section" mode later (stretch goal). |
| C5 | GPS-only | The menu item is hidden for activities without GPS. | [FREE] | **improve**: allow time-based crop on indoor activities using the HR/power stream. |
| C6 | Recalculation | Crop recalculates distance, time and HR totals and drops segment efforts in the removed part. A flagged activity can be fixed by cropping. | [FREE] | **keep** |

## 9. Delete activity

| # | Detail | Behaviour | Strava | Our app |
|---|---|---|---|---|
| D1 | Delete in overflow | Delete is in the three-dot menu and asks for confirmation (exact wording unverified). It is permanent. | [FREE] | **improve**: add a short undo snackbar, or a 30-day trash. |

---

## Acceptance checklist

- [ ] S1: An empty title saves as `<Morning|Afternoon|Evening|Night> <Sport>` based on the activity's local start hour (05-12, 12-17, 17-21, 21-05). A title the user typed is never overwritten.
- [ ] S1: Default titles are localised and use the activity's own timezone, not the phone's current one.
- [ ] S2: The title field is the first field on Save and Quick Edit.
- [ ] S3: The audience picker offers exactly Everyone / Followers / Only You and is pre-filled from the account default.
- [ ] S4: An optional per-sport audience default overrides the global default for that sport.
- [ ] S5: If the privacy default can't be read, the activity saves as Only You.
- [ ] S6: Hidden Details can be set on the Save screen before the first publish.
- [ ] S8: Pace uses moving time by default, and elapsed time for Race-tagged runs and the listed sports. The user can override the basis per activity.
- [ ] S9: Gear is pre-selected from the sport's default gear.
- [ ] Q1: The Quick Edit sheet opens automatically on first app open after a device sync, and a setting disables this.
- [ ] Q2: Quick Edit lets the user change title, audience, hidden details, hide map and media.
- [ ] Q3: Dismissing Quick Edit without changes keeps the account defaults.
- [ ] Q4: Quick Edit has a link to full Edit.
- [ ] Q5: Tag chips are selectable directly on Quick Edit.
- [ ] E1: Activity detail has an overflow menu containing Edit, Crop and Delete.
- [ ] E2: Private notes are never returned to non-owners by the UI or the API.
- [ ] E4: Every map style, including stat colouring, is available to all users.
- [ ] E5: A muted activity is absent from home and club feeds, present on the profile, and still counts toward goals and challenges.
- [ ] E6: An optional mute-by-default setting exists.
- [ ] E7: Leaving Edit with unsaved changes asks for confirmation.
- [ ] H1: Only the hideable stats that apply to the sport are shown, with one toggle per stat.
- [ ] H3: The owner sees hidden stats. Other viewers do not.
- [ ] H4: With start time hidden, viewers still see the date.
- [ ] H5: Hiding HR also hides the effort score from others, and the owner's training load is unchanged.
- [ ] H6/H7: Account hidden-detail defaults pre-fill new activities, and an optional "apply to past" covers all six stats.
- [ ] H8: An inference warning is shown in the Hidden Details section.
- [ ] H9/H10: Hide entire map, and hide start/end sections, hide the route from others while the owner still sees it.
- [ ] H11: An indoor-flagged activity shows no map, and the owner sees why.
- [ ] H12: Activities with a hidden map, or with an audience other than Everyone, are excluded from Flyby-style features.
- [ ] H13: The API never returns hidden map sections to any token except the owner's.
- [ ] H14: Changing the audience away from Everyone removes the activity from all-time leaderboards and shows a hint.
- [ ] H15: Photos on an Only You activity are hidden from everyone else. Geotagged photos inside hidden zones are not pinned on the map.
- [ ] H16: Group-activity privacy offers Followers / No One.
- [ ] P1: The PE slider runs 1-10 with Easy and Max Effort labels and a cue per band, and works without HR.
- [ ] P2: A per-activity "use PE instead of HR/power" option is available to everyone.
- [ ] T1: All nine tags are available and several can be selected.
- [ ] T2: Tags survive edits on every platform.
- [ ] T3: A Race tag opens the activity on the race-analysis tab and uses elapsed-time pace.
- [ ] T4/T5: Commute and Trainer are independent booleans. Indoor imports and non-GPS files auto-set Trainer.
- [ ] M1: Media can be added from Save, Quick Edit and Edit.
- [ ] M2: Any media item can be set as the feed-card highlight through long-press and through a menu item.
- [ ] M3: A video longer than 30 s lets the user choose the 30-second window to keep.
- [ ] M4: The media limit is the same on every platform and documented.
- [ ] C1: The crop screen shows the kept range in the accent colour and the removed range in grey.
- [ ] C2: The nudge arrows move each crop handle by exactly 1 s.
- [ ] C5: Crop works on activities without GPS using the time streams.
- [ ] C6: After a crop, distance, time and HR totals and segment efforts are recalculated, and efforts in the removed range are dropped.
- [ ] D1: Delete asks for confirmation and offers undo or trash recovery.

## Sources

- Strava Support, Hide Stats from your Activities: https://support.strava.com/hc/en-us/articles/360028654291-Hide-Stats-from-your-Activities
- Strava Support, Privacy Controls overview: https://support.strava.com/hc/articles/360025920332
- Strava Support, Editing an Activity: https://support.strava.com/hc/en-us/articles/216919557-Editing-an-Activity
- Strava Support, Perceived Exertion: https://support.strava.com/hc/en-us/articles/360032535512-Perceived-Exertion
- Strava Support, Muting an activity: https://support.strava.com/en-us/articles/15401718-how-do-i-mute-an-activity-on-strava
- Strava Support, Moving vs elapsed time: https://support.strava.com/hc/en-us/articles/115001188684-Hiding-Activity-Stats
- Strava Support, Crop (per finding): https://support.strava.com/hc/en-us/articles/216919437-How-do-I-get-my-activities-to-Strava
- Advnture, Quick Edit rollout: https://www.advnture.com/news/strava-update-allows-activity-dit-in-two-taps
- Endurance.biz, Quick Edit to all users (2024): https://endurance.biz/2024/industry-news/quick-edit-workout-feature-rolls-out-to-all-strava-users/
- BikeRadar, Edit Activity 2021 update: https://www.bikeradar.com/news/strava-edit-activity-2021-update/
- the5krunner, Strava cropping: https://the5krunner.com/2020/03/02/new-strava-activty-cropping-strava/
- Runner's World AU, Perceived exertion launch: https://runnersworldonline.com.au/strava-rolls-new-features-help-track-overall-fitness/
- Android Central, Adding videos on Android: https://www.androidcentral.com/wearables/how-to-add-videos-to-strava-app-android
- Cycling Weekly, Video sharing rollout: https://www.cyclingweekly.com/fitness/strava-quietly-rolls-out-video-sharing-function
- Travesia Pirenaica, Strava photos (media cap, low confidence): https://travesiapirenaica.com/en/strava-photos/
- Reddit r/Coros, Activity naming (mirror): https://lr.sudovanilla.org/r/Coros/comments/1e26hdd/running_activity_name_in_strava
- stravalib docs, update_activity commute/trainer fields: https://stravalib.readthedocs.io/en/latest/reference/api/stravalib.client.Client.update_activity.html
