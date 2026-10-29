-- SPDX-License-Identifier: AGPL-3.0-or-later
-- Hidden stats, private notes and map privacy never reach non-owners.
begin;
select plan(28);

create temp table ids (k text primary key, id uuid) on commit drop;
grant select on ids to anon, authenticated;

insert into ids values
  ('owner',    tests.create_user('hs_owner')),
  ('follower', tests.create_user('hs_follower')),
  ('stranger', tests.create_user('hs_stranger'));

insert into public.follows (follower_id, followee_id, status, accepted_at)
select (select id from ids where k = 'follower'), (select id from ids where k = 'owner'), 'accepted', now();

insert into public.gear (id, owner_id, kind, name)
select '9e000000-0000-4000-8000-000000000001', id, 'shoe', 'Trainers' from ids where k = 'owner';

insert into ids values ('hidden', tests.create_activity(
  (select id from ids where k = 'owner'), 'everyone', 'Hidden stats run',
  '{"hide_heart_rate": true, "hide_power": true, "hide_calories": true,
    "hide_start_time": true, "hide_elevation": true, "hide_pace": true,
    "gear_id": "9e000000-0000-4000-8000-000000000001"}'));
insert into ids values ('open', tests.create_activity(
  (select id from ids where k = 'owner'), 'everyone', 'Open run',
  '{"gear_id": "9e000000-0000-4000-8000-000000000001"}'));
insert into ids values ('nomap', tests.create_activity(
  (select id from ids where k = 'owner'), 'everyone', 'No map run', '{"hide_map": true}'));

insert into public.laps (activity_id, lap_index, start_offset_s, elapsed_s, moving_s, distance_m,
  avg_hr, max_hr, avg_watts, avg_speed_mps, elev_gain_m)
select id, 0, 0, 600, 590, 1000, 150, 170, 250, 2.5, 10 from ids where k = 'hidden';
insert into public.splits (activity_id, unit, split_index, distance_m, elapsed_s, moving_s,
  avg_hr, avg_speed_mps, avg_watts)
select id, 'km', 0, 1000, 400, 390, 151, 2.5, 245 from ids where k = 'hidden';
insert into public.activity_private_notes (activity_id, body)
select id, 'secret note' from ids where k = 'hidden';
insert into public.activity_streams (activity_id, owner_path, public_path, stream_types)
select id, 'o/owner.sfstream', 'o/public.sfstream',
       array['time', 'latlng', 'heartrate', 'watts', 'altitude', 'velocity_smooth']
  from ids where k = 'hidden';

-- Owner sees everything.
select tests.authenticate_as((select id from ids where k = 'owner'));
select is((select avg_hr from public.activity_cards where id = (select id from ids where k = 'hidden')),
  150::real, 'owner sees own hidden heart rate');
select isnt((select start_time from public.activity_cards where id = (select id from ids where k = 'hidden')),
  null, 'owner sees own hidden start time');
select is((select body from public.activity_private_notes), 'secret note', 'owner reads private notes');
select is((select avg_hr from public.activity_laps where activity_id = (select id from ids where k = 'hidden')),
  150::real, 'owner sees lap heart rate');

-- Follower: hidden fields are NULL, visible fields are present.
select tests.authenticate_as((select id from ids where k = 'follower'));
select results_eq(
  $$select avg_hr, max_hr, effort_score, avg_watts, kilojoules, calories,
           elev_gain_m, avg_speed_mps, start_time, tz_name
      from public.activity_cards where id = (select id from ids where k = 'hidden')$$,
  $$values (null::real, null::smallint, null::real, null::real, null::real, null::real,
            null::real, null::real, null::timestamptz, null::text)$$,
  'follower receives NULL for every hidden stat');
select is((select distance_m from public.activity_cards where id = (select id from ids where k = 'hidden')),
  4177::double precision, 'follower still sees distance');
select is((select start_date_local from public.activity_cards where id = (select id from ids where k = 'hidden')),
  date '2026-10-20', 'follower still sees the activity date');
select is((select avg_hr from public.activity_cards where id = (select id from ids where k = 'open')),
  150::real, 'follower sees heart rate when not hidden');
select isnt((select start_time from public.activity_cards where id = (select id from ids where k = 'open')),
  null, 'follower sees start time when not hidden');
select isnt((select gear_id from public.activity_cards where id = (select id from ids where k = 'open')),
  null, 'follower sees gear');
select results_eq(
  $$select avg_hr, max_hr, avg_watts, avg_speed_mps, elev_gain_m from public.activity_laps
     where activity_id = (select id from ids where k = 'hidden')$$,
  $$values (null::real, null::smallint, null::real, null::real, null::real)$$,
  'follower receives NULL hidden lap stats');
select results_eq(
  $$select avg_hr, avg_speed_mps, avg_watts from public.activity_splits
     where activity_id = (select id from ids where k = 'hidden')$$,
  $$values (null::real, null::real, null::real)$$,
  'follower receives NULL hidden split stats');
select is((select count(*)::int from public.activity_private_notes), 0, 'follower cannot read private notes');
select results_eq(
  $$select path, stream_types from public.activity_stream_files
     where activity_id = (select id from ids where k = 'hidden')$$,
  $$values ('o/public.sfstream'::text, array['time', 'latlng']::text[])$$,
  'follower gets the public stream file without hidden channels');
select is((select count(*)::int from public.activity_streams), 0, 'follower cannot read stream base rows');

-- Stranger: start time and gear are never shown, even on everyone activities.
select tests.authenticate_as((select id from ids where k = 'stranger'));
select is((select start_time from public.activity_cards where id = (select id from ids where k = 'open')),
  null, 'stranger never sees start time');
select is((select gear_id from public.activity_cards where id = (select id from ids where k = 'open')),
  null, 'stranger never sees gear');
select is((select count(*)::int from public.gear), 0, 'stranger cannot read gear rows');
select is((select avg_hr from public.activity_cards where id = (select id from ids where k = 'open')),
  150::real, 'stranger sees heart rate that is not hidden');

-- Map privacy: viewers get the trimmed route, never the owner's.
select ok(
  (select extensions.st_length(route) from public.activity_cards
    where id = (select id from ids where k = 'open'))
  < (select extensions.st_length(tests.park_loop())) - 390,
  'viewer route is trimmed by at least ~400 m (200 m each end)');
select ok(
  (select extensions.st_distance(start_point, extensions.st_startpoint(tests.park_loop()::extensions.geometry)::extensions.geography)
     from public.activity_cards where id = (select id from ids where k = 'open')) >= 199,
  'viewer start point is at least 200 m from the real start');
select is((select route from public.activity_cards where id = (select id from ids where k = 'nomap')),
  null, 'hide_map removes the route for viewers');
select is((select summary_polyline from public.activity_cards where id = (select id from ids where k = 'nomap')),
  null, 'hide_map removes the polyline for viewers');

-- A hidden place around the start pushes the public start out of the zone,
-- retroactively, for activities that were not edited individually.
select tests.authenticate_as((select id from ids where k = 'owner'));
insert into public.privacy_zones (owner_id, center, radius_m)
select (select id from ids where k = 'owner'), 'SRID=4326;POINT(13.3501 52.5145)', 600;
select is((select route is not null from public.activity_cards where id = (select id from ids where k = 'nomap')),
  true, 'owner still sees the full route with hide_map');

select tests.authenticate_as((select id from ids where k = 'stranger'));
select ok(
  (select extensions.st_distance(start_point, 'SRID=4326;POINT(13.3501 52.5145)'::extensions.geography)
     from public.activity_cards where id = (select id from ids where k = 'open')) >= 600,
  'public start point lies outside the hidden place');
select ok(
  (select extensions.st_distance(end_point, 'SRID=4326;POINT(13.3501 52.5145)'::extensions.geography)
     from public.activity_cards where id = (select id from ids where k = 'open')) >= 600,
  'public end point lies outside the hidden place');
select ok(
  not extensions.st_dwithin(
    (select route from public.activity_cards where id = (select id from ids where k = 'open')),
    'SRID=4326;POINT(13.3501 52.5145)'::extensions.geography, 590),
  'no public route vertex lies inside the hidden place');

-- Owner edits map privacy on one activity: later zone changes skip it.
select tests.authenticate_as((select id from ids where k = 'owner'));
update public.activities set hide_start_m = 0, hide_end_m = 0
 where id = (select id from ids where k = 'hidden');
select is((select map_privacy_overridden from public.activities where id = (select id from ids where k = 'hidden')),
  true, 'editing map privacy marks the activity as individually edited');

select * from finish();
rollback;
