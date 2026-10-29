# Backend (Supabase v0)

The server side of StravaFit: Postgres + PostGIS on Supabase (ADR-006), with privacy enforced in the database through row level security and read models. Heavy jobs run in a Kotlin worker that reads a Postgres job queue (ADR-008). The worker itself is a separate work package. Self-hosting instructions are in [`infra/docker/README.md`](../infra/docker/README.md).

```
backend/
├── supabase/
│   ├── config.toml        Supabase CLI config (local dev)
│   ├── migrations/        ordered SQL migrations (the schema)
│   ├── seed.sql           3 demo athletes in Berlin (local dev only)
│   └── tests/             pgTAP tests (RLS matrix, blocks, hidden stats, ...)
├── ci/supabase-stubs.sql  minimal Supabase stand-ins for bare-Postgres CI
└── scripts/verify-db.sh   run migrations + seed + pgTAP on a throwaway container
```

## Run it

| Goal | Command |
|---|---|
| Verify everything on plain PostGIS (CI) | `backend/scripts/verify-db.sh` (needs Docker) |
| Local Supabase with Studio | `cd backend && supabase start`, then `supabase db reset` |
| pgTAP against the local Supabase | `cd backend && supabase test db` |

`verify-db.sh` starts `postgis/postgis:16-3.4`, applies `ci/supabase-stubs.sql` (roles, `auth.uid()`, `storage.objects`), then every migration in order, then the seed. It installs pgTAP and runs `pg_prove` over `supabase/tests`. Pass `--keep` to leave the container running for inspection.

## Schema map

| Migration | Contents |
|---|---|
| `…0100_extensions` | `postgis`, `pgcrypto`, `citext` in schema `extensions`; `pgmq` when the server has it; the internal `private` schema |
| `…0200_enums` | sport types, visibility, audiences, upload status, report reasons, … |
| `…0300_profiles` | `profiles` (public card), `profile_settings` (owner-only), age gate, consent audit, staff roles, auth-user trigger |
| `…0400_social_graph` | `follows` (pending/accepted), `blocks`, `mutes`; relationship predicates |
| `…0500_privacy_zones_gear` | hidden places, gear |
| `…0600_activities` | `activities`, `laps`, `splits`, `activity_streams`, `activity_private_notes`; privacy trimming |
| `…0700_uploads_jobs` | job queue API, `uploads` lifecycle, upload RPCs, activity → job triggers |
| `…0800_engagement` | `high_fives`, `comments`, `notifications`, `reports` |
| `…1000_read_models` | `profile_cards`, `activity_cards`, `activity_laps`, `activity_splits`, `activity_stream_files`, feed RPCs |
| `…1100_social_rpcs` | `follow_user`, `unfollow_user`, `respond_follow_request`, `remove_follower`, `mark_notifications_read` |
| `…1200_storage` | buckets and object policies |
| `…1300_realtime_and_guards` | realtime publication, and a check that fails the migration if any table lacks RLS |

## Privacy model

1. **Base tables are owner-only.** `activities`, `laps`, `splits`, `activity_streams`, `profiles` and `profile_settings` can be selected only by their owner. Other users read the **read models** (`activity_cards`, `profile_cards`, …). These are security-barrier views that:
   - filter rows with `private.viewer_relation()`, which returns `self`, `follower`, `stranger`, `blocked` or `anonymous`, and with `private.can_view_activity()`;
   - return `NULL` for every hidden stat to anyone except the owner, so hidden values never leave the database (with hidden pace, split and lap times are `NULL` too);
   - serve the privacy-trimmed geometry (`route_public`, `*_point_public`, `summary_polyline_public`) to anyone except the owner.
2. **Visibility rules.** An `everyone` activity is visible to anyone, including anonymous users. A `followers` activity is visible to accepted followers only. An `only_me` activity is visible to its owner only. A block hides everything in both directions, and the blocked user is never told.
3. **Always hidden from strangers.** Start time and gear, even on `everyone` activities. Whenever a viewer may not see the start time, `published_at` is cut to the UTC day and `created_at`/`updated_at` are `NULL`, since the upload time would give the start time away. A followers-only profile shows strangers name, photo and approximate location only.
4. **Map privacy.** Every activity stores `hide_start_m`/`hide_end_m` (default 200 m, at least 400 m for minors) and `hide_map`. `private.privacy_trim()` follows privacy-safety §B:
   - each end is trimmed by its distance plus a deterministic per-activity jitter of up to 25 % (HMAC of the activity id with a per-instance secret in `private.instance_secrets`);
   - if any point in the first or last 20 % of the route lies inside a hidden place grown by the same jitter, the cut moves to where the route leaves it, so public endpoints never sit exactly on the circle. Points are sampled every 10 m and tested with exact geodesic distance;
   - if trimming leaves less than 500 m, or removes more than 60 % of the route, the whole map is hidden.

   Adding a hidden place re-trims past activities, except those whose map privacy the owner edited individually.
