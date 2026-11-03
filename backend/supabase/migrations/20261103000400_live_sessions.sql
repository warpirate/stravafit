-- SPDX-License-Identifier: AGPL-3.0-or-later
--
-- Live location sharing ("Beacon").
--
-- The athlete starts a session from the app and shares a link with a random
-- token. While recording, the app sends its position about every 30 s. Anyone
-- holding the link sees the athlete's last position and a short trail on a
-- web page served by the `live` Edge Function.
--
-- The link is the only key, so sessions are never readable through the REST
-- API by anyone but their owner: the viewer page reads them with the service
-- role through get_live_session_by_token, which only service_role may run.
-- Sessions expire (at most 48 h after the start) and the owner can end them
-- at any time; an ended or expired session returns no position at all, only
-- its state. The share link deliberately shows the exact position: that is
-- what the athlete chose to share, so hidden places do not apply here.

create table public.live_sessions (
  id              uuid primary key default gen_random_uuid(),
  owner_id        uuid not null references public.profiles (id) on delete cascade,
  token           text not null unique check (token ~ '^[A-Za-z0-9_-]{32}$'),
  sport           public.sport_type not null default 'Run',
  started_at      timestamptz not null default now(),
  ended_at        timestamptz,
  expires_at      timestamptz not null,
  last_lat        double precision check (last_lat between -90 and 90),
  last_lng        double precision check (last_lng between -180 and 180),
  last_accuracy_m real check (last_accuracy_m >= 0),
  last_speed_mps  real check (last_speed_mps >= 0),
  last_battery_pct smallint check (last_battery_pct between 0 and 100),
  last_at         timestamptz,
  -- Newest last: [[lat, lng, epoch_seconds], ...], at most 240 points
  -- (two hours at one point every 30 s).
  trail           jsonb not null default '[]'::jsonb check (jsonb_typeof(trail) = 'array'),
  created_at      timestamptz not null default now(),
  check (expires_at > started_at and expires_at <= started_at + interval '48 hours'),
  check (ended_at is null or ended_at >= started_at)
);

comment on table public.live_sessions is
  'Live location shares. Owner-only over REST; viewers read through the live Edge Function by token.';

create index live_sessions_owner_idx on public.live_sessions (owner_id, started_at desc);

alter table public.live_sessions enable row level security;
revoke all on table public.live_sessions from anon, authenticated;
grant all on table public.live_sessions to service_role;
grant select, delete on table public.live_sessions to authenticated;

create policy live_sessions_owner_select on public.live_sessions
  for select to authenticated using (owner_id = (select auth.uid()));
create policy live_sessions_owner_delete on public.live_sessions
  for delete to authenticated using (owner_id = (select auth.uid()));

-- ---------------------------------------------------------------------------
-- Owner RPCs
-- ---------------------------------------------------------------------------

-- Starts a session (and ends any other active one of the caller, so only one
-- link at a time shows a live position). Duration in minutes, 5 to 2880.
create or replace function public.start_live_session(
  p_sport            public.sport_type default 'Run',
  p_duration_minutes integer default 720
)
returns public.live_sessions
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_uid uuid := auth.uid();
  v_row public.live_sessions;
begin
  if v_uid is null then
    raise exception 'authentication required' using errcode = 'insufficient_privilege';
  end if;
  if p_duration_minutes is null or p_duration_minutes not between 5 and 2880 then
    raise exception 'duration must be between 5 minutes and 48 hours' using errcode = 'invalid_parameter_value';
  end if;
  if (select count(*) from public.live_sessions s
       where s.owner_id = v_uid and s.started_at > now() - interval '1 day') >= 20 then
    raise exception 'too many live sessions today' using errcode = 'program_limit_exceeded';
  end if;

  update public.live_sessions s set ended_at = now()
   where s.owner_id = v_uid and s.ended_at is null and s.expires_at > now();

  insert into public.live_sessions (owner_id, token, sport, expires_at)
  values (v_uid,
          translate(encode(extensions.gen_random_bytes(24), 'base64'), '+/', '-_'),
          coalesce(p_sport, 'Run'),
          now() + make_interval(mins => p_duration_minutes))
  returning * into v_row;
  return v_row;
end;
$$;

