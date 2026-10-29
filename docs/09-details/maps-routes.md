# Maps & Routes: Minor-Details Catalog

Area: `maps-routes` (Maps tab, heatmap toggles, route builder gestures, route detail, saved routes, offline maps, segment explore, map style picker, POIs, search).

**Legend**
- **Strava gating:** `[FREE]` / `[SUB]`. This is for reference only. StravaFit has no paywall, so every feature is free.
- **Our app:** `keep` (match Strava), `improve` (match it and do better), or `drop` (with a reason).
- `(unverified)` marks a detail that public sources don't firmly confirm for the current Android app.

Everything here is paraphrased from public sources (see Sources). No APKs were decompiled and no assets were copied.

> Note: the user asked for a faster run, so this pass merged the raw findings and judged them against the sources already cited. It did not do fresh web spot-checks. Low-confidence items from DC Rainmaker's 2020 review are kept but marked `(unverified)`.

---

## 1. Maps tab (home of maps & routes)

```
+--------------------------------------+
| [Map-only]        [Search........] [Layers]|
|                                      |
|          (map + heatmap)             |
|     * POI   * POI                    |
|                                      |
| [Layers/Photos]          [3D]        |
| +----------------------------------+ |
| | route card  | route card | ...   | |  <- suggested routes carousel
| +----------------------------------+ |
|                      [+ Create Route]|  <- FAB lower-right
+--------------------------------------+
```

| # | Detail | Behaviour | Strava | Our app |
|---|---|---|---|---|
| M1 | Create Route FAB | A "Create Route" button sits in the lower right of the Maps tab and opens the route builder. | [SUB] | keep |
| M2 | Map-only mode toggle | A top-left control hides route suggestions so you can browse the plain map and heatmap for a chosen sport. This is the only way to see winter-sport heat. | [FREE] | keep |
| M3 | Winter sports have no suggestions | Winter sports get no suggested routes. You view their heatmap via map-only mode and the sport switch, and build winter routes with Create Route. | [SUB] | improve: offer suggestions for Nordic/backcountry where OSM piste/trail data exists |
| M4 | Suggested routes carousel cards | Cards show photos and stats from athletes who have done the route. | [SUB] | keep, using our own users' photos/stats (opt-in) |
| M5 | Suggested route distance filter | Target distance in km or mi, roughly 1–100 mi, with running presets. Results land near the target, not on it (a 30 mi request returned 31.9–36 mi). (unverified, 2020) | [SUB] | improve: show the actual distance next to the target and allow a ±% tolerance |
| M6 | Three suggestions per query | The generator returns 3 options per query, labelled as data-generated. (unverified, 2020) | [SUB] | improve: 3 by default plus "show more" |
| M7 | Route feedback entry | Suggested-route detail has a feedback/rating entry. (unverified, 2020) | [SUB] | keep (thumbs up/down plus a reason) |
| M8 | OSM basemap with delayed refresh | Map and routing data come from OpenStreetMap. OSM edits only show after Strava's periodic refresh. | [FREE] | improve: publish our tile/graph refresh date in Settings > About maps |

## 2. Search

| # | Detail | Behaviour | Strava | Our app |
|---|---|---|---|---|
| S1 | Natural-language search | The Maps search bar accepts plain-language queries ("bike path near a lake") and returns routes and POIs. Exact keywords are not needed. | [SUB] | keep. Start with keyword plus place geocoding, then add NL parsing for surface, water, distance and so on. |
| S2 | Results mix routes + POIs | Results include both route cards and points of interest. | [SUB] | keep, with tabs for Routes / Places / Segments |

## 3. Heatmap & layers

```
Layers sheet
+------------------------------+
| Map style: (Standard)(Satellite)(Winter)(Dark) |
| Heatmap:   [Global] [Personal]               |
|   Type:    (All) (Night) (Weekly)            |
|   Sport:   [Run][Ride][Winter][Water] multi  |
|   Colour:  o o o o o o  (6 swatches)         |
|   Period:  [All time v] / [2024 v]           |
|   [ ] Activity clusters (dots)               |
| [ ] 3D terrain   [ ] Community photos  [ ] Segments |
+------------------------------+
```

