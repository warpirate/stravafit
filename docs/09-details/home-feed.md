# Home Feed: Minor-Details Catalog

Area: `home-feed`. This covers feed cards, card variants, kudos and comments, feed ordering and filters, suggested and club content, inline media, and timestamps.

How to read the tables:
- **Gating** shows how Strava gates the feature, for reference only. StravaFit has no paywall, so every feature is free in our app.
- **Our app** says whether we keep it, improve it, or drop it, with the reason.
- Items marked *(unverified)* rest on a single low-confidence or third-party source. Check them against a live device before treating them as spec.
- Speed note: to finish faster, this pass skipped live web spot-checks. Low-confidence items are flagged instead of re-verified.

---

## 1. Feed structure and ordering

| # | Detail | Behaviour (Strava) | Gating | Our app |
|---|---|---|---|---|
| 1.1 | Feed switcher | There are three feeds: **Following**, **You** (your activities only) and **Clubs** (one club at a time). Internally this is a `feed_type` parameter. | FREE | Keep. Use a segmented header control. |
| 1.2 | Feed Ordering setting | Path: Profile > Settings > Feed Ordering. The options are **Personalized** (relevance-based, the default since late 2017) and **Latest Activities** (chronological, added March 2020). | FREE | Improve. Make chronological the default, and offer the toggle inline in the feed header as well as in Settings. |
| 1.3 | "Chronological" means unseen first | Latest Activities mode puts unseen items first, sorted by date, then seen items. A late upload of an old activity still lands at the top of the unseen group. There is no "caught up" divider between the groups. | FREE | Improve. Keep the unseen-first grouping and add a "You're all caught up" divider. |
| 1.4 | Order change applies only to new items | Switching the ordering does not re-sort items already loaded. Only newer items follow the new order. | FREE | Improve. Re-sort the whole feed immediately when the setting changes. |
| 1.5 | You and Clubs feeds are always chronological | The ordering setting affects only the Following feed. | FREE | Keep. |
| 1.6 | Favorite athletes pin to top | On an athlete's profile, open the Following button's dropdown and choose Favorite. This also turns on notifications. Their new activities pin to the top of the feed until seen. No limit on favorites is documented. | FREE | Keep. Decouple "favorite" from "notify" so they are two separate toggles. |
| 1.7 | Server-side rollout of feed features | Favorite appeared for some users without an app update, which points to a remote feature flag. | n/a | Keep the pattern. Gate new feed features behind remote config. |
| 1.8 | Personalized ranking signals *(unverified, 2014 source)* | Ranking historically weighed group activities, distance, duration, elevation and Relative Effort (formerly Suffer Score). | FREE | Improve. If we ship a ranked mode, document the signals openly in the app. |
| 1.9 | No native feed filter | Strava cannot filter the feed by sport, distance, keyword or map/no-map. Userscripts fill this gap. | n/a | **Add.** Provide a filter chip row for sport, minimum distance, has-map, has-photos and group-only. |
| 1.10 | No bulk kudos | There is no "kudos all" action in the app. It exists only through a desktop Chrome extension. | n/a | Drop, with reason: bulk kudos devalues the gesture. Fast single-tap kudos covers the need (see 3.1). |

```
+-----------------------------------------------+
| [Following v]  [You]  [Clubs v]        (=) ⚙  |  <- switcher + filter + order
| [Run] [Ride] [>5 km] [Has map] [Photos]  ...  |  <- our filter chips (1.9)
+-----------------------------------------------+
| ★ Favorite: Alex · Morning Run           NEW  |  <- pinned until seen (1.6)
| ...unseen items (newest first)...             |
| ─────────── You're all caught up ───────────  |  <- our divider (1.3)
| ...seen items...                              |
+-----------------------------------------------+
```

## 2. Activity card: header, stats and media

