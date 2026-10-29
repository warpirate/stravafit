-- SPDX-License-Identifier: AGPL-3.0-or-later
--
-- Activities and their children (laps, splits, stream artifacts, private notes).
--
-- Privacy model
--   * Base tables are OWNER-ONLY for SELECT. Nobody else ever reads them.
--   * Other viewers read the security-barrier views in
--     20261029001000_read_models.sql, which filter by visibility/follows/blocks
--     and replace hidden stats with NULL. Hidden values therefore never leave
--     the database for non-owners.
--   * Two copies of the geometry exist: the owner's simplified route and a
--     privacy-trimmed public route (start/end trimming + hidden places + hide
--     map). Viewers only ever receive the trimmed copy.

create table public.activities (
  id                   uuid primary key default gen_random_uuid(),
  owner_id             uuid not null references public.profiles (id) on delete cascade,
  -- Idempotency key generated on the device for offline-first sync
  -- (blueprint §10 rule 2). Re-sending the same recording is a no-op.
  client_uuid          uuid not null,
  upload_id            uuid,  -- FK added in 20261029000700_uploads_jobs.sql
  name                 text not null check (length(name) between 1 and 255),
  description          text check (length(description) <= 5000),
  sport_type           public.sport_type not null,
  workout_kind         public.workout_kind not null default 'default',
  visibility           public.activity_visibility not null,
  muted                boolean not null default false,  -- stays on profile, never in feeds
  is_commute           boolean not null default false,
  is_indoor            boolean not null default false,
  is_manual            boolean not null default false,
  gear_id              uuid references public.gear (id) on delete set null,
  device_name          text check (length(device_name) <= 120),

  start_time           timestamptz not null,
  tz_name              text check (length(tz_name) <= 64),
  utc_offset_s         integer not null default 0 check (utc_offset_s between -64800 and 64800),
  start_date_local     date not null,  -- derived; shown even when start_time is hidden
  published_at         timestamptz not null default now(),  -- feed sort key

  -- Summary metrics (SI units).
  elapsed_s            integer not null check (elapsed_s >= 0),
  moving_s             integer not null check (moving_s >= 0),
  distance_m           double precision not null default 0 check (distance_m >= 0),
  elev_gain_m          real,
  elev_loss_m          real,
  elev_high_m          real,
  elev_low_m           real,
  avg_speed_mps        real check (avg_speed_mps >= 0),
  max_speed_mps        real check (max_speed_mps >= 0),
  avg_hr               real check (avg_hr between 0 and 260),
  max_hr               smallint check (max_hr between 0 and 260),
  avg_cadence          real,
  max_cadence          smallint,
  avg_watts            real,
  max_watts            smallint,
  weighted_avg_watts   real,
  kilojoules           real,
  calories             real,
  effort_score         real,
  metrics_version      smallint not null default 1 check (metrics_version >= 1),

  -- Owner geometry.
  route_simplified     extensions.geography(LineString, 4326),
  start_point          extensions.geography(Point, 4326),
  end_point            extensions.geography(Point, 4326),
  summary_polyline     text,
  -- Privacy-trimmed public geometry (derived by trigger; never client-written).
  route_public         extensions.geography(LineString, 4326),
  start_point_public   extensions.geography(Point, 4326),
  end_point_public     extensions.geography(Point, 4326),
  summary_polyline_public text,

  -- Map privacy (per activity; defaults copied from profile_settings).
  hide_start_m         integer not null check (hide_start_m between 0 and 1600),
  hide_end_m           integer not null check (hide_end_m between 0 and 1600),
  hide_map             boolean not null,
  map_privacy_overridden boolean not null default false,

  -- Hidden stats (settings-privacy-units §8.1). Owner always sees the values.
  hide_heart_rate      boolean not null,
  hide_pace            boolean not null,
  hide_power           boolean not null,
  hide_calories        boolean not null,
  hide_start_time      boolean not null,
  hide_elevation       boolean not null,

  -- Denormalised counters maintained by triggers.
  high_five_count      integer not null default 0 check (high_five_count >= 0),
  comment_count        integer not null default 0 check (comment_count >= 0),

  created_at           timestamptz not null default now(),
  updated_at           timestamptz not null default now(),
  deleted_at           timestamptz,

  constraint activities_owner_client_uuid_key unique (owner_id, client_uuid),
  check (moving_s <= elapsed_s)
);

