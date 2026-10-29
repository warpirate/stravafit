# Profile & Progress: minor-details catalog

Area: **profile-progress**. Covers the You tab, profile header, progress charts, Training Log, Best Efforts, Trophy Case, stats by sport, gear and mileage alerts, and followers/following lists.

> Gating is listed for reference only. Strava's tiers are **[FREE]** and **[SUB]**. StravaFit has no paywall, so every feature is free in our app.
> Sources are public only. Text is paraphrased. Items without a confirming source are marked **(unverified)**.
> Low-confidence findings were not spot-checked with web search in this pass, which was shortened to save time. They stay marked (unverified).

Column key: **Our app**: `keep` = match Strava, `improve` = match and extend, `drop` = do not build (reason given).

---

## 1. You tab (shell & header)

| # | Detail | Behaviour | Strava | Our app |
|---|---|---|---|---|
| Y1 | You tab replaced Profile tab | The 2021 nav redesign renamed "Profile" to "You". The tab combines **Progress** and **Activities** sub-sections, and the full profile opens from the avatar in the top bar. (Medium confidence: press coverage of the beta.) | [FREE] | keep |
| Y2 | Settings entry in header corner | A settings (gear/cog) icon sits in the upper corner of the You tab. | [FREE] | keep |
| Y3 | Pull to refresh | Pulling down on the You feed refreshes it. | [FREE] | keep |
| Y4 | Haptics and animations | Public sources do not document haptics, chart animations or swipe gestures on this tab. Any of these is **(unverified)**. | n/a | improve: add a light haptic tick on chart point selection as our own choice |

```
+--------------------------------------+
| (avatar)        You            [cog] |
|  [ Progress ]  [ Activities ]        |
|--------------------------------------|
|  progress chart ...                  |
```

## 2. Profile header & social lists

The raw findings had little detail here. The rows below are baseline expectations, all **(unverified)**, and need confirming in a later sweep.

| # | Detail | Behaviour | Strava | Our app |
|---|---|---|---|---|
| P1 | Header block (unverified) | Avatar, name, location, and follower/following counts. Tapping a count opens the list. | [FREE] | keep |
| P2 | Followers/following lists (unverified) | Scrollable lists of athletes with a follow/unfollow button on each row. Tapping a row opens that athlete's profile. | [FREE] | keep |
| P3 | Privacy on other profiles | Non-followers can see an athlete's running **shoes** but not their **bikes** or activity start times. | [FREE] | keep: respect the same default and make it configurable |

## 3. Progress summary chart

| # | Detail | Behaviour | Strava | Our app |
|---|---|---|---|---|
| C1 | Tap to drill into a week | Tapping a point or bar opens that week and lists the activities in its total. Tapping a row opens the activity. Only tap is documented, with no scrub or long-press. | [FREE] | improve: add drag-to-scrub as well as tap |
| C2 | Time-range chips | "See more of your progress" opens the chips **1W** (7 days), **1M** (30 days), **3M** (12 weeks), **6M** (24 weeks), **YTD** and **1Y** (52 weeks). 3M and 6M are counted in weeks, not calendar months. | gating unknown | keep: same week-based definitions |
| C3 | Sport picker limited to last 52 weeks | The picker lists only sports recorded in the past 52 weeks, plus a combined all-sports total. | [FREE] | improve: add "show older sports" so they do not vanish |
| C4 | Metrics depend on GPS vs indoor | Metrics are distance, time, elevation and count. GPS sports offer distance and elevation. Indoor sports offer time only. | [FREE] | keep |
| C5 | Compare Date Range | In the expanded view, overlays the current period on an earlier period of the same length. | [SUB] (medium confidence) | keep (free) |

```
 1W  1M  3M  6M  YTD  1Y      [Run v] [Distance v]
 km|        _/\
   |   _/\_/   \__/\      <- tap a point = open that week
   |__/_______________
        w1  w2  w3  w4
 [ Compare Date Range ]   (dashed overlay = earlier period)
```

## 4. Training Log (calendar)

