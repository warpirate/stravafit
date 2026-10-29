# Minor-details catalog: Groups, Clubs, Challenges, Messaging

Area: `groups-clubs-challenges`. This covers the Groups tab, clubs, club posts and events, challenges (join, progress, badges, trophies), and group chats and messages.

**Gating legend:** [FREE] means free on Strava. [SUB] means Strava requires a subscription. [?] means the gating is unknown. These labels are for reference only: **in StravaFit every feature is free.**

**Our app column:** *keep* means copy the behaviour. *improve* means copy it with the change described. *drop* means don't build it, for the reason given.

Items marked "(unverified)" come from secondary or low-confidence sources. Confirm them before treating them as spec.

Spot-checking was skipped in this pass to save time. All low-confidence items carry "(unverified)", and one API-only finding was dropped (see the end of this doc).

---

## 1. Groups tab (top level)

| # | Detail | Behaviour | Strava | Our app |
|---|---|---|---|---|
| G1 | Messages entry point | A speech-bubble icon at the top right of both the Home and Groups tabs opens the messages hub. The hub holds DMs, group chats, club channels and message requests. | [FREE] | keep. Add an unread-count badge on the icon. |
| G2 | Search | A magnifier icon at the top of Home or Groups opens search: keywords plus filters for location and sport type to find clubs. | [FREE] | keep |
| G3 | Events sub-tab | Added around July 2026. It lists nearby group runs, rides and races. Race filters: distance, date, location, sport, elevation and temperature. Club-event filters: date, location, format and distance. A races hub matches races to your running goal using Runna data (unverified details). | [?] | keep the list and filters. drop the Runna goal matching, which depends on a proprietary partner; use our own goal data instead. |
| G4 | Featured clubs | Featured clubs get promoted placement in club discovery. | [FREE] | improve: rank by proximity and activity instead of paid or editorial placement. |

```
+------------------------------------------+
| Groups                      [Q]  [chat]  |
| [Clubs] [Challenges] [Events]            |
|------------------------------------------|
| Your clubs ...                           |
```

## 2. Club discovery and creation

| # | Detail | Behaviour | Strava | Our app |
|---|---|---|---|---|
| C1 | Create Club wizard | Groups > Clubs > Create a Club. Steps: sport, up to 3 tags, logo, name and description, privacy (Public or Private), location (current or global). Mobile creation needs app version V396 or later. | [FREE] | keep the step order and 3-tag cap. |
| C2 | Club type label | Each club has a type: company, club/team, racing team, shop or other. It shows as a small label in the header and is picked in the wizard (unverified for the UI). | [FREE] | keep |
| C3 | Sport defined by activity types | A club lists the activity types it counts (for example Ride, VirtualRide, EBikeRide, Velomobile and Handcycle for cycling). The leaderboard and club feed include only those types. This list overrides the older single sport field. | [FREE] | keep. Use a multi-select of activity types in the wizard. |
| C4 | Location display | Stored as separate city, state and country fields. Shown as one comma-joined line under the club name. Also feeds nearby suggestions. | [FREE] | keep. Skip empty parts so there are no stray commas. |
| C5 | Vanity URL | Clubs can set a custom slug (`/clubs/<slug>`). Share links use the slug instead of the numeric ID when one is set. | [FREE] | keep. Slugs must be unique and validated. |
| C6 | Cover and avatar sizes | Large cover about 1185x580. Card crop about 360x176. Square avatar 60x60. | [FREE] | keep. Generate both cover sizes server-side. |
| C7 | Clubs on profile | Joined clubs are listed at the bottom of the athlete's profile. | [FREE] | keep |

## 3. Club detail page

```
+------------------------------------------+
| [cover image 1185x580]          [gear]*  |
| (av) Club Name  [v]     [lock] Private   |
| Club/Team - Ride - Pune, MH, India       |
| 1,240 members - 12 athletes you follow   |
| [ Join Club ]  -> [ Pending ]            |
| < Overview | Activities | Stats | Share >|
| [Messages bar >  Club Hub]               |
| Upcoming events ...  Recent posts ...    |
+------------------------------------------+
* gear is shown to admins only
```