| # | Detail | Behaviour (Strava) | Gating | Our app |
|---|---|---|---|---|
| 2.1 | Header line *(unverified)* | Avatar and athlete name, then the date and time and the city or region. The recording device shows on the detail page, not on the card. | FREE | Keep. |
| 2.2 | Timestamp format *(unverified)* | Recent items read "Today at h:mm" or "Yesterday at h:mm". Older items show the full date plus time. Thresholds are unconfirmed. | FREE | Keep. Our spec: under 1 h shows "N min ago"; today shows "Today at h:mm"; yesterday shows "Yesterday at h:mm"; within 7 days shows the weekday and time; older shows "d MMM yyyy · h:mm", plus the year only if it is not the current year. Use the device locale and its 12- or 24-hour setting. |
| 2.3 | Units follow the viewer's preference | Distance, pace or speed and elevation render in the **viewer's** metric or imperial setting, synced across app and web. Temperature is a separate setting. Share images use the same units. | FREE | Keep. |
| 2.4 | Hidden stats | Hidden fields (for example pace or start time) are removed from the card. Strava warns they may still leak elsewhere in the product. | FREE | Improve. Strip hidden fields server-side from every surface, including summaries, share images and the API. |
| 2.5 | No map for short or indoor activities *(unverified)* | Cards for activities without GPS, or very short ones, show stats only or a sport-specific placeholder. | FREE | Keep. Show a sport icon tile instead of an empty map. |
| 2.6 | Photos featured prominently | Since the Posts overhaul, photos show as a carousel alongside or in place of the map. | FREE | Keep. The map is slide 1 and photos follow, with a page indicator. |
| 2.7 | Group activity card | When athletes record the same activity together, the feed shows one merged card listing all participants instead of duplicates. | FREE | Keep. |
| 2.8 | "Ran with / rode with" line *(unverified wording)* | Group cards carry a with-athletes text line, which third-party filters key on. | FREE | Keep. Use "with A, B +N others", and make it tappable to open the participant list. |

```
+-----------------------------------------------+
| (o) Jane Doe                              ... |
|     Today at 7:42 · Bengaluru, KA             |  <- 2.1 / 2.2
|     with Sam, Lee +2 others                   |  <- 2.8 (group)
| Morning Run                                   |
| Distance   Pace        Time                   |
| 10.2 km    5:12 /km    53:04                  |  <- 2.3 units, 2.4 hidden fields omitted
| +-------------------------------------------+ |
| |  [map]  |  [photo]  |  [photo]            | |  <- 2.6 carousel  • ○ ○
| +-------------------------------------------+ |
| (o)(o)(o) 12 gave kudos · 3 comments          |  <- 3.4 facepile
| [ 👍 Kudos ]   [ 💬 Comment ]   [ ↗ Share ]  |
+-----------------------------------------------+
```

## 3. Kudos interactions

| # | Detail | Behaviour (Strava) | Gating | Our app |
|---|---|---|---|---|
| 3.1 | Kudos tap animation | The 2025 redesign (around April 2025) brought a longer animation. Users found it slow and repetitive when giving kudos to many cards in a row. Exact frames are *(unverified)*. | FREE | Improve. Keep the confirmation under 300 ms and never block the next tap or scroll. |
| 3.2 | Given state *(partly unverified)* | The icon fills and turns brand orange, and the count increments optimistically. A design teardown claims a ~1.2x scale pulse over ~200 ms plus a light haptic *(unverified)*. | FREE | Keep, using our own accent colour, not Strava's. Add a light haptic. Roll back and show a snackbar if the request fails. |
| 3.3 | No undo *(unverified)* | Kudos cannot be removed, and tapping again does nothing. Accidental kudos are a common complaint. | FREE | Improve. A second tap within 5 s undoes the kudos, and the notification is sent only after that window. |
| 3.4 | Kudos facepile *(unverified)* | Overlapping avatars plus "N gave kudos". Tapping opens the full kudos list. | FREE | Keep. |
| 3.5 | No kudos on own activity *(unverified)* | Your own cards show the facepile and count but no Kudos button. | FREE | Keep. |
| 3.6 | Kudos subscribes you to the thread | Giving kudos (or commenting) subscribes you to the activity's comment notifications. | FREE | Improve. Subscribe only on comment, not on kudos, and add a "Mute thread" toggle (see 4.2). |

```
 idle          tap (≤300 ms)        given
 [ 👍 ]  -->   [ 👍 ] 1.2x   -->   [ 👍 ] filled accent, count+1
 (outline)     haptic tick          (second tap ≤5 s = undo, ours only)
```

## 4. Comments and mentions