| # | Detail | Behaviour | Strava | Our app |
|---|---|---|---|---|
| T1 | Entry via preview card | On mobile, the log opens from a Training Log preview card in You > Progress. It is not a top-level tab. Everyone sees the preview, but the full log is for subscribers. | [SUB] (full log) | keep (free for all) |
| T2 | Calendar jump icon | A calendar icon at the top right jumps to an earlier month or year. | [SUB] | keep |
| T3 | Activity circles | Each activity is a circle colored by sport. Tags (commute, recovery, with-pet) get their own colors. Circle size scales with the chosen metric **(unverified)**. | [SUB] | keep |
| T4 | Tap circle shows day stats | Tapping a circle shows key stats for that day's activities, not the full activity page. | [SUB] | improve: add an "Open activity" link in the popover |
| T5 | Android filter icon | On Android, filters sit behind a sliders ("three switches") icon. On iOS they are inline above the calendar. | [SUB] | keep (Android pattern) |
| T6 | Sizing metric toggle | Choose time, distance or elevation. This is separate from the sport filter. | [SUB] | keep |
| T7 | Sport filter | Choose which sport types are shown. | [SUB] | keep |
| T8 | Tag filter | Choose whether commute, recovery and with-pet activities are included. Tags are set on the activity edit screen. | [SUB] | keep |
| T9 | Auto-refresh after upload | The log updates after each upload with no manual refresh. | [SUB] | keep |

```
Training Log                 [cal] [|||]
        Mon Tue Wed Thu Fri Sat Sun  Total
Wk 42    o       O       .       (O)  42km
Wk 43        o       o   O            31km
  o/O = size by metric, color = sport, ring = tag
```

## 5. Best Efforts

| # | Detail | Behaviour | Strava | Our app |
|---|---|---|---|---|
| B1 | Top 3 per category | Each distance or duration category shows the all-time PR plus the 2nd and 3rd best. Tapping a result opens its activity. | [SUB] | keep |
| B2 | View Analysis | Shows the best efforts grouped by year, the top efforts within each year (highlighted), and a trend line. For cycling power, "View Power Curve" replaces this view. | [SUB] | keep |
| B3 | Remove Effort | Deletes the effort from history and clears the achievement banner, but keeps the activity. If the removed effort was the PR, the next-best effort becomes the PR. | [FREE] | keep |
| B4 | Edit Time (runs) | Enter a chip time or fix a GPS error. The new time updates Best Efforts, PR Analysis and the activity's Results, and may remove the achievement banner. The GPS track is unchanged. | [FREE] | keep |
| B5 | Activity-page Best Efforts | The run or ride detail page lists the benchmarks covered. Lifetime and current-year top-3 placements get a medal icon. | [FREE] | keep |
| B6 | Elapsed time, not moving time | Efforts use elapsed time and are computed from the GPS stream, independent of splits. | [FREE] | keep: document this in-app |
| B7 | Bad GPS makes a segment ineligible | Missing, errant or false-acceleration points exclude that part of the activity. Falling just short of a distance yields no effort. | [FREE] | keep |
| B8 | Cycling and running both covered | Both sports have benchmark distances. On the web you can compare against another athlete. (Medium confidence.) | [FREE] | keep |
| B9 | Missing-effort fix is web-only | The fix is a "Refresh Stats" button under lifetime totals, on the web only. The app has no equivalent. (Medium confidence.) | [FREE] | improve: add an in-app "Recalculate stats" action |

```
Best Efforts  > 5K
 1  21:04  (gold)  12 Mar 2025  >
 2  21:30  (silver) 02 Feb 2025 >
 3  21:51  (bronze) 18 Oct 2024 >
 [ View Analysis ]
```

## 6. Trophy Case

| # | Detail | Behaviour | Strava | Our app |
|---|---|---|---|---|
| R1 | Four most recent on profile | The profile strip shows the 4 latest challenge badges by completion date. "All trophies" opens the full list. | [FREE] | keep |
| R2 | Mobile path | You > avatar > scroll > All trophies. Tapping a badge opens the challenge detail. | [FREE] | keep |
| R3 | Milestone challenges hidden until complete | Challenges without a single distance goal (for example, the monthly training series) appear only once the final milestone is reached. | [FREE] | keep |

```
Trophy Case                 All trophies >
 [b1] [b2] [b3] [b4]   (newest first)
```

## 7. Stats by sport