comment on table public.activities is
  'Owner-only base table. Other viewers must read public.activity_cards (masked, privacy-trimmed).';
comment on column public.activities.client_uuid is
  'Device-generated idempotency key; unique per owner. Duplicate uploads resolve to the existing row.';
comment on column public.activities.map_privacy_overridden is
  'Set when the owner edits map privacy on this activity; hidden-place changes then skip it.';

create index activities_owner_time_idx on public.activities (owner_id, start_time desc)
  where deleted_at is null;
create index activities_feed_idx on public.activities (owner_id, published_at desc, id desc)
  where deleted_at is null and not muted;
create index activities_owner_sport_idx on public.activities (owner_id, sport_type, start_time desc);
create index activities_gear_idx on public.activities (gear_id) where gear_id is not null;
create index activities_upload_idx on public.activities (upload_id) where upload_id is not null;
create index activities_route_public_gix on public.activities using gist (route_public);
create index activities_start_public_gix on public.activities using gist (start_point_public);

-- ---------------------------------------------------------------------------
-- Privacy trimming
-- ---------------------------------------------------------------------------

-- Per-instance secret that seeds the deterministic trim jitter (privacy-safety
-- §B step 3). Generated once at install; never exposed through the API.
create table private.instance_secrets (
  name  text primary key,
  value bytea not null check (octet_length(value) >= 32)
);

alter table private.instance_secrets enable row level security;
revoke all on table private.instance_secrets from public, anon, authenticated, service_role;

insert into private.instance_secrets (name, value)
values ('privacy_trim_jitter', extensions.gen_random_bytes(32));

-- Deterministic per-activity value in [0, 1): HMAC-SHA256 of the activity id
-- (plus a salt naming the route end) keyed with the instance secret. Stable
-- across re-trims, unpredictable without the secret. Falls back to 1 (the
-- largest trim) if the secret is missing, so a broken install over-trims.
create or replace function private.trim_jitter(p_activity uuid, p_salt text)
returns double precision
language sql
stable
security definer
set search_path = ''
as $$
  select coalesce((
    select ('x' || encode(substr(extensions.hmac(
              convert_to(p_activity::text || ':' || p_salt, 'UTF8'), s.value, 'sha256'), 1, 4),
              'hex'))::bit(32)::bigint / 4294967296.0
      from private.instance_secrets s
     where s.name = 'privacy_trim_jitter'), 1.0);
$$;

-- How far (as a fraction of the route, measured from the start when
-- p_from_start, else from the end) the route must be cut so that its visible
-- end lies outside every hidden place. Rule (privacy-safety §B step 4): when
-- any point within the first 20 % lies inside a hidden place grown by
-- `p_jitter` x 25 % of its radius, the cut moves to the first point after the
-- last such point where the route is outside again. The cut therefore lands at
-- a jittered distance beyond the circle, never exactly on it, so the centre
-- cannot be recovered from many activities.
--
-- Points are sampled every 10 m (at most 2000 samples over the half of the
-- route nearest this end) and tested with exact geodesic distance. Returns 0
-- when no hidden place is involved and 1 when the route never leaves it.
create or replace function private.hidden_place_cut(
  p_owner      uuid,
  p_merc       extensions.geometry,
  p_len_m      double precision,
  p_jitter     double precision,
  p_from_start boolean
)
returns double precision
language plpgsql
stable
security definer
set search_path = ''
as $$
declare
  c_window constant double precision := 0.2;  -- share of the route checked at each end
  c_span   constant double precision := 0.5;  -- never look past the middle
  v_n      integer := least(greatest(ceil(c_span * p_len_m / 10.0)::integer, 1), 2000);
  v_inside boolean[];
  v_last   integer;
begin
  select array_agg(
           exists (
             select 1 from public.privacy_zones z
              where z.owner_id = p_owner
                and extensions.st_dwithin(
                      z.center,
                      extensions.st_transform(
                        extensions.st_lineinterpolatepoint(
                          p_merc,
                          case when p_from_start then i * c_span / v_n
                               else 1.0 - i * c_span / v_n end),
                        4326)::extensions.geography,
                      z.radius_m * (1.0 + 0.25 * p_jitter)))
           order by i)
    into v_inside
    from generate_series(0, v_n) i;

  -- Last inside sample within the checked window (array index i + 1).
  for i in reverse v_n .. 0 loop
    if v_inside[i + 1] and i * c_span / v_n <= c_window then
      v_last := i;
      exit;
    end if;
  end loop;
  if v_last is null then
    return 0.0;
  end if;

  for i in v_last + 1 .. v_n loop
    if not v_inside[i + 1] then
      return i * c_span / v_n;
    end if;
  end loop;
  return 1.0;
