-- SPDX-License-Identifier: AGPL-3.0-or-later
--
-- Shared pgTAP helpers. This file runs first (files run in name order) and
-- COMMITS, so later test files can use the `tests` schema. Every other test
-- file runs inside a transaction that is rolled back.

begin;

create extension if not exists pgtap with schema extensions;

create schema if not exists tests;
grant usage on schema tests to anon, authenticated, service_role;

-- Creates an auth user (the auth trigger creates the profile) and returns its id.
create or replace function tests.create_user(
  p_handle         text,
  p_birthday       date default date '1990-01-01',
  p_country        text default 'US',
  p_health_consent boolean default true
)
returns uuid
language plpgsql
as $$
declare
  v_id uuid := gen_random_uuid();
begin
  insert into auth.users (id, email, raw_user_meta_data)
  values (v_id, p_handle || '@test.invalid',
          jsonb_build_object('handle', p_handle, 'first_name', initcap(p_handle),
                             'birthday', p_birthday, 'country', p_country));
  update public.profile_settings set health_data_consent = p_health_consent
   where user_id = v_id;
  return v_id;
end;
$$;

-- Default test route: a ~4.2 km loop in Berlin's Tiergarten.
create or replace function tests.park_loop()
returns extensions.geography
language sql
immutable
as $$
  select 'SRID=4326;LINESTRING(13.3501 52.5145,13.3560 52.5150,13.3620 52.5155,13.3680 52.5158,13.3740 52.5161,13.3777 52.5163,13.3770 52.5135,13.3700 52.5120,13.3600 52.5118,13.3510 52.5125,13.3501 52.5140)'::extensions.geography;
$$;

-- Inserts an activity with full metrics as the backend; returns its id.
create or replace function tests.create_activity(
  p_owner      uuid,
  p_visibility public.activity_visibility,
  p_name       text,
  p_overrides  jsonb default '{}'::jsonb
)
returns uuid
language plpgsql
as $$
declare
  v_id uuid;
  v_row public.activities;
begin
  v_row := jsonb_populate_record(null::public.activities, jsonb_build_object(
    'owner_id', p_owner, 'client_uuid', gen_random_uuid(), 'name', p_name,
    'sport_type', 'Run', 'visibility', p_visibility,
    'start_time', '2026-10-20T06:30:00Z', 'utc_offset_s', 3600, 'tz_name', 'Europe/Berlin',
    'elapsed_s', 1800, 'moving_s', 1750, 'distance_m', 4177,
    'elev_gain_m', 20, 'elev_loss_m', 20, 'avg_speed_mps', 2.4, 'max_speed_mps', 4.0,
    'avg_hr', 150, 'max_hr', 172, 'avg_watts', 240, 'max_watts', 400, 'kilojoules', 430,
    'calories', 400, 'effort_score', 55,
    'route_simplified', tests.park_loop()::text,
    'published_at', now()) || p_overrides);

  insert into public.activities (owner_id, client_uuid, name, sport_type, visibility,
    start_time, utc_offset_s, tz_name, elapsed_s, moving_s, distance_m, elev_gain_m,
    elev_loss_m, avg_speed_mps, max_speed_mps, avg_hr, max_hr, avg_watts, max_watts,
    kilojoules, calories, effort_score, route_simplified, published_at, muted,
    hide_start_m, hide_end_m, hide_map, hide_heart_rate, hide_pace, hide_power,
    hide_calories, hide_start_time, hide_elevation, gear_id)
  values (v_row.owner_id, v_row.client_uuid, v_row.name, v_row.sport_type, v_row.visibility,
    v_row.start_time, v_row.utc_offset_s, v_row.tz_name, v_row.elapsed_s, v_row.moving_s,
    v_row.distance_m, v_row.elev_gain_m, v_row.elev_loss_m, v_row.avg_speed_mps,
    v_row.max_speed_mps, v_row.avg_hr, v_row.max_hr, v_row.avg_watts, v_row.max_watts,
    v_row.kilojoules, v_row.calories, v_row.effort_score, v_row.route_simplified,
    v_row.published_at, coalesce(v_row.muted, false),
    v_row.hide_start_m, v_row.hide_end_m, v_row.hide_map, v_row.hide_heart_rate,
    v_row.hide_pace, v_row.hide_power, v_row.hide_calories, v_row.hide_start_time,
    v_row.hide_elevation, v_row.gear_id)
  returning id into v_id;
  return v_id;
end;
$$;

-- Act as an end user (what PostgREST does for a request with that JWT).
create or replace function tests.authenticate_as(p_user uuid)
returns void
language plpgsql
as $$
begin
  perform set_config('request.jwt.claims',
    json_build_object('sub', p_user, 'role', 'authenticated')::text, true);
  perform set_config('role', 'authenticated', true);
end;
$$;

create or replace function tests.authenticate_anon()
returns void
language plpgsql
as $$
begin
  perform set_config('request.jwt.claims', json_build_object('role', 'anon')::text, true);
  perform set_config('role', 'anon', true);
end;
$$;

-- Back to the superuser session (fixtures, backend-only assertions).
create or replace function tests.as_backend()
returns void
language plpgsql
as $$
begin
  perform set_config('role', 'none', true);
  perform set_config('request.jwt.claims', '', true);
end;
$$;

-- Job queue inspection that works for both backends (pgmq and the table fallback).
create or replace function tests.job_count(p_queue text, p_key text default null, p_value text default null)
returns integer
language plpgsql
as $$
declare
  v_count integer;
begin
  if private.job_backend() = 'pgmq' then
    execute format('select count(*) from pgmq.%I where $1 is null or message ->> $1 = $2', 'q_' || p_queue)
      into v_count using p_key, p_value;
  else
    select count(*) into v_count from private.job_queue j
     where j.queue = p_queue and (p_key is null or j.payload ->> p_key = p_value);
  end if;
  return v_count;
end;
$$;

create or replace function tests.job_payloads(p_queue text)
returns setof jsonb
language plpgsql
as $$
begin
  if private.job_backend() = 'pgmq' then
    return query execute format('select message from pgmq.%I order by msg_id', 'q_' || p_queue);
  else
    return query select j.payload from private.job_queue j where j.queue = p_queue order by j.msg_id;
  end if;
end;
$$;

create or replace function tests.job_visible_at(p_queue text, p_msg_id bigint)
returns timestamptz
language plpgsql
as $$
declare
  v_at timestamptz;
begin
  if private.job_backend() = 'pgmq' then
    execute format('select vt from pgmq.%I where msg_id = $1', 'q_' || p_queue) into v_at using p_msg_id;
  else
    select j.visible_at into v_at from private.job_queue j where j.msg_id = p_msg_id;
  end if;
  return v_at;
end;
$$;

create or replace function tests.job_make_visible(p_queue text, p_msg_id bigint)
returns void
language plpgsql
as $$
begin
  if private.job_backend() = 'pgmq' then
    execute format('update pgmq.%I set vt = now() where msg_id = $1', 'q_' || p_queue) using p_msg_id;
  else
    update private.job_queue set visible_at = now() where msg_id = p_msg_id;
  end if;
end;
$$;

grant execute on all functions in schema tests to anon, authenticated, service_role;

select plan(1);
select pass('test helpers installed');
select * from finish();

commit;