| # | Detail | Behaviour | Strava | Our app |
|---|---|---|---|---|
| S1 | Lifetime and annual totals per sport (unverified) | The profile shows per-sport totals (distance, time, elevation, count) for this year and all time. Only the web "Refresh Stats" (B9) is documented. | [FREE] | keep |

## 8. Gear & mileage alerts

| # | Detail | Behaviour | Strava | Our app |
|---|---|---|---|---|
| G1 | Add Bike fields | Nickname, brand, model and bike type. Weight is required on the web but optional on Android. Sport is optional. | [FREE] | keep: weight optional |
| G2 | Add Shoes fields | Brand comes from a fixed list and model is required. The app also asks for a nickname. | [FREE] | improve: allow a free-text brand as a fallback |
| G3 | Model catalogue lock | Users cannot add a model that is not in the catalogue. Model capitalization follows the first entry and cannot be edited. | [FREE] | drop: allow free-text models and editable casing (a locked catalogue frustrates users) |
| G4 | Shoe alert toggle at creation | The mileage-notification opt-in appears on the create-shoe form. Bikes have no equivalent. | [FREE] | improve: add alerts for bikes too |
| G5 | Shoe alert threshold | Defaults to 250 mi and can go up to 800. It is configured on the web (Settings > My Gear) and delivered by **email**. | [FREE] | improve: set it in the app, deliver by push and in-app, allow any value |
| G6 | Alert repeats every run | After the threshold, the alert fires after every run with that shoe until the shoe is retired or the alert is turned off. | [FREE] | improve: send once, then a weekly reminder |
| G7 | Default gear per sport | One item can be the default for several sports. Shoes: Run, Trail Run, Walk, Virtual Run, Hike. Bikes: Ride, MTB, Gravel, E-Bike, E-MTB, Handcycle, Virtual Ride, Velomobile. Path: item > Edit > Default Sports. | [FREE] | keep |
| G8 | Retire and restore | Retired gear is hidden from the list and the activity picker, and can be restored. Retiring a sport default means choosing a new default. | [FREE] | keep: prompt for a new default on retire |
| G9 | Mileage not editable | The total comes only from activities. The workaround is a manual activity with the old distance. Totals can lag by hours. | [FREE] | improve: add a "starting distance" field and update totals instantly |
| G10 | Bike components are web-only | Components such as chain and tyres are managed on the web, one per type. Retired components cannot be restored. Retiring a frame means creating a new bike. | [FREE] | improve: put components in the app and let them be restored |
| G11 | Save blocked by emoji or bad weight | The Save button stays disabled when a name has emoji or the weight is invalid. | [FREE] | drop: accept emoji and show inline validation errors instead of a silent disabled button |
| G12 | Shoes public, bikes followers-only | See P3. | [FREE] | keep |

```
Add Shoes
 Brand   [ Select brand   v ]
 Model   [ ............... ]
 Name    [ ............... ]
 [x] Notify me at [ 250 ] mi
              [ Save ]
```

---

## Acceptance checklist