end;
$$;

-- Returns the publicly visible part of a route (privacy-safety §B):
--   1. the first `p_start_m` and last `p_end_m` metres are removed, each grown
--      by a deterministic per-activity jitter of up to 25 %;
--   2. hidden places near either end push the cut out of the place (see
--      private.hidden_place_cut);
--   3. when any trimming applies and less than 500 m, or less than 40 % of the
--      route, would remain, the whole map is hidden (NULL).
-- Fractions are taken along the Web Mercator line, whose scale is locally
-- isotropic, so metre fractions are accurate along routes of ordinary length.
create or replace function private.privacy_trim(
  p_activity uuid,
  p_owner    uuid,
  p_route    extensions.geography,
  p_start_m  integer,
  p_end_m    integer
)
returns extensions.geography
language plpgsql
stable
security definer
set search_path = ''
as $$
declare
  c_min_remaining_m constant double precision := 500.0;
  c_max_removed     constant double precision := 0.6;
  v_len_m    double precision;
  v_merc     extensions.geometry;
  v_j_start  double precision := private.trim_jitter(p_activity, 'start');
  v_j_end    double precision := private.trim_jitter(p_activity, 'end');
  v_from     double precision;
  v_to       double precision;
  v_cut      double precision;
  v_trimming boolean := greatest(p_start_m, 0) > 0 or greatest(p_end_m, 0) > 0;
begin
  if p_route is null then
    return null;
  end if;

  v_len_m := extensions.st_length(p_route);
  if v_len_m is null or v_len_m <= 0 then
    return null;
  end if;

  v_merc := extensions.st_transform(p_route::extensions.geometry, 3857);
  v_from := greatest(p_start_m, 0) * (1.0 + 0.25 * v_j_start) / v_len_m;
  v_to   := 1.0 - greatest(p_end_m, 0) * (1.0 + 0.25 * v_j_end) / v_len_m;

  if exists (select 1 from public.privacy_zones z where z.owner_id = p_owner) then
    v_cut := private.hidden_place_cut(p_owner, v_merc, v_len_m, v_j_start, true);
    if v_cut > 0 then
      v_trimming := true;
      v_from := greatest(v_from, v_cut);
    end if;
    v_cut := private.hidden_place_cut(p_owner, v_merc, v_len_m, v_j_end, false);
    if v_cut > 0 then
      v_trimming := true;
      v_to := least(v_to, 1.0 - v_cut);
    end if;
  end if;

  if v_from >= v_to or v_from >= 1.0 or v_to <= 0.0 then
    return null;
  end if;
  if v_trimming and ((v_to - v_from) * v_len_m < c_min_remaining_m
                     or 1.0 - (v_to - v_from) > c_max_removed) then
    return null;
  end if;

  return extensions.st_transform(
           extensions.st_linesubstring(v_merc, greatest(v_from, 0.0), least(v_to, 1.0)), 4326
         )::extensions.geography;
end;
$$;

comment on function private.privacy_trim(uuid, uuid, extensions.geography, integer, integer) is
  'Privacy-trimmed public route. Mirrors the worker''s shared:geo trimming; DB copy guarantees no leak.';

-- ---------------------------------------------------------------------------
-- Triggers (BEFORE triggers fire in name order)
-- ---------------------------------------------------------------------------

-- True when the user has given explicit consent to health-data processing.
create or replace function private.has_health_consent(p_user uuid)
returns boolean
language sql
stable
security definer
set search_path = ''
as $$
  select coalesce(
    (select s.health_data_consent from public.profile_settings s where s.user_id = p_user),
    false);
$$;

-- 10: copy account defaults onto new activities and validate references.
create or replace function private.activities_apply_defaults()
returns trigger
language plpgsql
security definer
set search_path = ''
as $$
declare
  s public.profile_settings%rowtype;
