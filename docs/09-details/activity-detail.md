# 09 — Minor details: Activity Detail

Scope: activity detail page, stats grid, units/rounding, splits, laps, segments, achievement icons, analysis charts and scrubbing, zones, map, Flyover, matched activities, social bar, overflow menu.

Gating legend: **[FREE]** / **[SUB]** shows how Strava gates the feature, for reference only. In StravaFit everything is free.
"Our app": **keep** = copy the behaviour, **improve** = copy it with the change described, **drop** = leave it out (reason given).
Items marked "(unverified)" have weak sourcing or are documented for web only. Confirm them before building to match.

Note: this pass did not re-check the findings with new web searches, to save time. Low-confidence items are marked (unverified) instead.

---

## 1. Header and stats grid

```
+------------------------------------------+
| <-  Morning Run            [share] [...] |
| Today at 07:12  ·  Bengaluru             |   <- clock time hidden if "Start Time" is hidden
+------------------------------------------+
|  Distance     Moving Time     Pace       |
|  10.02 km     52:14           5:13 /km   |
|  Elev Gain    Calories        Elapsed    |
|  84 m         612             55:40      |
+------------------------------------------+
```

| # | Detail | Behaviour | Strava | Our app |
|---|---|---|---|---|
| 1.1 | Moving time is the headline time | The page, feed, goals and stats use moving time by default. Elapsed time is used instead for runs tagged Race, indoor sports (Yoga, Weight Training) and some outdoor sports (Ski, Surf). Ski speed still uses moving time. | [FREE] | keep. Also show both times in the grid. |
| 1.2 | Rest detection | Pause events in the uploaded file take precedence. Without them, a GPS-speed threshold decides moving time; a running cutoff of about 30 min/mi has been reported (unverified). In-app recording uses the accelerometer and respects manual pauses. | [FREE] | keep. Expose the threshold as a constant. |
| 1.3 | Average speed and pace | Total distance divided by moving time. Distance source priority: power hub, then speed sensor (revolutions × wheel circumference), then GPS. | [FREE] | keep |
| 1.4 | Max speed is raw | Max speed is the fastest pair of consecutive GPS points, so GPS error can inflate it. The analysis chart is smoothed and plots about every third point, so its peak can differ from the stat. | [FREE] | improve. Filter outliers before computing max speed so the stat matches the chart. |
| 1.5 | Elevation source | Uses barometric elevation from the file when the device is known. Otherwise uses "corrected" elevation from a basemap, which can inflate totals. | [FREE] | keep. Add a label showing the source and a "correct elevation" toggle. |
| 1.6 | Elevation gain threshold | Data is smoothed, then a climb only counts once the rise passes a threshold: 2 m for barometric data, 10 m for non-barometric (the 10 m figure is unverified). | [FREE] | keep. Use 2 m and 10 m as configurable values. |
| 1.7 | Calories | Calculated only for Ride, Run, Walk and Hike; other sport types use the partner's value or show nothing. Rides: power × efficiency. Runs: GAP speed × weight × moving time. Third-party values are not recalculated. | [FREE] | improve. Estimate calories for all sports using MET values, and label them "estimated". |
| 1.8 | Hidden start time | Hides only the clock time. The header still shows the date or "Today". | [FREE] | keep |
| 1.9 | Hidden stats: owner vs viewer | Hidden stats disappear only for other viewers. They still count toward fitness and Relative Effort. Hiding HR also hides Relative Effort. | [FREE] | keep |
| 1.10 | Stat labels link to glossary | Documented for web only. | [FREE] (unverified) | improve. On long-press of a label, show a bottom sheet that defines the stat. |

## 2. Splits table

```
 Km   Pace      Elev   HR
  1   5:08 /km  +4 m   142
  2   5:21 /km  +12 m  151   <- tap: highlight on map/profile, zoom
  3   5:02 /km  -6 m    —    <- HR missing: dash, not interpolated
```

| # | Detail | Behaviour | Strava | Our app |
|---|---|---|---|---|
| 2.1 | Split unit | Splits are generated automatically every 1 km or 1 mi, according to the user's unit preference. They appear as a list under the analysis preview. | [FREE] | keep. The last partial split shows its actual distance. |
| 2.2 | GAP column only on hilly runs | Per-split Grade Adjusted Pace is shown only when the run is hilly. | [SUB] | improve. Show GAP whenever elevation change passes a threshold, and let the user toggle it. |
| 2.3 | Missing HR | Splits with HR gaps show the cell blank rather than interpolated. How the UI displays this is unverified. | [FREE] | keep. Show an em dash. |
| 2.4 | Split selection | Selecting a split highlights it on the map (blue) and on the elevation profile (dark gray) and zooms the map to it. Tapping again releases the zoom. Laps behave the same way. Documented for web; mobile is unverified. | [FREE] | keep. Tap to select, tap again to deselect. |