| # | Detail | Behaviour | Strava | Our app |
|---|---|---|---|---|
| H1 | Heat colour picker | Six colours: orange, red, blue, blue-red, purple, grey. The original was yellow to red. | [SUB] | keep, plus a colour-blind-safe preset |
| H2 | Personal heatmap time filter | Shows all time or a single year. | [SUB] | improve: allow any date range, not just a single year |
| H3 | Activity clusters toggle | Swaps the heat lines for density dots by region. Off by default. | [SUB] | keep (off by default) |
| H4 | Base maps under heat | Standard dark/black, winter, and satellite. | [SUB] | keep. Satellite depends on tile licensing; use free imagery or drop it if none is available. |
| H5 | Sport filters include winter & water | Filter by one sport type or several at once. | [SUB] | keep (multi-select) |
| H6 | Night heatmap | Activity between sunset and sunrise only. | [SUB] | keep |
| H7 | Weekly heatmap | Activity from the last 7 days only. | [SUB] | keep |
| H8 | Community Photos layer | A layers-button toggle shows photo pins, and you can route to where a photo was taken. Documented for web; mobile unverified. | [SUB] | keep, with privacy: only photos from public activities, and start/end zones stripped |
| H9 | 3D terrain layer | Toggle in the top right. Works with standard or satellite style and labels summit heights. Reachable from the Maps tab, the Record map and any activity map. Two-finger tilt (unverified). | [SUB] | keep |
| H10 | Flyover replay | On an activity map, a "Flyover" button plays an animated 3D fly-through of the route. | [SUB] | keep (later phase) |

## 4. POIs

| # | Detail | Behaviour | Strava | Our app |
|---|---|---|---|---|
| P1 | POI categories | Peaks, landmarks, bike shops, cafes, popular start points, photo spots, water refills, snacks and toilets. Built from heatmap data plus OSM. | [FREE] | keep |
| P2 | No user-added POIs | Users cannot add their own POIs. | [FREE] | improve: let users suggest POIs, then moderate them or push them upstream to OSM |

## 5. Route builder: entry & gestures

```
+--------------------------------------+
| [x]   Ride v                     [...]|  <- top overflow: Manual Mode
|                                      |
|     o----auto-routed----o            |
|                          \           |
|                           o (tap pt -> Move/Delete)
|  [pencil][sketch-undo]               |
|                                      |
| 12.4 km   +180 m   0:42   (totals)   |  <- (unverified)
| [...]                       [undo <-]|  <- lower-left: Reverse/Delete route
+--------------------------------------+
```

| # | Detail | Behaviour | Strava | Our app |
|---|---|---|---|---|
| R1 | Sport confirm first | After Create Route, the builder asks you to confirm the sport. Your first map tap then sets the start. | [SUB] | keep (pre-select the last-used sport) |
| R2 | Mobile sport list (9) | Run, Trail Run, Walk, Hike, Ride, Mountain Bike Ride, Gravel Ride, Nordic Ski and Backcountry Ski. Web supports every sport. | [SUB] | improve: support all sports on mobile |
| R3 | Tap to add, auto-route | Each tap adds a point, joined to the previous one along popular community paths. | [SUB] | keep (routing engine weighted by heat) |
| R4 | Spacing controls auto-routing | Points far apart let the router fill a long gap. Points close together give tighter control. | [SUB] | keep. Add an onboarding tip. |
| R5 | Tap polyline > Add Point | Tapping the line shows an "Add Point" action that inserts a mid-route point. | [SUB] | improve: also support long-press-drag on the line to insert and move in one gesture |
| R6 | Point menu: Move / Delete | Tapping a waypoint shows Move Point or Delete. Move is two taps (choose Move, then tap the new spot) with no dragging. | [SUB] | improve: allow direct drag as well as the two-tap move |
| R7 | Delete reroutes neighbours | Deleting a middle point recalculates the segments either side, which can change the path. | [SUB] | keep. Briefly preview the new path before committing. |
| R8 | Undo (lower right) | Removes only the most recently added point, one per tap. | [SUB] | improve: full undo/redo stack covering moves and deletes too |
| R9 | Pencil draw, snap-to-trail | Tap the pencil and draw with your finger. On lift the stroke snaps to nearby roads or trails. | [SUB] | keep |
| R10 | Sport-aware snapping | Road Ride sketches snap to paved roads. Trail sports prefer unpaved paths. | [SUB] | keep |
| R11 | Stroke ends on finger lift | Lifting your finger ends draw mode, so you tap the pencil again for each stroke. | [SUB] | improve: "sticky" draw mode that stays on until toggled off |
| R12 | Sketch undo beside pencil | A separate undo next to the pencil removes the whole last stroke. Help copy warns it can remove a big section. | [SUB] | improve: per-segment undo inside a stroke |
| R13 | Manual Mode toggle | In the overflow (…) menu. Taps join with straight lines and drawing stays raw, with no snapping or routing. For off-network paths. | [SUB] | keep. Show a visible "Manual" chip while it's on. |
| R14 | Lower-left whole-route menu | A second ellipsis holds Reverse route and Delete route. | [SUB] | keep, plus "Out-and-back" and "Close loop to start" |
| R15 | Live totals bar | Running totals for distance, elevation gain/loss and estimated time update with each point. (unverified) | [SUB] | keep |
| R16 | Routing preferences | Popular vs direct, minimise vs maximise elevation, paved vs dirt. Documented for web; mobile unverified. | [SUB] | keep on mobile |