| # | Detail | Behaviour (Strava) | Gating | Our app |
|---|---|---|---|---|
| 4.1 | Commentable object types | You can comment on activities, posts, achievements, challenge completions, Challenges, and race or goal entries. | FREE | Keep. Every commentable card variant gets the same comment affordance. |
| 4.2 | Unsubscribing from a thread | Per support, the only way to stop notifications is to delete your own comment. There is no mute toggle. | FREE | Improve. Add "Mute this thread" to the card overflow menu and the discussion view. |
| 4.3 | Deleting a comment | Open the discussion view and use the three-dot menu on your own comment. The web shows "Delete" on hover. You cannot delete from the feed card. | FREE | Keep. Also allow long-press on your own comment, then Delete with a confirmation. |
| 4.4 | Notification preferences | Comment and kudos push settings live in Settings > Push Notifications (email preferences on the web), not on the card. | FREE | Keep. Also link to them from the card overflow menu. |
| 4.5 | @mentions *(rules unverified)* | Mentions in comments and descriptions tag and notify the athlete. Help docs group them with Feed, Kudos and Comments. | FREE | Keep. Use @-autocomplete limited to athletes you follow or who follow you. |

## 5. Card variants and suggested content

| # | Detail | Behaviour (Strava) | Gating | Our app |
|---|---|---|---|---|
| 5.1 | Athlete Post card | Text and photo posts not tied to an activity. Supports kudos and comments. | FREE | Keep. |
| 5.2 | Club post card | Admin posts (announcements, questions, photos, routes, segments, articles) appear in every member's home feed and in the club's Posts tab. | FREE | Keep. Add a per-club "hide club posts from home feed" toggle. |
| 5.3 | Group challenge invite card | Invites can be sent only between mutual followers, and the invitee must accept. | SUB | Keep, free for all users. |
| 5.4 | Group activity card | See 2.7 and 2.8. | FREE | Keep. |

## 6. Visibility and privacy affecting the feed

| # | Detail | Behaviour (Strava) | Gating | Our app |
|---|---|---|---|---|
| 6.1 | Mute Activity | Path: Activity > ... > Edit > Mute Activity. A muted activity does not appear in followers' home feeds or in club feeds. It stays on your profile and still counts toward progress charts, goals and challenges. | FREE | Keep. Also offer it as a toggle on the save screen. |
| 6.2 | "Only You" visibility | Removes the activity from all other athletes' feeds entirely. | FREE | Keep. |

## 7. Long-press and overflow menus, pull-to-refresh

These areas had no sourced findings in this pass. The items below are our own spec, not observations of Strava.

| # | Detail | Our app spec |
|---|---|---|
| 7.1 | Card overflow (…) | Own card: Edit, Mute, Visibility, Share, Delete. Others' cards: Share, Mute thread, Hide this athlete's activities, Report. |
| 7.2 | Pull-to-refresh | Standard pull spinner. New items insert above the current scroll position with a "N new activities ↑" pill, with no jump. |

---

## Acceptance checklist