begin
  if tg_op = 'INSERT' then
    select * into s from public.profile_settings where user_id = new.owner_id;

    new.visibility      := coalesce(new.visibility, s.default_visibility, 'followers');
    new.hide_start_m    := coalesce(new.hide_start_m, s.default_hide_start_m, 200);
    new.hide_end_m      := coalesce(new.hide_end_m, s.default_hide_end_m, 200);
    new.hide_map        := coalesce(new.hide_map, s.default_hide_map, false);
    new.hide_heart_rate := coalesce(new.hide_heart_rate, s.default_hide_heart_rate, false);
    new.hide_pace       := coalesce(new.hide_pace, s.default_hide_pace, false);
    new.hide_power      := coalesce(new.hide_power, s.default_hide_power, false);
    new.hide_calories   := coalesce(new.hide_calories, s.default_hide_calories, false);
    new.hide_start_time := coalesce(new.hide_start_time, s.default_hide_start_time, false);
    new.hide_elevation  := coalesce(new.hide_elevation, s.default_hide_elevation, false);

  end if;

  -- Health data (GDPR Art. 9): without explicit consent no HR is stored, also
  -- when the worker writes recomputed metrics later.
  if (tg_op = 'INSERT'
      or new.avg_hr is distinct from old.avg_hr
      or new.max_hr is distinct from old.max_hr)
     and not private.has_health_consent(new.owner_id) then
    new.avg_hr := null;
    new.max_hr := null;
  end if;

  -- Minors always keep at least 400 m of endpoint trimming.
  if private.is_minor(new.owner_id) then
    new.hide_start_m := greatest(new.hide_start_m, 400);
    new.hide_end_m   := greatest(new.hide_end_m, 400);
  end if;

  if new.gear_id is not null and not exists (
       select 1 from public.gear g where g.id = new.gear_id and g.owner_id = new.owner_id) then
    raise exception 'gear % does not belong to the activity owner', new.gear_id
      using errcode = 'foreign_key_violation';
  end if;

  new.start_date_local :=
    ((new.start_time at time zone 'UTC') + make_interval(secs => new.utc_offset_s))::date;

  return new;
end;
$$;

create trigger activities_10_defaults
  before insert or update on public.activities
  for each row execute function private.activities_apply_defaults();

-- 20: remember per-activity map edits made by the owner (not by backend reprocessing).
create or replace function private.activities_track_map_override()
returns trigger
language plpgsql
set search_path = ''
as $$
begin
  if not private.is_backend_role()
     and (new.hide_start_m is distinct from old.hide_start_m
          or new.hide_end_m is distinct from old.hide_end_m
          or new.hide_map is distinct from old.hide_map) then
    new.map_privacy_overridden := true;
  end if;
  return new;
end;
$$;

create trigger activities_20_map_override
  before update on public.activities
  for each row execute function private.activities_track_map_override();

-- 30: derive owner endpoints/polyline and the privacy-trimmed public geometry.
create or replace function private.activities_derive_geometry()
returns trigger
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_line extensions.geometry;
begin
  if new.route_simplified is not null then
    v_line := new.route_simplified::extensions.geometry;
    new.start_point := coalesce(new.start_point, extensions.st_startpoint(v_line)::extensions.geography);
    new.end_point   := coalesce(new.end_point, extensions.st_endpoint(v_line)::extensions.geography);
    if new.summary_polyline is null
       or (tg_op = 'UPDATE' and new.route_simplified is distinct from old.route_simplified) then
      new.summary_polyline := extensions.st_asencodedpolyline(v_line, 5);
    end if;
  end if;

  if new.hide_map then
    new.route_public := null;
  else
    new.route_public := private.privacy_trim(
      new.id, new.owner_id, new.route_simplified, new.hide_start_m, new.hide_end_m);
  end if;

  if new.route_public is null then
    new.start_point_public      := null;
    new.end_point_public        := null;
    new.summary_polyline_public := null;
  else
    v_line := new.route_public::extensions.geometry;
    new.start_point_public      := extensions.st_startpoint(v_line)::extensions.geography;
    new.end_point_public        := extensions.st_endpoint(v_line)::extensions.geography;
    new.summary_polyline_public := extensions.st_asencodedpolyline(v_line, 5);
  end if;

  return new;
end;
$$;

create trigger activities_30_geometry
  before insert or update of route_simplified, hide_start_m, hide_end_m, hide_map, owner_id
  on public.activities
  for each row execute function private.activities_derive_geometry();

