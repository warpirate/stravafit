# Segments & Leaderboards: Minor-Details Catalog

Scope: segment page, leaderboard filters, starring, Local Legends, KOM/QOM/CR crowns, PR medals, effort history, and flagging.
Gating tags (`[FREE]` / `[SUB]`) show how **Strava** gates each item, for reference only. **In StravaFit every feature is free.**
Items not confirmed by a public source are marked *(unverified)*. Everything here is paraphrased from public sources. No APK inspection was done and no assets were copied.

Values in the "Our app" column: **keep** (match Strava), **improve** (match it and do better), **drop** (with the reason).

---

## 1. Segment detail page: header & stats

```
+--------------------------------------------+
| <-  Hawk Hill Climb            [*]  [...]  |   * = star toggle, ... = overflow
|     Sausalito, CA, USA                     |   city, state, country subtitle
|     Ride  |  Cat 3                         |
| 2.4 km   5.2% avg   11.0% max   124 m      |
| Elev 12 m -> 136 m                         |
| 48,210 attempts by 9,830 people            |
+--------------------------------------------+
```

| # | Detail | Behaviour | Strava | Our app |
|---|---|---|---|---|
| 1.1 | Location subtitle | City, state and country appear under the segment name, both on the page and in search results. | [FREE] | keep |
| 1.2 | Stored units | Distance and elevation are stored in metres, grades in percent. They are converted to the user's km/m or mi/ft preference at display time. | [FREE] | keep |
| 1.3 | Grade format | Grade is shown as a percentage with one decimal, e.g. `5.2%` *(decimal format unverified)*. | [FREE] | keep |
| 1.4 | Max grade and elevation range | Max grade, lowest elevation and highest elevation sit alongside distance and average grade *(exact labels unverified)*. | [FREE] | keep |
| 1.5 | Climb category formula | The score is length in metres times average grade in percent. Above 8,000 is Cat 4, above 16,000 Cat 3, above 32,000 Cat 2, above 64,000 Cat 1, and above 80,000 HC. The climb's position within a ride does not affect its category. | [FREE] | keep |
| 1.6 | Category enum | The stored integer runs 0–5, displayed as `NC, 4, 3, 2, 1, HC`. A higher integer means a harder climb. Store it the same way so min/max category filters work. | [FREE] | keep |
| 1.7 | Category badge placement | The badge appears on the segment page and on each row of an activity's segment list. Uncategorized segments (NC) get no badge. | [FREE] | keep |
| 1.8 | Attempts and athletes counts | Two separate totals: all efforts by everyone, and unique athletes, e.g. "N attempts by M people" *(wording unverified)*. These are not the viewer's personal count. | [FREE] | keep |
| 1.9 | Star count | The number of athletes who starred the segment is stored. Strava's mobile UI may not show it *(unverified)*. | [FREE] | improve: show it as a popularity hint |

## 2. Star segment

```
 [ ☆ ]  --tap-->  [ ★ ] (orange, instant)   "Segment starred"
```

| # | Detail | Behaviour | Strava | Our app |
|---|---|---|---|---|
| 2.1 | Star toggle | The star in the segment header is a single on/off toggle. | [FREE] | keep |
| 2.2 | Micro-interaction | The star fills in the accent colour immediately (optimistic update) and a brief confirmation appears. If the request fails, the change rolls back *(haptics and wording unverified)*. | [FREE] | improve: add the rollback and the error toast |
| 2.3 | Starred filter | The profile's Segments list can be filtered to starred segments only. | [FREE] *(VeloViewer lists starring around subscription features; unverified)* | keep |
| 2.4 | Paging | The starred list loads 30 per page. Private segments appear only for their owner. | [FREE] | keep |
| 2.5 | Device sync cap | Starred segments sync to Garmin Live Segments, capped at 100 on the device. Live segments are [SUB] at Strava. | [SUB] | improve: offer free GPX/FIT segment export. Device sync can come later. |

## 3. Your results / effort history

```
+-- Your results ---------------------------+
| PR   7:42   12 Mar 2026        > activity |
| 2nd  7:55    3 Jan 2026                   |
| 3rd  8:10   20 Nov 2025                   |
| Attempts: 23                    [Compare] |
+-------------------------------------------+
```

