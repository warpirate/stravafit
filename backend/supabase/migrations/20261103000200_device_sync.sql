-- SPDX-License-Identifier: AGPL-3.0-or-later
--
-- Activity sync from the phone.
--
-- The app records and parses activities on the device, so the server-side
-- ingest worker (uploads_jobs.sql) is not on the sync path. Instead:
--
--   1. upsert_activity_from_device(payload) writes the activity, its laps and
--      splits in one call. It is idempotent on (owner, client_uuid): the
--      device's stable activity uuid. Sending the same activity again updates
--      it (edits, crops); nothing is duplicated.
--   2. The function returns where the streams files go and how to build the
--      public one. The client uploads two gzip JSON files to the `streams`
--      bucket: owner.json.gz (everything) and public.json.gz (trimmed at the
--      ends and without hidden channels, as the server instructs).
--   3. register_activity_streams(...) checks the files exist and records them.
--      Viewers only ever get public.json.gz, through the existing
--      activity_stream_files view and streams_public_select storage policy.
--   4. Any later privacy change (hidden stats, hide map, a new hidden place
--      that re-trims the route) clears the public file's registration, so
--      viewers lose access until the client re-uploads it.
--      list_stale_public_streams() tells the client which ones to rebuild.
--      Privacy therefore fails closed.
--
-- The RPCs that write activities run as the caller (security invoker): row
-- level security, column grants and the existing activity triggers (account
-- defaults, health-data stripping, minor trimming, privacy_trim, map override
-- tracking) apply exactly as for any client write. Only the steps that need
-- to read Storage metadata or write activity_streams are security definer.
--
-- The laps and splits replacement helpers live in the next two files (…0210,
-- …0220): the tooling that applies migrations to the hosted project refused a
-- migration with several DELETE statements without an interactive
-- confirmation, so each helper ships in its own migration.
--
-- Deleting an activity needs no RPC: the client deletes the row through the
-- REST API (the activities_owner_delete policy allows the owner only; laps,
-- splits, streams and photo rows cascade) and then removes its Storage files.
-- Until it does, those files are unreadable to others because every storage
-- read policy checks the activity.

-- ---------------------------------------------------------------------------
-- Grants: the device may correct its own recording (crop, re-parse), so the
-- owner may update the metrics and geometry too, not only the metadata.
-- Derived columns (route_public, *_public, counters, start_date_local) stay
-- server-only.
-- ---------------------------------------------------------------------------

grant update (is_indoor, is_manual, device_name, start_time, tz_name, utc_offset_s,
  elapsed_s, moving_s, distance_m, elev_gain_m, elev_loss_m, elev_high_m, elev_low_m,
  avg_speed_mps, max_speed_mps, avg_hr, max_hr, avg_cadence, max_cadence, avg_watts,
  max_watts, weighted_avg_watts, kilojoules, calories, effort_score, metrics_version,
  route_simplified, start_point, end_point, summary_polyline)
  on table public.activities to authenticated;

-- published_at is the feed sort key. The device sets it to when the activity
-- ended (see below); the trigger keeps it from being set in the future, which
-- would pin a post to the top of followers' feeds.
grant insert (published_at) on table public.activities to authenticated;

create or replace function private.activities_clamp_published_at()
returns trigger
language plpgsql
set search_path = ''
as $$
begin
  new.published_at := least(coalesce(new.published_at, now()), now());
  return new;
end;
$$;

create trigger activities_05_clamp_published_at
  before insert on public.activities
  for each row execute function private.activities_clamp_published_at();

-- Device-generated ids for hidden places, so the phone can upsert by its own id.
grant insert (id) on table public.privacy_zones to authenticated;

-- The streams bucket also takes the device's gzip JSON files.
update storage.buckets
   set allowed_mime_types = array['application/octet-stream', 'application/gzip', 'application/json'],
       file_size_limit = 52428800
 where id = 'streams';

-- ---------------------------------------------------------------------------
-- Helpers
-- ---------------------------------------------------------------------------

-- Reads an optional jsonb number as double precision, NULL when absent or null.
create or replace function private.j_num(p jsonb, k text)
returns double precision
language sql
immutable
set search_path = ''
as $$
  select case when jsonb_typeof(p -> k) = 'number' then (p ->> k)::double precision end;
$$;