- [ ] 1.1 The feed header switches between Following, You and a selected Club, and each loads the correct items.
- [ ] 1.2 Feed ordering defaults to chronological and can be changed from both the feed header and Settings.
- [ ] 1.3 In chronological mode, unseen items appear before seen items, and a "You're all caught up" divider separates the groups.
- [ ] 1.4 Changing the ordering re-sorts the already-loaded feed immediately.
- [ ] 1.5 The You and Clubs feeds are always chronological, whatever the setting.
- [ ] 1.6 A favorited athlete's unseen activities appear at the top of the Following feed until viewed, and favoriting does not force notifications on.
- [ ] 1.7 New feed features can be toggled through remote config without an app release.
- [ ] 1.8 If ranked mode ships, its ranking signals are listed on an in-app info screen.
- [ ] 1.9 Filter chips (sport, minimum distance, has map, has photos, group only) filter the feed client-side and survive app restart.
- [ ] 2.1 The card header shows avatar, name, timestamp and location, and the device is shown only on the detail page.
- [ ] 2.2 Timestamps follow the relative/absolute rules in 2.2 and respect the device's 12- or 24-hour setting.
- [ ] 2.3 Card stats render in the viewer's unit preference, and changing the setting updates all cards.
- [ ] 2.4 Fields the owner hid never appear on the card, in summaries, in share images or in API responses to other users.
- [ ] 2.5 Activities without GPS show a sport icon tile and no empty map.
- [ ] 2.6 Map and photos render as a swipeable carousel with a page indicator.
- [ ] 2.7 A group activity renders as one card listing all participants, with no duplicate cards.
- [ ] 2.8 The "with A, B +N others" line opens the participant list when tapped.
- [ ] 3.1 The kudos animation completes in under 300 ms, and the next card's kudos can be tapped immediately.
- [ ] 3.2 Kudos updates optimistically (icon fills, count increments, haptic fires) and rolls back with an error message on failure.
- [ ] 3.3 A second tap within 5 s removes the kudos, and no notification is sent in that case.
- [ ] 3.4 The facepile shows up to 3 avatars plus "N gave kudos" and opens the full list.
- [ ] 3.5 Own cards show no Kudos button but do show the facepile and count.
- [ ] 3.6 Giving kudos does not subscribe the user to comment notifications.
- [ ] 4.1 Activity, Post, achievement, challenge and goal cards all support comments.
- [ ] 4.2 "Mute this thread" stops further comment notifications without deleting anything.
- [ ] 4.3 Users can delete their own comment through the overflow menu or long-press, after a confirmation step.
- [ ] 4.4 The card overflow menu links to the notification settings.
- [ ] 4.5 Typing @ suggests athletes, and a posted mention notifies the tagged athlete.
- [ ] 5.1 Post cards render text and photos and support kudos and comments.
- [ ] 5.2 Club posts appear in members' home feeds, and a per-club toggle hides them.
- [ ] 5.3 Group challenge invites work for all users, are limited to mutual followers and require acceptance.
- [ ] 6.1 A muted activity is absent from followers' home and club feeds but present on the profile and counted in stats and challenges.
- [ ] 6.2 An "Only You" activity never appears in any other user's feed.
- [ ] 7.1 The overflow menu shows the correct action set for own cards versus others' cards.
- [ ] 7.2 Pull-to-refresh loads new items without moving the scroll position and shows a "N new" pill.

## Sources

- DC Rainmaker, chronological ordering and favoriting (2020): https://dcrainmaker.com/2020/03/chronological-ordering-favoriting.html
- road.cc, Strava brings back chronological ordering: https://road.cc/content/tech-news/271757-strava-has-brought-back-chronological-ordering-and-allows-you-favourite
- Strava Support, Mute Activity: https://support.strava.com/en-us/articles/15401718-mute-activity
- Strava Support, Hide Details from your Activities: https://support.strava.com/hc/en-us/articles/360028654291-Hide-Details-from-your-Activities
- Strava Support, Giving and Receiving Comments: https://support.strava.com/en-us/articles/15402008-giving-and-receiving-comments
- Strava Support, Feed, Kudos and Comments collection: https://support.strava.com/en-us/collections/19668897-feed-kudos-and-comments
- BikeRadar, Athlete Posts launch: https://www.bikeradar.com/news/strava-launches-athlete-posts-for-the-masses
- BikeRadar, Kudo All extension: https://www.bikeradar.com/news/strava-kudo-all
- Coach, Posts available to all: https://www.coachweb.com/fitness-apps/6986/time-to-update-your-strava-app-the-posts-feature-is-now-available-to-all
- Trizone, Club Posts: https://trizone.com.au/strava-unveils-club-posts-newest-way-groups-athletes-connect
- Endurance.biz, Group Challenges: https://endurance.biz/2021/new-product-development/new-group-challenges-feature-in-stravas-biggest-app-update-of-the-year/
- MacMyths, distance units: https://macmyths.com/how-to-change-strava-distance-units-between-kilometers-and-miles/
- Greasy Fork, Strava Feed Buttons userscript: https://greasyfork.org/en/scripts/413862-strava-feed-buttons/code
- Greasy Fork, Strava Feed Filter userscript: https://greasyfork.org/en/scripts/550272-strava-feed-filter
- Reddit r/Strava (via mirror), kudos animation thread: https://reddit.birdcat.cafe/r/Strava/comments/1jpjwps/why/mkzu1ee/?context=3
- Blake Crosley, Strava design teardown (third-party): https://blakecrosley.com/es/guides/design/strava
- SF2G Google Group, 2014 feed-ranking note: https://groups.google.com/g/sf2g/c/KLD5DnMGGok