## 3. Laps and Workout Analysis

```
 Workout Analysis            [Pace v]
 ▇▇▇  ▂▂  ▇▇▇  ▂▂  ▇▇▇  ▂▂
 Lap  Dist    Time    Pace
  1   1.00 km 4:30    4:30 /km
  2   0.40 km 2:40    6:40 /km
```

| # | Detail | Behaviour | Strava | Our app |
|---|---|---|---|---|
| 3.1 | When it appears | Needs 2 or more laps; rides also need power. Mobile shows it for every run that has laps. Runs without laps get Pace Analysis by splits instead. | [SUB] | keep. Rides do not need power: fall back to speed. |
| 3.2 | Lap bar chart | Bar per lap (pace by default; can switch to HR, power, speed or distance depending on sport). Each row shows distance, elapsed time and pace. The chart scrolls, and dragging highlights a range. | [SUB] | keep |
| 3.3 | Preview card | The activity page shows a preview card; tapping it opens full-screen analysis. | [SUB] | keep |
| 3.4 | Splits/laps toggle | Pace analysis switches between splits and laps when laps exist. The slowest, fastest and average splits are marked. | [SUB] | keep |
| 3.5 | Race-tagged runs | Web drops the standard run analysis for Race runs. Mobile behaviour is unverified. | [FREE] | drop. Show analysis for all runs; there is no reason to hide it. |

## 4. Analysis charts and scrubbing

```
 pace ~~~/\~~~\__/~~~~  ---- avg
 elev ░░▒▒▓▓▓▒▒░░░░▒▒  (shaded)
          |  <- finger: 5:12/km · 812 m · 41 m
```

| # | Detail | Behaviour | Strava | Our app |
|---|---|---|---|---|
| 4.1 | Pace chart over elevation | Live pace is drawn over a shaded elevation profile, with an average line. | [SUB] | keep |
| 4.2 | Scrub values | Dragging a finger along the chart shows values at that point. | [SUB] | improve. Also sync a marker on the map while scrubbing. |
| 4.3 | GAP chart | Plots GAP for the whole run, with the overall average. | [SUB] | keep |
| 4.4 | GAP curve | Uphill GAP is faster than actual pace; downhill it is slower. The downhill correction peaks around a -10% grade and then eases. Terrain technicality is ignored. | [SUB] | keep. Implement this curve shape. |
| 4.5 | Chart smoothing | Charts are smoothed and plot about every third point (see 1.4). | [FREE] | keep. Make the smoothing window configurable. |

## 5. Zones and effort

| # | Detail | Behaviour | Strava | Our app |
|---|---|---|---|---|
| 5.1 | Pace zones | Time in 6 pace zones, summarised as zones 1-3 (easier) and zones 4-6 (harder). Zones come from past performances and are edited on mobile under Edit Profile > Performance Potential. | [SUB] | keep. Also allow manual zone editing. |
| 5.2 | Relative Effort card | Shown only when the activity has HR or Perceived Exertion. Weighted by sport and personalised to HR zones. Press coverage says it compares against the athlete's 3-week average. | [SUB] | keep |
| 5.3 | Best Efforts trends | Strava lists these as mobile-only. | [SUB] | keep |

## 6. Segments and achievements

```
 Segments
 [crown]   Lake Loop Sprint      2:04   KOM
 [trophy]  Hill Rd Climb         5:40   3rd overall
 [medal]   Park Straight         1:12   PR
 [laurel]  River Path                    Local Legend
```

| # | Detail | Behaviour | Strava | Our app |
|---|---|---|---|---|
| 6.1 | Icon taxonomy | Crown = KOM/QOM or course record. Trophy = all-time places 2-10. Medal = personal 1st (PR), 2nd or 3rd. Laurel = Local Legend (most efforts in 90 days). | [FREE] | keep the meanings. Use our own icon art; do not copy Strava's assets. |
| 6.2 | Solid vs outlined | Solid icons for all-time, outlined for yearly (2015 change; current use unverified). | [FREE] (unverified) | improve. Use this distinction for yearly vs all-time. |
| 6.3 | Same sport only | Efforts are ranked only against the same sport type. | [FREE] | keep |
| 6.4 | Elapsed time for segments | Segment and best-effort times use elapsed time, measured from crossing the start point to crossing the end point, not from distance. | [FREE] | keep |
| 6.5 | Run best-effort distances | 400 m, 1/2 mi, 1 km, 1 mi, 5 km, 10 km, half marathon, marathon. | [FREE] | keep. Allow custom distances. |
| 6.6 | Privacy and achievements | PRs stay when an activity is set to Only You. Crowns and Top 10s are removed when privacy changes. | [FREE] | keep |
| 6.7 | Segment row actions | Star a segment, unhide hidden ones, compare with leaders, set a goal, open the leaderboard. Documented on web; mobile is unverified. | [FREE] | keep |