| # | Detail | Behaviour | Strava | Our app |
|---|---|---|---|---|
| D1 | Tab carousel | A horizontal carousel of tabs: Overview, Activities, Stats, Share. Icons under the name give info and actions. The settings gear at the top right is admin-only. Upcoming events and recent posts appear on the page. | [FREE] | keep |
| D2 | Verified badge | A checkmark next to the name marks official brand or org clubs. | [FREE] | improve: base verification on a documented manual review, not paid status. |
| D3 | Social proof | A line like "N athletes you follow" appears alongside the separate member count. | [FREE] | keep. Tapping the line opens those members. |
| D4 | Join button states | On a public club, Join Club joins immediately. On a private club the button changes to "Pending" until an admin approves. | [FREE] | keep. Add a way to cancel a pending request. |
| D5 | Membership model | There are only two states, `member` and `pending`, and no record means not joined. Admin and owner are separate boolean flags. | [FREE] | keep the model. Add a `banned` state so removed spammers can't rejoin. |
| D6 | Private club, non-member view | Shows a lock and a "Private" label. Non-members see only the header and the request button; the feed, leaderboard and members stay hidden until approved (unverified). | [FREE] | keep |
| D7 | Posts empty state | The club keeps a post count. With zero posts, the section shows a prompt to write the first post (unverified). | [FREE] | keep |
| D8 | Leave Club | Overview > scroll to the bottom > Leave Club. Owners also see Delete club there. | [FREE] | improve: add a confirm dialog and also offer Leave in the overflow menu, so it isn't buried. |

## 4. Club admin and members

| # | Detail | Behaviour | Strava | Our app |
|---|---|---|---|---|
| A1 | Member role menu | Overview > View all members > three-dot menu per member. A member can be removed or promoted to admin. An admin can be promoted to owner, demoted or removed. | [FREE] | keep |
| A2 | Pending requests | Admins get a Pending Requests list with Approve and Decline. Join-request push notifications can be toggled in settings. | [FREE] | keep |

## 5. Club stats and leaderboard

```
Stats (clubs of 500 or fewer)
 dist ^   .  ..   .
      |  .:. .:  ...      [Distance|Elev|Time]
      +---------------> members
 Week total: 1,204 km - 312 activities
 Leader: 210 km - Most activities: 9
 Leaderboard: 1..10 ... 23 24 [YOU 25] 26 27
```

| # | Detail | Behaviour | Strava | Our app |
|---|---|---|---|---|
| S1 | Scatterplot size limit | Clubs with 500 or fewer members get a weekly scatterplot of member efforts by distance, elevation or time. Larger clubs see totals plus your own weekly figures only. | [FREE] | improve: show the chart at any size, sampled or binned above 500 members. |
| S2 | Weekly totals | Primary stat, activity count, the leader's value, and the most activities by one member. | [FREE] | keep |
| S3 | Top 10 plus your context | The leaderboard shows the top 10. If you're outside it, scrolling shows your rank with the 2 athletes above and 2 below. | [FREE] | keep |
| S4 | Weekly reset | The week runs Monday to Sunday and resets at local midnight. A toggle shows last week's board (unverified). | [FREE] | keep. Use the club's time zone so every member sees the same board. |
| S5 | Columns | Ranked by distance by default. Other columns: activity count, longest activity, average pace or speed, elevation. Values use the viewer's units, distance to 1 decimal (unverified). | [FREE] | keep. Make every column sortable. |

## 6. Club events

| # | Detail | Behaviour | Strava | Our app |
|---|---|---|---|---|
| E1 | Who can create events | Owners and admins only, and only for rides or runs. | [FREE] | improve: allow every activity type. |
| E2 | Fields | Required: title, start date, start time. Optional: meeting point (drag a pin or type a place), a route from your public routes, terrain, level, repeat, and who can join. | [FREE] | keep |
| E3 | Reminder | RSVPed athletes get a push reminder 24 hours before the start. | [FREE] | keep. Make the lead time configurable. |
| E4 | Event card | Local date and time, a "Going" count with an avatar row, the sport icon, and a recurrence label such as "Every Tuesday" (unverified). | [FREE] | keep |
| E5 | Pace or skill label | Events carry a pace or skill tag (for example casual, tempo or race pace) shown on the card (unverified). | [FREE] | keep |

```
+------------------------------+
| [run] Tue 6:00 AM - Every Tue|
| Tempo - 10 km - Riverside    |
| (o)(o)(o) +14 going   [RSVP] |
+------------------------------+
```

## 7. Club messaging (Club Hub and channels)

