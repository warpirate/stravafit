# StravaFit backend API (v1, 2026-11-03)

The contract the Android app codes against. Everything here is live on the hosted Supabase project and
covered by the pgTAP tests in `supabase/tests/` (run against the hosted project, see the end).

| | |
|---|---|
| Project URL | `https://kolohifwfegqsayrzkqn.supabase.co` |
| Publishable key (use this) | `sb_publishable_vxp0BNo6nXEMVJCoYZWtKQ_UDbjFzmc` |
| Legacy anon key (same rights) | `eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6ImtvbG9oaWZ3ZmVncXNheXJ6a3FuIiwicm9sZSI6ImFub24iLCJpYXQiOjE3OTM2NzIxMTQsImV4cCI6MjEwOTI0ODExNH0.Dptke9WLJ0YAtoS9aKvuxMdW12o111eAgIF4vA8L0LQ` |
| REST | `{URL}/rest/v1/…`, RPC: `POST {URL}/rest/v1/rpc/<name>` with a JSON body of named parameters |
| Auth | `{URL}/auth/v1/…` |
| Storage | `{URL}/storage/v1/object/<bucket>/<path>` |
| Functions | `{URL}/functions/v1/<name>` |

Both keys are public by design (they only grant what row-level security allows). Never ship a service key.
Send `apikey: <publishable key>` on every request, plus `Authorization: Bearer <access_token>` once signed
in. `supabase-kt` (Auth, Postgrest, Storage modules) does all of this; the shapes below are what it sends.

Sign-in is optional. Everything on the phone works without an account; the server is only for sync and
social features.

## Conventions

- Times are ISO-8601 with offset (`timestamptz`), distances metres, durations seconds, speeds m/s.
- Ids are UUIDs. The device's activity `uuid` (`ActivityEntity.uuid`) is the sync key `client_uuid`; the
  Room `Long` id never leaves the phone.
- Enum values are lower-case as in the DB (`everyone`, `followers`, `only_me`), except `sport_type`, which
  uses the device's `Sport` names (`Run`, `TrailRun`, `Ride`, `WeightTraining`, …; the server has 50+).
- Errors come back as PostgREST JSON `{ code, message, details, hint }`. `code` is the SQLSTATE:

| code | meaning | typical cause |
|---|---|---|
| `22023` | invalid parameter | bad payload field, unknown sport, bad timezone |
| `P0002` | not found | unknown id/token; also used for "not allowed" so nothing leaks |
| `42501` | not permitted | not signed in, RLS refused the row, role too low |
| `55000` | not ready | file not uploaded yet, live session already ended |
| `54000` | limit reached | see [Limits](#limits) |
| `23514` | check failed | age gate, birthday change, bad photo path |
| `23505` | duplicate | id already used by another account |

- Lists are keyset-paginated: pass the last row's sort key back (`p_before…`), never offsets.
- `geography` columns (`route`, `start_point`, …) come back as hex EWKB. Draw maps from the encoded
  polyline columns (`summary_polyline`, precision 5) instead.

## Auth (email + password, no confirmation)

Sign-up returns a session immediately (email confirmation is off; see the dashboard checklist).

```http
POST /auth/v1/signup
{ "email": "a@b.c", "password": "…",
  "data": { "handle": "maria_runs", "first_name": "Maria", "last_name": "Rossi",
            "country": "IT", "birthday": "1994-05-17" } }
→ 200 { "access_token", "refresh_token", "expires_in", "user": { "id", … } }
```

The database trigger turns `data` into the profile:

- `handle`: `^[A-Za-z0-9_.]{3,30}$`, unique ignoring case. Check it first with
  `rpc/is_handle_available { "p_handle": "…" } → true|false` (works signed out). If it is taken or
  malformed anyway, the account gets `user_<12 hex>` and the user can rename it later.
- `birthday` `YYYY-MM-DD`. **Age gate:** under 13 (under 16 for DE, NL, IE, HU, LU, SK, RO, HR, PL; table
  `signup_age_rules`, readable signed out) rejects the sign-up. GoTrue reports that only as
  `500 "Database error saving new user"`, so check the age in the app first and show a proper message. A
  missing or malformed birthday is accepted, but the account keeps minor privacy defaults until one is set.
- Under-18s start with a private profile and stricter defaults (≥ 400 m map trimming, followers-only).
- Other failures: `422 user_already_exists`, `422 weak_password`.

| Action | Call |
|---|---|
| Sign in | `POST /auth/v1/token?grant_type=password` `{ email, password }` |
| Refresh | `POST /auth/v1/token?grant_type=refresh_token` `{ refresh_token }` (access tokens last 1 h) |
| Sign out | `POST /auth/v1/logout` |
| Password reset | `POST /auth/v1/recover` `{ email }`, with `redirect_to=openactivity://auth-callback`. The mail link opens the app with a recovery session; then `PUT /auth/v1/user { password }`. Needs custom SMTP (dashboard checklist). |
| Change email/password | `PUT /auth/v1/user { email }` / `{ password }` |

Password rules on the hosted project are whatever the dashboard says (default: at least 6 characters). The
local config uses 10+ with upper, lower and digits; validate at least that in the app.

## Profile and settings

| Read / write | How |
|---|---|
| My profile | `GET /rest/v1/profiles?id=eq.<me>`; `PATCH` the same with any of `handle, first_name, last_name, avatar_path, bio, city, region, country, profile_visibility` |
| Private profile toggle | `profile_visibility`: `everyone` (default, follows accepted at once) or `followers` (follows become requests; strangers see name, photo, city/region/country only). Switching back to `everyone` accepts all waiting requests. |
| My settings | `GET/PATCH /rest/v1/profile_settings?user_id=eq.<me>`: `birthday` (once), `gender`, `units`, `default_visibility` (default `followers`), `default_hide_start_m/end_m` (0–1600), `default_hide_map`, `default_hide_heart_rate/pace/power/calories/start_time/elevation`, `health_data_consent` (heart rate is dropped server-side until true), `feed_order`, audiences |
| Anyone's card | `GET /rest/v1/profile_cards?id=eq.<user>`: `handle, first_name, last_name, avatar_path, city, region, country, profile_visibility, bio` (only if `full_profile`), `full_profile`, `viewer_relation` (`self/follower/stranger/blocked/anonymous`), `viewer_follow_status` (`pending/accepted/null`), `follower_count`, `following_count` (null when not `full_profile`) |
| Avatar | upload to `avatars/<me>/<file>.jpg` (public bucket, JPEG/PNG/WebP ≤ 5 MB), then set `avatar_path` to `<me>/<file>.jpg`. Public URL: `{URL}/storage/v1/object/public/avatars/<avatar_path>` |
| Blocks / mutes | `POST/DELETE /rest/v1/blocks` `{ blocker_id: me, blocked_id }`; `mutes` `{ muter_id: me, muted_id }`. A block removes follows both ways and the other side is never told. |

## Activity sync from the phone

### 1. Upsert the activity

`POST /rest/v1/rpc/upsert_activity_from_device { "p_payload": { … } }`

Idempotent on `client_uuid`: the first call creates, later calls update (edits, crops, re-parses). Send the
whole activity every time.

```jsonc
{
  "client_uuid": "6f1c…",               // required: ActivityEntity.uuid
  "sport_type": "Run",                   // required
  "start_time": "2026-11-03T06:30:00+05:30", // required
  "elapsed_s": 1800, "moving_s": 1750,   // required, 0 <= moving <= elapsed <= 30 days
  "name": "Morning Run", "description": "…",
  "visibility": "followers",             // optional; omitted = account default on create, unchanged on update
  "workout_kind": "default",             // default | race | long | workout | commute
  "tz_name": "Asia/Kolkata", "utc_offset_s": 19800,
  "distance_m": 5012.4, "elev_gain_m": 31, "elev_loss_m": 30, "elev_high_m": 220, "elev_low_m": 190,
  "avg_speed_mps": 2.86, "max_speed_mps": 4.1, "avg_hr": 151, "max_hr": 176,
  "avg_cadence": 168, "max_cadence": 182, "avg_watts": 240, "max_watts": 410,
  "weighted_avg_watts": 250, "kilojoules": 430, "calories": 390, "effort_score": 55,
  "is_commute": false, "is_indoor": false, "is_manual": false, "muted": false, "device_name": "Pixel 8",
  "polyline": "u{~vFvyys@…",             // encoded polyline, precision 5, of the simplified track (<= 200k chars)
  "hide_start_m": 200, "hide_end_m": 200, "hide_map": false,  // optional, as for visibility
  "hide_heart_rate": false, "hide_pace": false, "hide_power": false,
  "hide_calories": false, "hide_start_time": false, "hide_elevation": false,
  "laps":   [{ "lap_index": 0, "trigger": "DISTANCE", "start_offset_s": 0, "elapsed_s": 300,
               "moving_s": 298, "distance_m": 1000, "elev_gain_m": 5, "avg_speed_mps": 3.3,
               "max_speed_mps": 4, "avg_hr": 150, "max_hr": 160, "avg_cadence": 170,
               "avg_watts": 240, "max_watts": 300, "name": null }],
  "splits": [{ "unit": "METRIC", "split_index": 0, "distance_m": 1000, "elapsed_s": 300,
               "moving_s": 298, "elev_diff_m": 2, "avg_speed_mps": 3.3, "gap_speed_mps": 3.4,
               "avg_hr": 150, "avg_watts": 240 }]
}
```

- `laps` / `splits`, when present, replace the stored list (≤ 1000 laps, ≤ 2000 splits). Omit the key to
  leave them alone. Lap `trigger` is `MANUAL/DISTANCE/TIME/POSITION/SESSION_END` (anything else → manual);
  split `unit` is `METRIC`/`km` or `IMPERIAL`/`mi`.
- Heart rate is dropped unless `health_data_consent` is on; minors keep ≥ 400 m trimming whatever is sent.
- The feed time (`published_at`) is when the activity ended, so syncing an old history does not flood
  followers' feeds.
- The server derives the privacy-trimmed public route (endpoint trim with per-activity jitter, hidden
  places, hide map). Viewers only ever get that.

Returns:

```json
{ "id": "<activity uuid on the server>", "client_uuid": "…", "created": true,
  "owner_stream_path": "<me>/<id>/owner.json.gz",
  "public_stream": { "path": "<me>/<id>/public.json.gz", "include_latlng": true,
                     "trim_start_m": 312.4, "trim_end_m": 287.9, "drop_channels": ["heartrate"] },
  "photo_prefix": "<me>/<id>/" }
```

Errors: `22023` (bad/missing field), `54000` (polyline too long), `42501` (not signed in).

### 2. Upload the streams (optional but needed for charts)

Two gzip-compressed JSON files to the `streams` bucket, `Content-Type: application/gzip`, ≤ 50 MB, upsert
allowed (`x-upsert: true`):

- `streams/<owner_stream_path>`: everything, only the owner can read it.
- `streams/<public_stream.path>`: what viewers get. Build it from the same samples, then **drop every
  sample whose `distance` < `trim_start_m` or > (last distance − `trim_end_m`)**, remove every channel in
  `drop_channels`, and leave out `latlng` when `include_latlng` is false.

Stream file format (columnar, one entry per sample, `null` where a sample has no value):

```jsonc
{ "v": 1, "client_uuid": "…", "sample_count": 1800,
  "time":      [0, 1, 2, …],          // seconds since start (monotonic offset)
  "latlng":    [[12.97, 77.59], …],
  "distance":  [0.0, 2.8, …],         // cumulative metres
  "altitude":  [212.4, …],
  "velocity_smooth": [2.8, …],        // m/s
  "heartrate": [140, …], "cadence": [168, …], "watts": [240, …],
  "moving":    [true, …] }
```

Then `POST /rest/v1/rpc/register_activity_streams { p_activity_id, p_sample_count, p_stream_types }` with
the channel names present (allowed: `time, latlng, distance, altitude, velocity_smooth, heartrate, cadence,
watts, temp, moving, grade_smooth`). Returns `{ owner_path, public_path, public_registered }`. `55000` if
`owner.json.gz` is not uploaded yet.

Viewers find the file through `GET /rest/v1/activity_stream_files?activity_id=eq.<id>` → `{ bucket, path,
stream_types, sample_count }` and download `streams/<path>` with their token (or anonymously for public
activities). The owner gets `owner.json.gz` from the same view.

**Privacy changes fail closed.** When hidden stats, hide map or the trimmed route change (an edit, or a new
hidden place that re-trims old activities), the public file is unregistered at once and viewers lose it.
After any such change, and on every sync, call `POST /rest/v1/rpc/list_stale_public_streams { p_limit }` →
`[{ activity_id, client_uuid, public_stream }]`, rebuild and re-upload each `public.json.gz`, then call
`register_activity_streams` again. A public file uploaded before the change is never re-registered.

### 3. Photos

1. Resize to 1600 px on the long side and **re-encode as JPEG (or WebP)** so no EXIF/GPS survives. The
   server cannot strip metadata: what you upload is what viewers get.
2. Upload to `photos/<me>/<activity id>/<photo uuid>.jpg` (`image/jpeg` or `image/webp`, ≤ 2 MB). Only the
   owner of that activity may write there.
3. `POST /rest/v1/activity_photos { id: <photo uuid>, activity_id, storage_path, width, height, position,
   caption }`. `23514` if the path is not exactly `<owner>/<activity id>/<id>.jpg|webp`, `55000` if the
   file is missing, `54000` after 20 photos.

Read: `GET /rest/v1/activity_photos?activity_id=in.(…)&order=position` and download with the viewer's token.
Visibility follows the activity, and strangers see photos only on open profiles. Reorder or caption:
`PATCH activity_photos?id=eq.…` (`position`, `caption`). Remove: `DELETE activity_photos?id=eq.…`, then
delete the file.

### 4. Edits and deletes

- Edit: call `upsert_activity_from_device` again with the full payload. Small metadata edits may also
  `PATCH /rest/v1/activities?client_uuid=eq.<uuid>` (name, description, sport_type, visibility, hide_*, …).
- Delete: `DELETE /rest/v1/activities?client_uuid=eq.<uuid>` (owner only; laps, splits, streams row, photo
  rows, comments and high-fives cascade). Then remove the Storage objects: `streams/<me>/<id>/owner.json.gz`,
  `…/public.json.gz` and every photo path (list them before deleting the row). Until then they are already
  unreadable to others.
- My own rows: `GET /rest/v1/activities?select=id,client_uuid,updated_at` to reconcile.

### 5. Hidden places

`POST /rest/v1/rpc/upsert_privacy_zone { p_id, p_lat, p_lng, p_radius_m, p_label }` upserts by the
device's own uuid (radius 200–1600 m). `GET /rest/v1/privacy_zones` lists them, `DELETE
/rest/v1/privacy_zones?id=eq.<uuid>` removes one. Changing hidden places re-trims older activities, so run
the stale-stream step afterwards.

## Feed, profiles, engagement

| Call | Returns |
|---|---|
| `rpc/get_home_feed { p_before_published_at, p_before_id, p_limit }` | `activity_cards` rows of me + accepted follows, newest first, no muted activities/accounts. Page with the last row's `published_at` and `id`. Limit 1–100 (20). |
| `rpc/get_profile_activities { p_owner, p_before_published_at, p_before_id, p_limit }` | one athlete's visible activities (works signed out for public ones) |
| `GET /rest/v1/activity_cards?id=eq.<id>` | one activity as the caller may see it. Hidden stats are `null` for others, `start_time` only for the owner and followers (when not hidden), gear only for owner/followers. Map: `summary_polyline` (already trimmed for viewers). Also `high_five_count`, `comment_count`, `viewer_high_fived`, `is_owner`. |
| `GET /rest/v1/activity_laps?activity_id=eq.<id>&order=lap_index`, `activity_splits?activity_id=eq.<id>&unit=eq.km&order=split_index` | laps/splits with hidden values nulled |
| High-five | `POST /rest/v1/high_fives { activity_id, user_id: me }`; undo `DELETE high_fives?activity_id=eq.…&user_id=eq.<me>`. Not on your own activity (`42501`). |
| Comments | `GET /rest/v1/comments?activity_id=eq.<id>&order=created_at`; `POST { activity_id, author_id: me, body }` (1–2000 chars); `PATCH body` (author); `DELETE` (author or activity owner) |
| Report | `POST /rest/v1/reports { reporter_id: me, target_type, target_id, reason, details }` (20 per day) |

## Social graph

| RPC | Params → returns | Notes |
|---|---|---|
| `follow_user` | `p_target` → `accepted` or `pending` | idempotent; `P0002` for missing *or* blocked |
| `unfollow_user` | `p_target` → bool | also cancels a pending request |
| `respond_follow_request` | `p_follower, p_approve` → bool | clears the request from the inbox |
| `remove_follower` | `p_follower` → bool | they may follow again (unlike a block) |
| `get_followers` | `p_user, p_before, p_limit` (50, ≤ 200) → `[{ followed_at, profile }]` | `profile` is a `profile_cards` object; empty for a private profile you don't follow. Page with `followed_at`. |
| `get_following` | same | |
| `get_follow_requests` | `p_limit` → `[{ requested_at, profile }]` | my incoming requests |
| `search_profiles` | `p_query, p_limit` (20, ≤ 50) → `profile_cards` rows | handle or name contains the query (≥ 2 chars), exact/prefix handle first; people blocked either way are left out |

## Notifications inbox (in-app, no push)

| Call | |
|---|---|
| `rpc/get_notifications { p_before, p_limit }` (30, ≤ 100) | `[{ id, kind, created_at, read_at, payload, actor_id, actor_handle, actor_first_name, actor_last_name, actor_avatar_path, activity_id, activity_name, comment_id, comment_body }]`, newest first; page with `created_at`. `kind`: `high_five, comment, follow, follow_request, follow_accepted, upload_ready, upload_failed`. Activity fields are null when you can no longer see it. |
| `rpc/unread_notification_count {}` | integer for the badge |
| `rpc/mark_notifications_read { p_ids }` | `null` = all; returns how many changed |
| `DELETE /rest/v1/notifications?id=eq.…` | dismiss |
| Realtime | `notifications` is in the `supabase_realtime` publication; RLS limits each user to their own rows |

## Groups

Invite-only clubs. Members see the group, its members, leaderboard and challenges; nobody else does.

| Call | |
|---|---|
| `rpc/create_group { p_name, p_sport, p_description, p_timezone, p_avatar_path }` | → the `groups` row incl. `invite_token`. Name 1–80, description ≤ 1000, IANA timezone (default `UTC`, `22023` if unknown), `p_sport` null = all sports. ≤ 50 owned groups. Avatar: upload to `avatars/<me>/…` and pass the path. |
| `GET /rest/v1/groups` | my groups (`id, owner_id, name, sport, description, avatar_path, timezone, invite_token, member_count, …`) |
| `PATCH /rest/v1/groups?id=eq.…` | owner/admin: `name, sport, description, avatar_path, timezone` |
| `DELETE /rest/v1/groups?id=eq.…` | owner |
| `rpc/get_group_invite { p_token }` | preview before joining, works signed out → `[{ group_id, name, sport, description, avatar_path, member_count, is_member }]` (empty when invalid) |
| `rpc/join_group { p_token }` | → group id; idempotent. `P0002` for a bad/rotated/revoked token or when blocked by the owner; `54000` at 1000 members. |
| `rpc/rotate_group_invite { p_group }` | owner/admin → new token, old links die |
| `rpc/revoke_group_invite { p_group }` | owner/admin → invites off (`invite_token` null) until the next rotate |
| `rpc/get_group_members { p_group }` | `[{ user_id, role, joined_at, handle, first_name, last_name, avatar_path }]`, owner first |
| `rpc/set_group_member_role { p_group, p_user, p_role }` | owner only: `admin` / `member`, or `owner` to hand over (old owner becomes admin) |
| Leave / remove | `DELETE /rest/v1/group_members?group_id=eq.…&user_id=eq.<user>`: yourself (not the owner), the owner removes anyone, an admin removes plain members. RLS silently deletes nothing otherwise, so check the returned rows (`Prefer: return=representation`). |
| `rpc/get_group_leaderboard { p_group, p_day }` | the ISO week (Mon–Sun, group timezone) containing `p_day` (default today) → `[{ user_id, handle, first_name, last_name, avatar_path, distance_m, moving_s, elev_gain_m, activity_count, rank_distance, rank_moving_time, rank_elevation, week_start, week_end }]`, every member, zeros included |

**Leaderboard visibility rule.** A member's activity counts for you only if you could open it anyway:
`everyone` activities count for all members, `followers` activities only for members who follow that
athlete, `only_me` only for the athlete. So two members may see different totals for the same person, but
no leaderboard ever reveals an activity its viewer cannot see. Hidden elevation counts as 0 for others,
members blocked with you are left out, and when the group has a sport only that sport counts. Members
should be told: "Totals include activities you can see."

Challenges (owner/admin create; members read):

| Call | |
|---|---|
| `POST /rest/v1/group_challenges { group_id, title, description, metric, target, sport, starts_on, ends_on }` | `metric`: `distance` (m), `moving_time` (s), `elevation` (m), `activity_count`. Dates inclusive, in the group timezone, ≤ 366 days. `sport` null = the group's sport. `PATCH`/`DELETE` by owner/admin. |
| `rpc/get_group_challenges { p_group }` | `[{ id, title, description, metric, target, sport, starts_on, ends_on, created_by, progress, completed, status }]`, `status` = `upcoming/active/finished`; `progress` is the collective total you can see |
| `rpc/get_challenge_progress { p_challenge }` | `[{ user_id, handle, first_name, last_name, avatar_path, value, share, rank }]`, `share` = value / target |

**Invite link.** Share `https://kolohifwfegqsayrzkqn.supabase.co/functions/v1/join?t=<invite_token>`. It
redirects to the landing page `https://warpirate.github.io/stravafit/join/?t=…` (group name, members,
"Open in StravaFit"), which opens `openactivity://group/join?t=<token>` (on Android Chrome via an
`intent://` URL that falls back to the GitHub releases page). The app must handle that deep link: add
`<data android:host="group" />` to the existing `openactivity` intent filter (it has `activity`, `route`,
`record` today), then show the preview (`get_group_invite`) and, once signed in, call `join_group`.
JSON for the page: `GET …/functions/v1/join?t=…&format=json` → `{ name, sport, description, member_count,
avatar_url }` or 404.

## Live location (Beacon)

| Call | |
|---|---|
| `rpc/start_live_session { p_sport, p_duration_minutes }` | → the `live_sessions` row (`id`, `token`, `expires_at`, …). 5–2880 min (default 720). Ends any other active session of yours. ≤ 20 per day. |
| `rpc/update_live_position { p_session, p_lat, p_lng, p_accuracy_m, p_speed_mps, p_battery_pct, p_recorded_at }` | every ~30 s while recording → `true`, or `false` if < 5 s after the previous point (ignored). `55000` once ended/expired. Keeps the newest 240 points as the trail. Send the fix time as `p_recorded_at` when flushing queued points after a network gap, oldest first. |
| `rpc/end_live_session { p_session }` | → bool. Call it when the recording stops. |
| `GET /rest/v1/live_sessions?order=started_at.desc` | my sessions; `DELETE` to remove one |

Share URL: `https://kolohifwfegqsayrzkqn.supabase.co/functions/v1/live?t=<token>`. It redirects to the
viewer `https://warpirate.github.io/stravafit/live/?t=…`: map (MapLibre + OpenStreetMap tiles), first name,
avatar, sport, "Live / Ended / Expired", "updated 30 s ago", speed, battery, accuracy; it refreshes every
15 s. Nobody can read sessions over REST except their owner; the page reads
`…/functions/v1/live?t=…&format=json` → `{ state, sport, started_at, ended_at, expires_at, athlete:
{ first_name, avatar_url }, position: { lat, lng, accuracy_m, speed_mps, battery_pct, at } | null, trail:
[[lat, lng, epoch_s], …], server_time }`. Ended and expired sessions return no position and no trail. The
position is exact on purpose (hidden places don't apply), so say so in the share sheet.

## Storage buckets

| Bucket | Path | Write | Read | Types, size |
|---|---|---|---|---|
| `avatars` (public) | `<me>/<file>` | owner | anyone | JPEG/PNG/WebP, 5 MB |
| `streams` | `<owner>/<activity>/owner.json.gz`, `…/public.json.gz` | owner of the activity | owner: both; viewers of the activity: `public.*` | gzip/JSON, 50 MB |
| `photos` | `<owner>/<activity>/<photo>.jpg\|webp` | owner of the activity | as the activity (strangers only on open profiles) | JPEG/WebP, 2 MB |
| `originals`, `photo-uploads`, `exports` | | for the future server worker; the app does not use them now | | |

## Limits

Polyline 200 000 chars · 1000 laps · 2000 splits · stream file 50 MB · photo 2 MB, 20 per activity ·
avatar 5 MB · 50 owned groups · 1000 members per group · live: one update per 5 s, 240-point trail,
48 h max, 20 sessions per day · reports 20 per day · page sizes as listed · Auth rate limits are the
hosted defaults (dashboard > Authentication > Rate Limits).

## Dashboard steps the user must do by hand

These are project settings, not migrations, and the tooling used here cannot change them:

1. **Authentication > Sign In / Providers > Email**: turn **Confirm email** off (keep Email enabled). Until
   then sign-up returns no session and waits for a confirmation mail.
2. **Authentication > URL Configuration > Redirect URLs**: add `openactivity://auth-callback` (password
   reset links). Site URL may stay as is.
3. **Authentication > Emails > SMTP Settings**: set up a custom SMTP sender. The built-in sender only mails
   the project team's own addresses and is heavily rate limited, so password reset does not work for real
   users without it.
4. Optional, to match `supabase/config.toml`: **Authentication > Policies (Passwords)**: minimum length 10,
   require lower, upper case and digits; turn on leaked-password protection if offered.
5. **GitHub > repository Settings > Pages > Source: GitHub Actions** (once). The `Pages` workflow then
   publishes `web/` on the next push to `main` that touches `web/`, or run it by hand (Actions > Pages >
   Run workflow). Until then the live and invite links redirect to a 404.

## Not built (by decision)

No server-side file parsing worker: jobs still go to the pgmq queues (`post_process`, `privacy_reprocess`,
`cleanup`) on every activity change and nothing consumes them yet. They are small, but they grow until a
worker or a purge job exists. No push notifications, no segments, no messaging.
