-- SPDX-License-Identifier: AGPL-3.0-or-later
-- Device sync: idempotent activity upsert with laps/splits, defaults and
-- privacy on the device path, streams registration and invalidation, photos,
-- hidden places by client id, and deletes through the REST table.
begin;
select plan(30);

create temp table ids (k text primary key, id uuid) on commit drop;
grant select on ids to anon, authenticated;

insert into ids values
  ('owner',    tests.create_user('ds_owner')),
  ('follower', tests.create_user('ds_follower')),
  ('stranger', tests.create_user('ds_stranger')),
  ('client',   '11111111-1111-4111-8111-111111111111');

insert into public.follows (follower_id, followee_id, status, accepted_at)
select (select id from ids where k = 'follower'), (select id from ids where k = 'owner'), 'accepted', now();

create temp table payload (p jsonb) on commit drop;
grant select on payload to authenticated;
insert into payload values (jsonb_build_object(
  'client_uuid', '11111111-1111-4111-8111-111111111111',
  'name', 'Morning Run', 'sport_type', 'Run',
  'start_time', '2026-10-20T06:30:00Z', 'tz_name', 'Europe/Berlin', 'utc_offset_s', 3600,
  'elapsed_s', 1800, 'moving_s', 1750, 'distance_m', 4177, 'elev_gain_m', 20,
  'avg_speed_mps', 2.4, 'max_speed_mps', 4.0, 'avg_hr', 150, 'max_hr', 172,
  'polyline', extensions.st_asencodedpolyline(tests.park_loop()::extensions.geometry, 5),
  'laps', jsonb_build_array(
    jsonb_build_object('lap_index', 0, 'trigger', 'DISTANCE', 'start_offset_s', 0, 'elapsed_s', 900,
                       'moving_s', 880, 'distance_m', 2000),
    jsonb_build_object('lap_index', 1, 'trigger', 'SESSION_END', 'start_offset_s', 900, 'elapsed_s', 900,
                       'moving_s', 870, 'distance_m', 2177)),
  'splits', jsonb_build_array(
    jsonb_build_object('unit', 'METRIC', 'split_index', 0, 'distance_m', 1000, 'elapsed_s', 410, 'moving_s', 400),
    jsonb_build_object('unit', 'IMPERIAL', 'split_index', 0, 'distance_m', 1609, 'elapsed_s', 660, 'moving_s', 650))));

create temp table results (k text primary key, r jsonb) on commit drop;
grant select, insert on results to authenticated;

-- First sync creates; the second with the same client_uuid updates.
select tests.authenticate_as((select id from ids where k = 'owner'));
insert into results select 'first', public.upsert_activity_from_device(p) from payload;
select ok((select (r ->> 'created')::boolean from results where k = 'first'), 'first sync creates the activity');

insert into results select 'second', public.upsert_activity_from_device(
  p || '{"name": "Morning Run (edited)", "laps": [{"lap_index": 0, "trigger": "MANUAL", "start_offset_s": 0,
          "elapsed_s": 1800, "moving_s": 1750, "distance_m": 4177}]}') from payload;
select ok(not (select (r ->> 'created')::boolean from results where k = 'second'), 'resync updates instead of duplicating');
select is((select r ->> 'id' from results where k = 'second'), (select r ->> 'id' from results where k = 'first'),
  'resync returns the same activity id');
select is((select count(*)::int from public.activities), 1, 'exactly one activity exists');
select is((select name from public.activities), 'Morning Run (edited)', 'edits from the device are applied');
select is((select count(*)::int from public.laps), 1, 'laps are replaced by the device list');
select results_eq($$select unit::text from public.splits order by unit$$, array['km', 'mi'],
  'METRIC/IMPERIAL splits map to km/mi');
select is((select visibility from public.activities), 'followers'::public.activity_visibility,
  'absent visibility takes the account default');
select is((select published_at from public.activities), timestamptz '2026-10-20T07:00:00Z',
  'feed time is when the activity ended, not the sync time');
select ok((select route_public is not null and summary_polyline_public is not null from public.activities),
  'the privacy-trimmed public route is derived on the device path too');
select is((select avg_hr from public.activities), 150::real, 'heart rate is kept with consent');
select ok((select (r -> 'public_stream' ->> 'trim_start_m')::numeric > 200 from results where k = 'second'),
  'the public stream spec trims at least the hidden start distance');
select is((select r -> 'public_stream' -> 'drop_channels' from results where k = 'second'), '[]'::jsonb,
  'nothing to drop while no stat is hidden');

-- Invalid payloads are rejected with clear errors.
select throws_ok($$select public.upsert_activity_from_device('{"client_uuid": "11111111-1111-4111-8111-111111111112", "start_time": "2026-10-20T06:30:00Z", "elapsed_s": 10, "moving_s": 10}')$$,
  '22023', null, 'sport_type is required');
select throws_ok($$select public.upsert_activity_from_device('{"client_uuid": "nope", "sport_type": "Run"}')$$,
  '22023', null, 'client_uuid must be a uuid');