## 6. Save sheet

```
+------------------------------+
| Name: [Morning loop        ] |
| Download for offline use [ON]|
| Privacy: (o) Everyone        |
|          ( ) Only You        |
|              [Confirm]       |
+------------------------------+
```

| # | Detail | Behaviour | Strava | Our app |
|---|---|---|---|---|
| V1 | Save fields | Name, a "Download for offline use" toggle, and privacy. | [SUB] | keep |
| V2 | Offline toggle defaults ON | Confirm starts the download unless the user switches it off first. | [SUB] | keep. Show the estimated size next to the toggle. |
| V3 | Privacy labels | "Everyone" means listed under the profile's Routes and shareable. "Only You" means hidden and not shareable, even by the owner. | [SUB] | improve: add a "Followers" level and let owners share private routes by link |
| V4 | Saved routes starred / synced | Saving stars the route by default, which syncs it to connected head units. (unverified, 2020) | [SUB] | keep: export to GPX/FIT and sync to devices via open APIs where possible |

## 7. Route detail & saved routes

| # | Detail | Behaviour | Strava | Our app |
|---|---|---|---|---|
| D1 | Profile > Routes list | Every saved route appears under Profile > Routes, whatever its privacy or offline state. | [FREE] | keep, plus sort/filter by sport, distance and offline |
| D2 | Editable after save | A saved route reopens in the builder for editing points, sketching, reversing or deleting. | [SUB] | keep. Add "Save as copy". |
| D3 | Estimated moving time | Based on your average pace/speed over the last 4 weeks. Shared routes recalculate it with the viewer's own stats. | [SUB] | keep |
| D4 | Surface breakdown bar | A surface-type bar sits along the elevation profile, with unknown surfaces in white or neutral. (unverified, 2020) | [SUB] | keep |
| D5 | Offline toggle on detail | The same "Download for offline use" toggle lives here. Switching it off deletes the download. There is no separate downloads manager. | [SUB] | improve: add Settings > Offline storage listing each download with its size and a delete-all option |
| D6 | Web-saved routes not auto-downloaded | You must enable the toggle in the app while online. | [SUB] | improve: optional "auto-download starred routes on Wi-Fi" |

## 8. Offline

```
Home (no signal)
+------------------------------+
| Navigate offline             |
|  Morning loop   12 km  [>]   |
|  Hill reps       6 km  [>]   |
| Record offline               |
|  Walk [>]   Ride [>]         |
+------------------------------+
```