5. **Private notes** live in their own owner-only table, and no view reads it.
6. **Health data.** Heart rate (on activities, laps and splits) is dropped on every write unless the owner has given health-data consent. Consent changes are recorded in `consent_records`. Purging heart rate already stored when consent is withdrawn is a worker job that is not built yet.
7. **Age gate.** A sign-up whose birthday is under 13 is rejected. The limit is 16 for countries listed in `signup_age_rules`. Minors cannot weaken their privacy defaults, and a birthday cannot be changed once it is set.

## API surface (PostgREST)

- Read: `profile_cards`, `activity_cards`, `activity_laps`, `activity_splits`, `activity_stream_files`, `comments`, `high_fives`, `notifications`, `uploads`, plus the owner's own base tables.
- Write: owner rows in `activities` (only the columns clients may set; idempotent on `(owner_id, client_uuid)`), `laps`, `splits`, `activity_private_notes`, `privacy_zones`, `gear`, `blocks`, `mutes`, `high_fives`, `comments`, `reports`, and own `profiles`/`profile_settings`.
- RPC:
  - `get_home_feed(before_published_at, before_id, limit)`: the home feed. Chronological (ADR-013), keyset-paginated, without muted activities or accounts.
  - `get_profile_activities(owner, …)`
  - `follow_user`, `unfollow_user`, `respond_follow_request`, `remove_follower`
  - `mark_notifications_read`
  - `create_upload`, `submit_upload`

## Upload pipeline and worker contract

1. The app calls `create_upload(client_uuid, format, byte_size, sha256)`. The call is idempotent per `client_uuid` and returns the reserved `storage_path`.
2. The app uploads the file to `originals/<storage_path>`. Storage policy only accepts that exact path.
3. The app calls `submit_upload(id)`. The status moves to `queued` and an `ingest` job is enqueued.
4. The worker, using the service role over JDBC:
   ```sql
   select * from private.job_claim('ingest', 10, 60);        -- lease jobs
   select private.worker_upload_started(:upload_id);          -- queued -> processing
   -- parse, recompute metrics (shared:metrics), insert activity/laps/splits/streams
   select private.worker_upload_finished(:upload_id, :activity_id);  -- -> ready / duplicate
   select private.worker_upload_failed(:upload_id, :error);   -- -> error (+ inbox row)
   select private.job_complete('ingest', :msg_id);            -- or job_fail(...) to retry
   ```
   `job_fail` retries with exponential backoff, from 30 s up to a cap of 1 h. After the queue's `max_attempts` the job is dead-lettered into `private.job_failures`.
5. Triggers on `activities` enqueue `post_process` on insert, `privacy_reprocess` on a visibility or hidden-field change, and `cleanup` on delete. The worker rebuilds public stream files and purges derived data.

Queues use **pgmq** when the extension is installed (Supabase has it). Otherwise they fall back to a `FOR UPDATE SKIP LOCKED` table. The API is the same either way.

## Storage layout

| Bucket | Path | Who reads | Who writes |
|---|---|---|---|
| `originals` | `<owner>/<upload>.<ext>` | owner | owner (reserved path only) |
| `streams` | `<owner>/<activity>/{owner,public}.sfstream` | owner: all; viewers: `public.*` | worker |
| `photo-uploads` | `<owner>/<activity>/<photo>` | owner | owner (own activities only) |
| `photos` | `<owner>/<activity>/<photo>.webp` | activity viewers (strangers only if the profile is public) | worker, after it strips EXIF/GPS |
| `avatars` | `<owner>/…` | public | owner |
| `exports` | `<owner>/<archive>.zip` | owner | worker |

## Not in v0

These are deferred, and no table or column pretends otherwise:
- segments, leaderboards, routes, clubs, challenges and messaging;
- the moderation case/strike model beyond `reports`;
- push fan-out (Edge Function or worker) and the worker itself;
- account export and deletion jobs;
- the Strava import pipeline.
