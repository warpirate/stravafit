# Design direction: "strava" (ADR-014)

StravaFit is distributed through GitHub releases only. Under ADR-014 the owner wants it to look and feel as close to the Strava Android app as practical. This folder holds the reference mockups and the token spec for that look.

Hard limits we kept:
- No Strava logo, wordmark or image assets. Every icon, avatar, photo and map here is our own SVG.
- The social action is called **High-five**, never "kudos".
- No wordmark appears on these screens. The top bars use plain screen titles, as Strava's own tabs do.

## Files

| Screen | Light | Dark |
|---|---|---|
| Home feed | `home-light.html/.png` | `home-dark.html/.png` |
| Maps tab | `maps-light.html/.png` | `maps-dark.html/.png` |
| Record, before start | `record-light.html/.png` | `record-dark.html/.png` |
| Record, recording in progress | `record-live-light.html/.png` | `record-live-dark.html/.png` |
| Record, paused (Resume / Finish) | `record-paused-light.html/.png` | `record-paused-dark.html/.png` |
| Activity detail | `activity-light.html/.png` | `activity-dark.html/.png` |
| You → Progress | `you-light.html/.png` | `you-dark.html/.png` |

- Each HTML file is self-contained: inline CSS and SVG, plus one Google Fonts link. It is a 390×844 frame with an Android status bar and a gesture bar. The PNGs are rendered at 2× (780×1688).
- Every file opens with the same `DESIGN SPEC` comment block.
- To regenerate, run `python _build/gen.py`, then `sh _build/render.sh [names…]`. The render script uses headless Edge.
- The maps come from two hand-laid "worlds" in km coordinates, Pune and Bengaluru, rendered at a different zoom per screen. Each world has a river or lake, parks, a rail line, POIs, named arterials and secondary roads. Its neighbourhoods each get their own street grid, at its own angle and block size. Grids are sparse and straight, with long runs, T-junctions and empty landuse patches. Light and dark versions of a screen use the same geometry.
- Avatars come from `_build/avatars.py`, which `gen.py` imports; the output HTML stays self-contained.
- `_build/gen_v1.py` is the round-2 generator and `_build/gen_v2.py` the round-3 one, kept for reference.

## Research basis

- Strava reorganised its bottom navigation into **Home · Maps · Record · Groups · You** in 2023. It shipped dark mode on Android and iOS in mid-2024, with Always dark, Always light and Follow device options. Dark mode covers map buttons, progress and stats pages, and recording.
- In July 2025 Strava redesigned Record, Android first. Live stats now sit over the map, so there is no hard toggle between map and stats. Pace is more prominent for runs. Sport and route choice are more prominent on the start screen. The maps use a new dark rendering engine.
- Layout patterns also come from our teardown docs: `02-navigation/information-architecture.md`, `03-wireframes/core-screens.md`, `05-design-system/design-system-observed.md` and `09-details/{home-feed,record,activity-detail,maps-routes,profile-progress}.md`.
- **Typeface:** Strava's UI typeface is a custom humanist grotesque. Third-party style references call it "Boathouse", but that is unverified, because no official source was found. Older material points to a Maison Neue-style grotesk.
  - We use **Figtree** (OFL) as the closest open match. It has the same friendly round bowls, a tight grotesque rhythm, and heavy weights for numbers.
  - We tested it in headless Edge, and its `tnum` tabular figures work (`1111` and `0000` are the same width).
  - Hanken Grotesk and Archivo also have tabular figures. They were the runners-up: Hanken looks colder and Archivo looks more condensed.