create or replace function private.j_bool(p jsonb, k text)
returns boolean
language sql
immutable
set search_path = ''
as $$
  select case when jsonb_typeof(p -> k) = 'boolean' then (p ->> k)::boolean end;
$$;

-- How the client must build public.json.gz for an activity (see API.md):
--   trim_start_m / trim_end_m : drop every sample whose cumulative distance is
--                               below trim_start_m or above distance_m - trim_end_m
--   drop_channels             : channels that must not appear in the file
--   include_latlng            : false when the map is hidden for viewers
-- The trim distances come from the server's privacy-trimmed route (jittered
-- endpoint trim plus hidden places) scaled to the device distance, plus a
-- 50 m margin because the simplified route is a little shorter than the track.
create or replace function private.public_stream_spec(p_activity uuid)
returns jsonb
language plpgsql
stable
security definer
set search_path = ''
as $$
declare
  a        public.activities;
  v_merc   extensions.geometry;
  v_from   double precision := 0;
  v_to     double precision := 1;
  v_drop   text[] := '{}';
  v_margin constant double precision := 50.0;
begin
  -- Owner only: the spec reveals the trim distances and hidden flags.
  select * into a from public.activities
   where id = p_activity and owner_id = auth.uid();
  if not found then
    return null;
  end if;

  if a.route_public is not null and a.route_simplified is not null then
    v_merc := extensions.st_transform(a.route_simplified::extensions.geometry, 3857);
    v_from := extensions.st_linelocatepoint(v_merc,
                extensions.st_transform(a.start_point_public::extensions.geometry, 3857));
    v_to   := extensions.st_linelocatepoint(v_merc,
                extensions.st_transform(a.end_point_public::extensions.geometry, 3857));
  end if;

  if a.hide_heart_rate then v_drop := v_drop || array['heartrate']; end if;
  if a.hide_power      then v_drop := v_drop || array['watts']; end if;
  -- Time plus distance is pace, so hidden pace drops the clock as well.
  if a.hide_pace       then v_drop := v_drop || array['velocity_smooth', 'time']; end if;
  if a.hide_elevation  then v_drop := v_drop || array['altitude', 'grade_smooth']; end if;
  if a.route_public is null then v_drop := v_drop || array['latlng']; end if;

  return jsonb_build_object(
    'path', a.owner_id::text || '/' || a.id::text || '/public.json.gz',
    'include_latlng', a.route_public is not null,
    'trim_start_m', case when a.route_public is null and a.route_simplified is not null
                         then 0 else round((v_from * a.distance_m + case when v_from > 0 then v_margin else 0 end)::numeric, 1) end,
    'trim_end_m',   case when a.route_public is null and a.route_simplified is not null
                         then 0 else round(((1 - v_to) * a.distance_m + case when v_to < 1 then v_margin else 0 end)::numeric, 1) end,
    'drop_channels', to_jsonb(v_drop));
end;
$$;

revoke execute on function private.public_stream_spec(uuid) from public, anon;
grant execute on function private.public_stream_spec(uuid) to authenticated, service_role;

-- ---------------------------------------------------------------------------
-- upsert_activity_from_device
-- ---------------------------------------------------------------------------

create or replace function public.upsert_activity_from_device(p_payload jsonb)
returns jsonb
language plpgsql
set search_path = ''
as $$
declare
  v_uid      uuid := auth.uid();
  p          jsonb := p_payload;
  v_client   uuid;
  v_sport    public.sport_type;
  v_kind     public.workout_kind := 'default';
  v_vis      public.activity_visibility;
  v_start    timestamptz;
  v_elapsed  integer;
  v_moving   integer;
  v_poly     text := nullif(p ->> 'polyline', '');
  v_route    extensions.geography;
  v_line     extensions.geometry;
  v_id       uuid;
  v_inserted boolean;