| # | Detail | Behaviour | Strava | Our app |
|---|---|---|---|---|
| M1 | Club Hub entry | Tapping the messages bar on the club page opens the Club Hub, which lists every channel. A channel's push notification opens that channel directly. | [FREE] | keep |
| M2 | Member minimum | Clubs with 2 or fewer members can't use Club Messages. | [FREE] | drop the limit. It isn't needed without Strava's abuse economics. |
| M3 | Channel creation | Admins only, up to 100 channels per club. Fields: name, optional description, posting rights (all members or admins only), join mode (auto-add current and future members, or opt-in). Members are notified when a channel is created. | [FREE] | keep |
| M4 | No private channels | All channels are open to all members. There are no hidden admin channels, and video sharing isn't supported. | [FREE] | improve: add an optional admin-only channel. |
| M5 | Auto Announcements | At launch, clubs under 20,000 members got an auto-created Announcements channel that every member is in, and new members are added on join. A one-time welcome module prompted admins to post a first message. | [FREE] | keep: create Announcements when a club is created. |
| M6 | Rights in admin-only channels | Admins can post, reply, react, share Routes, Events, Clubs or URLs, create polls and pin messages. Members can only react. | [FREE] | keep |
| M7 | Rights in member channels | Members can post, @mention, reply, react, share and vote in polls. URLs show a link preview. | [FREE] | keep |
| M8 | Admin delete | An admin's deletion leaves a "Message deleted" placeholder in place. It doesn't notify Trust & Safety. | [FREE] | keep |
| M9 | Leave and rejoin a channel | Settings > Leave channel, then confirm. A left channel stays in the available list for rejoining. Leaving the club removes you from all channels, but your past messages stay visible. | [FREE] | keep |
| M10 | Mute | "Mute conversation" mutes one channel. "Mute all channels" in the Club Hub settings mutes every channel in the club. | [FREE] | keep |
| M11 | Remove from channel | An admin can remove a member from a channel while they stay in the club, and they can rejoin later. Channel delete is confirmed and permanent. | [FREE] | keep |

```
Club Hub
+-------------------------------+
| # Announcements   [admin] (3) |
| # Sunday-long-run         (1) |
| # General                     |
| [ + New channel ]  (admins)   |
+-------------------------------+
```

## 8. Direct and group messages

| # | Detail | Behaviour | Strava | Our app |
|---|---|---|---|---|
| X1 | New chat | A pencil icon in the hub starts a chat. The picker lists athletes who follow you. The Message button on a profile also starts one. Athletes whose privacy blocks you can't be selected. | [FREE] | keep |
| X2 | Group chat name | Only the creator can rename a group chat. | [FREE] | improve: let any member rename it, and show a system line "X renamed the chat". |
| X3 | Long-press menu | Shows emoji reactions plus Reply and Copy. On your own messages: Edit and Delete. On others': Flag/Report, which sends a copy to Trust & Safety. No published edit time limit. | [FREE] | keep. Add an "edited" marker. |
| X4 | Reactions, GIFs, replies | Group chats support reactions, GIFs and threaded replies (unverified). | [FREE] | keep. Use an open GIF provider, or drop GIFs. |
| X5 | Block wipes the thread | If someone blocks you, every message in that conversation is deleted and you can't open it. | [FREE] | keep |
| X6 | Age gate | Messaging needs a birthdate and is 18+ only. Under-18 club admins lose club messaging. Suspicious accounts may be restricted. | [FREE] | keep the 18+ gate. |
| X7 | Privacy controls | Who can message you: Following, Mutuals or No One. The default follows profile visibility. A separate "Show When Online" toggle. | [FREE] | keep |
| X8 | Share to chat | The share sheet on an activity, route or event can send it to an existing or new chat. Your activity must be visible to Everyone or Followers. For a followers-only activity, everyone in the chat sees a preview, but only your followers can open it. | [FREE] | keep |
| X9 | Message requests | Messages from people outside your allowed list land in a Requests section. | [FREE] | keep |

```
[long-press]
 (thumbs)(heart)(fire)(clap)(+)
 Reply  Copy  Edit*  Delete*  Report**
 * own messages only   ** others' messages only
```

## 9. Challenges

