-- SPDX-License-Identifier: AGPL-3.0-or-later
-- Endpoint-hiding algorithm (privacy-safety §B), timestamps that would reveal a
-- hidden start time, split/lap times under hidden pace, health-data consent on
-- every write, and minor defaults for crossings.
begin;
select plan(24);

-- A fixed jitter secret makes every trim in this file deterministic.
update private.instance_secrets set value = decode(repeat('5a', 32), 'hex')
 where name = 'privacy_trim_jitter';

create temp table ids (k text primary key, id uuid) on commit drop;
grant select on ids to anon, authenticated;

insert into ids values
  ('owner',     tests.create_user('ph_owner')),
  ('follower',  tests.create_user('ph_follower')),
  ('stranger',  tests.create_user('ph_stranger')),
  ('noconsent', tests.create_user('ph_noconsent', p_health_consent => false)),
  ('minor',     tests.create_user('ph_minor', date '2012-01-01'));

insert into public.follows (follower_id, followee_id, status, accepted_at)
select (select id from ids where k = 'follower'), (select id from ids where k = 'owner'), 'accepted', now();

-- ---------------------------------------------------------------------------
-- Jitter
-- ---------------------------------------------------------------------------

select ok(
  (select bool_and(private.trim_jitter(g, 'start') >= 0 and private.trim_jitter(g, 'start') < 1)
     from (select gen_random_uuid() as g from generate_series(1, 200)) x),
  'jitter is always in [0, 1)');
select is(
  private.trim_jitter('00000000-0000-4000-8000-000000000001', 'start'),
  private.trim_jitter('00000000-0000-4000-8000-000000000001', 'start'),
  'jitter is deterministic per activity');
select isnt(
  private.trim_jitter('00000000-0000-4000-8000-000000000001', 'start'),
  private.trim_jitter('00000000-0000-4000-8000-000000000001', 'end'),
  'start and end use independent jitter');
select ok(
  (select count(distinct round(private.trim_jitter(g, 'start')::numeric, 3))
     from (select gen_random_uuid() as g from generate_series(1, 200)) x) > 150,
  'jitter varies between activities');

-- ---------------------------------------------------------------------------
-- Endpoint trimming with jitter: removed length is trim .. trim * 1.25 per end
-- ---------------------------------------------------------------------------

insert into ids values ('loop', tests.create_activity(
  (select id from ids where k = 'owner'), 'everyone', 'Jittered loop'));

select ok(
  (select extensions.st_length(route_simplified) - extensions.st_length(route_public)
     from public.activities where id = (select id from ids where k = 'loop'))
  between 399 and 501,
  'public route is shorter by 400..500 m with 200 m trims and up to 25 % jitter');
select ok(
  (select abs(extensions.st_length(route_simplified) - extensions.st_length(route_public)
              - 200 * (2 + 0.25 * private.trim_jitter(id, 'start') + 0.25 * private.trim_jitter(id, 'end')))
     from public.activities where id = (select id from ids where k = 'loop')) < 2,
  'removed length matches the jittered trims');

-- ---------------------------------------------------------------------------
-- Short routes: trimming that leaves < 500 m hides the whole map
-- ---------------------------------------------------------------------------

-- ~900 m straight line along a parallel.
insert into ids values ('short', tests.create_activity(
  (select id from ids where k = 'owner'), 'everyone', 'Short trimmed',
  '{"route_simplified": "SRID=4326;LINESTRING(13.3500 52.5200,13.3633 52.5200)"}'));