begin
  if v_uid is null then
    raise exception 'authentication required' using errcode = 'insufficient_privilege';
  end if;
  if p is null or jsonb_typeof(p) <> 'object' then
    raise exception 'payload must be a JSON object' using errcode = 'invalid_parameter_value';
  end if;

  begin
    v_client := (p ->> 'client_uuid')::uuid;
    v_start  := (p ->> 'start_time')::timestamptz;
  exception when others then
    raise exception 'client_uuid must be a uuid and start_time an ISO-8601 timestamp'
      using errcode = 'invalid_parameter_value';
  end;
  if v_client is null or v_start is null then
    raise exception 'client_uuid and start_time are required' using errcode = 'invalid_parameter_value';
  end if;
  if v_start > now() + interval '1 day' or v_start < timestamptz '1990-01-01' then
    raise exception 'start_time out of range' using errcode = 'invalid_parameter_value';
  end if;

  begin
    v_sport := (p ->> 'sport_type')::public.sport_type;
    if p ? 'workout_kind' then
      v_kind := lower(p ->> 'workout_kind')::public.workout_kind;
    end if;
    if p ? 'visibility' then
      v_vis := lower(p ->> 'visibility')::public.activity_visibility;
    end if;
  exception when invalid_text_representation then
    raise exception 'unknown sport_type, workout_kind or visibility' using errcode = 'invalid_parameter_value';
  end;
  if v_sport is null then
    raise exception 'sport_type is required' using errcode = 'invalid_parameter_value';
  end if;

  v_elapsed := round(coalesce(private.j_num(p, 'elapsed_s'), -1));
  v_moving  := round(coalesce(private.j_num(p, 'moving_s'), -1));
  if v_elapsed < 0 or v_moving < 0 or v_moving > v_elapsed or v_elapsed > 30 * 86400 then
    raise exception 'elapsed_s and moving_s are required, with 0 <= moving_s <= elapsed_s <= 30 days'
      using errcode = 'invalid_parameter_value';
  end if;

  if v_poly is not null then
    if length(v_poly) > 200000 then
      raise exception 'polyline too long (max 200000 characters)' using errcode = 'program_limit_exceeded';
    end if;
    v_line := extensions.st_linefromencodedpolyline(v_poly, 5);
    if v_line is null or extensions.st_npoints(v_line) < 2 then
      v_line := null;
    elsif extensions.st_xmin(v_line) < -180 or extensions.st_xmax(v_line) > 180
       or extensions.st_ymin(v_line) < -90 or extensions.st_ymax(v_line) > 90 then
      raise exception 'polyline has coordinates out of range' using errcode = 'invalid_parameter_value';
    end if;
    v_route := extensions.st_setsrid(v_line, 4326)::extensions.geography;
  end if;

  if jsonb_typeof(coalesce(p -> 'laps', '[]')) <> 'array'
     or jsonb_typeof(coalesce(p -> 'splits', '[]')) <> 'array'
     or jsonb_array_length(coalesce(p -> 'laps', '[]')) > 1000
     or jsonb_array_length(coalesce(p -> 'splits', '[]')) > 2000 then
    raise exception 'laps and splits must be arrays (max 1000 laps, 2000 splits)'
      using errcode = 'invalid_parameter_value';
  end if;

  -- Absent hide_* and visibility fall back to the account defaults on insert
  -- (activities_apply_defaults) and keep their current value on update.
  insert into public.activities as a (
    owner_id, client_uuid, name, description, sport_type, workout_kind, visibility, muted,
    is_commute, is_indoor, is_manual, device_name, start_time, tz_name, utc_offset_s,
    published_at, elapsed_s, moving_s, distance_m, elev_gain_m, elev_loss_m, elev_high_m,
    elev_low_m, avg_speed_mps, max_speed_mps, avg_hr, max_hr, avg_cadence, max_cadence,
    avg_watts, max_watts, weighted_avg_watts, kilojoules, calories, effort_score,
    route_simplified, start_point, end_point, summary_polyline,
    hide_start_m, hide_end_m, hide_map, hide_heart_rate, hide_pace, hide_power,
    hide_calories, hide_start_time, hide_elevation)
  values (
    v_uid, v_client,
    coalesce(nullif(left(btrim(p ->> 'name'), 255), ''), 'Activity'),
    nullif(left(p ->> 'description', 5000), ''),
    v_sport, v_kind, v_vis,
    coalesce(private.j_bool(p, 'muted'), false),
    coalesce(private.j_bool(p, 'is_commute'), false),
    coalesce(private.j_bool(p, 'is_indoor'), false),
    coalesce(private.j_bool(p, 'is_manual'), false),
    nullif(left(p ->> 'device_name', 120), ''),
    v_start,
    nullif(left(p ->> 'tz_name', 64), ''),
    coalesce(round(private.j_num(p, 'utc_offset_s'))::integer, 0),
    -- Feed position = when the activity ended, so a first sync of an old
    -- history does not flood followers' feeds with "just now" posts.
    least(now(), v_start + make_interval(secs => v_elapsed)),
    v_elapsed, v_moving,
    greatest(coalesce(private.j_num(p, 'distance_m'), 0), 0),
    private.j_num(p, 'elev_gain_m'), private.j_num(p, 'elev_loss_m'),
    private.j_num(p, 'elev_high_m'), private.j_num(p, 'elev_low_m'),
    private.j_num(p, 'avg_speed_mps'), private.j_num(p, 'max_speed_mps'),
    private.j_num(p, 'avg_hr'), round(private.j_num(p, 'max_hr'))::smallint,
    private.j_num(p, 'avg_cadence'), round(private.j_num(p, 'max_cadence'))::smallint,
    private.j_num(p, 'avg_watts'), round(private.j_num(p, 'max_watts'))::smallint,
    private.j_num(p, 'weighted_avg_watts'), private.j_num(p, 'kilojoules'),
    private.j_num(p, 'calories'), private.j_num(p, 'effort_score'),
    v_route, null, null, null,
    round(private.j_num(p, 'hide_start_m'))::integer, round(private.j_num(p, 'hide_end_m'))::integer,
    private.j_bool(p, 'hide_map'), private.j_bool(p, 'hide_heart_rate'),
    private.j_bool(p, 'hide_pace'), private.j_bool(p, 'hide_power'),
    private.j_bool(p, 'hide_calories'), private.j_bool(p, 'hide_start_time'),
    private.j_bool(p, 'hide_elevation'))
  on conflict on constraint activities_owner_client_uuid_key do update set
    name               = excluded.name,
    description        = excluded.description,
    sport_type         = excluded.sport_type,
    workout_kind       = excluded.workout_kind,
    visibility         = case when p ? 'visibility' then excluded.visibility else a.visibility end,
    muted              = excluded.muted,
    is_commute         = excluded.is_commute,
    is_indoor          = excluded.is_indoor,
    is_manual          = excluded.is_manual,
    device_name        = excluded.device_name,
    start_time         = excluded.start_time,
    tz_name            = excluded.tz_name,
    utc_offset_s       = excluded.utc_offset_s,
    elapsed_s          = excluded.elapsed_s,
    moving_s           = excluded.moving_s,
    distance_m         = excluded.distance_m,
    elev_gain_m        = excluded.elev_gain_m,
    elev_loss_m        = excluded.elev_loss_m,
    elev_high_m        = excluded.elev_high_m,
    elev_low_m         = excluded.elev_low_m,
    avg_speed_mps      = excluded.avg_speed_mps,
    max_speed_mps      = excluded.max_speed_mps,
    avg_hr             = excluded.avg_hr,
    max_hr             = excluded.max_hr,
    avg_cadence        = excluded.avg_cadence,
    max_cadence        = excluded.max_cadence,
    avg_watts          = excluded.avg_watts,
    max_watts          = excluded.max_watts,
    weighted_avg_watts = excluded.weighted_avg_watts,
    kilojoules         = excluded.kilojoules,
    calories           = excluded.calories,
    effort_score       = excluded.effort_score,
    -- Endpoints and the owner polyline are re-derived from the new route.
    route_simplified   = excluded.route_simplified,
    start_point        = null,
    end_point          = null,
    summary_polyline   = null,
    hide_start_m       = case when p ? 'hide_start_m' then excluded.hide_start_m else a.hide_start_m end,
    hide_end_m         = case when p ? 'hide_end_m' then excluded.hide_end_m else a.hide_end_m end,
    hide_map           = case when p ? 'hide_map' then excluded.hide_map else a.hide_map end,
    hide_heart_rate    = case when p ? 'hide_heart_rate' then excluded.hide_heart_rate else a.hide_heart_rate end,
    hide_pace          = case when p ? 'hide_pace' then excluded.hide_pace else a.hide_pace end,
    hide_power         = case when p ? 'hide_power' then excluded.hide_power else a.hide_power end,
    hide_calories      = case when p ? 'hide_calories' then excluded.hide_calories else a.hide_calories end,
    hide_start_time    = case when p ? 'hide_start_time' then excluded.hide_start_time else a.hide_start_time end,
    hide_elevation     = case when p ? 'hide_elevation' then excluded.hide_elevation else a.hide_elevation end
  returning a.id, (a.xmax = 0) into v_id, v_inserted;

  -- Laps and splits are replaced as a whole: the device's list is the truth.
  if p ? 'laps' then
    perform private.device_replace_laps(v_id, p -> 'laps');
  end if;
  if p ? 'splits' then
    perform private.device_replace_splits(v_id, p -> 'splits');
  end if;

  return jsonb_build_object(
    'id', v_id,
    'client_uuid', v_client,
    'created', v_inserted,
    'owner_stream_path', v_uid::text || '/' || v_id::text || '/owner.json.gz',
    'public_stream', private.public_stream_spec(v_id),
    'photo_prefix', v_uid::text || '/' || v_id::text || '/');