create trigger activities_90_updated_at before update on public.activities
  for each row execute function private.set_updated_at();

-- Hidden places apply retroactively, except to activities edited individually.
create or replace function private.privacy_zones_reapply()
returns trigger
language plpgsql
security definer
set search_path = ''
as $$
begin
  update public.activities a
     set hide_start_m = a.hide_start_m
   where a.owner_id = coalesce(new.owner_id, old.owner_id)
     and not a.map_privacy_overridden
     and a.deleted_at is null;
  return null;
end;
$$;

create trigger privacy_zones_reapply
  after insert or update or delete on public.privacy_zones
  for each row execute function private.privacy_zones_reapply();

-- ---------------------------------------------------------------------------
-- Children
-- ---------------------------------------------------------------------------

-- Copies the activity owner onto child rows so RLS needs no join. Runs as the
-- invoking role on purpose: an end user can only see (and so attach children
-- to) their own activities, while the worker's service role sees all of them.
create or replace function private.child_set_owner()
returns trigger
language plpgsql
set search_path = ''
as $$
declare
  v_owner uuid;
begin
  select a.owner_id into v_owner from public.activities a where a.id = new.activity_id;
  if v_owner is null then
    raise exception 'activity % not found', new.activity_id using errcode = 'foreign_key_violation';
  end if;
  new.owner_id := v_owner;
  return new;
end;
$$;

-- Laps and splits carry heart rate too: drop it without health-data consent.
create or replace function private.child_strip_hr()
returns trigger
language plpgsql
set search_path = ''
as $$
begin
  if not private.has_health_consent(new.owner_id) then
    new := json_populate_record(new, '{"avg_hr": null, "max_hr": null}'::json);
  end if;
  return new;
end;
$$;

-- Lap start is stored as an offset so a hidden start time cannot be recovered.
create table public.laps (
  activity_id    uuid not null references public.activities (id) on delete cascade,
  owner_id       uuid not null references public.profiles (id) on delete cascade,
  lap_index      smallint not null check (lap_index >= 0),
  name           text check (length(name) <= 80),
  trigger        public.lap_trigger not null default 'manual',
  start_offset_s integer not null check (start_offset_s >= 0),
  elapsed_s      integer not null check (elapsed_s >= 0),
  moving_s       integer not null check (moving_s >= 0),
  distance_m     double precision not null check (distance_m >= 0),
  elev_gain_m    real,
  avg_speed_mps  real,
  max_speed_mps  real,
  avg_hr         real,
  max_hr         smallint,
  avg_cadence    real,
  avg_watts      real,
  max_watts      smallint,
  primary key (activity_id, lap_index)
);

create index laps_owner_idx on public.laps (owner_id);

create table public.splits (
  activity_id   uuid not null references public.activities (id) on delete cascade,
  owner_id      uuid not null references public.profiles (id) on delete cascade,
  unit          public.split_unit not null,
  split_index   smallint not null check (split_index >= 0),
  distance_m    real not null check (distance_m >= 0),
  elapsed_s     integer not null check (elapsed_s >= 0),
  moving_s      integer not null check (moving_s >= 0),
  elev_diff_m   real,
  avg_speed_mps real,
  gap_speed_mps real,
  avg_hr        real,
  avg_watts     real,
  primary key (activity_id, unit, split_index)
);

create index splits_owner_idx on public.splits (owner_id);

-- Raw streams live in Storage (FIT original + columnar `.sfstream`); this row
-- points at them. `public_path` is a worker-built derivative with privacy-zone
-- points removed and hidden channels (HR, power, ...) dropped.
create table public.activity_streams (
  activity_id     uuid primary key references public.activities (id) on delete cascade,
  owner_id        uuid not null references public.profiles (id) on delete cascade,
  bucket          text not null default 'streams',
  owner_path      text not null check (length(owner_path) <= 512),
  public_path     text check (length(public_path) <= 512),
  formats         public.file_format[] not null default '{sfstream}',
  stream_types    text[] not null default '{}'
                    check (stream_types <@ array['time', 'latlng', 'distance', 'altitude',
                      'velocity_smooth', 'heartrate', 'cadence', 'watts', 'temp', 'moving',
                      'grade_smooth']::text[]),
  sample_count    integer not null default 0 check (sample_count >= 0),
  metrics_version smallint not null default 1,
  updated_at      timestamptz not null default now()
);