insert into ids values ('short_open', tests.create_activity(
  (select id from ids where k = 'owner'), 'everyone', 'Short untrimmed',
  '{"route_simplified": "SRID=4326;LINESTRING(13.3500 52.5200,13.3633 52.5200)",
    "hide_start_m": 0, "hide_end_m": 0}'));

select is((select route_public from public.activities where id = (select id from ids where k = 'short')),
  null, 'a 900 m route trimmed by 2 x 200 m keeps no public map');
select isnt((select route_public from public.activities where id = (select id from ids where k = 'short_open')),
  null, 'a short route is shown when the owner trims nothing');

-- ---------------------------------------------------------------------------
-- Hidden place passed early in the route (not at the endpoint)
-- ---------------------------------------------------------------------------

-- The loop passes (13.3560 52.5150) about 410 m after the start, well inside
-- the first 20 %. A 200 m hidden place there must push the public start past it.
insert into public.privacy_zones (owner_id, center, radius_m)
select id, 'SRID=4326;POINT(13.3560 52.5150)', 200 from ids where k = 'owner';

select ok(
  not extensions.st_dwithin(
    (select route_public from public.activities where id = (select id from ids where k = 'loop')),
    'SRID=4326;POINT(13.3560 52.5150)'::extensions.geography, 200),
  'no public route point lies within a hidden place passed in the first 20 %');
select ok(
  (select extensions.st_distance(start_point_public, 'SRID=4326;POINT(13.3560 52.5150)'::extensions.geography)
     from public.activities where id = (select id from ids where k = 'loop'))
  >= 200 * (1 + 0.25 * private.trim_jitter((select id from ids where k = 'loop'), 'start')) - 10,
  'the public start lies beyond the jittered radius, not on the circle');

-- ---------------------------------------------------------------------------
-- Timestamps never date an activity whose start time the viewer may not see
-- ---------------------------------------------------------------------------

insert into ids values ('timed', tests.create_activity(
  (select id from ids where k = 'owner'), 'everyone', 'Timed run',
  '{"published_at": "2026-10-20T07:05:42Z"}'));
insert into ids values ('untimed', tests.create_activity(
  (select id from ids where k = 'owner'), 'everyone', 'Untimed run',
  '{"published_at": "2026-10-20T07:05:42Z", "hide_start_time": true, "hide_pace": true}'));
insert into public.splits (activity_id, unit, split_index, distance_m, elapsed_s, moving_s, avg_speed_mps)
select id, 'km', 0, 1000, 300, 298, 3.3 from ids where k in ('timed', 'untimed');
insert into public.laps (activity_id, lap_index, start_offset_s, elapsed_s, moving_s, distance_m)
select id, 0, 0, 600, 590, 2000 from ids where k in ('timed', 'untimed');

select tests.authenticate_as((select id from ids where k = 'stranger'));
select results_eq(
  $$select published_at, created_at, updated_at from public.activity_cards
     where id = (select id from ids where k = 'timed')$$,
  $$values ('2026-10-20T00:00:00Z'::timestamptz, null::timestamptz, null::timestamptz)$$,
  'stranger gets only the publish day and no created/updated time');

select tests.authenticate_as((select id from ids where k = 'follower'));
select is((select published_at from public.activity_cards where id = (select id from ids where k = 'timed')),
  '2026-10-20T07:05:42Z'::timestamptz, 'follower gets the exact publish time when start time is shown');
select isnt((select created_at from public.activity_cards where id = (select id from ids where k = 'timed')),
  null, 'follower gets created_at when start time is shown');
select results_eq(
  $$select published_at, created_at, updated_at from public.activity_cards
     where id = (select id from ids where k = 'untimed')$$,
  $$values ('2026-10-20T00:00:00Z'::timestamptz, null::timestamptz, null::timestamptz)$$,
  'follower gets only the publish day when the start time is hidden');
select is(
  (select count(*)::int from public.get_home_feed() where id in (select id from ids where k in ('timed', 'untimed'))),
  2, 'the feed still pages over activities with a coarse publish time');

-- Split and lap times are pace in disguise.
select results_eq(
  $$select elapsed_s, moving_s from public.activity_splits
     where activity_id = (select id from ids where k = 'untimed')$$,
  $$values (null::integer, null::integer)$$,
  'follower gets no split times when pace is hidden');
select results_eq(
  $$select start_offset_s, elapsed_s, moving_s from public.activity_laps
     where activity_id = (select id from ids where k = 'untimed')$$,
  $$values (null::integer, null::integer, null::integer)$$,
  'follower gets no lap times when pace is hidden');
select results_eq(
  $$select elapsed_s, moving_s from public.activity_splits
     where activity_id = (select id from ids where k = 'timed')$$,
  $$values (300, 298)$$,
  'follower gets split times when pace is shown');

select tests.authenticate_as((select id from ids where k = 'owner'));
select results_eq(
  $$select s.elapsed_s, c.published_at from public.activity_splits s
      join public.activity_cards c on c.id = s.activity_id
     where s.activity_id = (select id from ids where k = 'untimed')$$,
  $$values (300, '2026-10-20T07:05:42Z'::timestamptz)$$,
  'owner always gets split times and the exact publish time');

-- ---------------------------------------------------------------------------
-- Health-data consent applies to every write, including laps and splits
-- ---------------------------------------------------------------------------

select tests.as_backend();
insert into ids values ('nc', tests.create_activity(
  (select id from ids where k = 'noconsent'), 'everyone', 'No consent run'));
insert into public.laps (activity_id, lap_index, start_offset_s, elapsed_s, moving_s, distance_m, avg_hr, max_hr)
select id, 0, 0, 600, 590, 2000, 150, 170 from ids where k = 'nc';
insert into public.splits (activity_id, unit, split_index, distance_m, elapsed_s, moving_s, avg_hr)
select id, 'km', 0, 1000, 300, 298, 151 from ids where k = 'nc';
update public.activities set avg_hr = 149, max_hr = 171 where id = (select id from ids where k = 'nc');

select results_eq(
  $$select avg_hr, max_hr from public.activities where id = (select id from ids where k = 'nc')$$,
  $$values (null::real, null::smallint)$$,
  'a later write cannot store heart rate without consent');
select results_eq(
  $$select avg_hr, max_hr from public.laps where activity_id = (select id from ids where k = 'nc')$$,
  $$values (null::real, null::smallint)$$,
  'lap heart rate is dropped without consent');
select is((select avg_hr from public.splits where activity_id = (select id from ids where k = 'nc')),
  null::real, 'split heart rate is dropped without consent');

-- ---------------------------------------------------------------------------
-- Minors start with crossings set to "no one"
-- ---------------------------------------------------------------------------

select is((select crossings_audience from public.profile_settings where user_id = (select id from ids where k = 'minor')),
  'no_one'::public.audience, 'a minor starts with crossings visible to no one');
select is((select crossings_audience from public.profile_settings where user_id = (select id from ids where k = 'owner')),
  'followers'::public.audience, 'an adult starts with crossings visible to followers');

select * from finish();
rollback;