end;
$$;

comment on function public.upsert_activity_from_device(jsonb) is
  'Idempotent create/update of a device-recorded activity with laps and splits, keyed by client_uuid. See backend/API.md.';

revoke execute on function public.upsert_activity_from_device(jsonb) from public, anon;
grant execute on function public.upsert_activity_from_device(jsonb) to authenticated;

-- ---------------------------------------------------------------------------
-- Streams registration
-- ---------------------------------------------------------------------------

-- Records the uploaded streams files. owner.json.gz must exist; the public
-- file is registered only when it exists AND was uploaded after the last
-- privacy change (stream_public_valid_after), so a stale public file is never
-- served again.
alter table public.activity_streams
  add column public_valid_after timestamptz not null default '-infinity';

comment on column public.activity_streams.public_valid_after is
  'Privacy changes move this forward and clear public_path; a public file uploaded before it is stale.';

create or replace function public.register_activity_streams(
  p_activity_id  uuid,
  p_sample_count integer,
  p_stream_types text[]
)
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_uid       uuid := auth.uid();
  v_owner     uuid;
  v_owner_obj text;
  v_pub_obj   text;
  v_pub_at    timestamptz;
  v_valid     timestamptz;
  v_public    text;
begin
  select a.owner_id into v_owner from public.activities a where a.id = p_activity_id;
  if v_uid is null or v_owner is distinct from v_uid then
    raise exception 'activity not found' using errcode = 'no_data_found';
  end if;
  if p_sample_count is null or p_sample_count < 0 or p_sample_count > 2000000 then
    raise exception 'sample_count out of range' using errcode = 'invalid_parameter_value';
  end if;

  v_owner_obj := v_uid::text || '/' || p_activity_id::text || '/owner.json.gz';
  v_pub_obj   := v_uid::text || '/' || p_activity_id::text || '/public.json.gz';

  if not exists (select 1 from storage.objects o
                  where o.bucket_id = 'streams' and o.name = v_owner_obj) then
    raise exception 'file not uploaded yet: streams/%', v_owner_obj
      using errcode = 'object_not_in_prerequisite_state';
  end if;

  select coalesce(o.updated_at, o.created_at) into v_pub_at
    from storage.objects o where o.bucket_id = 'streams' and o.name = v_pub_obj;
  select s.public_valid_after into v_valid
    from public.activity_streams s where s.activity_id = p_activity_id;
  if v_pub_at is not null and v_pub_at > coalesce(v_valid, '-infinity') then
    v_public := v_pub_obj;
  end if;

  insert into public.activity_streams (activity_id, owner_id, bucket, owner_path, public_path,
    formats, stream_types, sample_count)
  values (p_activity_id, v_uid, 'streams', v_owner_obj, v_public, '{json}',
    coalesce(p_stream_types, '{}'), p_sample_count)
  on conflict (activity_id) do update set
    owner_path   = excluded.owner_path,
    public_path  = excluded.public_path,
    formats      = excluded.formats,
    stream_types = excluded.stream_types,
    sample_count = excluded.sample_count;

  return jsonb_build_object('owner_path', v_owner_obj, 'public_path', v_public,
                            'public_registered', v_public is not null);