## 7. Map, Flyover and matched activities

```
 +----------------------------+
 |  3D map   ~~orange line~~  |
 |            (blue dot)  [⋮] |  <- ⋮ = stats overlay toggle
 |  Morning Run 12.1 km/h 4.2km 88m
 | [||] 1x  ====o-------  [⊕] |  <- play/pause, speed, scrub, re-center
 +----------------------------+
```

| # | Detail | Behaviour | Strava | Our app |
|---|---|---|---|---|
| 7.1 | Auto-play | Flyover starts playing on open. The 2023 launch build opened it from a button on the map. | [SUB] | keep |
| 7.2 | Player controls | Play/pause, speed control, draggable scrub bar. The launch build had none of these, and touching the screen ended playback. | [SUB] | keep |
| 7.3 | Camera | Pinch or scroll to zoom and rotate; Re-Center restores the default camera. | [SUB] | keep |
| 7.4 | Stats overlay | Shows title, speed, distance and elevation; segment flyovers show distance and elevation only. Toggled from the ⋮ menu. | [SUB] | keep |
| 7.5 | Visual style | Orange line with a moving blue dot. The camera barely moves on simple routes such as tracks. | [SUB] | improve. Use our own colours and add camera motion on loop routes. |
| 7.6 | Duration compression | Nonlinear: 1 h → about 60 s, 8 h → about 3 min, 12 h → about 6 min (launch build). | [SUB] | keep. Use a log-style mapping. |
| 7.7 | Render notification | Long activities send a push notification when the flyover is ready. | [SUB] | improve. Render on-device if fast enough; otherwise notify. |
| 7.8 | Segment flyover | Play button at the bottom-right of a segment effort's map. | [SUB] | keep |
| 7.9 | Share | Share to Instagram Stories or DMs, or "More". Shared video honours map visibility and privacy zones. | [SUB] | keep. Privacy zones are mandatory. |
| 7.10 | Matched activities bar | Appears only when matches exist. Opens a page with a route count and a pace-over-time graph. | [SUB] | keep |
| 7.11 | Matched list | Grouped by year. Scrolling the list highlights the matching graph point; tapping a row opens the activity. Visible to the owner only. | [SUB] | keep |

## 8. Social bar

| # | Detail | Behaviour | Strava | Our app |
|---|---|---|---|---|
| 8.1 | Kudos | The thumbs-up turns orange and cannot be undone. Tapping the count opens the list of people who gave kudos. | [FREE] | improve. Allow undo within 5 s. |

## 9. Overflow menu and Edit Activity

| # | Detail | Behaviour | Strava | Our app |
|---|---|---|---|---|
| 9.1 | Edit Activity fields | Title, sport type, run type, tags, map type, perceived exertion, gear, privacy, hidden stats, description, private notes, photos. | [FREE] | keep |
| 9.2 | Hidden Details | Start Time, Pace, Speed, Calories, Power and Heart Rate can be hidden, via ⋮ > Edit Activity > Hidden Details. Also offered on the Save screen. | [FREE] | keep |
| 9.3 | Default hidden details | Set under Settings > Privacy Controls and apply to future uploads only. HR can be hidden in bulk on past activities; other stats cannot. | [FREE] | improve. Allow bulk-hiding of every stat. |
| 9.4 | Correct Distance | Web-only, so it is not in the Android menu. It changes totals but not segment times. | [FREE] | improve. Add it to the mobile ⋮ menu. |

---

## Acceptance checklist