| # | Detail | Behaviour | Strava | Our app |
|---|---|---|---|---|
| 3.1 | PR block | Shows the PR time, the PR date (when that effort started) and the total attempt count. Tapping it opens the activity that holds the PR. | [FREE] | keep |
| 3.2 | Time format | Elapsed time in whole seconds, shown as `m:ss` or `h:mm:ss`. | [FREE] | keep |
| 3.3 | Elapsed, not moving time | Rankings and PRs use elapsed time, so a stop inside the segment counts against the athlete. | [FREE] | keep, and explain it in an info tooltip |
| 3.4 | Rest at endpoints | Time spent waiting or turning around at the start or end can be counted in the effort. Cropping the activity fixes it. | [FREE] | improve: flag efforts with a long zero-speed stretch at either endpoint |
| 3.5 | Effort metrics | Each effort can show average and max HR, average watts (measured or estimated), and average cadence. | [FREE] | keep, with an "est." label on estimated power |
| 3.6 | Compare efforts | Overlays two efforts: two athletes, or the latest effort against the PR. | [SUB] | keep (free) |
| 3.7 | Segment Intensity | Power on the segment as a percentage of the athlete's best power for the same duration in the last 6 weeks, e.g. 300 W against a 330 W best is about 90%. | [SUB] | keep (free) |
| 3.8 | Local date | Efforts store both UTC and local start times. The Today and This Year filters use the local date *(unverified)*. | [FREE] | keep: use the effort's local date |

## 4. Leaderboard & filters

```
[Overall][Women][Men][Following][Club v][Age][Weight]   [All-time v]
 1  Crown  A. Rider     6:58   312 W  171 bpm   2024-05-02
 2         B. Rider     7:01   ...
 ...                                         (Strava: free users stop at 10)
```

| # | Detail | Behaviour | Strava | Our app |
|---|---|---|---|---|
| 4.1 | Top-10 cap | Free Strava users see only the top 10 rows. The full board and all filters are subscriber-only. | [SUB] | improve: show the full paginated board to everyone |
| 4.2 | Time filters | On mobile: All-Time, This Year and Today. On the web, This Week and This Month are also available. | [SUB] | improve: offer every period on mobile |
| 4.3 | Gender boards | On mobile, the men's and women's boards are available only for All-Time and This Year. The web lets gender combine with any period. | [SUB] | improve: allow any combination |
| 4.4 | Age and weight boards | Show only the viewer's own bracket. Each PR is filed under the age and weight on the profile at recording time, so one athlete can appear on several brackets. | [SUB] | keep the snapshot behaviour |
| 4.5 | Following and club boards | A Following filter, plus one entry per club the viewer has joined. | [SUB] | keep (free) |
| 4.6 | Reroute workaround | When a trail is rerouted, old-route efforts stay on the board, and Strava advises using the date filter instead. | [SUB] | improve: add a "reset leaderboard after date" option for segment owners and moderators |
| 4.7 | Power and HR columns | Rows can show watts and HR. | [FREE] | keep |

## 5. Crowns, medals & trophies (activity segment list)

```
 Hawk Hill   Cat 3   7:42   [gold PR]   [cup #4]   [laurel LCL]   [crown KOM]
```

| # | Detail | Behaviour | Strava | Our app |
|---|---|---|---|---|
| 5.1 | PR medals | The athlete's fastest, 2nd and 3rd efforts on the segment get gold, silver and bronze medals. | [FREE] | keep, with our own icon art |
| 5.2 | Top-10 trophy | A cup appears only if the effort placed in the overall top 10 at upload *(current icon art unverified)*. | [FREE] | keep |
| 5.3 | Frozen ranks | The overall rank (top 10) and personal rank (top 3) are recorded only at upload time and are not updated when someone later beats them. | [FREE] | keep, and label it "at time of upload" |
| 5.4 | KOM/QOM/CR crown | The crown marks the fastest recorded time. CR (course record) is used for sports other than ride and run. | [FREE] | keep |
| 5.5 | My KOMs/QOMs/CRs list | A profile list of segments where the athlete currently holds the crown. | [FREE] | keep |

## 6. Local Legends

```
+-- Local Legend (last 90 days) ------------+
| [laurel]  J. Walker   41 efforts          |
| You: 12 efforts                      >    |   tap opens the comparison (Strava: SUB)
+-------------------------------------------+
```