end;
$$;

comment on function public.register_activity_streams(uuid, integer, text[]) is
  'Record uploaded streams files (owner.json.gz required, public.json.gz when fresh). Re-call after re-uploading public.json.gz.';

revoke execute on function public.register_activity_streams(uuid, integer, text[]) from public, anon;
grant execute on function public.register_activity_streams(uuid, integer, text[]) to authenticated;

-- Privacy changes invalidate the public streams file at once.
create or replace function private.activities_invalidate_public_stream()
returns trigger
language plpgsql
security definer
set search_path = ''
as $$
begin
  if (new.hide_heart_rate, new.hide_pace, new.hide_power, new.hide_elevation, new.hide_map,
      extensions.st_asewkb(new.route_public::extensions.geometry))
     is distinct from
     (old.hide_heart_rate, old.hide_pace, old.hide_power, old.hide_elevation, old.hide_map,
      extensions.st_asewkb(old.route_public::extensions.geometry)) then
    update public.activity_streams s
       set public_path = null, public_valid_after = now()
     where s.activity_id = new.id;
  end if;
  return null;
end;
$$;

create trigger activities_invalidate_public_stream
  after update on public.activities
  for each row execute function private.activities_invalidate_public_stream();

-- The caller's activities whose public streams file must be (re)built, with
-- the instructions for each.
create or replace function public.list_stale_public_streams(p_limit integer default 100)
returns table (activity_id uuid, client_uuid uuid, public_stream jsonb)
language sql
stable
security definer
set search_path = ''
as $$
  select a.id, a.client_uuid, private.public_stream_spec(a.id)
    from public.activity_streams s
    join public.activities a on a.id = s.activity_id
   where s.owner_id = auth.uid()
     and s.public_path is null
     and a.deleted_at is null
   order by a.start_time desc
   limit least(greatest(coalesce(p_limit, 100), 1), 500);