- [ ] 1.1 By default, the headline time is moving time. Race runs, Yoga, Weight Training, Ski and Surf show elapsed time instead.
- [ ] 1.2 Pause events in an imported file take precedence over automatic rest detection. Without them, a configurable speed threshold is applied.
- [ ] 1.3 Average pace/speed = distance / moving time. Sensor distance takes precedence over GPS distance.
- [ ] 1.4 The Max Speed stat ignores GPS spikes, and the chart peak is within 5% of the stat.
- [ ] 1.5 The elevation source (barometric or corrected) is labelled on the page.
- [ ] 1.6 Elevation gain applies a 2 m threshold to barometric data and a 10 m threshold to other data (both configurable).
- [ ] 1.7 Calories appear for every sport type, labelled "estimated" when calculated by the app.
- [ ] 1.8 With Start Time hidden, viewers see the date or "Today" but no clock time.
- [ ] 1.9 The owner always sees hidden stats. Viewers do not. Hiding HR also hides Relative Effort from viewers.
- [ ] 1.10 Long-pressing a stat label opens a definition sheet.
- [ ] 2.1 Splits follow the unit preference, and the final partial split shows its real distance.
- [ ] 2.2 The GAP column appears on hilly runs and can be toggled.
- [ ] 2.3 Splits with no HR data show "—".
- [ ] 2.4 Tapping a split or lap highlights it on the map and elevation profile and zooms the map. Tapping again resets.
- [ ] 3.1 Workout Analysis appears for activities with 2 or more laps. Runs without laps show split analysis.
- [ ] 3.2 The lap chart can switch metrics, scrolls, and supports range selection. Rows show distance, time and pace.
- [ ] 3.3 Tapping the preview card opens full-screen analysis.
- [ ] 3.4 The splits/laps toggle appears when laps exist, and the fastest, slowest and average splits are marked.
- [ ] 3.5 Analysis is shown for Race-tagged runs.
- [ ] 4.1 The pace chart shows the elevation fill and an average line.
- [ ] 4.2 Scrubbing the chart shows point values and moves a marker on the map.
- [ ] 4.3 The GAP chart shows the overall average GAP.
- [ ] 4.4 GAP is faster than pace uphill and slower downhill, with the downhill correction peaking near -10%.
- [ ] 5.1 The pace zones card shows 6 zones with an easy (1-3) vs hard (4-6) summary, and zones can be edited.
- [ ] 5.2 The Relative Effort card appears only when HR or Perceived Exertion is present.
- [ ] 5.3 Best-effort trends are available for each best-effort distance.
- [ ] 6.1 Crown, trophy, medal and laurel icons appear with the correct meanings, using original artwork.
- [ ] 6.2 All-time and yearly achievements are visually distinct.
- [ ] 6.3 Leaderboards never mix sport types.
- [ ] 6.4 Segment times use elapsed time from crossing the start point to crossing the end point.
- [ ] 6.5 Runs award best efforts at the 8 standard distances, plus any custom distances.
- [ ] 6.6 PRs remain after setting Only You. Crowns and Top 10s are removed.
- [ ] 6.7 A segment row supports star, unhide, compare, goal and leaderboard.
- [ ] 7.1 Flyover auto-plays on open.
- [ ] 7.2 Play/pause, speed and the scrub bar all work, and tapping the map does not end playback.
- [ ] 7.3 Pinch zoom and rotate work, and Re-Center restores the default camera.
- [ ] 7.4 The ⋮ menu toggles the stats overlay. Segment flyovers show distance and elevation only.
- [ ] 7.6 Flyover length scales nonlinearly: 1 h ≈ 60 s and 12 h ≤ 6 min.
- [ ] 7.7 A notification fires when an off-screen flyover render finishes.
- [ ] 7.8 A segment effort map has a flyover play button at the bottom-right.
- [ ] 7.9 Shared flyover video excludes privacy zones and respects map visibility.
- [ ] 7.10 The matched activities bar is shown only when matches exist.
- [ ] 7.11 The matched list is grouped by year, scrolling highlights graph points, and only the owner can see it.
- [ ] 8.1 Kudos turns the icon orange, can be undone within 5 s, and tapping the count lists the people who gave kudos.
- [ ] 9.1 Edit Activity exposes all 12 field groups.
- [ ] 9.2 Six stats can be hidden from both Edit and Save.
- [ ] 9.3 Default hidden details apply to new uploads, and every stat can be bulk-hidden on past activities.
- [ ] 9.4 Correct Distance is in the mobile ⋮ menu, changes totals, and leaves segment times unchanged.

## Sources

- https://support.strava.com/en-us/articles/15401641-flyover
- https://dcrainmaker.com/2023/11/launches-activity-flyover.html
- https://support.strava.com/hc/en-us/articles/216918597 (matched activities)
- https://support.strava.com/hc/en-us/articles/216917067 (GAP)
- https://support.strava.com/hc/en-us/articles/216919567-Run-Activity-Pages
- https://support.strava.com/en-us/articles/15401883-run-activity-pages
- https://support.strava.com/hc/en-us/articles/216917137-Achievement-Awards-Glossary
- https://www.cyclingshorts.uk.com/?p=19159 (solid vs outlined icons)
- https://support.strava.com/hc/en-us/articles/216918397 (kudos)
- https://support.strava.com/hc/en-us/articles/216919487 (correct distance)
- https://support.strava.com/hc/en-us/articles/115001188684-Moving-Time-Speed-and-Pace-Calculations
- https://support.strava.com/hc/en-us/articles/115001294564-Elevation-on-Strava-FAQs
- https://support.strava.com/en-us/articles/15402159-how-does-strava-calculate-calories
- https://support.strava.com/hc/en-us/articles/360028654291-Hide-Details-from-your-Activities
- https://support.strava.com/hc/en-us/articles/115001136770-Mobile-Workout-Analysis-Premium-iOS
- https://bikerumor.com/strava-adds-new-mobile-relative-effort-training-metric-for-premium-members/
- https://support.strava.com/hc/en-us/articles/216917657-Strava-Subscription-Features