Sources: [Strava app redesign (the5krunner)](https://the5krunner.com/2025/07/16/strava-app-redesign/) · [BikeRadar: Strava redesigned record feature](https://www.bikeradar.com/news/strava-redesigned-record-feature) · [BikeRadar: new layout / You tab](https://www.bikeradar.com/news/strava-beta-test-layout/) · [Advnture: Strava dark mode](https://www.advnture.com/news/strava-dark-mode) · [PhoneArena: dark mode](https://www.phonearena.com/news/strava-dark-mode-android-ios_id159838) · [Velo: Strava overhauls mobile app look and feel](https://velo.outsideonline.com/road/road-racing/strava-overhauls-mobile-app-look-and-feel/?scope=anon) · [Advnture: Strava updates Record (2025)](https://www.advnture.com/tech/apps-software/no-more-switching-between-screens-strava-updates-record-function-for-a-smoother-and-smarter-experience)

## Palette

### Light theme

| Token | Hex | Role |
|---|---|---|
| `bg` / `surface` | `#FFFFFF` | Screen, cards, sheets, nav |
| `gap` | `#EEEEF0` | Thin grey gutter between edge-to-edge feed cards and You blocks |
| `surface-2` | `#F5F5F7` | Sunken wells (rarely used) |
| `text` | `#242428` | Titles, stat values (15.5:1) |
| `text-2` | `#45454E` | Body and description (9.5:1) |
| `muted` | `#6D6D78` | Stat labels, meta, timestamps, inactive nav (5.1:1 on white). Never placed on `gap`, where it drops to 4.4:1 |
| `hair` | `#E5E5EA` | Dividers, stat-row separators |
| `hair-strong` | `#CBCBD3` | Outlined round buttons, inactive sport circles, sheet handle |
| `accent` | `#FC5200` | Strava's current orange, from its developer guidelines; see `05-design-system/design-system-observed.md` line 60. Used for fills, icons, the route line, the record button, the active tab underline, chart line and area. White on it is 3.3:1, which passes for non-text UI and large text |
| `accent-press` | `#E04A00` | Pressed state |
| `accent-text` | `#CC3D00` | Small orange text such as "See all", the active nav label and links (5.0:1) |
| `on-accent` | `#FFFFFF` | Labels on orange, used only at ≥19px/800 (large text, 3.3:1: Start, Finish) or as icons |
| `ok` | `#16884A` | GPS ready dot, success |
| `gold` / `silver` / `bronze` | `#D6A01E` / `#9DA5AF` / `#C98A55` | Ribbon medal glyphs. The numeral is `on-medal` `#242428` on every metal: 6.6, 6.2 and 5.3:1 |
| `hr` | `#E0245E` | Heart-rate glyph |
| `bar` / `bar-fast` | `#C3CCD8` / `#FC5200` | Split pace bars; the fastest split is orange |
| `chart-line` / `chart-elev` | `#3C78D8` / `#E3E3E8` | Pace line over the elevation area |
| `float` | `#FFFFFF` | Round buttons, chips and the search pill over maps, with `shadow` |
| `loc` | `#2F74F0` | Live location dot and GPS halo |

### Dark theme

| Token | Hex | Role |
|---|---|---|
| `bg` | `#121214` | Screen behind app bars |
| `gap` | `#000000` | True-black gutters between cards, giving the rich layered look |
| `surface` | `#1C1C1F` | Cards, nav, sheets, panels, and the whole You header block (status bar, top bar and tabs share one colour) |
| `surface-2` | `#242428` | Sunken wells |
| `raised` / `float` | `#2A2A2F` | Buttons and chips over maps, dialogs |
| `text` | `#F2F2F4` | 15.9:1 on surface |
| `text-2` | `#D2D2D8` | 11.8:1 |
| `muted` | `#A3A3AD` | 6.9:1 on surface, 5.9:1 on raised |
| `hair` / `hair-strong` | `#2E2E34` / `#43434B` | Dividers and outlines |
| `accent` | `#FC5200` | Same hue as light. 5.3:1 on surface, so it is AA even as text |
| `accent-text` | `#FF6A2B` | Small orange text (6.2:1) |
| `ok` | `#3CC47A` | GPS ready |
| `gold` / `silver` / `bronze` | `#E7B43A` / `#B4BBC4` / `#C98A55` | Medals, with `#242428` numerals |
| `hr` | `#FF5C8A` | Heart rate |
| `bar` | `#424B59` | Split bars |
| `chart-line` / `chart-elev` | `#6FA2F5` / `#2C2C33` | Analysis chart |
| `loc` | `#4C8DFF` | Location dot |

### Map styles

| Token | Light | Dark | Role |
|---|---|---|---|
| `m-land` | `#F2F1EC` | `#1A1C20` | Base land |
| `m-built` | `#ECEAE3` | `#1D2025` | Built-up neighbourhood landuse, under the street grid. Empty landuse (cantonment, rail yard, palace grounds, parade ground) is cut back to `m-land` with straight edges |
| `m-park` | `#CFE5BF` | `#18291E` | Parks; footpaths dashed in `m-path` (`#AFCB9A` / `#2C4434`) |
| `m-water` | `#A7D0EA` | `#11314A` | River and lakes |
| `m-road` / `m-case` | `#FFFFFF` / `#E2DED5` | `#2B2F36` / `#1A1C20` | Minor streets, 0.55–2.6px by zoom, cased only above 1.3px. They are sparse: straight runs of 3–8 blocks with T-junctions and whole streets missing, block spacing 1.8× at feed and detail zoom and 1.3× at Maps and Record zoom, no cul-de-sac stubs. Secondary roads use the same colours at 1.2–4.6px with casing |
| `m-major` / `m-major-case` | `#FFFFFF` / `#C7C0B2` | `#3A3F48` / `#262A30` | Main roads |
| `m-art` / `m-art-case` | `#FCE7B0` / `#DDBA6E` | `#4B4535` / `#2F2B22` | Arterials (Nagar Rd, Station Rd, Hosur Rd, MG Road, Residency Rd). Arterials and main roads always run off the frame edge |
| `m-rail` | `#B4B0A6` | `#454A53` | Rail line with tie ticks |
| `m-poi` | `#8A8790` | `#7D828C` | POI dot with a halo ring |
| `m-label` | `#5A5B61` | `#A2A6B0` | Area labels (uppercase, letter-spaced) and road labels. Every label gets an `m-halo` stroke |
| `m-water-label` / `m-park-label` | `#33689A` / `#3B6A2E` | `#78A6CF` / `#86B48A` | Italic water labels, green park labels |
| `route-case` | `#FFFFFF` | `#121214` | Casing under the orange route |
| `heat` / `heat-core` | `#EE3F0A` / `#C42600` | `#FF6A12` / `#FFC46B` | Heatmap. Hot corridors are slices of drawn roads, sampled from the same curve the road is drawn with, so the heat sits on the road centreline. They get a glow and a core line. Faint heat is whole minor-street runs of 3+ blocks near the focus, 1px at `heat-faint` (0.24 light, 0.30 dark), drawn on the street layer under water and parks, so it never crosses a river except on a bridge |

Labels are drawn above the route with a halo. Each label is checked against the frame edge, the floating UI (each screen passes avoid-rects for pills, buttons and panels), other labels and the route line. If it collides, it tries offsets and is dropped when none fit, so no label renders partly clipped or cut by the track.

Route markers: start is a green dot (`#1FA45B`) with a white ring. Finish is a checkered dot. A top scrim (map land fading out over 96px) keeps the status bar readable over maps. Labels keep 14px from the frame's side edges. Maps end on a hard edge plus a hairline; there are no fades into the surface.

## Typography

- **Figtree** in weights 400, 500, 600, 700 and 800, loaded from Google Fonts.
- Every number uses `font-feature-settings: "tnum"`, so live stats don't jitter.

| Style | Size / line height | Weight | Used for |
|---|---|---|---|
| display | 84 / 1.0, −2.5 tracking | 800 | Live Time on the Record screen (the only 800 numeral) |
| stat-xl | 52 / 1.05, −1 tracking | 700 | Live Split Avg and Distance |
| stat-l | 26 / 1.15, −0.02em | 700 | You → This week totals |
| title-xl | 22 / 1.2 | 800 | Activity title |
| top-bar title | 21 / 1.2 | 700 | Screen titles ("Home", "You") |
| title-l | 19 / 1.25 | 700 | Feed card title, section headers ("Splits", "Suggested routes") |
| stat-m | 20 / 1.2, −0.02em | 700 | Feed and detail stat values. The tighter tracking narrows Figtree's round 1, 2 and 4 |
| title-m | 15 / 1.25 | 700 | Athlete name, route card name |
| body | 15 / 1.45 | 400 | Descriptions |
| meta | 13 / 1.35 | 400 | Date · place, device, counts |
| label | 12 / 1.3 | 500 | Stat labels in Title Case, as Strava does ("Moving Time", "Elev Gain"). Record-screen labels are 13px/600 and carry the unit, e.g. "Split Avg (/km)" or "Distance (km)", over a bare number |
| nav | 11 / 1.2 | 600 | Bottom-nav labels |

- Units sit beside values at about 65% of the value size, in weight 600: `21.34 km`, `5:28 /km`.
- **Casing.** Stat labels are Title Case on every screen: Distance, Pace, Avg Pace, Moving Time, Elev Gain, Calories, Avg Heart Rate, Heart Rate, Split Avg, Est. Time. Headings, buttons and body text are sentence case. Map area labels and chart month ticks (AUG SEP OCT) are uppercase.

## Spacing, radii, elevation

- **Grid:** 4pt base. The scale is 4, 8, 12, 16, 20, 24 and 32. The screen gutter is 16.
- **Feed:** cards run edge to edge. An 8px `gap` band separates them. Card padding is 16px horizontal.
- **Heights:**
  - Status bar: 30
  - Top app bar: 52
  - Bottom nav: 60 plus a 20px gesture area
  - Action row: 48
  - Splits row: 25
  - Touch targets: 44 minimum
- **Radii:**
  - 0 for cards, maps and photos in the feed
  - 4 for buttons and inputs
  - 8 for route-card thumbnails
  - 16 for the top of sheets and record panels
  - 999 for avatars, map chips, the search pill, round buttons and the record button
- **Elevation:** surfaces are flat. Only things floating over a map get `shadow`: chips, round buttons, the search pill, sheets and the record panel. The light shadow is `0 1px 2px rgba(0,0,0,.16), 0 3px 10px rgba(0,0,0,.10)`. Dark uses heavier black.

## Icons

- We drew our own 24px line set: 1.75 stroke, round caps and joins, simple geometric forms. It lives in `I{}` in `_build/gen.py`.
- The active bottom-nav tab switches to a filled glyph in `accent`, with an `accent-text` label.
- The set covers:
  - Nav: home, maps, record, groups, you
  - Top bar: search, chat, bell
  - Social: high-five (open palm), comment, share (Android share-nodes). The action row draws them at 22px with a 1.6 stroke so all three weigh the same
  - Sports: shoe, bike, walk
  - Achievements: trophy, medal
  - Map: layers, locate, route
  - Record: sensor, beacon, music, splits, gear, lock, flag, back, chevrons

## Component anatomy

**Top app bar.** Title on the left at 21/700. On Home, the title is followed by a feed switcher, "Following ▾" at 15/600 in `text-2`. Icon buttons on the right (44px). The notification dot is an orange 9px dot with a 2px ring in the bg colour. A hairline sits at the bottom.

**Bottom nav.** Five equal slots: Home · Maps · Record · Groups · You. Each has a 24px icon over an 11px label. Inactive slots use `muted`. The active slot is filled orange. There is no pill indicator.

**Feed activity card.** All edge to edge, top to bottom:
1. Header row: 40px photo avatar; name at 15/700; meta line with sport glyph, "Today at 6:12 AM · Pune, Maharashtra"; overflow `…`. The device line appears only on activity detail.
2. Title at 19/700, 10px below the header.
3. Optional description at 15/1.4 in `text-2`. An optional group line ("with Kabir Sethi and 2 others") in `muted`.
4. Stat row: up to three columns, each with a 12px muted label over a 20px/700 value. Columns are separated by 34px of space only, with no dividers. Bike cards swap Pace for Elev Gain.
5. Achievements line: trophy in gold, with **bold count** plus the reason.
6. Media carousel at 236px. Slide 1 is the full-width (390px) map. Three 6px page dots sit below the map on the surface (active `text`, others `hair-strong`), with no counter badge. Photos follow on the next slides.
7. Social row: 24px facepile (overlap −7px, 2px ring in the surface colour), "**24** gave high-fives" and "4 comments" on the right.
8. Action row: high-five, comment and share, evenly spaced. It is separated by spacing only, with no hairline.

**Maps tab.**
- Full-bleed map with the heatmap on.
- Floating search pill plus a layers button.
- A chip row that scrolls horizontally, with a 44px fade at the right edge. The sport chip is inverted dark with a caret. An active layer chip (Heatmap) is also dark-filled, with an orange check. Inactive chips (Routes, Segments, Points of interest) are white and float with a shadow.
- A "You are here" location dot, clear of the KOREGAON PARK label.
- 3D and locate round buttons.
- A flat "Create route" pill: `#242428` with white text in light, `#F2F2F4` with dark text in dark, plus the standard float shadow. No gradient.
- A bottom sheet with a handle, "Suggested routes" with "See all", a filter summary line, and horizontally scrolling route cards. In dark, the sheet is lifted to `#232327` with a `hair-strong` top edge, so it reads as a separate layer over the dark map. Each card has a 252×88 map thumbnail with its own route shape: a riverside loop over two bridges, and an organic Koregaon Park loop. Below it come the name and the place and distance. Then three small stats, each a muted label over a value: Distance `10.2 km` · Elev Gain `38 m` · Est. Time `55 min`. The card's bottom padding and border sit fully above the nav.

**Record, before start.**
- Full-screen map with the GPS halo and dot. Cubbon Park is labelled. Queens Rd, Kasturba Rd and the N–S streets run off the top edge, and MG Road and Residency Rd are yellow arterials, as on the Pune maps.
- A chevron-down close button and a settings gear.
- A "GPS signal acquired" pill with a green dot.
- Layers and locate buttons.
- Bottom panel:
  - Two outlined 40px pill selectors (14/700, 20px icons): sport ("Run ▾") and "Add route ›". This follows the 2025 redesign, which puts sport and route choice first.
  - **Sensors** (56px outline), **Start** (96px flat orange circle, white 20/800 label, only a 1px neutral shadow, no glow) and **Beacon**.
  - A settings summary line: "Auto-pause on · Splits every 1 km · Spotify linked".

**Record, recording.**
- The map fills the top 372px. It shows the whole 8.27 km track, Cubbon Park loop included, from the start dot to the live location dot. Every leg is snapped to a drawn street: north up Kasturba Rd, round the park perimeter and east along MG Road. The track is framed clear of the pill and the panel.
- A "Recording · Run" pill with a red dot.
- The stats panel holds:
  - Time at 84/800.
  - A two-column row: "Split Avg (/km)" `5:09` and "Distance (km)" `8.27` at 52/700.
  - A three-column row: "Avg Pace (/km)" `5:21`, "Elev Gain (m)" `42` and "Heart Rate (bpm)" `152`. The heart glyph hangs in the left margin, so `152` is centred under its label.
  - A split progress bar.
- Controls: Lap (outline), **Stop** (84px flat orange circle with a white stop square, no glow) and Lock.

**Record, paused.** Tapping Stop pauses the activity. The pill turns amber ("Paused · Run"). A full-width 34px strip in `#C43B00` with white "❚❚ Paused" (5.3:1) heads the panel. No stat is dimmed. Time drops to 72px and the rows tighten to make room. Controls become **Resume** (96px, 2px orange outline, `accent-text` label) and **Finish** (96px flat orange, white 19/800).

**Activity detail.**
- Map hero (236px) showing the whole half-marathon. It ends on a hard edge with a hairline in both themes, with no fade. The bounds use a top inset of 84px, so the whole route sits below the floating back, share and more buttons. The river label is dropped from this frame.
- Athlete row: name; sport glyph with date and place; device line. Then the title at 22/800.
- A 3×2 stat grid with hairline rows: Distance, Avg Pace, Moving Time, Elev Gain, Calories, Avg Heart Rate.
- Achievement row: ribbon medal, then "**PR** · Half-Marathon", the effort time `1:56:12` and a "+3 more" link.
- **Splits** table (two rows, then "All 22 splits"): Km, Pace, Relative pace (the bar column has its own header; longer means faster), Elev and HR. Every bar is the same muted blue-grey `bar`; only the fastest pace is bold. Elev uses a hyphen-minus so `-3` and `+2` align without a gap.
- **Pace** chart: a 110px plot. A smoothed blue line (scale 5:00–6:12, so the mid-race surge and the humid fade after 15 km show) runs over a grey elevation area. A dashed average line carries an "Avg 5:28" label at its left end. Right-aligned pace labels and km ticks at 0, 5, 10, 15 and 20 km. It sits fully above the sticky bar.
- A sticky social bar: high-five 24, comment 4, share.

**You → Progress.**
- Avatar, "You", search and settings. The status bar, top bar and Progress | Activities tabs share one `surface` colour, with no seam. The active tab has a 3px orange underline.
- Sport selector: 48px outline circles; the active one has a 2px orange ring and an orange glyph.
- "This week · 26 Oct – 1 Nov" (today is Thu 29 Oct 2026), then three big totals.
- A 12-week line chart across the full 358px width: orange line with hollow dots, and an orange area fade that is gone by 70% of the height (0.22 opacity in light, 0.16 in dark). The y labels (25 km, 50 km) sit inside the plot at the left, just above their gridlines. The current week is a filled dot with a dashed drop line and a "32.4 km" tooltip that hangs below it, right-aligned inside the frame.
- Training log: discs sized by distance on Mon (6.0), Wed (8.1) and today, Thu (18.3, the largest, with a bold day letter). These add up to 32.4 km. Tue is a rest day, an 8px `hair-strong` dot. Fri–Sun are future days, a smaller 4px `hair` dot, so the two states differ in size.
- Best efforts: every row has a 24px ribbon medal slot, so the label column stays aligned. Then name, time and date.

**Avatars and photos.** Avatars are photo-like crops in SVG that never draw a face (code in `_build/avatars.py`):
- Ananya: back-lit side profile with a ponytail against a sunrise over the river, with a warm rim light.
- Rohan: cap and shoulders from behind, against a hazy sea front at dusk with city lights.
- Lukas: white helmet and wrap shades at 3/4, the face in deep back-lit shade, against a blurred tree-lined road.
- Priya: a forest trail with light shafts and no person.
- Arjun: a small figure on a Western Ghats ridge at dusk.
- Kabir: a shoulder silhouette against night-city bokeh.
- Every crop gets a colour cast (warm morning, cool shade), a blur scaled to the render size so no edge is crisp, a vignette, film grain and a 10% dark inner ring. The facepile is 24px.

They have no outlines and are never pastel initial discs. Feed photos sit on carousel slides 2 and 3, so they are not visible in these frames.

## Accessibility (WCAG AA)

- All body and meta text is at least 4.5:1 in both themes. The table above lists the ratios.
- Orange is used as small text only through `accent-text`: `#CC3D00` in light and `#FF6A2B` in dark.
- White on `#FC5200` (3.3:1) appears only as an icon or a ≥19px/800 label: Start (20px) and Finish (19px). The paused strip uses white on `#C43B00` (5.3:1) because its label is 14px.
- Medals always carry the numerals 1/2/3, in `#242428` on gold, silver and bronze (≥5.3:1 in both themes). The fastest split is marked in bold and has the longest bar. The GPS state is shown as text as well as the dot.
- Map labels carry halos and reach at least 4.7:1 against their halo or fill.

## Self-critique log

Round 1, first render:
- The heatmap blanketed every street.
  - Rebuilt it as corridor-weighted walks with per-edge intensity buckets.
  - Busy corridors now glow hot in dark mode.
- The map grid looked synthetic. Rows and columns now have uneven block sizes.
- The lake was drawn under the streets. It is now drawn after them.
- Labels clashed with chips and buttons. Moved them or dropped them per screen.
- The status bar was unreadable over maps. Added a top scrim.
- Suggested-route cards were clipped by the nav. Raised the sheet and made the thumbnails shorter.
- The feed social line and the second card's meta wrapped. Shortened both.
- The second "Settings" entry duplicated the gear. Replaced it with Splits.

Round 2:
- Activity detail lost its analysis chart below the fold.
  - Made the title shorter and the map hero more compact.
  - Cut the splits shown to 3 rows (with "All 22 splits" to see the rest).
  - Added axis labels to the pace chart.
- The fastest split bar touched the Elev column. Capped bar width at 90%.
- The live recording map didn't frame the track. Centred it on the recorded part and zoomed out.
- Added a split progress bar to use the empty space in the live panel.
- The route start on the detail map sat under the scrim. Re-centred the map.

Round 3, applying two reviews:
- **Colour.** The accent is now `#FC5200`. Medal numerals are dark on every metal, and bronze is lightened to `#C98A55`. The medals are ribbon glyphs instead of flat dots.
- **Avatars.** Rebuilt as photo-like crops: off-centre, tight shoulder crop, bokeh, side light, blur and grain, with sunglasses, cap or helmet variants.
- **Status bar.** The clock read 9:41 at 14/500 (replaced in round 4).
- **Home.**
  - Added the "Following ▾" feed switcher and dropped the device lines from feed cards.
  - Tightened the title gap.
  - The carousel's slide 1 is now the full-width map, with a 1/3 badge and page dots.
  - Facepile at 20px; "24 gave high-fives".
  - The share icon is now Android share-nodes, and all action icons are 22px at a 1.6 stroke.
- **Maps.**
  - Replaced the lattice generator with a world model: road hierarchy, cased arterials with names, fine 0.55px alleys, organic and diagonal neighbourhood grids with cul-de-sacs, a rail line, POIs, and neighbourhoods running to the riverbanks.
  - The half-marathon is now about 21 km of curves at a zoom that fits it. It runs out and back along the river, with a lollipop at Shivajinagar and a Koregaon Park loop.
  - Each route card has its own shape.
- **Heatmap.** Sparse now: a handful of hot corridors, with the rest faint or absent. The chip shows as active with a check. The chip row fades at the edge instead of slicing a chip. Added a location dot.
- **Labels.** Edge, UI, label and route collision avoidance. Nothing is clipped at the frame edge, and "Shanthala Nagar" is no longer cut by the track.
- **Record.**
  - Sport and route pill selectors above Start, and a settings summary line.
  - The lake and its label sit inside the frame.
  - Live labels carry the units; the split avg is 5:09, against 5:21 overall.
  - The stop-square button replaces the pause bars.
  - New paused frame with Resume and Finish.
  - The track is framed clear of the pill.
- **Activity.**
  - Hero fade added, and the sport glyph is on the meta line.
  - Single achievement row.
  - "Relative pace" bar header and a true minus sign.
  - Smoothed pace chart with km ticks, fully above the bar.
- **You.**
  - One-colour header.
  - Correct 2026 week, with discs only on past days and today's disc the largest. The totals add up.
  - Every best-effort row has a medal, so the labels align.
  - Value tooltip on the chart, and a thinner area fade in dark.
- **Dark.** The surface is lifted to `#1C1C1F` for clearer layering over the black gaps. Numerals are 700 except the live Time, which stays 800.

Round 4, applying two more reviews (fidelity 7/7, craft 7/7, slop 3–4):
- **Avatars.** The clay-face portraits are gone. Avatars are now faceless photo crops: back-lit profile, cap from behind, helmet in shade, trail, ridge and night bokeh. Each has a colour cast, size-scaled blur, a vignette and grain. The facepile is 24px.
- **Basemap.** Minor streets are about 45% as dense at feed and detail zoom. They are straight, run 3–8 blocks with T-junctions, drop whole streets and have no stubs. Empty landuse patches were added, and the light case colour is lighter. Dark water was raised to `#11314A`.
- **Heatmap.** The isolated orange stubs are gone. Faint heat is whole street runs of 3+ blocks on the street layer, so it never sits on water. Corridors are slices of real roads sampled from the road's own curve, which fixes the Nagar Rd offset. Every free-floating corridor now has a drawn street under it.
- **Home.** The page dots moved below the map onto the surface, and the 1/3 badge is gone. The stat dividers and the action-row hairline were removed. The title is now 21/700.
- **Maps.** Create route is a flat fill; the "gradient" was the sheet fade, which was removed. The dark sheet is lifted to `#232327` with a top hairline. Thumbnails are 88px and the sheet sits 12px higher, so card bottoms clear the nav. The KOREGAON PARK label moved off the location dot.
- **Record.** No orange glow on Start, Stop or Finish. Selector pills are 40px. Cubbon Park is labelled on the start map. Arterials run off the frame. MG Road and Residency Rd are yellow arterials. The live start leg is snapped to Kasturba Rd. Labels keep 14px from the side edges (Ulsoor Lake drops rather than touching the edge). The HR value is optically centred.
- **Paused.** The whole panel stays at full contrast, with a strong "Paused" strip, an orange-outline Resume and a flat orange Finish.
- **Activity.** The hero is reframed with a top inset of 84px, so the route clears every button, and the river label is dropped. Hard map edge plus hairline in both themes. The pace chart is 110px with real variation and an "Avg 5:28" label. Splits: two rows, uniform bar colour, bold fastest, no minus gap. Title Case labels.
- **You.** Future days are small grey dots, distinct from the rest-day dot. The chart's y labels are inside on the left and the tooltip hangs under the point, so the right edge is clear.
- **Casing.** One rule: Title Case stat labels everywhere (the spec and README were updated).
- **Status bar.** Android-plausible times per screen: Home 8:24, Activity 8:31, Maps 8:36, You 8:42, Record start 6:09, recording 6:56, paused 6:58.
- **Not done.** No collapsed-header frame for Activity, and Figtree stays (with −0.02em tracking on stat-m and up) rather than switching numerals to Archivo or Inter.

Known gaps:
- Avatars avoid faces and read as small photos, but they are still vector. Real profile photos will replace them in the app. We chose not to embed CC0 photos so the frames stay fully self-made.
- The basemap has no building footprints, and its geography is plausible rather than surveyed.
- Strava's exact icon shapes, its Record control layout after the July 2025 redesign, and its feed header treatment are matched from press coverage and our teardown, not from screenshots. Fresh web research confirmed the redesign (stats over the map; sport and route selection made more prominent) but found no description of the stop, resume and finish controls. Round 4 research again found only press text, which places the Android rollout on 16 July 2025, plus Pratt's iOS critique, which describes a Resume/Finish choice when paused. The Record controls should still be checked against a current Android build.
- In the dark theme, the minor-street grid is deliberately quiet, at about 1.4:1 against land, and reads as texture.