| # | Detail | Behaviour | Strava | Our app |
|---|---|---|---|---|
| O1 | Offline home sections | With no signal, Home shows "Navigate offline" (downloaded routes, subscribers only) and "Record offline" (a play button per sport, for everyone). | [SUB]/[FREE] | keep (both for everyone) |
| O2 | Start opens route, not recording | Start on an offline route opens it on the record screen. Recording begins only on a second Start tap. | [SUB] | keep |
| O3 | Download size ~10 MB | Most offline routes take about 10 MB. Cycling routes can be larger because they cover more area. | [SUB] | keep. Show the size before downloading. |
| O4 | Downloads survive lapse | Downloads keep working after a subscription ends. Not available on watches or connected devices. | [SUB] | improve: no lapse applies to us. Plan watch offline maps for later. |
| O5 | Auto upload, no manual sync | Offline activities upload when data returns. There is no manual sync if an upload fails. | [FREE] | improve: add a visible upload queue with Retry |
| O6 | Offline region maps | Strava only offers per-route offline maps. | n/a | improve: let users download an area (bounding box) too |

## 9. Following a route

| # | Detail | Behaviour | Strava | Our app |
|---|---|---|---|---|
| F1 | Off-route alert | The route shows on the record map with an alert when you leave it. Spoken turn-by-turn for run, walk and hike was announced as "coming soon". | [SUB] | improve: off-route alert plus voice turn cues from day one |

## 10. Segment explore

| # | Detail | Behaviour | Strava | Our app |
|---|---|---|---|---|
| G1 | Segments in visible area | Lists the segments within the visible map bounds. The public API returns up to 10 per bounds query. (unverified for the in-app UI) | [FREE] | improve: paginate past 10 and cluster pins when zoomed out |
| G2 | Climb-category filter | Switch between all segments and climbs by category. The API exposes min/max climb category. (unverified UI) | [FREE] | keep, plus filters for sport and distance |

### Dropped / merged
- Duplicate findings were merged: sport confirm, spacing, stroke-on-lift, sketch undo, delete reroute, privacy labels and the sport list each appeared twice.
- Nothing was dropped as wrong. The 2020-era items (M5, M6, M7, V4, D4) are kept but marked unverified.

---

## Acceptance checklist

**Maps tab & search**
- [ ] M1: A "Create Route" FAB is visible in the lower right of the Maps tab and opens the builder.
- [ ] M2: A top-left map-only toggle hides suggestion cards and leaves the heatmap visible.
- [ ] M3: Choosing a winter sport shows its heatmap, and suggestions appear where data exists.
- [ ] M4: Suggested route cards show a photo, distance, elevation and completion stats.
- [ ] M5: The distance filter accepts km or mi, and each result shows its actual distance next to the target.
- [ ] M6: A query returns at least 3 suggestions, and "show more" loads more.
- [ ] M7: Route detail has a feedback control that records a rating.
- [ ] M8: Settings shows the date of the last map data refresh.
- [ ] S1: Searching "bike path near a lake" returns relevant routes or POIs.
- [ ] S2: Search results are split into Routes / Places / Segments tabs.

**Heatmap & layers**
- [ ] H1: The heat colour can be set to each of the 6 colours plus a colour-blind preset, and the choice persists.
- [ ] H2: The personal heatmap can be filtered to all time, a single year or a custom date range.
- [ ] H3: The clusters toggle is off by default and shows density dots when on.
- [ ] H4: The base map can switch between standard, dark, winter and (if licensed) satellite.
- [ ] H5: Several sports, including winter and water, can be selected at once.
- [ ] H6: The Night heatmap shows only activity between sunset and sunrise.
- [ ] H7: The Weekly heatmap shows only the last 7 days.
- [ ] H8: The photos layer shows pins from public activities only, and "Route here" builds a route to a pin.
- [ ] H9: 3D terrain toggles from the Maps tab, the Record map and the activity map, and two-finger tilt works.
- [ ] H10: The activity map has a Flyover button that plays a 3D fly-through.

**POIs**
- [ ] P1: All 9 POI categories render on the base map.
- [ ] P2: Users can submit a POI suggestion, which goes into a moderation queue.

