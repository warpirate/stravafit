# 09 – Details: Notifications & Search

Minor-details catalogue for Strava Android's notifications (push, in-app, email, messaging) and search (athletes, clubs, segments, own activities). It is a reference for building StravaFit to the same level.

- **Gating column:** `[FREE]` / `[SUB]` / `[?]` shows Strava's own gating, for reference only. **In StravaFit everything is free.**
- **Our app column:** `keep` (match Strava), `improve` (match it and do better), or `drop` (with the reason).
- `(unverified)` marks a detail no public source confirms. Check it against the live app before building.
- All text is paraphrased from public sources. No assets or verbatim copy are used.

---

## 1. Entry points (global chrome)

```
Home tab header                      You tab header
+--------------------------------+   +--------------------------------+
| Home            [msg 2] [bell 5]|  | You            [search] [gear] |
+--------------------------------+   +--------------------------------+
```

| # | Detail | Behaviour | Strava | Our app |
|---|---|---|---|---|
| E1 | Bell on Home | A bell icon in the top-right of Home opens the in-app notification list. | [FREE] | keep |
| E2 | Messages icon next to the bell | A speech bubble on Home (and Groups) opens the messages inbox, separate from the bell. Shipped Dec 2023. | [FREE] | keep |
| E3 | Separate unread badges | The bell and the messages icon each show their own unread badge. A numeric count turns into a dot when it gets large. (unverified) | [FREE] | keep |
| E4 | Athlete search lives on You | On Android, the magnifier for finding athletes is on the **You** tab, not Home. | [FREE] | improve: also add a global search entry on Home |
| E5 | Settings gear on You | Push and email settings are reached from the gear in the top-right of You. | [FREE] | keep |

---

## 2. In-app notification list (Home > Bell)

```
+--------------------------------------------+
| < Notifications                            |
|--------------------------------------------|
|##(oo) Ana and 5 others gave you kudos   2h |  <- unread: tinted row
|  (o) Ben commented: "Nice pace!"        5h |
|  (o) Cara started following you         1d |
|        [Approve] [Decline]                 |  <- only for approval accounts
|  (o) You lost the QOM on Hill Rd     Oct 3 |
+--------------------------------------------+
```

| # | Detail | Behaviour | Strava | Our app |
|---|---|---|---|---|
| N1 | Independent of push | In-app entries arrive even when the matching push type is off. On the web, bell notifications cannot be disabled at all. | [FREE] | improve: add an optional "also hide in inbox" setting per type |
| N2 | Kudos batching | Kudos collapse into one row ("A and N others…") with stacked avatars. Tapping opens the activity or its kudos list. (unverified) | [FREE] | keep |
| N3 | Relative timestamps | Rows show 5m / 2h / 3d, then switch to a short date for older items. (unverified) | [FREE] | keep |
| N4 | Unread styling and clearing | Unread rows are tinted. Opening the list marks everything seen and clears the badge. (unverified) | [FREE] | improve: add "mark all read" and per-row read state |
| N5 | Inline follow-request actions | For accounts that need follower approval, request rows show Approve and Decline in the row itself. (unverified) | [FREE] | keep |

---

## 3. Notification types and tap destinations