$$;

revoke execute on function public.list_stale_public_streams(integer) from public, anon;
grant execute on function public.list_stale_public_streams(integer) to authenticated;

-- ---------------------------------------------------------------------------
-- Photos
-- ---------------------------------------------------------------------------
--
-- The client uploads a 1600 px JPEG (or WebP) with EXIF stripped (re-encoding
-- the bitmap drops it) straight to photos/<owner>/<activity_id>/<photo_id>.jpg
-- and then inserts the row here. Viewing follows can_view_activity_photos:
-- the activity's visibility, and for strangers only when the profile is open.

update storage.buckets
   set file_size_limit = 2097152,
       allowed_mime_types = array['image/jpeg', 'image/webp']
 where id = 'photos';

create table public.activity_photos (
  id           uuid primary key,  -- generated on the device
  activity_id  uuid not null references public.activities (id) on delete cascade,
  owner_id     uuid not null references public.profiles (id) on delete cascade,
  storage_path text not null unique check (length(storage_path) <= 512),
  width        integer check (width between 1 and 10000),
  height       integer check (height between 1 and 10000),
  position     smallint not null default 0 check (position between 0 and 99),
  caption      text check (length(caption) <= 500),
  created_at   timestamptz not null default now()
);

comment on table public.activity_photos is
  'Photos of an activity, stored in the photos bucket. Visible with the activity (strangers only on open profiles).';

create index activity_photos_activity_idx on public.activity_photos (activity_id, position);
create index activity_photos_owner_idx on public.activity_photos (owner_id);

-- Owner from the activity; the path must be the canonical one for this photo
-- and the file must already be in Storage; at most 20 photos per activity.
create or replace function private.activity_photos_before_insert()
returns trigger
language plpgsql
security definer
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

  if new.storage_path !~ ('^' || v_owner::text || '/' || new.activity_id::text || '/'
                          || new.id::text || '\.(jpg|webp)$') then
    raise exception 'storage_path must be <owner>/<activity_id>/<photo_id>.jpg or .webp'
      using errcode = 'check_violation';
  end if;
  if not exists (select 1 from storage.objects o
                  where o.bucket_id = 'photos' and o.name = new.storage_path) then
    raise exception 'file not uploaded yet: photos/%', new.storage_path
      using errcode = 'object_not_in_prerequisite_state';
  end if;
  if (select count(*) from public.activity_photos ph where ph.activity_id = new.activity_id) >= 20 then
    raise exception 'an activity can have at most 20 photos' using errcode = 'program_limit_exceeded';
  end if;
  return new;
end;
$$;

create trigger activity_photos_before_insert before insert on public.activity_photos
  for each row execute function private.activity_photos_before_insert();

alter table public.activity_photos enable row level security;
revoke all on table public.activity_photos from anon, authenticated;
grant all on table public.activity_photos to service_role;
grant select on table public.activity_photos to anon, authenticated;
grant insert (id, activity_id, owner_id, storage_path, width, height, position, caption),
  delete on table public.activity_photos to authenticated;