| # | Detail | Behaviour | Strava | Our app |
|---|---|---|---|---|
| H1 | Best activity per day | When several activities fall on one day, only the one that contributes most counts (for distance, the longest). Strava advises recording about 0.5 km extra on runs and 3 km on rides to cover GPS error. | [FREE] | improve: let challenge rules choose "sum per day" or "best per day". |
| H2 | Visibility rule | Leaderboards need Everyone visibility. Followers-only and private activities still count for progress. Manual activities never appear on leaderboards; some challenges count them toward progress. | [FREE] | keep |
| H3 | Group-goal challenges | Show a Collective Total and Top Contributors list instead of individual completion. Every challenge has a Details/Eligibility section. | [FREE] | keep |
| H4 | Units | Progress reads "X / Y km" or "mi" in the user's units. A thin bar on the card and a % on the detail page. Elevation in m or ft (unverified). | [FREE] | keep |
| H5 | Local time window | Start and end use each athlete's local time. An activity counts if its start time is inside the window. Late uploads count while the challenge is open (unverified). | [FREE] | keep. Add a documented grace period for uploads. |
| H6 | Late join backfill | Joining after the start counts earlier qualifying activities, so progress can jump right after Join (unverified). | [FREE] | keep. Animate the jump. |
| H7 | Countdown | Cards show "N days left", then "Ends today" on the last day. Finished challenges move to a Completed/Past section (unverified). | [FREE] | keep |
| H8 | Participant count | Abbreviated as "1.2K" or "1.2M athletes" (unverified). | [FREE] | keep. Use locale-aware compact numbers. |
| H9 | Subscriber challenges | Some challenges, including user-created group challenges, show a subscription label or an upsell on Join (unverified detail). | [SUB] | drop the gate. Everything is free. |
| H10 | User-created group challenge | The creator picks a sport, a goal (distance, time, elevation or longest activity), dates and invitees. They can edit or delete before the start. Invitees join free (unverified). | [SUB] | keep, free for everyone. |
| H11 | Notification toggles | Settings > Push Notifications: challenge share or invite, joined, progress, completed, rewards or donations. | [FREE] | keep. Drop "rewards/donations" unless we add partner rewards. |

```
+-------------------------------------+
| [badge] October 5K x 4              |
| ======-------  62%   12.4 / 20 km   |
| 9 days left - 1.2M athletes  [Join] |
+-------------------------------------+
```

## 10. Badges and Trophy Case