| # | Type | Trigger | Tap destination | Strava | Our app |
|---|---|---|---|---|---|
| T1 | Friend upload | Someone you follow uploads | That activity. The push confirmation is low-confidence (unverified) | [FREE] | keep |
| T2 | Per-athlete upload alert | Upload by an athlete you turned alerts on for (see S6) | That activity | [FREE] | keep |
| T3 | Kudos | Kudos on your activity | Activity or kudos list (unverified) | [FREE] | keep |
| T4 | Comment / reply | Comment on your activity or thread | Activity comments, scrolled to the comments (unverified) | [FREE] | improve: scroll to and highlight the exact comment |
| T5 | Mention | @mention in a comment or description | The post or activity with the mention (unverified). Club admins also get an in-app alert when their club is @mentioned | [FREE] | keep |
| T6 | New follower | Someone follows you | Their profile, with a Follow back button (unverified) | [FREE] | keep |
| T7 | Follow request | Someone requests to follow an approval-required account | Approve or Decline, inline or on the profile (unverified) | [FREE] | keep |
| T8 | Group activity tag | Added to a group activity, manually or by auto-match | Activity, with options to add it to your profile or remove yourself (unverified) | [FREE] | keep |
| T9 | Lost KOM/QOM/CR | Your crown is beaten | Segment or activity. This is also the entry point to flag an illegitimate time | [FREE] | keep |
| T10 | Lost-crown suppression | No alert when the segment's average grade is 0% or below. Strava also skips some alerts it judges inaccurate | – | [FREE] | keep (make the grade threshold configurable server-side) |
| T11 | Auto-flagged effort | An implausible effort is removed from leaderboards | Activity, with a crop tool to cut the bad section | [FREE] | keep |
| T12 | Local Legend gained/lost | You become, or are displaced as, the athlete with the most efforts in 90 days | Segment > Local Legend tab (unverified) | [?] | keep, all free |
| T13 | Challenge invite | Invited to a challenge | Challenge detail (unverified) | [FREE] | keep |
| T14 | Challenge progress/complete | Completed or "ending soon" | Challenge detail with progress bar and badge (unverified) | [FREE] | keep |
| T15 | Club post | New post in a club | Club post. Follows per-club setting C1 | [FREE] | keep |
| T16 | Club event | New event, changed or cancelled event, RSVP reminder | Event detail with the Join/Going toggle (unverified) | [FREE] | keep |
| T17 | Club join request (admins) | Request to join a private club | Club > Members > Requests, with Approve and Decline (unverified) | [FREE] | keep |
| T18 | Device-sync upload | Activity synced from Garmin, Wahoo or similar | New activity, ready to edit title, description and photos (unverified) | [FREE] | keep |
| T19 | Direct / group message | New message | That conversation, not the inbox list (unverified) | [FREE] | keep |
| T20 | Ongoing recording | While recording | Persistent foreground notification with elapsed time and distance. Tapping returns to the Record screen (unverified) | [FREE] | improve: add pause/resume actions in the notification |

---

## 4. Notification settings

```
Settings > Push Notifications          Club > gear > Notifications
+------------------------------+       +------------------------------+
| Kudos                  [ON ] |       | ( ) All posts                |
| Comments               [ON ] |       | (*) Announcements only       |
| Mentions on Activities [ON ] |       | ( ) None                     |
| New followers          [OFF] |       | (greyed if Clubs>New Post    |
| Clubs > New Post       [ON ] |       |  is OFF globally)            |
+------------------------------+       +------------------------------+
```

| # | Detail | Behaviour | Strava | Our app |
|---|---|---|---|---|
| S1 | Per-type push toggles | Each push type has its own on/off switch. There is no single master switch. | [FREE] | improve: add a master switch as well |
| S2 | Mentions toggle | A separate "Mentions on Activities" push toggle. Mentions can also arrive by email. | [FREE] | keep |
| S3 | Email settings in app and web | Email types can be set at You > Settings > Email Notifications or on the web. Each event sends its own email, with no digest. | [FREE] | improve: offer an optional daily digest |
| S4 | Newsletter coupling | The newsletter toggle also controls the monthly stats email, so turning off the newsletter loses the stats. | [FREE] | drop the coupling: the monthly stats email gets its own toggle |
| S5 | Web email and push columns | The web shows each event with separate email and push checkboxes, set independently. (unverified) | [FREE] | keep (as a channel matrix) |
| S6 | Per-athlete upload alerts | Profile > Following button > "Turn on Notifications". | [FREE] | keep |
| C1 | Per-club three-way setting | Club details > gear: All posts / Announcements only / None. Mobile only. | [FREE] | keep (also add it on web) |
| C2 | Global gate for club settings | Per-club choices only apply while Push > Clubs > New Post is on. | [FREE] | improve: grey out per-club options and explain why while the global toggle is off |
| A1 | Android channels | Separate OS channels (social, recording, messaging and others). Muting a channel in Android settings overrides the in-app toggle. (unverified) | [FREE] | improve: one channel per category, kept in sync, plus a deep link to OS settings |