| # | Detail | Behaviour | Strava | Our app |
|---|---|---|---|---|
| 6.1 | 90-day rolling count | The athlete with the most efforts in a rolling 90 days holds the title, regardless of pace. Efforts made before the segment was created don't count. | [FREE] | keep |
| 6.2 | Eligible sports | Run, ride, walk, hike, alpine/backcountry/nordic skiing and snowboard segments. Only activities visible to Everyone count. | [FREE] | keep |
| 6.3 | Laurel icon | A laurel icon, distinct from the crown, appears on the activity's segment row and on the segment page. | [FREE] | keep, with our own art |
| 6.4 | Comparison drill-down | Compares the viewer's effort count with the LCL, all efforts, women's efforts and mutual followers. | [SUB] | keep (free) |
| 6.5 | Local Legends list | Path: You > profile > Segments > Local Legends. Lists the segments where the athlete currently holds the title. | [FREE] | keep |
| 6.6 | Opt-out | "Leave Local Legends" can be set per segment, or globally with Privacy > Local Legends: No One. Nothing counts while opted out, and rejoining does not backfill. | [FREE] | keep |
| 6.7 | Virtual segments | Only selected Zwift segments have Local Legends. A segment without the module can't have it added. | [FREE] | drop: we have no virtual-platform segments yet. Revisit later. |

## 7. Segment lists (profile)

| # | Detail | Behaviour | Strava | Our app |
|---|---|---|---|---|
| 7.1 | Tabs | Starred, KOMs/QOMs/CRs, Local Legends and Created segments. | [FREE] | keep |
| 7.2 | Search limitation | New or unpopular segments may not appear in search. | [FREE] | improve: index every public segment |

## 8. Segment Explore (map)

```
 [Ride|Run] [Climb: flat/hilly/steep] [Length] [Surface]
 +-----------------------------+
 |  map with up to 10 lines    |  pan or zoom to load more
 +-----------------------------+
 | Hawk Hill  2.4 km 5.2% +124m |  card shows KOM 6:58, You 7:42
```

| # | Detail | Behaviour | Strava | Our app |
|---|---|---|---|---|
| 8.1 | Filters | Sport, length, surface and climb (flat, hilly, steep). | [FREE] | keep |
| 8.2 | 10-per-view limit | Each map viewport returns only the 10 most popular segments. | [FREE] | improve: cluster and show more |
| 8.3 | Explore card | Shows the description, the KOM time and the viewer's best time if they have matched the segment. | [FREE] | keep |
| 8.4 | Elevation figure | Explore uses the end-minus-start elevation difference, not total gain, so it can differ from the segment page. | [FREE] | improve: label it "net elev." |

## 9. Activity segment list: visibility & matching

| # | Detail | Behaviour | Strava | Our app |
|---|---|---|---|---|
| 9.1 | Hide a single effort | One effort can be hidden from an activity's list without hiding the whole activity. | [FREE] | keep |
| 9.2 | Unhiding is web-only | "Show hidden segments" exists only on the web. | [FREE] | improve: add it to mobile |
| 9.3 | Privacy-zone efforts | An effort that starts or ends inside a hidden map area is visible only on its own activity, with no leaderboard placement or achievements, until the map is revealed with Edit Map Visibility. | [FREE] | keep |
| 9.4 | "Don't see the segment?" link | Opens a match diagnostic that explains why a segment didn't match and can link to support *(Android presence unverified)*. | [FREE] | keep: show the match diagnostic in-app |

## 10. Flagging & hazards

| # | Detail | Behaviour | Strava | Our app |
|---|---|---|---|---|
| 10.1 | Report Segment | Appears in the overflow menu only if the user has matched the segment on their own activity *(Android path unverified)*. | [FREE] | keep |
| 10.2 | Hazardous state | A flagged segment hides its leaderboard behind a waiver, accepted once per segment. It awards no PRs or KOMs and can't have goals set on it. | [FREE] | keep |
| 10.3 | Activity flag | Flagging an activity (e.g. a drive logged as a run) removes all its segment results from leaderboards. This is separate from segment hazard flags. | [FREE] | keep |

## 11. Segment creation & privacy

| # | Detail | Behaviour | Strava | Our app |
|---|---|---|---|---|
| 11.1 | Private segments | Only the creator sees a private segment and it has no public leaderboard. | [FREE] | keep |
| 11.2 | Duplicate check | Similar existing segments are suggested during creation. A true duplicate can only be saved as private. | [FREE] | keep |
| 11.3 | Creation guidance | Segments under about 30 s are error-prone, as is 5–10 s recording. Tunnels over 500 m trip the GPS gap threshold, and very long segments fail on any deviation. | [FREE] | improve: show these as inline warnings while drawing |