| # | Detail | Behaviour | Strava | Our app |
|---|---|---|---|---|
| T1 | 4 latest badges on profile | Badges are sorted by completion date, and only the 4 most recent show on the profile. The full list is under You > profile picture > All trophies. Tapping a badge opens its challenge. | [?] (the help URL hints Premium; the article text doesn't say) | keep, free. |
| T2 | Milestone challenges | Challenges without a single goal, such as the Monthly Training Series, add no badge until the final milestone. | [FREE] | improve: show partial milestone badges as greyed placeholders. |
| T3 | Trophy grid labels | Each trophy shows the completion month and year. "See all" opens the full grid (unverified). | [FREE] | keep. Use original badge art only, never Strava's assets. |

---

## Acceptance checklist

**Groups tab**
- [ ] G1: The chat icon on Home and Groups opens a hub with DMs, group chats, club channels and requests, and shows an unread count.
- [ ] G2: Club search filters by keyword, location and sport type.
- [ ] G3: The Events tab lists nearby group events and races. Each documented filter narrows the results.
- [ ] G4: Discovery ranks clubs by proximity and activity, with no paid placement.

**Club creation and discovery**
- [ ] C1: The Create Club wizard runs sport, tags (max 3), logo, name and description, privacy, then location, and blocks a 4th tag.
- [ ] C2: The club type is selectable and shows as a header label.
- [ ] C3: Only the listed activity types appear in the club feed and leaderboard.
- [ ] C4: Location renders as "City, State, Country" and omits empty parts.
- [ ] C5: A unique vanity slug resolves and is used in share links.
- [ ] C6: The cover is served at about 1185x580 and about 360x176, and the avatar at 60x60.
- [ ] C7: Joined clubs appear on the athlete's profile.

**Club page**
- [ ] D1: The club page shows Overview, Activities, Stats and Share tabs, and only admins see the gear.
- [ ] D2: Verified clubs show a checkmark.
- [ ] D3: The page shows "N athletes you follow", and tapping it lists them.
- [ ] D4: Join on a public club joins at once. Join on a private club shows Pending, which can be cancelled.
- [ ] D5: The data model supports member, pending, banned, an admin flag and an owner flag.
- [ ] D6: Non-members of a private club see only the header and the request button.
- [ ] D7: A club with zero posts shows a "write the first post" prompt.
- [ ] D8: Leave Club is reachable from Overview and the overflow menu, and needs confirmation.

**Admin and members**
- [ ] A1: Admins can remove a member or promote them to admin, and promote an admin to owner, demote or remove them.
- [ ] A2: Admins can approve or decline pending requests, and the request notification toggle works.

**Stats and leaderboard**
- [ ] S1: The scatterplot renders for clubs of any size and switches metric between distance, elevation and time.
- [ ] S2: Weekly totals show the primary stat, activity count, leader value and most activities.
- [ ] S3: A user ranked outside the top 10 sees their row with 2 athletes above and 2 below.
- [ ] S4: The leaderboard resets Monday 00:00 in the club's time zone, and a last-week toggle works.
- [ ] S5: Columns respect the user's units and are sortable.

**Events**
- [ ] E1: An admin can create an event for any sport, and a non-admin can't.
- [ ] E2: An event can't be saved without a title, date and time. The meeting point accepts a dragged pin or typed text.
- [ ] E3: RSVPed users get a reminder at the configured lead time (default 24 h).
- [ ] E4: Event cards show the local date and time, the Going count with avatars, the sport icon and the recurrence label.
- [ ] E5: The pace or skill tag shows on the event card.

**Club messaging**
- [ ] M1: The messages bar opens the Club Hub, and a channel push notification deep-links to that channel.
- [ ] M2: Club messaging works at any member count.
- [ ] M3: Only admins can create channels, creating a 101st channel is rejected, and both join modes behave as specified.
- [ ] M4: An optional admin-only channel is available.
- [ ] M5: New clubs get an Announcements channel that every member, including future members, is in.
- [ ] M6: In admin-only channels, members can react but can't post.
- [ ] M7: In member channels, members can post, mention, reply, react, share and vote. URLs render a preview.
- [ ] M8: A message deleted by an admin shows "Message deleted" in place.
- [ ] M9: A left channel stays listed and can be rejoined. Leaving the club removes the user from all channels but keeps their old messages.
- [ ] M10: Muting one channel and muting all channels each suppress pushes as specified.
- [ ] M11: A member removed from a channel stays in the club. Channel delete needs confirmation.

**Direct and group messages**
- [ ] X1: The new-chat picker excludes athletes whose privacy blocks the sender.
- [ ] X2: Any member can rename a group chat, and a system line records the rename.
- [ ] X3: The long-press menu shows Edit and Delete only on your own messages and Report only on others'. Edited messages show "edited".
- [ ] X4: Group chats support reactions, GIFs and threaded replies.
- [ ] X5: After a block, the blocked user can no longer open the conversation and its messages are gone.
- [ ] X6: Users under 18, or with no birthdate set, can't access messaging.
- [ ] X7: The Following, Mutuals and No One settings are enforced, and the online toggle hides presence.
- [ ] X8: Sharing a followers-only activity into a chat shows a preview to everyone but full details only to followers.
- [ ] X9: Messages from outside the allowed list land in Requests.

**Challenges**
- [ ] H1: With two activities on one day, the "best per day" rule counts only the larger one.
- [ ] H2: A followers-only activity counts for progress but doesn't appear on the leaderboard. Manual activities follow each challenge's rule.
- [ ] H3: Group-goal challenges show a Collective Total and Top Contributors.
- [ ] H4: Progress shows "X / Y" in the user's units, with a bar on the card and a % on the detail page.
- [ ] H5: An activity starting inside the user's local window counts, and late uploads within the grace period count.
- [ ] H6: Joining late backfills qualifying activities since the start.
- [ ] H7: Cards show "N days left", then "Ends today". Finished challenges move to Completed.
- [ ] H8: Participant counts use compact K/M formatting.
- [ ] H9: No challenge shows a paywall or upsell.
- [ ] H10: Any user can create a group challenge (sport, goal type, dates, invitees) and edit or delete it before the start.
- [ ] H11: Each challenge notification toggle independently controls its push.

**Badges and Trophy Case**
- [ ] T1: The profile shows the 4 most recent badges, All trophies lists every badge, and tapping a badge opens its challenge.
- [ ] T2: Milestone challenges show greyed placeholder badges until the final milestone.
- [ ] T3: Trophies show the completion month and year and use original art only.

## Sources

- https://support.strava.com/hc/en-us/articles/19255163090573-Messaging-on-Strava
- https://support.strava.com/hc/en-us/articles/45629997681037-Club-Messaging
- https://support.strava.com/hc/en-us/articles/221622188-Clubs-on-the-Mobile-App
- https://support.strava.com/hc/en-us/articles/216919177-Strava-Challenges
- https://support.strava.com/hc/en-us/articles/216918557-The-Strava-Trophy-Case
- https://developers.strava.com/docs/reference/
- https://www.bikeradar.com/news/you-can-finally-send-direct-and-group-messages-on-strava
- https://9to5mac.com/2026/07/09/strava-gets-an-events-tab-with-upcoming-races-group-runs-more/
- https://support.strava.com/hc/en-us (general help center; the source for items marked unverified)

Dropped finding: "Member names truncated outside club". The "Jane D." format applies only to third-party API output, not to the app's UI.

Merged findings: "Manual and private activities may not count" was merged into H2, and the duplicate trophy-case item into T1 and T3.