---

## 5. Messaging

```
+--------------------------------------+
| < Ana Ruiz                     (i)   |
|--------------------------------------|
|  [activity card: Morning Run 10k]    |
|                 Great run!  <3 (1)   |
|  > replying to "Great run!"          |
|  Thanks! Same time Sunday?           |
|--------------------------------------|
| [+] [GIF]  Message...         [Send] |
+--------------------------------------+
```

| # | Detail | Behaviour | Strava | Our app |
|---|---|---|---|---|
| M1 | DMs and named group chats | Includes reactions, GIFs, replies to a specific message, and sharing activities or routes. | [FREE] | keep (GIFs optional, depending on the provider's licence) |
| M2 | Message button on profile | Opens an existing thread with the athlete or creates one. | [FREE] | keep |
| M3 | Who can message you | The default follows profile visibility. Options: Following / Mutuals / No One. A separate control sets who can add you to group chats. | [FREE] | keep |
| M4 | Mobile-only | No messaging on the web at launch. Photo messages and club chats were announced as planned (unverified whether shipped). | [FREE] | improve: on web too, with photos |

---

## 6. Mentions composer

| # | Detail | Behaviour | Strava | Our app |
|---|---|---|---|---|
| X1 | @ autocomplete | Typing @ in a comment or description suggests athletes and clubs. | [FREE] | keep |
| X2 | Mention privacy | Privacy Controls > Mentions: Everyone / Followers / No One. A blocked mention sends no notification. | [FREE] | keep |

---

## 7. Athlete search (You > magnifier, "Find Friends")

```
+--------------------------------------+
| [Q Search athletes...        ] [QR]  |
|--------------------------------------|
| Suggested                            |
| (o) Dan Lee          [ Follow ] [x]  |
|     Ann Arbor, MI - 3 mutual         |
| From your contacts                   |
| (o) Eve Park         [ Follow ]      |
| Invite contacts      [ Invite ]      |
+--------------------------------------+
```

| # | Detail | Behaviour | Strava | Our app |
|---|---|---|---|---|
| F1 | Combined page | One screen holds the name search bar, suggested athletes and contacts already on Strava. | [FREE] | keep |
| F2 | Inline Follow | An orange Follow button on each row, so you can follow without opening the profile. The Requested and Following states are not documented (unverified). | [FREE] | improve: define Follow / Requested / Following states |
| F3 | Location subtitle | Each row shows avatar, name and city/region (when public) to tell apart identical names. A subscriber badge may appear (unverified). | [FREE] | keep (drop the subscriber badge, since there are no subscriptions) |
| F4 | Suggestion reasons | Suggestions carry a reason (mutual, contact, nearby) and can be dismissed. (unverified) | [FREE] | keep |
| F5 | Contacts groups | Shows contacts you already follow, suggested contacts, and contacts not on Strava with an Invite button. | [FREE] | keep |
| F6 | Invite via share sheet | Invite uses the Android share sheet. | [FREE] | keep |
| F7 | Contact sync off in two places | The in-app "Contact Sync Enabled" checkbox (You > gear > Contacts) or revoking the OS permission. The checkbox does nothing if permission was never granted. | [FREE] | improve: show permission state and a "Grant" button |
| F8 | QR follow code | A personal QR code that others scan to follow you. | [FREE] | keep |
| F9 | Follower approval and blocking | An optional approval setting turns follows into requests. Block and remove-follower are in the profile overflow menu. (unverified) | [FREE] | keep |

---

## 8. Club, segment and activity search

| # | Detail | Behaviour | Strava | Our app |
|---|---|---|---|---|
| G1 | Web vs app search scope | Web global search covers athletes, activities, segments and clubs. The app's magnifier covers athletes only. In the app, clubs are found via Groups and segments via Maps/Explore. (unverified for latest builds) | [FREE] | improve: one unified search with tabs for Athletes / Clubs / Segments |
| G2 | Club search results | Each row shows name, sport icon, location and member count. Public clubs show **Join**. Private clubs show **Request to Join**, which an admin approves. (unverified) | [FREE] | keep |
| G3 | Segment explore | A map lists popular segments in the visible area, with filters for length, surface and grade (flat/hilly/steep). The API also supports climb category, for rides only. Exact Android filters unconfirmed. (unverified) | [?] | keep, all free |
| G4 | No segment text search in app | Segments are found via map Explore and the Starred Segments list, not by name. Starred segments feed Live Segments on devices, which is [SUB] at Strava. (unverified) | [SUB] (live) | improve: add segment name search; live segments free |
| G5 | Own activity search | Filter your own activities by keyword and sport, with ranges for distance, duration and elevation. Strava's gating is unclear. (unverified) | [?] | keep, free |

---

## Acceptance checklist

**Entry points and inbox**
- [ ] E1: A bell icon in the Home header opens the notification list.
- [ ] E2: A messages icon in the Home header (and Groups) opens the messages inbox.
- [ ] E3: The bell and messages icons have independent unread badges. A count above 99 shows as a dot or "99+".
- [ ] E4: Athlete search is reachable from the You tab and from a global search on Home.
- [ ] E5: The gear on You opens Settings, which contains Push and Email notification screens.
- [ ] N1: Disabling a push type still delivers the in-app inbox entry, unless the optional "hide in inbox" setting is on.
- [ ] N2: Several kudos on one activity render as a single row, "X and N others", with stacked avatars.
- [ ] N3: Rows under 7 days old show a relative age (m/h/d). Older rows show a short date.
- [ ] N4: Unread rows are tinted. Opening the list clears the badge. A "mark all read" action exists.
- [ ] N5: Follow-request rows show working Approve and Decline buttons inline.

**Types and destinations**
- [ ] T1/T2: Tapping an upload notification opens that activity.
- [ ] T3: Tapping a kudos notification opens the activity's kudos list.
- [ ] T4: Tapping a comment notification opens the comments and highlights the exact comment.
- [ ] T5: Tapping a mention notification opens the post or activity containing the mention. Club admins get an in-app alert for club mentions.
- [ ] T6: Tapping a new-follower notification opens that profile with a Follow back button.
- [ ] T7: Approving or declining from the notification updates the follower list immediately.
- [ ] T8: A group-activity tag notification offers "Add to my profile" and "Remove me".
- [ ] T9: A lost-crown notification opens the segment or activity, and a Flag action is reachable from it.
- [ ] T10: No lost-crown notification is sent for segments with average grade <= 0% (threshold configurable server-side).
- [ ] T11: An auto-flagged effort sends a notification that opens the activity with the crop tool available.
- [ ] T12: Gaining or losing Local Legend sends a notification that opens the segment's Local Legend tab.
- [ ] T13/T14: Challenge invite, completion and ending-soon notifications open the challenge detail with progress.
- [ ] T15: Club post notifications respect the per-club setting (C1) and the global gate (C2).
- [ ] T16: Event created, changed, cancelled and reminder notifications open the event detail with the RSVP toggle.
- [ ] T17: Private-club admins get join-request notifications that open the requests list with Approve and Decline.
- [ ] T18: A device-sync upload notification opens the new activity in edit mode.
- [ ] T19: A message notification opens the specific conversation.
- [ ] T20: While recording, a persistent notification shows live time and distance, with Pause/Resume. Tapping returns to Record.

**Settings**
- [ ] S1: Each push type has its own toggle, plus a master switch.
- [ ] S2: "Mentions on Activities" has its own push toggle and email toggle.
- [ ] S3: Email types are configurable in-app and on web. A user can opt into a daily digest instead of per-event email.
- [ ] S4: The monthly stats email has its own toggle, independent of the newsletter.
- [ ] S5: Settings show a matrix of event x channel (push/email/in-app) with independent checkboxes.
- [ ] S6: The Following button menu on a profile has "Turn on notifications", which enables per-upload alerts for that athlete.
- [ ] C1: A club's settings offer All posts / Announcements only / None, on mobile and web.
- [ ] C2: When global Clubs > New Post is off, the per-club options are disabled and an explanation is shown.
- [ ] A1: The app registers separate Android channels (social, recording, messaging, clubs, segments). Settings link to the OS channel screen.

**Messaging and mentions**
- [ ] M1: Users can create DMs and named group chats, react, reply to a specific message, and share activities or routes.
- [ ] M2: A Message button on a profile opens the existing thread or creates a new one.
- [ ] M3: The "Who can message me" setting (Following / Mutuals / No One) and the separate group-add setting are enforced server-side.
- [ ] M4: Messaging works on web and supports photo messages.
- [ ] X1: Typing @ in comments or descriptions shows athlete and club suggestions.
- [ ] X2: The mention privacy setting (Everyone / Followers / No One) is enforced, and blocked mentions send no notification.

**Search**
- [ ] F1: Find Friends shows the search bar, suggestions and contacts on one screen.
- [ ] F2: Result rows have an inline Follow button that switches to Requested or Following without leaving the list.
- [ ] F3: Result rows show the location subtitle when the athlete's profile is public.
- [ ] F4: Each suggestion shows a reason and can be dismissed. A dismissed athlete does not reappear.
- [ ] F5: With contacts synced, the page shows "already following", "suggested from contacts" and "invite" groups.
- [ ] F6: Invite opens the Android share sheet with a referral link.
- [ ] F7: Contact sync can be turned off in-app. The screen shows the OS permission state and offers a grant action.
- [ ] F8: The user can display a personal QR code. Scanning it opens their profile with Follow.
- [ ] F9: Follower approval, block and remove-follower work from privacy settings and the profile overflow menu.
- [ ] G1: Unified search has Athletes / Clubs / Segments tabs in the app.
- [ ] G2: Club results show name, sport, location and member count, with Join for public clubs and Request for private clubs.
- [ ] G3: Segment Explore lists segments in the map viewport, filterable by length, surface, grade and (for rides) climb category.
- [ ] G4: Segments can be searched by name, and starred segments are listed under You. Live segments are free.
- [ ] G5: Users can search their own activities by keyword, sport and distance/duration/elevation ranges.

---

## Sources

- Strava Support – Strava notifications: https://support.strava.com/en-us/articles/15402139-strava-notifications
- Strava Support – Mentions: https://support.strava.com/en-us/articles/15401832-how-do-i-use-mentions-on-strava
- Strava Support – Finding friends (Android): https://support.strava.com/en-us/articles/15401973-finding-friends-and-managing-contacts-on-strava-android
- Strava Support – Finding friends (general / web search): https://support.strava.com/en-us/articles/15401934-finding-friends-and-managing-contacts-on-strava
- Strava Support – Finding friends (legacy, QR): https://support.strava.com/hc/en-us/articles/216919127-Finding-Friends-and-Managing-Contacts-on-Strava
- Strava Support – How to find segments: https://support.strava.com/hc/en-us/articles/216918147-How-to-Find-Segments
- Strava web email notification settings: https://www.strava.com/settings/email_notifications
- Road Bike Action – favourite athletes / per-athlete alerts: https://roadbikeaction.com/?p=40885
- Runner's World SA – platform updates (auto-flag, crop): https://runnersworld.co.za/news/strava-announce-platform-updates-based-on-runners-feedback
- BikeRadar – Strava messaging: https://www.bikeradar.com/news/you-can-finally-send-direct-and-group-messages-on-strava
- Android Authority – Strava messaging: https://www.androidauthority.com/strava-messaging-3391139
- GCN – Messaging launch and privacy: https://www.globalcyclingnetwork.com/tech/news/messaging-comes-to-strava-functionality-launches-for-dms-and-group-chats
- Cycling Weekly – Strava messaging: https://www.cyclingweekly.com/news/strava-introduces-messaging
- Reteno gallery – Strava push flows (low confidence): https://gallery.reteno.com/flows/push-notifications-strava
- Slowtwitch forum – follower approval and blocking (low confidence): https://forum.slowtwitch.com/t/strava-porn-or-sex-ads/758941
- Items marked (unverified) with only the Strava Help Centre root as their source come from prior knowledge. Confirm them against the live app.