---

## Acceptance checklist

- [ ] Segment header shows the name, a city/state/country subtitle, the sport and a category badge (none when NC).
- [ ] Distance and elevation display in the user's unit preference. Grades show one decimal followed by %.
- [ ] Stats row includes average grade, max grade, lowest and highest elevation, and elevation gain.
- [ ] Climb category is computed as length(m) × avg grade(%) with thresholds 8k/16k/32k/64k/80k giving Cat 4/3/2/1/HC, stored as an integer 0–5.
- [ ] Segment page shows total attempts and unique athletes, separate from the user's own count.
- [ ] Star toggle updates instantly, rolls back on failure, and the starred list pages 30 at a time.
- [ ] The Segments list can be filtered to starred only.
- [ ] "Your results" shows PR time, PR date, attempt count and a link to the PR activity, plus 2nd and 3rd.
- [ ] Rankings and PRs use elapsed time, formatted m:ss or h:mm:ss.
- [ ] Effort detail shows avg/max HR, avg power (with an "est." label when estimated) and cadence when available.
- [ ] Compare overlays any two efforts (two athletes, or latest vs PR) for every user.
- [ ] Segment Intensity % = effort power ÷ best same-duration power in the last 6 weeks.
- [ ] Leaderboard shows all rows, paginated, to every user.
- [ ] Filters work in any combination: Overall/Women/Men × Today/Week/Month/Year/All-time, plus Following, each joined club, own age bracket and own weight bracket.
- [ ] Age and weight bracket placement uses the profile values at the time the activity was recorded.
- [ ] Date filters use the effort's local date.
- [ ] Activity segment rows show gold/silver/bronze PR medals, a top-10 trophy, a KOM/QOM/CR crown and an LCL laurel, each as a distinct icon.
- [ ] Trophy and medal ranks are frozen at upload and never recomputed.
- [ ] Local Legend = most efforts in a rolling 90 days, counting only Everyone-visible activities in eligible sports and only efforts made after the segment was created.
- [ ] LCL comparison (you, LCL, overall, women, mutuals) is available to all users.
- [ ] Profile has Starred, KOMs/QOMs/CRs, Local Legends and Created segment lists.
- [ ] LCL opt-out works per segment and globally, and rejoining does not backfill.
- [ ] Explore filters by sport, climb type, length and surface. The card shows the KOM time and the user's best.
- [ ] Explore labels its elevation figure "net elev."
- [ ] A single effort can be hidden from, and unhidden on, mobile.
- [ ] Efforts starting or ending in a privacy zone stay off leaderboards and achievements until the map is revealed.
- [ ] A "Don't see the segment?" diagnostic explains failed matches.
- [ ] Report Segment appears only for users who have matched the segment.
- [ ] A hazardous segment hides its leaderboard behind a per-segment waiver, awards no PR or KOM, and blocks goals.
- [ ] Flagging an activity removes all its efforts from leaderboards.
- [ ] Private segments are visible only to their creator. A duplicate segment can be saved only as private, after similar segments are suggested.
- [ ] The creation flow warns about short segments (under ~30 s), long tunnels (over 500 m) and very long segments.

## Sources

- https://support.strava.com/hc/en-us/articles/360043099552-Local-Legends
- https://support.strava.com/en-us/articles/15401771-how-do-i-filter-segment-leaderboards-on-strava
- https://support.strava.com/sv/articles/15402025-hur-rapporterar-jag-ett-segment-som-farligt-pa-strava
- https://support.strava.com/hc/en-us/articles/216918187-Segments
- https://support.strava.com/hc/en-us/articles/216918147-Segment-Leaderboard-Guidelines
- https://support.strava.com/hc/en-us/articles/216918227-Segment-Achievements-PRs-KOMs
- https://support.strava.com/hc/en-us/articles/216917057-Climb-Categorization
- https://support.strava.com/hc/en-us/articles/216917147-Segments
- https://support.strava.com/en-us/articles/15402044-strava-subscription-features
- https://developers.strava.com/docs/reference/
- https://blog.veloviewer.com/?p=1973
- https://hungerfordhares.co.uk/?p=1012