grant update (position, caption) on table public.activity_photos to authenticated;

create policy activity_photos_select on public.activity_photos
  for select to anon, authenticated
  using (coalesce(private.can_view_activity_photos(activity_id), false));
create policy activity_photos_insert_own on public.activity_photos
  for insert to authenticated
  with check (owner_id = (select auth.uid())
              and private.activity_owner(activity_id) = (select auth.uid()));
create policy activity_photos_update_own on public.activity_photos
  for update to authenticated
  using (owner_id = (select auth.uid())) with check (owner_id = (select auth.uid()));
create policy activity_photos_delete_own on public.activity_photos
  for delete to authenticated using (owner_id = (select auth.uid()));

-- ---------------------------------------------------------------------------
-- Storage policies for device uploads
-- ---------------------------------------------------------------------------

-- streams: the owner writes owner.json.gz / public.json.gz of their own activity.
create policy streams_owner_insert on storage.objects
  for insert to authenticated
  with check (
    bucket_id = 'streams'
    and (storage.foldername(name))[1] = (select auth.uid())::text
    and storage.filename(name) in ('owner.json.gz', 'public.json.gz')
    and private.activity_owner(private.try_uuid((storage.foldername(name))[2])) = (select auth.uid()));

create policy streams_owner_update on storage.objects
  for update to authenticated
  using (bucket_id = 'streams'
         and (storage.foldername(name))[1] = (select auth.uid())::text)
  with check (
    bucket_id = 'streams'
    and (storage.foldername(name))[1] = (select auth.uid())::text
    and storage.filename(name) in ('owner.json.gz', 'public.json.gz')
    and private.activity_owner(private.try_uuid((storage.foldername(name))[2])) = (select auth.uid()));

create policy streams_owner_delete on storage.objects
  for delete to authenticated
  using (bucket_id = 'streams'
         and (storage.foldername(name))[1] = (select auth.uid())::text);

-- photos: the owner uploads into a folder of one of their own activities.
create policy photos_owner_insert on storage.objects
  for insert to authenticated
  with check (
    bucket_id = 'photos'
    and (storage.foldername(name))[1] = (select auth.uid())::text
    and private.activity_owner(private.try_uuid((storage.foldername(name))[2])) = (select auth.uid()));

create policy photos_owner_delete on storage.objects
  for delete to authenticated
  using (bucket_id = 'photos'
         and (storage.foldername(name))[1] = (select auth.uid())::text);

-- ---------------------------------------------------------------------------
-- Hidden places from the device
-- ---------------------------------------------------------------------------

-- Upsert by the device's own id. Changing a hidden place re-trims past
-- activities (privacy_zones_reapply), which in turn invalidates their public
-- streams files; the client then rebuilds them from list_stale_public_streams.
create or replace function public.upsert_privacy_zone(
  p_id       uuid,
  p_lat      double precision,
  p_lng      double precision,
  p_radius_m integer,
  p_label    text default null
)
returns public.privacy_zones
language plpgsql
set search_path = ''
as $$
declare
  v_uid uuid := auth.uid();
  v_row public.privacy_zones;
begin
  if v_uid is null then
    raise exception 'authentication required' using errcode = 'insufficient_privilege';
  end if;
  if p_id is null or p_lat not between -90 and 90 or p_lng not between -180 and 180 then
    raise exception 'id, lat and lng are required and must be in range'
      using errcode = 'invalid_parameter_value';
  end if;

  insert into public.privacy_zones as z (id, owner_id, label, center, radius_m)
  values (p_id, v_uid, nullif(left(p_label, 60), ''),
          extensions.st_setsrid(extensions.st_makepoint(p_lng, p_lat), 4326)::extensions.geography,
          p_radius_m)
  on conflict (id) do update set
    label    = excluded.label,
    center   = excluded.center,
    radius_m = excluded.radius_m
  where z.owner_id = v_uid
  returning * into v_row;

  if v_row.id is null then
    raise exception 'privacy zone id belongs to another account' using errcode = 'unique_violation';
  end if;
  return v_row;
end;
$$;

revoke execute on function public.upsert_privacy_zone(uuid, double precision, double precision, integer, text)
  from public, anon;
grant execute on function public.upsert_privacy_zone(uuid, double precision, double precision, integer, text)
  to authenticated;

do $$ begin perform private.assert_rls_everywhere(); end $$;