- [ ] Y1: The bottom nav has a "You" tab with Progress and Activities sub-sections, and tapping the avatar opens the full profile.
- [ ] Y2: A settings icon in the You header opens Settings.
- [ ] Y3: Pulling down on the You tab refreshes its data.
- [ ] Y4: Selecting a chart point triggers a light haptic, which respects the system haptics setting.
- [ ] P1: The profile header shows avatar, name, location and follower/following counts, and tapping a count opens the matching list.
- [ ] P2: Follower/following rows open a profile, and the follow button changes state without leaving the list.
- [ ] P3: A non-follower viewing a profile sees shoes but not bikes or activity start times by default.
- [ ] C1: Tapping a chart point lists that week's activities, tapping a row opens the activity, and drag-to-scrub updates the selected week.
- [ ] C2: The chips 1W, 1M, 3M, 6M, YTD and 1Y exist, and 3M/6M cover exactly 12 and 24 weeks.
- [ ] C3: The sport picker defaults to sports from the last 52 weeks, includes "All sports", and offers older sports through "show older".
- [ ] C4: Indoor sports offer only time (and count). GPS sports also offer distance and elevation.
- [ ] C5: Compare Date Range overlays a previous equal-length period, and is free.
- [ ] T1: The Training Log opens from a preview card in Progress, and is free for all users.
- [ ] T2: A calendar icon jumps to any earlier month or year.
- [ ] T3: Activities appear as circles colored by sport, tagged activities have distinct colors, and circle size scales with the chosen metric.
- [ ] T4: Tapping a circle shows that day's stats in a popover with an "Open activity" link.
- [ ] T5: On Android, the filters open from a sliders icon.
- [ ] T6: The sizing metric toggles between time, distance and elevation.
- [ ] T7: The sport filter hides and shows sport types.
- [ ] T8: The tag filter includes or excludes commute, recovery and with-pet activities.
- [ ] T9: A newly uploaded activity appears in the log without a manual refresh.
- [ ] B1: Each Best Effort category shows the top 3, and each result opens its activity.
- [ ] B2: View Analysis shows yearly bests, highlights each year's top effort and draws a trend line. Cycling power links to a power curve.
- [ ] B3: Remove Effort deletes the effort, clears the banner and keeps the activity, and the next-best effort is promoted to PR.
- [ ] B4: Edit Time on a run updates Best Efforts, PR analysis and activity results, and leaves the GPS track unchanged.
- [ ] B5: The activity detail page lists the benchmarks covered, with medals for lifetime and current-year top 3.
- [ ] B6: Best Effort times use elapsed time from the GPS stream.
- [ ] B7: GPS segments with gaps or spikes are excluded, and a run that falls short of a distance yields no effort.
- [ ] B8: Best Efforts exist for both running and cycling distances.
- [ ] B9: An in-app "Recalculate stats" action rebuilds Best Efforts and totals.
- [ ] R1: The profile Trophy Case strip shows the 4 most recent badges, newest first, with an "All trophies" link.
- [ ] R2: Tapping a badge opens its challenge detail.
- [ ] R3: Milestone-series challenges appear only when the final milestone is completed.
- [ ] S1: The profile shows per-sport totals for this year and all time (distance, time, elevation, count).
- [ ] G1: Bike creation requires nickname, brand, model and type, with weight optional.
- [ ] G2: Shoe creation offers a brand list plus a free-text fallback, and requires a model.
- [ ] G3: Users can enter a new model name and edit its capitalization.
- [ ] G4: Both shoes and bikes have a distance-alert toggle when created.
- [ ] G5: The alert threshold defaults to 250 mi / 400 km, can be edited in the app, and is delivered by push and in-app.
- [ ] G6: The alert fires once on crossing the threshold, then at most weekly until gear is retired or the alert is turned off.
- [ ] G7: Gear can be the default for several sports, and new activities of those sports auto-assign it.
- [ ] G8: Retired gear is hidden from pickers and can be restored, and retiring a default prompts for a new one.
- [ ] G9: Gear has a "starting distance" field, and totals update straight after upload.
- [ ] G10: Bike components can be managed in the app, and retired components can be restored.
- [ ] G11: Gear names accept emoji, and invalid weight shows an inline error rather than a silently disabled Save.
- [ ] G12: Gear visibility follows P3.

## Sources

- Strava Support: Progress Summary Chart: https://support.strava.com/hc/en-us/articles/28437860016141-Progress-Summary-Chart
- Strava Support: Training Log: https://support.strava.com/hc/en-us/articles/206535704-Training-Log
- Strava Support: Best Efforts (Progress): https://support.strava.com/hc/articles/19685360245005
- Strava Support: Best Efforts for Running: https://support.strava.com/hc/en-us/articles/216917127-Best-Efforts-for-Running
- Strava Support: The Strava Trophy Case: https://support.strava.com/hc/en-us/articles/216918557-The-Strava-Trophy-Case
- Strava Support: Managing Shoe Notifications: https://support.strava.com/hc/en-us/articles/216918887-Managing-Shoe-Notifications
- Strava Support: Strava Gear: https://support.strava.com/hc/en-us/articles/216918727-Strava-Gear
- Strava Support: You tab / settings: https://support.strava.com/hc/en-us/articles/216917527
- BikeRadar: Strava beta layout: https://bikeradar.com/news/strava-beta-test-layout