-- Records the latest position. Updates closer than 5 s apart are ignored
-- (returns false) so a misbehaving client cannot flood the table.
create or replace function public.update_live_position(
  p_session     uuid,
  p_lat         double precision,
  p_lng         double precision,
  p_accuracy_m  real default null,
  p_speed_mps   real default null,
  p_battery_pct smallint default null,
  p_recorded_at timestamptz default null
)
returns boolean
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_uid uuid := auth.uid();
  v_row public.live_sessions;
  v_at  timestamptz := least(coalesce(p_recorded_at, now()), now());
begin
  select * into v_row from public.live_sessions s
   where s.id = p_session and s.owner_id = v_uid
   for update;
  if v_row.id is null then
    raise exception 'live session not found' using errcode = 'no_data_found';
  end if;
  if v_row.ended_at is not null or v_row.expires_at <= now() then
    raise exception 'live session has ended' using errcode = 'object_not_in_prerequisite_state';
  end if;
  if p_lat is null or p_lng is null or p_lat not between -90 and 90 or p_lng not between -180 and 180 then
    raise exception 'lat and lng are required and must be in range' using errcode = 'invalid_parameter_value';
  end if;
  if v_row.last_at is not null and v_at < v_row.last_at + interval '5 seconds' then
    return false;
  end if;

  update public.live_sessions s set
    last_lat         = p_lat,
    last_lng         = p_lng,
    last_accuracy_m  = greatest(p_accuracy_m, 0),
    last_speed_mps   = greatest(p_speed_mps, 0),
    last_battery_pct = case when p_battery_pct between 0 and 100 then p_battery_pct end,
    last_at          = v_at,
    trail            = (select coalesce(jsonb_agg(e order by n), '[]'::jsonb)
                          from (select e, n
                                  from jsonb_array_elements(
                                         s.trail || jsonb_build_array(jsonb_build_array(
                                           round(p_lat::numeric, 6), round(p_lng::numeric, 6),
                                           extract(epoch from v_at)::bigint)))
                                       with ordinality as t(e, n)
                                 order by n desc
                                 limit 240) last_points)
   where s.id = p_session;
  return true;
end;
$$;

create or replace function public.end_live_session(p_session uuid)
returns boolean
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_found boolean;
begin
  update public.live_sessions s set ended_at = now()
   where s.id = p_session and s.owner_id = auth.uid() and s.ended_at is null;
  get diagnostics v_found = row_count;
  return v_found;
end;
$$;

-- ---------------------------------------------------------------------------
-- Viewer read (service role only: the live Edge Function)
-- ---------------------------------------------------------------------------

-- State is 'live', 'ended' or 'expired'. Position and trail are returned only
-- while live. The athlete's first name and avatar path come along so the page
-- can say whose position it is.
create or replace function public.get_live_session_by_token(p_token text)
returns jsonb
language sql
stable
security definer
set search_path = ''
as $$
  select jsonb_build_object(
           'state', case when s.ended_at is not null then 'ended'
                         when s.expires_at <= now() then 'expired'
                         else 'live' end,
           'sport', s.sport,
           'started_at', s.started_at,
           'ended_at', s.ended_at,
           'expires_at', s.expires_at,
           'athlete', jsonb_build_object('first_name', p.first_name, 'avatar_path', p.avatar_path),
           'position', case when s.ended_at is null and s.expires_at > now() and s.last_at is not null then
             jsonb_build_object('lat', s.last_lat, 'lng', s.last_lng,
                                'accuracy_m', s.last_accuracy_m, 'speed_mps', s.last_speed_mps,
                                'battery_pct', s.last_battery_pct, 'at', s.last_at) end,
           'trail', case when s.ended_at is null and s.expires_at > now() then s.trail
                         else '[]'::jsonb end,
           'server_time', now())
    from public.live_sessions s
    join public.profiles p on p.id = s.owner_id
   where p_token ~ '^[A-Za-z0-9_-]{32}$'
     and s.token = p_token;
$$;

revoke execute on function public.start_live_session(public.sport_type, integer),
  public.update_live_position(uuid, double precision, double precision, real, real, smallint, timestamptz),
  public.end_live_session(uuid) from public, anon;
grant execute on function public.start_live_session(public.sport_type, integer),
  public.update_live_position(uuid, double precision, double precision, real, real, smallint, timestamptz),
  public.end_live_session(uuid) to authenticated, service_role;

revoke execute on function public.get_live_session_by_token(text) from public, anon, authenticated;
grant execute on function public.get_live_session_by_token(text) to service_role;

do $$ begin perform private.assert_rls_everywhere(); end $$;