-- Streams: registering before the upload fails; after it, viewers get the public file.
select throws_ok(format('select public.register_activity_streams(%L, 1800, %L)',
                        (select r ->> 'id' from results where k = 'first'), '{time,latlng,heartrate}'),
  '55000', null, 'streams cannot be registered before owner.json.gz is uploaded');

select tests.as_backend();
insert into storage.objects (bucket_id, name)
select 'streams', (select id::text from ids where k = 'owner') || '/' || (r ->> 'id') || '/' || f
  from results, unnest(array['owner.json.gz', 'public.json.gz']) f where k = 'first';
update storage.objects set created_at = now() - interval '1 minute', updated_at = now() - interval '1 minute'
 where bucket_id = 'streams';

select tests.authenticate_as((select id from ids where k = 'owner'));
insert into results select 'reg', public.register_activity_streams(
  (select (r ->> 'id')::uuid from results where k = 'first'), 1800, '{time,latlng,heartrate}');
select ok((select (r ->> 'public_registered')::boolean from results where k = 'reg'), 'fresh public file is registered');

select tests.authenticate_as((select id from ids where k = 'follower'));
select is((select path from public.activity_stream_files),
  (select (select id::text from ids where k = 'owner') || '/' || (r ->> 'id') || '/public.json.gz' from results where k = 'first'),
  'a follower is served the public streams file');
select is((select stream_types from public.activity_stream_files), '{time,latlng,heartrate}'::text[],
  'channel list is complete while nothing is hidden');

-- Hiding heart rate from the device invalidates the public file at once.
select tests.authenticate_as((select id from ids where k = 'owner'));
insert into results select 'hide', public.upsert_activity_from_device(p || '{"hide_heart_rate": true}') from payload;
select ok((select r -> 'public_stream' -> 'drop_channels' ? 'heartrate' from results where k = 'hide'),
  'hidden heart rate is dropped from the public stream spec');
select is((select count(*)::int from public.list_stale_public_streams()), 1,
  'the activity is listed for a public stream rebuild');
select tests.authenticate_as((select id from ids where k = 'follower'));
select is((select count(*)::int from public.activity_stream_files), 0,
  'viewers lose the stale public file until it is rebuilt');
select tests.authenticate_as((select id from ids where k = 'owner'));
select ok(not (public.register_activity_streams(
  (select (r ->> 'id')::uuid from results where k = 'first'), 1800, '{time,latlng}') ->> 'public_registered')::boolean,
  'a public file uploaded before the privacy change is not registered again');

-- Photos: canonical path and an uploaded file are required.
select tests.as_backend();
insert into storage.objects (bucket_id, name)
select 'photos', (select id::text from ids where k = 'owner') || '/' || (r ->> 'id') || '/22222222-2222-4222-8222-222222222222.jpg'
  from results where k = 'first';
select tests.authenticate_as((select id from ids where k = 'owner'));
select lives_ok(format($$insert into public.activity_photos (id, activity_id, storage_path, width, height)
  values ('22222222-2222-4222-8222-222222222222', %L, %L, 1600, 1200)$$,
  (select r ->> 'id' from results where k = 'first'),
  (select (select id::text from ids where k = 'owner') || '/' || (r ->> 'id') || '/22222222-2222-4222-8222-222222222222.jpg' from results where k = 'first')),
  'the owner records an uploaded photo');
select throws_ok(format($$insert into public.activity_photos (id, activity_id, storage_path)
  values ('33333333-3333-4333-8333-333333333333', %L, 'somewhere/else.jpg')$$,
  (select r ->> 'id' from results where k = 'first')),
  '23514', null, 'photo rows must use the canonical storage path');
select tests.authenticate_as((select id from ids where k = 'stranger'));
select is((select count(*)::int from public.activity_photos), 0,
  'strangers cannot see photos of a followers-only activity');
select tests.authenticate_as((select id from ids where k = 'follower'));
select is((select count(*)::int from public.activity_photos), 1, 'followers see the photos');

-- Hidden places upsert by the device's id and cannot be taken over.
select tests.authenticate_as((select id from ids where k = 'owner'));
select public.upsert_privacy_zone('44444444-4444-4444-8444-444444444444', 52.5145, 13.3501, 400, 'Home');
select public.upsert_privacy_zone('44444444-4444-4444-8444-444444444444', 52.5145, 13.3501, 800, 'Home');
select is((select radius_m from public.privacy_zones), 800, 'a hidden place is updated by its client id');
select tests.authenticate_as((select id from ids where k = 'stranger'));
select throws_ok($$select public.upsert_privacy_zone('44444444-4444-4444-8444-444444444444', 0, 0, 400)$$,
  null, null, 'another account cannot overwrite a hidden place by id');

-- Deleting from the device is a plain REST delete by client_uuid (owner-only
-- policy, children cascade). Checked by privilege and policy here because the
-- hosted-project tooling refuses to run DELETE statements, even rolled back.
select ok(has_table_privilege('authenticated', 'public.activities', 'DELETE')
          and exists (select 1 from pg_policies where tablename = 'activities'
                         and policyname = 'activities_owner_delete' and cmd = 'DELETE'),
  'owners can delete their activities through the REST API');

select * from finish();
rollback;