**Route builder**
- [ ] R1: The builder asks for the sport first, pre-selects the last one used, and the first tap sets the start.
- [ ] R2: Every app sport type can be chosen for a mobile route.
- [ ] R3: Tapping two distant points auto-routes along popular paths.
- [ ] R4: A tip explains that wider spacing gives more auto-routing.
- [ ] R5: Tapping the polyline offers "Add Point", and long-press-drag on the line inserts and moves a point.
- [ ] R6: A waypoint can be moved either by dragging or by Move then tap.
- [ ] R7: Deleting a middle point reroutes its neighbours and previews the change.
- [ ] R8: Undo/redo reverses adds, moves and deletes one step at a time.
- [ ] R9: A freehand stroke snaps to roads or trails on finger lift.
- [ ] R10: Road Ride strokes snap only to paved ways, and trail sports prefer unpaved.
- [ ] R11: Sticky draw mode stays on across strokes until toggled off.
- [ ] R12: Sketch undo can remove part of a stroke, not just the whole stroke.
- [ ] R13: With Manual Mode on, points join with straight lines, and a "Manual" chip is visible.
- [ ] R14: The whole-route menu offers Reverse, Delete, Out-and-back and Close loop.
- [ ] R15: Distance, gain/loss and estimated time update within 1 s of each edit.
- [ ] R16: Popular/direct, elevation and surface preferences change the generated route.

**Save, detail, saved routes**
- [ ] V1: The save sheet has name, offline toggle and privacy fields.
- [ ] V2: The offline toggle defaults ON and shows the estimated size.
- [ ] V3: Privacy offers Everyone / Followers / Only You, and an owner can share a private route by link.
- [ ] V4: A saved route can be exported as GPX/FIT and synced to a connected device.
- [ ] D1: Profile > Routes lists every saved route and filters by sport, distance and offline.
- [ ] D2: A saved route opens in the builder, and "Save as copy" makes a new route.
- [ ] D3: Estimated time uses the viewer's last 4 weeks of pace.
- [ ] D4: Route detail shows a surface breakdown with unknown surfaces in a neutral colour.
- [ ] D5: Settings > Offline storage lists downloads with sizes and can delete each one or all.
- [ ] D6: With auto-download on, starred routes download over Wi-Fi.

**Offline & navigation**
- [ ] O1: With no network, Home shows "Navigate offline" and "Record offline" for every user.
- [ ] O2: Start on an offline route opens the record screen without starting the recording.
- [ ] O3: The download size is shown before the download starts.
- [ ] O4: Downloaded routes work fully in airplane mode.
- [ ] O5: Failed uploads appear in a queue with a Retry button.
- [ ] O6: A user can download a map area chosen by bounding box.
- [ ] F1: Leaving a followed route by more than a threshold triggers an alert, and voice turn cues play.

**Segment explore**
- [ ] G1: All segments in view load, past 10, with pagination, and pins cluster when zoomed out.
- [ ] G2: The segments layer filters by climb category, sport and distance.

---

## Sources
- Strava Support: Creating Routes on Mobile. https://support.strava.com/hc/en-us/articles/18001474720397-Creating-Routes-on-Mobile
- Strava Support: Routes on Strava. https://support.strava.com/hc/en-us/articles/216918387-Routes-on-Strava
- Strava Support: How do I use Strava offline. https://support.strava.com/en-us/articles/17699138-how-do-i-use-strava-offline
- Canadian Running: heatmap updates. https://runningmagazine.ca/?p=77597
- Endurance Business: Night and Weekly heatmaps. https://endurance.biz/2024/industry-news/strava-expands-mapping-tools-with-night-and-weekly-heatmaps/
- Android Authority: natural-language search. https://www.androidauthority.com/strava-update-natural-language-search-3727411/
- Marathon Handbook: route search & audio directions. https://marathonhandbook.com/strava-route-search-audio-directions/
- BikeRadar: Strava points of interest. https://www.bikeradar.com/news/strava-points-of-interest
- BikeRadar: Strava 3D maps. https://www.bikeradar.com/news/strava-3d-maps
- AlternativeTo: Flyover launch. https://alternativeto.net/news/2023/11/strava-launches-new-3d-video-map-feature-flyover-for-premium-subscribers
- TechRadar: How to create a Strava route. https://www.techradar.com/how-to/how-to-create-a-strava-route
- DC Rainmaker: Strava route suggestions (2020). https://dcrainmaker.com/2020/03/strava-significant-feature.html
- stravalib docs: explore_segments (API). https://stravalib.readthedocs.io/en/latest/reference/api/stravalib.client.Client.explore_segments.html