create index activity_streams_owner_idx on public.activity_streams (owner_id);

create trigger activity_streams_updated_at before update on public.activity_streams
  for each row execute function private.set_updated_at();

-- Private notes: owner-only, separate table so no read model can expose them.
create table public.activity_private_notes (
  activity_id uuid primary key references public.activities (id) on delete cascade,
  owner_id    uuid not null references public.profiles (id) on delete cascade,
  body        text not null check (length(body) <= 5000),
  updated_at  timestamptz not null default now()
);

create index activity_private_notes_owner_idx on public.activity_private_notes (owner_id);

create trigger laps_set_owner before insert or update on public.laps
  for each row execute function private.child_set_owner();
create trigger splits_set_owner before insert or update on public.splits
  for each row execute function private.child_set_owner();
-- Fires after *_set_owner (BEFORE triggers run in name order).
create trigger laps_strip_hr before insert or update on public.laps
  for each row execute function private.child_strip_hr();
create trigger splits_strip_hr before insert or update on public.splits
  for each row execute function private.child_strip_hr();
create trigger activity_streams_set_owner before insert or update on public.activity_streams
  for each row execute function private.child_set_owner();
create trigger activity_private_notes_set_owner before insert or update on public.activity_private_notes
  for each row execute function private.child_set_owner();
create trigger activity_private_notes_updated_at before update on public.activity_private_notes
  for each row execute function private.set_updated_at();

-- ---------------------------------------------------------------------------
-- RLS: every base table here is owner-only. Reads by others go through views.
-- ---------------------------------------------------------------------------

alter table public.activities             enable row level security;
alter table public.laps                   enable row level security;
alter table public.splits                 enable row level security;
alter table public.activity_streams       enable row level security;
alter table public.activity_private_notes enable row level security;

revoke all on table public.activities, public.laps, public.splits, public.activity_streams,
  public.activity_private_notes from anon, authenticated;
grant all on table public.activities, public.laps, public.splits, public.activity_streams,
  public.activity_private_notes to service_role;

grant select, delete on table public.activities to authenticated;
grant insert (owner_id, client_uuid, name, description, sport_type, workout_kind, visibility,
  muted, is_commute, is_indoor, is_manual, gear_id, device_name, start_time, tz_name,
  utc_offset_s, elapsed_s, moving_s, distance_m, elev_gain_m, elev_loss_m, elev_high_m,
  elev_low_m, avg_speed_mps, max_speed_mps, avg_hr, max_hr, avg_cadence, max_cadence,
  avg_watts, max_watts, weighted_avg_watts, kilojoules, calories, effort_score,
  metrics_version, route_simplified, start_point, end_point, summary_polyline,
  hide_start_m, hide_end_m, hide_map, hide_heart_rate, hide_pace, hide_power,
  hide_calories, hide_start_time, hide_elevation)
  on table public.activities to authenticated;
grant update (name, description, sport_type, workout_kind, visibility, muted, is_commute,
  gear_id, hide_start_m, hide_end_m, hide_map, hide_heart_rate, hide_pace, hide_power,
  hide_calories, hide_start_time, hide_elevation)
  on table public.activities to authenticated;

create policy activities_owner_select on public.activities
  for select to authenticated using (owner_id = (select auth.uid()));
create policy activities_owner_insert on public.activities
  for insert to authenticated with check (owner_id = (select auth.uid()));
create policy activities_owner_update on public.activities
  for update to authenticated
  using (owner_id = (select auth.uid())) with check (owner_id = (select auth.uid()));
create policy activities_owner_delete on public.activities
  for delete to authenticated using (owner_id = (select auth.uid()));

grant select, insert, update, delete on table public.laps, public.splits,
  public.activity_private_notes to authenticated;
grant select on table public.activity_streams to authenticated;

create policy laps_owner_all on public.laps
  for all to authenticated
  using (owner_id = (select auth.uid())) with check (owner_id = (select auth.uid()));
create policy splits_owner_all on public.splits
  for all to authenticated
  using (owner_id = (select auth.uid())) with check (owner_id = (select auth.uid()));
create policy activity_streams_owner_select on public.activity_streams
  for select to authenticated using (owner_id = (select auth.uid()));
create policy activity_private_notes_owner_all on public.activity_private_notes
  for all to authenticated
  using (owner_id = (select auth.uid())) with check (owner_id = (select auth.uid()));
